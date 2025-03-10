package com.tom.storagemod.gui;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;

import net.minecraftforge.fml.ModList;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import com.tom.storagemod.StoredItemStack;
import com.tom.storagemod.StoredItemStack.ComparatorAmount;
import com.tom.storagemod.StoredItemStack.IStoredItemStackComparator;
import com.tom.storagemod.StoredItemStack.SortingTypes;
import com.tom.storagemod.gui.ContainerStorageTerminal.SlotAction;
import com.tom.storagemod.jei.JEIHandler;
import com.tom.storagemod.network.IDataReceiver;

public abstract class GuiStorageTerminalBase<T extends ContainerStorageTerminal> extends ContainerScreen<T> implements IDataReceiver {
	private static final LoadingCache<StoredItemStack, List<String>> tooltipCache = CacheBuilder.newBuilder().expireAfterAccess(5, TimeUnit.SECONDS).build(new CacheLoader<StoredItemStack, List<String>>() {

		@Override
		public List<String> load(StoredItemStack key) throws Exception {
			return key.getStack().getTooltipLines(Minecraft.getInstance().player, getTooltipFlag()).stream().map(ITextComponent::getString).collect(Collectors.toList());
		}

	});
	protected Minecraft mc = Minecraft.getInstance();

	/** Amount scrolled in Creative mode inventory (0 = top, 1 = bottom) */
	protected float currentScroll;
	/** True if the scrollbar is being dragged */
	protected boolean isScrolling;
	/**
	 * True if the left mouse button was held down last time drawScreen was
	 * called.
	 */
	private boolean refreshItemList;
	protected boolean wasClicking;
	protected TextFieldWidget searchField;
	protected int slotIDUnderMouse = -1, controllMode, rowCount, searchType;
	private String searchLast = "";
	protected boolean loadedSearch = false;
	private IStoredItemStackComparator comparator = new ComparatorAmount(false);
	protected static final ResourceLocation creativeInventoryTabs = new ResourceLocation("textures/gui/container/creative_inventory/tabs.png");
	protected GuiButton buttonSortingType, buttonDirection, buttonSearchType, buttonCtrlMode;

	public GuiStorageTerminalBase(T screenContainer, PlayerInventory inv, ITextComponent titleIn) {
		super(screenContainer, inv, titleIn);
		screenContainer.onPacket = this::onPacket;
	}

	protected void onPacket() {
		int s = menu.terminalData;
		controllMode = (s & 0b000_00_0_11) % ControllMode.VALUES.length;
		boolean rev = (s & 0b000_00_1_00) > 0;
		int type = (s & 0b000_11_0_00) >> 3;
		comparator = SortingTypes.VALUES[type % SortingTypes.VALUES.length].create(rev);
		searchType = (s & 0b111_00_0_00) >> 5;
		searchField.setCanLoseFocus((searchType & 1) == 0);
		if(!searchField.isFocused() && (searchType & 1) > 0) {
			searchField.setFocus(true);
		}
		buttonSortingType.state = type;
		buttonDirection.state = rev ? 1 : 0;
		buttonSearchType.state = searchType;
		buttonCtrlMode.state = controllMode;

		if(!loadedSearch && menu.search != null) {
			loadedSearch = true;
			if((searchType & 2) > 0)
				searchField.setValue(menu.search);
		}
	}

	protected void sendUpdate() {
		CompoundNBT c = new CompoundNBT();
		c.putInt("d", updateData());
		CompoundNBT msg = new CompoundNBT();
		msg.put("c", c);
		menu.sendMessage(msg);
	}

	protected int updateData() {
		int d = 0;
		d |= (controllMode & 0b000_0_11);
		d |= ((comparator.isReversed() ? 1 : 0) << 2);
		d |= (comparator.type() << 3);
		d |= ((searchType & 0b111) << 5);
		return d;
	}

