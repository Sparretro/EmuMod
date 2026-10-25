package com.emumod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Lo que tiene conectado una N64: lo mismo que una NES (televisor por cable y control). */
public class N64BlockEntity extends NesBlockEntity {
	public N64BlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.N64_ENTIDAD, pos, state);
	}

	@Override
	public String avisos() {
		return "n64";
	}
}
