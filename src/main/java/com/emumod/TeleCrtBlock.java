package com.emumod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Televisor de tubo para la NES: se conecta haciendo click derecho con el cable que sale de una NES. */
public class TeleCrtBlock extends HorizontalDirectionalBlock implements EntityBlock {
	// El modelo mide 1,3 de ancho, 1,25 de alto y 1,37 de fondo: se pasa un poco del bloque, como las vallas
	private static final VoxelShape FORMA = Block.box(-2.4, 0, -2.4, 18.4, 20, 18.4);

	// Mirando al norte o al sur, y al este u oeste
	private final VoxelShape formaNs, formaEo;

	public TeleCrtBlock(Properties properties) {
		this(properties, FORMA, FORMA);
	}

	/** Otro televisor (el plasma), con su forma: {@code formaNs} mirando al norte o al sur y {@code formaEo} girada. */
	public TeleCrtBlock(Properties properties, VoxelShape formaNs, VoxelShape formaEo) {
		super(properties);
		this.formaNs = formaNs;
		this.formaEo = formaEo;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	@SuppressWarnings("deprecation")
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(FACING).getAxis() == Direction.Axis.Z ? formaNs : formaEo;
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
			return InteractionResult.SUCCESS;
		}
		if (player instanceof ServerPlayer jugador) {
			CablesNes.enchufarEnTele(jugador, pos);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TeleCrtBlockEntity(pos, state);
	}
}
