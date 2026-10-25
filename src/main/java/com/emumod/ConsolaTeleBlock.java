package com.emumod;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Una PlayStation 2 o 3, clásica o Slim (cada consola usa un mismo emulador para sus dos modelos). Se arma igual que la
 * NES (ver {@link NesBlock}): televisor CRT por cable a un bloque, su control enchufado y a jugar sentado con el control en
 * la mano.
 */
public class ConsolaTeleBlock extends NesBlock {
	private final VoxelShape formaNs, formaEo;
	private final Supplier<BlockEntityType<ConsolaTeleBlockEntity>> tipo;
	private final Supplier<Item> control;
	private final String avisos;

	/**
	 * @param formaNs la forma mirando al norte o al sur; {@code formaEo}, la misma girada
	 * @param avisos  prefijo de sus mensajes del chat (mensaje.emumod.<avisos>.*)
	 */
	public ConsolaTeleBlock(Properties properties, VoxelShape formaNs, VoxelShape formaEo, Supplier<BlockEntityType<ConsolaTeleBlockEntity>> tipo,
			Supplier<Item> control, String avisos) {
		super(properties);
		this.formaNs = formaNs;
		this.formaEo = formaEo;
		this.tipo = tipo;
		this.control = control;
		this.avisos = avisos;
	}

	String avisos() {
		return avisos;
	}

	@Override
	@SuppressWarnings("deprecation")
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(FACING).getAxis() == Direction.Axis.Z ? formaNs : formaEo;
	}

	@Override
	protected Item control() {
		return control.get();
	}

	@Override
	protected BlockEntityType<? extends NesBlockEntity> tipoEntidad() {
		return tipo.get();
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ConsolaTeleBlockEntity(tipo.get(), pos, state);
	}
}