	@Override
	protected void init() {
		children.clear();
		buttons.clear();
		inventoryLabelY = imageHeight - 92;
		super.init();
		this.searchField = new TextFieldWidget(getFont(), this.leftPos + 82, this.topPos + 6, 89, this.getFont().lineHeight, new TranslationTextComponent("narrator.toms_storage.terminal_search"));
		this.searchField.setMaxLength(100);
		this.searchField.setBordered(false);
		this.searchField.setVisible(true);
		this.searchField.setTextColor(16777215);
		buttons.add(searchField);
		buttonSortingType = addButton(new GuiButton(leftPos - 18, topPos + 5, 0, b -> {
			comparator = SortingTypes.VALUES[(comparator.type() + 1) % SortingTypes.VALUES.length].create(comparator.isReversed());
			buttonSortingType.state = comparator.type();
			sendUpdate();
			refreshItemList = true;
		}));
		buttonDirection = addButton(new GuiButton(leftPos - 18, topPos + 5 + 18, 1, b -> {
			comparator.setReversed(!comparator.isReversed());
			buttonDirection.state = comparator.isReversed() ? 1 : 0;
			sendUpdate();
			refreshItemList = true;
		}));
		buttonSearchType = addButton(new GuiButton(leftPos - 18, topPos + 5 + 18*2, 2, b -> {
			searchType = (searchType + 1) & ((ModList.get().isLoaded("jei") || this instanceof GuiCraftingTerminal) ? 0b111 : 0b011);
			buttonSearchType.state = searchType;
			sendUpdate();
		}) {
			@Override
			public void renderButton(MatrixStack st, int mouseX, int mouseY, float pt) {
				if (this.visible) {
					mc.getTextureManager().bind(getGui());
					RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
					this.isHovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
					//int i = this.getYImage(this.isHovered);
					RenderSystem.enableBlend();
					RenderSystem.defaultBlendFunc();
					RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
					this.blit(st, this.x, this.y, texX, texY + tile * 16, this.width, this.height);
					if((state & 1) > 0)this.blit(st, this.x+1, this.y+1, texX + 16, texY + tile * 16, this.width-2, this.height-2);
					if((state & 2) > 0)this.blit(st, this.x+1, this.y+1, texX + 16+14, texY + tile * 16, this.width-2, this.height-2);
					if((state & 4) > 0)this.blit(st, this.x+1, this.y+1, texX + 16+14*2, texY + tile * 16, this.width-2, this.height-2);
				}
			}
		});
		buttonCtrlMode = addButton(new GuiButton(leftPos - 18, topPos + 5 + 18*3, 3, b -> {
			controllMode = (controllMode + 1) % ControllMode.VALUES.length;
			buttonCtrlMode.state = controllMode;
			sendUpdate();
		}));
		updateSearch();
	}

	protected void updateSearch() {
		String searchString = searchField.getValue();
		if (refreshItemList || !searchLast.equals(searchString)) {
			getMenu().itemListClientSorted.clear();
			boolean searchMod = false;
			if (searchString.startsWith("@")) {
				searchMod = true;
				searchString = searchString.substring(1);
			}
			Pattern m = null;
			try {
				m = Pattern.compile(searchString.toLowerCase(), Pattern.CASE_INSENSITIVE);
			} catch (Throwable ignore) {
				try {
					m = Pattern.compile(Pattern.quote(searchString.toLowerCase()), Pattern.CASE_INSENSITIVE);
				} catch (Throwable __) {
					return;
				}
			}
			boolean notDone = false;
			try {
				for (int i = 0;i < getMenu().itemListClient.size();i++) {
					StoredItemStack is = getMenu().itemListClient.get(i);
					if (is != null && is.getStack() != null) {
						String dspName = searchMod ? is.getStack().getItem().delegate.name().getNamespace() : is.getStack().getHoverName().getString();
						notDone = true;
						if (m.matcher(dspName.toLowerCase()).find()) {
							addStackToClientList(is);
							notDone = false;
						}
						if (notDone) {
							for (String lp : tooltipCache.get(is)) {
								if (m.matcher(lp).find()) {
									addStackToClientList(is);
									notDone = false;
									break;
								}
							}
						}
					}
				}
			} catch (Exception e) {
			}
			Collections.sort(getMenu().itemListClientSorted, comparator);
			if(!searchLast.equals(searchString)) {
				getMenu().scrollTo(0);
				this.currentScroll = 0;
				if ((searchType & 4) > 0) {
					if(ModList.get().isLoaded("jei"))
						JEIHandler.setJeiSearchText(searchString);
				}
				if ((searchType & 2) > 0) {
					CompoundNBT nbt = new CompoundNBT();
					nbt.putString("s", searchString);
					menu.sendMessage(nbt);
				}
				onUpdateSearch(searchString);
			} else {
				getMenu().scrollTo(this.currentScroll);
			}
			refreshItemList = false;
			this.searchLast = searchString;
		}
	}

