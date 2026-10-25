package com.emumod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.emumod.client.ManosGba;
import com.emumod.client.ManosDoble;
import com.emumod.client.SesionNes;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;

// Jugando (GBA, SP o 3DS delante de los ojos, o NES) se usa el campo de visión elegido en su barra, para el mundo y las manos
@Mixin(GameRenderer.class)
public class FovMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void emumod$fovConsola(Camera camara, float partialTick, boolean usarAjuste, CallbackInfoReturnable<Double> cir) {
		if (ManosGba.activa()) {
			cir.setReturnValue(ManosGba.fov(cir.getReturnValueD()));
		} else if (ManosDoble.activa()) {
			cir.setReturnValue(ManosDoble.fov(cir.getReturnValueD()));
		} else if (SesionNes.activa()) {
			cir.setReturnValue(SesionNes.fov(cir.getReturnValueD()));
		}
	}
}
