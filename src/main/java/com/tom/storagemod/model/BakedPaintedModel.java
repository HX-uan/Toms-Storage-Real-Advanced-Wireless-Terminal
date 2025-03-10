package com.tom.storagemod.model;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.client.renderer.model.BakedQuad;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockDisplayReader;

import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.model.data.IDynamicBakedModel;
import net.minecraftforge.client.model.data.IModelData;

import com.tom.storagemod.tile.TileEntityPainted;

public class BakedPaintedModel implements IDynamicBakedModel {
	private Block blockFor;
	private IBakedModel parent;
	public BakedPaintedModel(Block blockFor, IBakedModel parent) {
		this.blockFor = blockFor;
		this.parent = parent;
	}

	@Override
	public boolean useAmbientOcclusion() {
		return true;
	}

	@Override
	public boolean isGui3d() {
		return false;
	}

	@Override
	public boolean usesBlockLight() {
		return false;
	}

	@Override
	public boolean isCustomRenderer() {
		return false;
	}

	@SuppressWarnings("deprecation")
	@Override
	public TextureAtlasSprite getParticleIcon() {
		return parent.getParticleIcon();
	}

	@Override
	public ItemOverrideList getOverrides() {
		return null;
	}

	@SuppressWarnings("deprecation")
	@Override
	public List<BakedQuad> getQuads(BlockState state, Direction side, Random rand, IModelData modelData) {
		IBakedModel model = null;
		Supplier<BlockState> blockstateSupp = modelData.getData(TileEntityPainted.FACADE_STATE);
		BlockState blockstate = null;
		if(blockstateSupp != null)blockstate = blockstateSupp.get();
		RenderType layer = MinecraftForgeClient.getRenderLayer();
		if (blockstate == null || blockstate == Blocks.AIR.defaultBlockState()) {
			blockstate = state;
			model = parent;
			if(layer != null && layer != RenderType.solid())return Collections.emptyList();
		}
		if (layer != null && ! RenderTypeLookup.canRenderInLayer(blockstate, layer)) { // always render in the null layer or the block-breaking textures don't show up
			return Collections.emptyList();
		}
		if(model == null)
			model = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getBlockModel(blockstate);
		return model.getQuads(blockstate, side, rand);
	}

	@Override
	public IModelData getModelData(IBlockDisplayReader world, BlockPos pos, BlockState state, IModelData tileData) {
		return tileData;
	}

}