	private void addStackToClientList(StoredItemStack is) {
		getMenu().itemListClientSorted.add(is);
	}

	public static ITooltipFlag getTooltipFlag(){
		return Minecraft.getInstance().options.advancedItemTooltips ? ITooltipFlag.TooltipFlags.ADVANCED : ITooltipFlag.TooltipFlags.NORMAL;
	}

	@Override
	public void tick() {
		super.tick();
		updateSearch();
	}

	@Override
	public void render(MatrixStack st, int mouseX, int mouseY, float partialTicks) {
		boolean flag = GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_RELEASE;
		int i = this.leftPos;
		int j = this.topPos;
		int k = i + 174;
		int l = j + 18;
		int i1 = k + 14;
		int j1 = l + rowCount * 18;

		if (!this.wasClicking && flag && mouseX >= k && mouseY >= l && mouseX < i1 && mouseY < j1) {
			this.isScrolling = this.needsScrollBars();
		}

		if (!flag) {
			this.isScrolling = false;
		}
		this.wasClicking = flag;

		if (this.isScrolling) {
			this.currentScroll = (mouseY - l - 7.5F) / (j1 - l - 15.0F);
			this.currentScroll = MathHelper.clamp(this.currentScroll, 0.0F, 1.0F);
			getMenu().scrollTo(this.currentScroll);
		}
		super.render(st, mouseX, mouseY, partialTicks);

		RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
		RenderHelper.turnOff();
		minecraft.textureManager.bind(creativeInventoryTabs);
		i = k;
		j = l;
		k = j1;
		this.blit(st, i, j + (int) ((k - j - 17) * this.currentScroll), 232 + (this.needsScrollBars() ? 0 : 12), 0, 12, 15);
		st.pushPose();
		RenderHelper.turnBackOn();
		slotIDUnderMouse = getMenu().drawSlots(st, this, mouseX, mouseY);
		st.popPose();
		this.renderTooltip(st, mouseX, mouseY);

		if (buttonSortingType.isHovered()) {
			renderTooltip(st, new TranslationTextComponent("tooltip.toms_storage.sorting_" + buttonSortingType.state), mouseX, mouseY);
		}
		if (buttonSearchType.isHovered()) {
			renderTooltip(st, new TranslationTextComponent("tooltip.toms_storage.search_" + buttonSearchType.state), mouseX, mouseY);
		}
		if (buttonCtrlMode.isHovered()) {
			renderComponentTooltip(st, Arrays.stream(I18n.get("tooltip.toms_storage.ctrlMode_" + buttonCtrlMode.state).split("\\\\")).map(StringTextComponent::new).collect(Collectors.toList()), mouseX, mouseY);
		}
	}

