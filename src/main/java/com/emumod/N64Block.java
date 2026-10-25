package com.emumod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * La Nintendo 64. Se arma igual que la NES (ver {@link NesBlock}): televisor CRT por cable a un bloque, control de N64
 * enchufado y a jugar sentado con el control en la mano.
 */
public class N64Block extends NesBlock {
	// 12,5 de ancho, 6 de alto con el cartucho puesto y 11 de fondo, como la consola en escala
	private static final VoxelShape FORMA_NS = Block.box(1.75, 0, 2.5, 14.25, 6, 13.5);
	private static final VoxelShape FORMA_EO = Block.box(2.5, 0, 1.75, 13.5, 6, 14.25);

	public N64Block(Properties properties) {
		super(properties);
	}

	@Override
	@SuppressWarnings("deprecation")
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(FACING).getAxis() == Direction.Axis.Z ? FORMA_NS : FORMA_EO;
	}

	@Override
	protected Item control() {
		return ModItems.CONTROL_N64;
	}

	@Override
	protected BlockEntityType<? extends NesBlockEntity> tipoEntidad() {
		return ModBlocks.N64_ENTIDAD;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new N64BlockEntity(pos, state);
	}
}
