package com.emumod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Sin datos: existe para que el cliente dibuje el televisor CRT con su modelo 3D y el juego en la pantalla. */
public class TeleCrtBlockEntity extends BlockEntity {
	public TeleCrtBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.TELE_CRT_ENTIDAD, pos, state);
	}
}
