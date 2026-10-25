package com.emumod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Lo que tiene conectado una PS2 o una PS3 (clásicas o Slim): lo mismo que una NES (televisor por cable y control). */
public class ConsolaTeleBlockEntity extends NesBlockEntity {
	public ConsolaTeleBlockEntity(BlockEntityType<?> tipo, BlockPos pos, BlockState state) {
		super(tipo, pos, state);
	}

	@Override
	public String avisos() {
		return getBlockState().getBlock() instanceof ConsolaTeleBlock consola ? consola.avisos() : "nes";
	}
}
