package com.emumod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * El cable que va de una NES a un emumod: el jugador lo agarra de la NES y lo enchufa haciendo click derecho en un
 * televisor CRT que esté a un bloque. Solo en el servidor.
 */
public final class CablesNes {
	/** El televisor tiene que estar pegado a la NES: a lo sumo un bloque en cada eje. */
	public static final int ALCANCE_CABLE = 1;
	// Más lejos que esto el jugador suelta el cable
	private static final double LARGO_MAXIMO = 8;

	private static final Map<UUID, GlobalPos> EN_MANO = new HashMap<>();

	private CablesNes() {
	}

	static void agarrar(ServerPlayer jugador, NesBlockEntity nes) {
		soltar(jugador);
		EN_MANO.put(jugador.getUUID(), GlobalPos.of(jugador.level().dimension(), nes.getBlockPos()));
		nes.setCableEnManoDe(jugador.getUUID());
	}

	static void soltar(ServerPlayer jugador) {
		GlobalPos anterior = EN_MANO.remove(jugador.getUUID());
		if (anterior != null && jugador.getServer() != null) {
			ServerLevel nivel = jugador.getServer().getLevel(anterior.dimension());
			if (nivel != null && nivel.getBlockEntity(anterior.pos()) instanceof NesBlockEntity nes
					&& jugador.getUUID().equals(nes.cableEnManoDe())) {
				nes.setCableEnManoDe(null);
			}
		}
	}

	static void enchufarEnTele(ServerPlayer jugador, BlockPos tele) {
		GlobalPos origen = EN_MANO.get(jugador.getUUID());
		if (origen == null || !origen.dimension().equals(jugador.level().dimension())
				|| !(jugador.level().getBlockEntity(origen.pos()) instanceof NesBlockEntity nes)) {
			EN_MANO.remove(jugador.getUUID());
			Avisos.decir(jugador, "tele_sin_cable");
			return;
		}
		BlockPos d = tele.subtract(origen.pos());
		if (Math.max(Math.abs(d.getX()), Math.max(Math.abs(d.getY()), Math.abs(d.getZ()))) > ALCANCE_CABLE) {
			Avisos.decir(jugador, nes, "cable_no_llega");
			return;
		}
		EN_MANO.remove(jugador.getUUID());
		nes.conectarTele(tele);
		Avisos.decir(jugador, nes, "tele_conectada");
		Avisos.queFalta(jugador, nes);
	}

	/** Suelta el cable si quien lo lleva se desconectó o se alejó demasiado de la NES. */
	static void revisar(ServerLevel nivel, NesBlockEntity nes) {
		UUID quien = nes.cableEnManoDe();
		if (quien == null) {
			return;
		}
		Player jugador = nivel.getPlayerByUUID(quien);
		if (jugador == null || jugador.distanceToSqr(nes.getBlockPos().getCenter()) > LARGO_MAXIMO * LARGO_MAXIMO) {
			EN_MANO.remove(quien);
			nes.setCableEnManoDe(null);
			if (jugador instanceof ServerPlayer sp) {
				Avisos.decir(sp, nes, "cable_lejos");
			}
		}
	}
}
