package com.emumod.client;

import com.emumod.EmuMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

/** El saludo de Spar Retro, creador de EmuMod, en el chat al entrar a un mundo. */
final class Creditos {
	static final String CANAL = "https://www.youtube.com/@sparretro";

	// El saludo sale una vez por partida de Minecraft, no cada vez que se entra a un mundo
	private static boolean saludado;

	private Creditos() {
	}

	static void registrar() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, cliente) -> {
			if (saludado) {
				return;
			}
			saludado = true;
			String version = FabricLoader.getInstance().getModContainer(EmuMod.MOD_ID)
					.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
			Component enlace = Component.literal("youtube.com/@sparretro").withStyle(Style.EMPTY
					.withColor(ChatFormatting.RED).withUnderlined(true)
					.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, CANAL))
					.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(CANAL))));
			cliente.execute(() -> cliente.gui.getChat().addMessage(
					Component.translatable("mensaje.emumod.bienvenida", version, enlace).withStyle(ChatFormatting.GOLD)));
		});
	}
}
