package com.emumod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Sin datos: existe solo para que el cliente dibuje el modelo 3D de la consola apoyada. */
public class GbaBlockEntity extends BlockEntity {
	public GbaBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.GBA_ENTIDAD, pos, state);
	}
}
