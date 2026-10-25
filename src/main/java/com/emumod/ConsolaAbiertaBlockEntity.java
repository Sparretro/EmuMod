package com.emumod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Sin datos: existe solo para que el cliente dibuje el modelo 3D de la 3DS o la DS apoyada. */
public class ConsolaAbiertaBlockEntity extends BlockEntity {
	public ConsolaAbiertaBlockEntity(BlockPos pos, BlockState state) {
		super(state.is(ModBlocks.GBA_SP) ? ModBlocks.GBA_SP_ENTIDAD : ModBlocks.N3DS_ENTIDAD, pos, state);
	}
}
