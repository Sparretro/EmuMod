package com.emumod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * La NES. Para jugar necesita un televisor CRT conectado por cable (a un bloque de distancia) y un control: con la
 * mano vacía se agarra el cable, con el control se lo enchufa y, ya enchufado, con el control en la mano se juega.
 */
public class NesBlock extends HorizontalDirectionalBlock implements EntityBlock {
	// 12 de ancho, 4 de alto y 9,6 de fondo, como la consola en escala
	private static final VoxelShape FORMA_NS = Block.box(2, 0, 3.2, 14, 4.2, 12.8);
	private static final VoxelShape FORMA_EO = Block.box(3.2, 0, 2, 12.8, 4.2, 14);

	public NesBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	// El frente queda mirando al jugador que la coloca
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
		return state.getValue(FACING).getAxis() == Direction.Axis.Z ? FORMA_NS : FORMA_EO;
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
		if (!(level.getBlockEntity(pos) instanceof NesBlockEntity nes) || !(player instanceof ServerPlayer jugador)) {
			return InteractionResult.PASS;
		}
		ItemStack mano = player.getItemInHand(hand);
		if (mano.is(control())) {
			if (!nes.control()) {
				nes.conectarControl();
				Avisos.decir(jugador, nes, "control_conectado");
				if (!nes.teleSigueAhi()) {
					Avisos.decir(jugador, nes, "falta_tele");
				}
				return InteractionResult.CONSUME;
			}
			SesionesNes.intentarJugar(jugador, nes);
			return InteractionResult.CONSUME;
		}
		if (mano.isEmpty()) {
			// Una vez conectado, el televisor queda conectado hasta que se rompa la NES o el televisor
			if (nes.teleSigueAhi()) {
				Avisos.decir(jugador, nes, "tele_ya_conectada");
				Avisos.queFalta(jugador, nes);
				return InteractionResult.CONSUME;
			}
			if (jugador.getUUID().equals(nes.cableEnManoDe())) {
				CablesNes.soltar(jugador);
				Avisos.decir(jugador, nes, "cable_soltado");
			} else {
				CablesNes.agarrar(jugador, nes);
				Avisos.decir(jugador, nes, "cable_en_mano");
			}
			return InteractionResult.CONSUME;
		}
		Avisos.queFalta(jugador, nes);
		return InteractionResult.CONSUME;
	}

	/** El control que se enchufa en esta consola y con el que se juega. */
	protected Item control() {
		return ModItems.CONTROL_NES;
	}

	protected BlockEntityType<? extends NesBlockEntity> tipoEntidad() {
		return ModBlocks.NES_ENTIDAD;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new NesBlockEntity(pos, state);
	}

	// En el servidor, suelta el cable si quien lo lleva se fue lejos o se desconectó
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> tipo) {
		if (level.isClientSide || tipo != tipoEntidad()) {
			return null;
		}
		return (nivel, pos, estado, entidad) -> {
			if (nivel.getGameTime() % 10 == 0) {
				CablesNes.revisar((ServerLevel) nivel, (NesBlockEntity) entidad);
			}
		};
	}
}
