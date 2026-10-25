package com.emumod;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lo que tiene conectado una NES: el televisor CRT (por cable) y si ya se le enchufó un control. También quién está
 * llevando un cable desde esta NES hacia un televisor, para que el cliente lo dibuje.
 */
public class NesBlockEntity extends BlockEntity {
	private BlockPos tele;
	private boolean control;
	// No se guarda: si se cierra el mundo con el cable en la mano, se suelta
	private UUID cableEnManoDe;

	public NesBlockEntity(BlockPos pos, BlockState state) {
		this(ModBlocks.NES_ENTIDAD, pos, state);
	}

	protected NesBlockEntity(BlockEntityType<?> tipo, BlockPos pos, BlockState state) {
		super(tipo, pos, state);
	}

	/** Prefijo de los mensajes del chat de esta consola (mensaje.emumod.<prefijo>.*). */
	public String avisos() {
		return "nes";
	}

	public BlockPos tele() {
		return tele;
	}

	public boolean control() {
		return control;
	}

	public UUID cableEnManoDe() {
		return cableEnManoDe;
	}

	void conectarTele(BlockPos pos) {
		tele = pos == null ? null : pos.immutable();
		cableEnManoDe = null;
		avisarCambio();
	}

	void conectarControl() {
		control = true;
		avisarCambio();
	}

	void setCableEnManoDe(UUID jugador) {
		cableEnManoDe = jugador;
		avisarCambio();
	}

	/** Si el televisor guardado ya no está (lo rompieron), se desconecta. */
	boolean teleSigueAhi() {
		if (tele != null && level != null && !(level.getBlockState(tele).getBlock() instanceof TeleCrtBlock)) {
			conectarTele(null);
		}
		return tele != null;
	}

	private void avisarCambio() {
		setChanged();
		if (level != null && !level.isClientSide) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		if (tele != null) {
			tag.put("tele", NbtUtils.writeBlockPos(tele));
		}
		tag.putBoolean("control", control);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		tele = tag.contains("tele") ? NbtUtils.readBlockPos(tag.getCompound("tele")) : null;
		control = tag.getBoolean("control");
		cableEnManoDe = tag.hasUUID("cable") ? tag.getUUID("cable") : null;
	}

	// Al cliente se le manda también quién lleva el cable
	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag tag = saveWithoutMetadata();
		if (cableEnManoDe != null) {
			tag.putUUID("cable", cableEnManoDe);
		}
		return tag;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
