package com.emumod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.emumod.client.ManosGba;
import com.emumod.client.ManosDoble;
import com.emumod.client.SesionNes;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;

// Mientras se juega, las manos en primera persona sostienen la GBA, la SP o la 3DS delante de los ojos o el control de la consola de televisor
@Mixin(ItemInHandRenderer.class)
public class ManosMixin {
	@Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void emumod$sostenerGba(float partialTick, PoseStack pose, MultiBufferSource.BufferSource buffers, LocalPlayer jugador,
			int luz, CallbackInfo ci) {
		if (ManosGba.activa()) {
			ManosGba.dibujar(pose, buffers, jugador, luz);
			ci.cancel();
		} else if (ManosDoble.activa()) {
			ManosDoble.dibujar(pose, buffers, jugador, luz);
			ci.cancel();
		} else if (SesionNes.activa()) {
			SesionNes.dibujarManos(pose, buffers, jugador, luz);
			ci.cancel();
		}
	}
}
