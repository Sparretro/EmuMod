package com.emumod.client;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Primera persona mientras se juega con la Game Boy Advance: el personaje se lleva la consola a los ojos con las dos
 * manos (y la baja al salir). Reemplaza el dibujo normal de las manos desde {@code ManosMixin}.
 */
public final class ManosGba {
	private static final float DURACION_MS = 450;
	// Pose final delante de los ojos y cuánto más abajo arranca
	private static final float Y_ARRIBA = -0.03f, Z_ARRIBA = -0.54f, BAJADA = 0.95f, INCLINACION_ABAJO = -45;
	private static final float ESCALA_CONSOLA = 0.85f;
	// Agarre de la mano derecha respecto del centro de la consola (la izquierda es el espejo) y hacia dónde sube el brazo
	private static final float AGARRE_X = 0.41f, AGARRE_Y = -0.13f, AGARRE_Z = -0.03f;
	private static final float BRAZO_X = 0.32f, BRAZO_Y = 0.93f, BRAZO_Z = -0.18f;
	private static final float GIRO_MANO = 0;

	private static boolean arriba;
	private static float progreso;
	private static long ultimo;
	private static int botones;
	// Del cubo del ítem de la consola a coordenadas de GUI, para clickear los botones; null si no se dibujó
	private static Matrix4f matrizGui;

	private ManosGba() {
	}

	public static void subir() {
		if (!activa()) {
			ultimo = System.nanoTime();
		}
		arriba = true;
	}

	public static void bajar() {
		arriba = false;
	}

	/** Botones del GBA que se ven apretados en la consola. */
	static void setBotones(int mascara) {
		botones = mascara;
	}

	static Matrix4f matrizGui() {
		return matrizGui;
	}

	/** Campo de visión mientras se sostiene la consola: pasa suave del normal al de la barra de FOV. */
	public static double fov(double normal) {
		return normal + (EmuladorCliente.get().fovConsola() - normal) * suavizado();
	}

	private static float suavizado() {
		return progreso * progreso * (3 - 2 * progreso);
	}

	public static boolean activa() {
		return arriba || progreso > 0;
	}

	private static void avanzar() {
		long ahora = System.nanoTime();
		float paso = (ahora - ultimo) / 1e6f / DURACION_MS;
		ultimo = ahora;
		progreso = arriba ? Math.min(1, progreso + paso) : Math.max(0, progreso - paso);
		if (progreso == 0) {
			matrizGui = null;
		}
	}

	public static void dibujar(PoseStack pose, MultiBufferSource.BufferSource buffers, LocalPlayer jugador, int luz) {
		avanzar();
		// Arranque y llegada suaves
		float bajo = 1 - suavizado();
		pose.pushPose();
		pose.translate(0, Y_ARRIBA - bajo * BAJADA, Z_ARRIBA);
		pose.mulPose(Axis.XP.rotationDegrees(bajo * INCLINACION_ABAJO));
		if (!jugador.isInvisible()) {
			mano(pose, buffers, luz, jugador, HumanoidArm.RIGHT);
			mano(pose, buffers, luz, jugador, HumanoidArm.LEFT);
		}
		pose.pushPose();
		pose.scale(ESCALA_CONSOLA, ESCALA_CONSOLA, ESCALA_CONSOLA);
		pose.translate(-0.5f, -0.5f, -0.5f);
		EmuladorCliente emulador = EmuladorCliente.get();
		ModeloGba3D.dibujar(pose, buffers, luz, OverlayTexture.NO_OVERLAY, botones, emulador.hayJuego() ? emulador.textura() : null,
				emulador.pausadoPorJugador());
		Window ventana = Minecraft.getInstance().getWindow();
		float ancho = ventana.getGuiScaledWidth(), alto = ventana.getGuiScaledHeight();
		matrizGui = new Matrix4f().translate(ancho / 2, alto / 2, 0).scale(ancho / 2, -alto / 2, 1)
				.mul(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
		pose.popPose();
		pose.popPose();
		buffers.endBatch();
	}

	// Cada mano agarra un costado de la consola, abajo, y el brazo baja hacia el hombro fuera de la vista
	private static void mano(PoseStack pose, MultiBufferSource buffers, int luz, LocalPlayer jugador, HumanoidArm lado) {
		brazo(pose, buffers, luz, jugador, lado, AGARRE_X, AGARRE_Y, AGARRE_Z, BRAZO_X, BRAZO_Y, BRAZO_Z);
	}

	/**
	 * Dibuja el brazo con la mano en el agarre (x, y, z) del lado derecho; el izquierdo es el espejo en x. El brazo
	 * sube desde el hombro en la dirección (bx, by, bz), también del lado derecho.
	 */
	static void brazo(PoseStack pose, MultiBufferSource buffers, int luz, LocalPlayer jugador, HumanoidArm lado,
			float x, float y, float z, float bx, float by, float bz) {
		PlayerRenderer renderer = (PlayerRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(jugador);
		float f = lado == HumanoidArm.RIGHT ? 1 : -1;
		pose.pushPose();
		pose.translate(f * x, y, z);
		// El brazo del modelo va del hombro (y = 0) a la mano (y = 0,75): su eje +y apunta del hombro al agarre
		pose.mulPose(new Quaternionf().rotationTo(0, 1, 0, -f * bx, by, bz));
		pose.mulPose(Axis.YP.rotationDegrees(f * GIRO_MANO));
		// Lleva la punta de la mano al origen (el brazo derecho está corrido 6 píxeles a la izquierda de su pivote)
		pose.translate(f * 0.375f, -0.75f, 0);
		if (lado == HumanoidArm.RIGHT) {
			renderer.renderRightHand(pose, buffers, luz, jugador);
		} else {
			renderer.renderLeftHand(pose, buffers, luz, jugador);
		}
		pose.popPose();
	}
}
