package com.emumod;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Mensajes del chat para armar y usar las consolas de televisor (NES y N64). */
public final class Avisos {
	private Avisos() {
	}

	/** Mensaje que no depende de la consola (por ejemplo, click en un televisor sin cable en la mano). */
	static void decir(ServerPlayer jugador, String clave) {
		decir(jugador, "nes", clave);
	}

	static void decir(ServerPlayer jugador, NesBlockEntity consola, String clave) {
		decir(jugador, consola.avisos(), clave);
	}

	private static void decir(ServerPlayer jugador, String consola, String clave) {
		jugador.sendSystemMessage(Component.translatable("mensaje.emumod." + consola + "." + clave).withStyle(ChatFormatting.YELLOW));
	}

	/** Los pasos para hacer funcionar la consola, al fabricarla ("nes" o "n64"). */
	static void pasos(Player jugador, String consola) {
		jugador.sendSystemMessage(Component.translatable("mensaje.emumod." + consola + ".pasos_titulo").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		for (int i = 1; i <= 4; i++) {
			jugador.sendSystemMessage(Component.translatable("mensaje.emumod." + consola + ".paso" + i).withStyle(ChatFormatting.YELLOW));
		}
	}

	/** Dice el primer paso que le falta a esta NES para poder jugar. */
	static void queFalta(ServerPlayer jugador, NesBlockEntity nes) {
		if (!nes.teleSigueAhi()) {
			decir(jugador, nes, "falta_tele");
		} else if (!nes.control()) {
			decir(jugador, nes, "falta_control");
		} else {
			decir(jugador, nes, "listo");
		}
	}
}