	protected boolean needsScrollBars() {
		return this.getMenu().itemListClientSorted.size() > rowCount * 9;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
		if (slotIDUnderMouse > -1) {
			if (isPullOne(mouseButton)) {
				if (getMenu().getSlotByID(slotIDUnderMouse).stack != null && getMenu().getSlotByID(slotIDUnderMouse).stack.getQuantity() > 0) {
					for (int i = 0;i < getMenu().itemList.size();i++) {
						if (getMenu().getSlotByID(slotIDUnderMouse).stack.equals(getMenu().itemList.get(i))) {
							storageSlotClick(getMenu().getSlotByID(slotIDUnderMouse).stack.getStack(), SlotAction.PULL_ONE, isTransferOne(mouseButton) ? 1 : 0);
							return true;
						}
					}
				}
				return true;
			} else if (pullHalf(mouseButton)) {
				if (!mc.player.inventory.getCarried().isEmpty()) {
					storageSlotClick(ItemStack.EMPTY, hasControlDown() ? SlotAction.GET_QUARTER : SlotAction.GET_HALF, 0);
				} else {
					if (getMenu().getSlotByID(slotIDUnderMouse).stack != null && getMenu().getSlotByID(slotIDUnderMouse).stack.getQuantity() > 0) {
						for (int i = 0;i < getMenu().itemList.size();i++) {
							if (getMenu().getSlotByID(slotIDUnderMouse).stack.equals(getMenu().itemList.get(i))) {
								storageSlotClick(getMenu().getSlotByID(slotIDUnderMouse).stack.getStack(), hasControlDown() ? SlotAction.GET_QUARTER : SlotAction.GET_HALF, 0);
								return true;
							}
						}
					}
				}
			} else if (pullNormal(mouseButton)) {
				if (!mc.player.inventory.getCarried().isEmpty()) {
					storageSlotClick(ItemStack.EMPTY, SlotAction.PULL_OR_PUSH_STACK, 0);
				} else {
					if (getMenu().getSlotByID(slotIDUnderMouse).stack != null) {
						if (getMenu().getSlotByID(slotIDUnderMouse).stack.getQuantity() > 0) {
							for (int i = 0;i < getMenu().itemList.size();i++) {
								if (getMenu().getSlotByID(slotIDUnderMouse).stack.equals(getMenu().itemList.get(i))) {
									storageSlotClick(getMenu().getSlotByID(slotIDUnderMouse).stack.getStack(), hasShiftDown() ? SlotAction.SHIFT_PULL : SlotAction.PULL_OR_PUSH_STACK, 0);
									return true;
								}
							}
						}
					}
				}
			}
		} else if (GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_SPACE) != GLFW.GLFW_RELEASE) {
			storageSlotClick(ItemStack.EMPTY, SlotAction.SPACE_CLICK, 0);
		} else {
			if (mouseButton == 1 && isHovering(searchField.x - leftPos, searchField.y - topPos, 89, this.getFont().lineHeight, mouseX, mouseY))
				searchField.setValue("");
			else if(this.searchField.mouseClicked(mouseX, mouseY, mouseButton))return true;
			else
				return super.mouseClicked(mouseX, mouseY, mouseButton);
		}
		return true;
	}

	protected void storageSlotClick(ItemStack slotStack, SlotAction act, int mod) {
		CompoundNBT c = new CompoundNBT();
		c.put("s", slotStack.save(new CompoundNBT()));
		c.putInt("a", act.ordinal());
		c.putByte("m", (byte) mod);
		CompoundNBT msg = new CompoundNBT();
		msg.put("a", c);
		menu.sendMessage(msg);
	}

	public boolean isPullOne(int mouseButton) {
		switch (ctrlm()) {
		case AE:
			return mouseButton == 1 && hasShiftDown();
		case RS:
			return mouseButton == 2;
		case DEF:
			return mouseButton == 1 && !mc.player.inventory.getCarried().isEmpty();
		default:
			return false;
		}
	}

	public boolean isTransferOne(int mouseButton) {
		switch (ctrlm()) {
		case AE:
			return hasShiftDown() && hasControlDown();//not in AE
		case RS:
			return hasShiftDown() && mouseButton == 2;
		case DEF:
			return mouseButton == 1 && hasShiftDown();
		default:
			return false;
		}
	}

	public boolean pullHalf(int mouseButton) {
		switch (ctrlm()) {
		case AE:
			return mouseButton == 1;
		case RS:
			return mouseButton == 1;
		case DEF:
			return mouseButton == 1 && mc.player.inventory.getCarried().isEmpty();
		default:
			return false;
		}
	}

	public boolean pullNormal(int mouseButton) {
		switch (ctrlm()) {
		case AE:
		case RS:
		case DEF:
			return mouseButton == 0;
		default:
			return false;
		}
	}

	private ControllMode ctrlm() {
		return ControllMode.VALUES[controllMode];
	}

	public final void renderItemInGui(MatrixStack st, ItemStack stack, int x, int y, int mouseX, int mouseY, boolean hasBg, int color, boolean tooltip, String... extraInfo) {
		if (stack != null) {
			if (!tooltip) {
				if (hasBg) {
					fill(st, x, y, 16, 16, color | 0x80000000);
				}
				st.translate(0.0F, 0.0F, 32.0F);
				//this.setBlitOffset(100);
				//this.itemRenderer.zLevel = 100.0F;
				FontRenderer font = null;
				if (stack != null)
					font = stack.getItem().getFontRenderer(stack);
				if (font == null)
					font = this.getFont();
				RenderSystem.enableDepthTest();
				this.itemRenderer.renderAndDecorateItem(stack, x, y);
				this.itemRenderer.renderGuiItemDecorations(font, stack, x, y, null);
				//this.setBlitOffset(0);
				//this.itemRenderer.zLevel = 0.0F;
			} else if (mouseX >= x - 1 && mouseY >= y - 1 && mouseX < x + 17 && mouseY < y + 17) {
				List<ITextComponent> list = getTooltipFromItem(stack);
				// list.add(I18n.format("tomsmod.gui.amount", stack.stackSize));
				if (extraInfo != null && extraInfo.length > 0) {
					for (int i = 0; i < extraInfo.length; i++) {
						list.add(new StringTextComponent(extraInfo[i]));
					}
				}
				for (int i = 0;i < list.size();++i) {
					ITextComponent t = list.get(i);
					IFormattableTextComponent t2 = t instanceof IFormattableTextComponent ? (IFormattableTextComponent) t : t.copy();
					if (i == 0) {
						list.set(i, t2.withStyle(stack.getRarity().color));
					} else {
						list.set(i, t2.withStyle(TextFormatting.GRAY));
					}
				}
				this.renderComponentTooltip(st, list, mouseX, mouseY);
			}
		}
	}

	public FontRenderer getFont() {
		return font;
	}

	@Override
	public boolean keyPressed(int p_keyPressed_1_, int p_keyPressed_2_, int p_keyPressed_3_) {
		if (p_keyPressed_1_ == 256) {
			this.onClose();
			return true;
		}
		return !this.searchField.keyPressed(p_keyPressed_1_, p_keyPressed_2_, p_keyPressed_3_) && !this.searchField.canConsumeInput() ? super.keyPressed(p_keyPressed_1_, p_keyPressed_2_, p_keyPressed_3_) : true;
	}

	@Override
	public boolean charTyped(char p_charTyped_1_, int p_charTyped_2_) {
		if(searchField.charTyped(p_charTyped_1_, p_charTyped_2_))return true;
		return super.charTyped(p_charTyped_1_, p_charTyped_2_);
	}

	@Override
	public boolean mouseScrolled(double p_mouseScrolled_1_, double p_mouseScrolled_3_, double p_mouseScrolled_5_) {
		if (!this.needsScrollBars()) {
			return false;
		} else {
			int i = ((this.menu).itemListClientSorted.size() + 9 - 1) / 9 - 5;
			this.currentScroll = (float)(this.currentScroll - p_mouseScrolled_5_ / i);
			this.currentScroll = MathHelper.clamp(this.currentScroll, 0.0F, 1.0F);
			this.menu.scrollTo(this.currentScroll);
			return true;
		}
	}

	public abstract ResourceLocation getGui();

	@Override
	protected void renderBg(MatrixStack st, float partialTicks, int mouseX, int mouseY) {
		mc.textureManager.bind(getGui());
		this.blit(st, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
	}

	public class GuiButton extends Button {
		protected int tile;
		protected int state;
		protected int texX = 194;
		protected int texY = 30;
		public GuiButton(int x, int y, int tile, IPressable pressable) {
			super(x, y, 16, 16, null, pressable);
			this.tile = tile;
		}

		public void setX(int i) {
			x = i;
		}

		/**
		 * Draws this button to the screen.
		 */
		@Override
		public void renderButton(MatrixStack st, int mouseX, int mouseY, float pt) {
			if (this.visible) {
				mc.getTextureManager().bind(getGui());
				RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
				this.isHovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
				//int i = this.getYImage(this.isHovered);
				RenderSystem.enableBlend();
				RenderSystem.defaultBlendFunc();
				RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
				this.blit(st, this.x, this.y, texX + state * 16, texY + tile * 16, this.width, this.height);
			}
		}
	}

	protected void onUpdateSearch(String text) {}

	@Override
	public void receive(CompoundNBT tag) {
		menu.receiveClientNBTPacket(tag);
		refreshItemList = true;
	}
}
