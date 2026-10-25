package com.emumod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.emumod.client.ManosDoble;
import com.emumod.client.ManosGba;
import com.emumod.client.SesionNes;
import com.emumod.client.TelevisorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;

// Mientras se juega en una consola no se dibuja la interfaz de Minecraft (barra de inventario, vida, chat, mira...), como
// con F1: solo quedan la consola y las opciones del mod
@Mixin(Gui.class)
public class HudMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void emumod$sinInterfaz(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
		if (ManosGba.activa() || ManosDoble.activa() || SesionNes.activa() || Minecraft.getInstance().screen instanceof TelevisorScreen) {
			ci.cancel();
		}
	}
}
