package com.emumod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Una portátil de tapa apoyada en el mundo (la 3DS o la DS): abierta, sobre el piso o una mesa (no se cuelga: abierta no
 * se sostiene en la pared). Como la GBA, se dibuja con su modelo 3D desde el cliente y con click derecho se juega.
 */
public class ConsolaAbiertaBlock extends GbaBlock {
	// Abierta mide casi lo mismo de ancho que de fondo: 12 x 12 y 4,5 de alto (la tapa inclinada)
	private static final VoxelShape FORMA = Block.box(2, 0, 2, 14, 4.5, 14);

	private final ConsolaItem.Modelo modelo;

	public ConsolaAbiertaBlock(ConsolaItem.Modelo modelo, Properties properties) {
		super(properties);
		this.modelo = modelo;
	}

	public ConsolaItem.Modelo modelo() {
		return modelo;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// Siempre acostada, con la pantalla de abajo del lado del jugador
		BlockState estado = defaultBlockState().setValue(FACE, AttachFace.FLOOR).setValue(FACING, context.getHorizontalDirection());
		return estado.canSurvive(context.getLevel(), context.getClickedPos()) ? estado : null;
	}

	@Override
	@SuppressWarnings("deprecation")
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return FORMA;
	}

	@Override
	@SuppressWarnings("deprecation")
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide) {
			ConsolaItem.abrirPantalla.accept(modelo);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ConsolaAbiertaBlockEntity(pos, state);
	}
}
