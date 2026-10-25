package com.emumod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * La Game Boy Advance apoyada en el mundo: acostada en el piso o en una mesa, colgada en la pared o pegada al techo,
 * como una palanca. Se dibuja con su modelo 3D desde el cliente; con click derecho se juega.
 */
public class GbaBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {
	// Acostada: 12 de ancho por 7 de alto (vista desde arriba) y 2 de grosor; parada en la pared es lo mismo de pie
	private static final VoxelShape PISO_NS = Block.box(2, 0, 4.5, 14, 2, 11.5);
	private static final VoxelShape PISO_EO = Block.box(4.5, 0, 2, 11.5, 2, 14);
	private static final VoxelShape TECHO_NS = Block.box(2, 14, 4.5, 14, 16, 11.5);
	private static final VoxelShape TECHO_EO = Block.box(4.5, 14, 2, 11.5, 16, 14);
	private static final VoxelShape PARED_NORTE = Block.box(2, 4.5, 14, 14, 11.5, 16);
	private static final VoxelShape PARED_SUR = Block.box(2, 4.5, 0, 14, 11.5, 2);
	private static final VoxelShape PARED_OESTE = Block.box(14, 4.5, 2, 16, 11.5, 14);
	private static final VoxelShape PARED_ESTE = Block.box(0, 4.5, 2, 2, 11.5, 14);

	public GbaBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
				.setValue(FACE, AttachFace.FLOOR));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, FACE);
	}

	@Override
	@SuppressWarnings("deprecation")
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		boolean norteSur = state.getValue(FACING).getAxis() == Direction.Axis.Z;
		return switch (state.getValue(FACE)) {
			case FLOOR -> norteSur ? PISO_NS : PISO_EO;
			case CEILING -> norteSur ? TECHO_NS : TECHO_EO;
			case WALL -> switch (state.getValue(FACING)) {
				case SOUTH -> PARED_SUR;
				case WEST -> PARED_OESTE;
				case EAST -> PARED_ESTE;
				default -> PARED_NORTE;
			};
		};
	}

	@Override
	@SuppressWarnings("deprecation")
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.ENTITYBLOCK_ANIMATED;
	}

	@Override
	@SuppressWarnings("deprecation")
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide) {
			ConsolaItem.abrirPantalla.accept(ConsolaItem.Modelo.GBA);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new GbaBlockEntity(pos, state);
	}
}
