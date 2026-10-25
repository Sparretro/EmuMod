package com.emumod;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Sentarse a jugar a la NES frente a su televisor, y levantarse al terminar. */
public final class SesionesNes {
	/** Servidor a cliente: empezó la partida (NES, televisor). */
	public static final ResourceLocation JUGAR = EmuMod.id("nes_jugar");
	/** Cliente a servidor: el jugador dejó de jugar (cerró la pantalla). */
	public static final ResourceLocation DEJAR = EmuMod.id("nes_dejar");
	/** Más lejos que esto del televisor no se puede jugar. */
	public static final double DISTANCIA_MAXIMA = 5;
	/** A cuántos bloques del televisor se sienta el jugador, enfrente de la pantalla. */
	static final double DISTANCIA_ASIENTO = 2.5;

	private SesionesNes() {
	}

	static void registrar() {
		ServerPlayNetworking.registerGlobalReceiver(DEJAR, (server, jugador, handler, buf, respuesta) -> server.execute(() -> {
			if (jugador.getVehicle() instanceof AsientoEntity) {
				jugador.stopRiding();
			}
		}));
	}

	/**
	 * Dónde sentarse: justo enfrente de la pantalla, alineado con el televisor, a {@link #DISTANCIA_ASIENTO} bloques y a la
	 * altura de los pies del jugador. Si ahí no hay lugar libre, se sienta donde está parado.
	 */
	private static Vec3 lugarFrenteALaTele(ServerPlayer jugador, BlockPos tele) {
		BlockState estado = jugador.level().getBlockState(tele);
		if (!(estado.getBlock() instanceof TeleCrtBlock)) {
			return jugador.position();
		}
		Direction mira = estado.getValue(TeleCrtBlock.FACING);
		Vec3 centro = tele.getCenter();
		Vec3 lugar = new Vec3(centro.x + mira.getStepX() * DISTANCIA_ASIENTO, jugador.getY(), centro.z + mira.getStepZ() * DISTANCIA_ASIENTO);
		BlockPos pies = BlockPos.containing(lugar);
		// Que entre el jugador entero (sentado sobre el asiento, un poco más alto) y que tenga piso abajo
		boolean libre = jugador.level().noCollision(jugador, jugador.getBoundingBox().move(lugar.subtract(jugador.position())).expandTowards(0, 0.3, 0))
				&& !jugador.level().getBlockState(pies.below()).getCollisionShape(jugador.level(), pies.below()).isEmpty();
		return libre ? lugar : jugador.position();
	}

	static void intentarJugar(ServerPlayer jugador, NesBlockEntity nes) {
		if (!nes.teleSigueAhi()) {
			Avisos.decir(jugador, nes, "falta_tele");
			return;
		}
		BlockPos tele = nes.tele();
		Vec3 centroTele = tele.getCenter();
		if (jugador.position().distanceTo(centroTele) > DISTANCIA_MAXIMA) {
			Avisos.decir(jugador, nes, "acercate");
			return;
		}
		if (jugador.isPassenger()) {
			jugador.stopRiding();
		}
		Vec3 lugar = lugarFrenteALaTele(jugador, tele);
		AsientoEntity asiento = new AsientoEntity(ModEntidades.ASIENTO, jugador.level());
		float giro = (float) Math.toDegrees(Math.atan2(-(centroTele.x - lugar.x), centroTele.z - lugar.z));
		asiento.moveTo(lugar.x, lugar.y + 0.3, lugar.z, giro, 0);
		asiento.setBajada(jugador.position());
		jugador.level().addFreshEntity(asiento);
		jugador.startRiding(asiento, true);

		FriendlyByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(nes.getBlockPos());
		buf.writeBlockPos(tele);
		ServerPlayNetworking.send(jugador, JUGAR, buf);
	}
}
