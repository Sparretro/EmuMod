package com.emumod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.emumod.SesionesNes;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * La partida de una consola de televisor (NES o N64) del lado del cliente: el servidor sienta al jugador frente al
 * televisor y avisa; acá se gira la cámara hacia la pantalla, se abre la pantalla de juego y se anima el control subiendo
 * a las manos.
 */
public final class SesionNes {
	private static final float DURACION_MS = 450;

	private static BlockPos nes, tele;
	private static ConsolaTele consola = ConsolaTele.NES;
	private static boolean arriba;
	private static float progreso;
	private static long ultimo;

	private SesionNes() {
	}

	static void registrar() {
		ClientPlayNetworking.registerGlobalReceiver(SesionesNes.JUGAR, (mc, handler, buf, respuesta) -> {
			BlockPos consola = buf.readBlockPos(), televisor = buf.readBlockPos();
			mc.execute(() -> empezar(consola, televisor));
		});
	}

	static void empezar(BlockPos consola, BlockPos televisor) {
		Minecraft mc = Minecraft.getInstance();
		nes = consola;
		tele = televisor;
		SesionNes.consola = mc.level != null ? ConsolaTele.de(mc.level.getBlockEntity(consola)) : ConsolaTele.NES;
		if (mc.player != null) {
			// Mirar al centro de la pantalla del televisor
			Vec3 ojos = mc.player.getEyePosition();
			Vec3 pantalla = Vec3.atBottomCenterOf(televisor).add(0, TeleCrtRenderer.de(mc.level.getBlockState(televisor)).alturaPantalla(), 0);
			Vec3 d = pantalla.subtract(ojos);
			mc.player.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
			mc.player.setXRot((float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
			mc.player.yRotO = mc.player.getYRot();
			mc.player.xRotO = mc.player.getXRot();
		}
		mc.setScreen(TelevisorScreen.tele(SesionNes.consola));
	}

	static void subir() {
		if (!activa()) {
			ultimo = System.nanoTime();
		}
		arriba = true;
	}

	/** Al cerrar la pantalla: baja el control y le avisa al servidor para levantarse. */
	static void terminar() {
		arriba = false;
		if (Minecraft.getInstance().getConnection() != null) {
			ClientPlayNetworking.send(SesionesNes.DEJAR, PacketByteBufs.empty());
		}
	}

	public static boolean activa() {
		return arriba || progreso > 0;
	}

	/** Televisor que muestra el juego ahora, o null. */
	static BlockPos teleActiva() {
		return activa() ? tele : null;
	}

	static BlockPos nesActiva() {
		return activa() ? nes : null;
	}

	/** Las manos con el control de la consola que se está jugando (para el mixin de las manos). */
	public static void dibujarManos(PoseStack pose, MultiBufferSource.BufferSource buffers, LocalPlayer jugador, int luz) {
		if (consola.manos != null) {
			consola.manos.dibujar(pose, buffers, jugador, luz);
		} else {
			ManosNes.dibujar(pose, buffers, jugador, luz);
		}
	}

	/** La consola de la última partida (sigue valiendo mientras el control baja). */
	static ConsolaTele consola() {
		return consola;
	}

	/** Avanza la animación; llamarlo una vez por cuadro (desde el dibujo de las manos). */
	static float avanzar() {
		long ahora = System.nanoTime();
		float paso = (ahora - ultimo) / 1e6f / DURACION_MS;
		ultimo = ahora;
		progreso = arriba ? Math.min(1, progreso + paso) : Math.max(0, progreso - paso);
		return suavizado();
	}

	static float suavizado() {
		return progreso * progreso * (3 - 2 * progreso);
	}

	/** Campo de visión mientras se juega: pasa suave del normal al de la barra de FOV de la consola. */
	public static double fov(double normal) {
		return normal + (consola.emulador().fovConsola() - normal) * suavizado();
	}
}
