package com.tom.storagemod.jei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.container.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;

import com.mojang.blaze3d.matrix.MatrixStack;

import com.google.common.base.Function;

import com.tom.storagemod.gui.ContainerCraftingTerminal;

import mezz.jei.api.constants.VanillaRecipeCategoryUid;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.gui.ingredient.IGuiIngredient;
import mezz.jei.api.gui.ingredient.IGuiItemStackGroup;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.registration.IRecipeTransferRegistration;

@SuppressWarnings("rawtypes")
public class CraftingTerminalTransferHandler implements IRecipeTransferHandler {
	private final Class<? extends Container> containerClass;
	private static final List<Class<? extends Container>> containerClasses = new ArrayList<>();
	private static final Function<IRecipeLayout, ItemStack[][]> transferFunc = new Function<IRecipeLayout, ItemStack[][]>() {
		@Override
		public ItemStack[][] apply(IRecipeLayout t) {
			List<ItemStack[]> inputs = new ArrayList<>();
			IGuiItemStackGroup itemStackGroup = t.getItemStacks();
			for (IGuiIngredient<ItemStack> ingredient : itemStackGroup.getGuiIngredients().values()) {
				if (ingredient.isInput()) {
					if (!ingredient.getAllIngredients().isEmpty() && ingredient.getAllIngredients().get(0) != null) {
						inputs.add(ingredient.getAllIngredients().toArray(new ItemStack[]{}));
					} else {
						inputs.add(null);
					}
				}
			}
			return inputs.toArray(new ItemStack[][]{});
		}
	};
	private static final IRecipeTransferError ERROR_INSTANCE = new IRecipeTransferError() {
		@Override public IRecipeTransferError.Type getType() { return IRecipeTransferError.Type.INTERNAL; }
		@Override public void showError(MatrixStack matrixStack, int mouseX, int mouseY, IRecipeLayout recipeLayout, int recipeX, int recipeY) {}
	};
	static {
		containerClasses.add(ContainerCraftingTerminal.class);
	}

	public CraftingTerminalTransferHandler(Class<? extends Container> containerClass) {
		this.containerClass = containerClass;
	}

	@Override
	public Class<? extends Container> getContainerClass() {
		return containerClass;
	}

	@Override
	public IRecipeTransferError transferRecipe(Container container, IRecipeLayout recipeLayout, PlayerEntity player, boolean maxTransfer, boolean doTransfer) {
		if (container instanceof IJEIAutoFillTerminal) {
			if (doTransfer) {
				ItemStack[][] stacks = transferFunc.apply(recipeLayout);
				CompoundNBT compound = new CompoundNBT();
				ListNBT list = new ListNBT();
				for (int i = 0;i < stacks.length;++i) {
					if (stacks[i] != null) {
						CompoundNBT CompoundNBT = new CompoundNBT();
						CompoundNBT.putByte("s", (byte) i);
						for (int j = 0;j < stacks[i].length && j < 3;j++) {
							if (stacks[i][j] != null && !stacks[i][j].isEmpty()) {
								CompoundNBT tag = new CompoundNBT();
								stacks[i][j].save(tag);
								CompoundNBT.put("i" + j, tag);
							}
						}
						CompoundNBT.putByte("l", (byte) Math.min(3, stacks[i].length));
						list.add(CompoundNBT);
					}
				}
				compound.put("i", list);
				((IJEIAutoFillTerminal) container).sendMessage(compound);
			}
		} else {
			return ERROR_INSTANCE;
		}
		return null;
	}

	public static void registerTransferHandlers(IRecipeTransferRegistration recipeTransferRegistry) {
		for (int i = 0;i < containerClasses.size();i++)
			recipeTransferRegistry.addRecipeTransferHandler(new CraftingTerminalTransferHandler(containerClasses.get(i)), VanillaRecipeCategoryUid.CRAFTING);
	}
}
