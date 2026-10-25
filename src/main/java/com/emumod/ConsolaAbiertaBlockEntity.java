package com.emumod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Sin datos: existe solo para que el cliente dibuje el modelo 3D de la portátil apoyada (3DS, SP o PSP). */
public class ConsolaAbiertaBlockEntity extends BlockEntity {
	public ConsolaAbiertaBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.entidadAbierta(((ConsolaAbiertaBlock) state.getBlock()).modelo()), pos, state);
	}
}
