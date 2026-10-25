package com.emumod.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Primera persona mientras se juega con la 3DS o la DS: como la GBA (ver {@link ManosGba}), el personaje se la lleva a
 * los ojos con las dos manos, abierta e inclinada para ver las dos pantallas. Reemplaza el dibujo normal de las manos
 * desde {@code ManosMixin}.
 */
public final class ManosDoble {
	private static final float DURACION_MS = 450;
	// Cuánto más abajo arranca y cuánto más inclinada
	private static final float BAJADA = 0.95f, INCLINACION_ABAJO = -45;

	// La consola que se está sosteniendo (o bajando)
	private static PortatilDoble consola = PortatilDoble.N3DS;
	private static boolean arriba;
	private static float progreso;
	private static long ultimo;
	private static int botones;
	private static int[] palanca;
	// Del cubo del ítem de la consola a coordenadas de GUI, para tocar la pantalla y los botones; null si no se dibujó
	private static Matrix4f matrizGui;

	private ManosDoble() {
	}

	public static void subir(PortatilDoble cual) {
		if (!activa()) {
			ultimo = System.nanoTime();
		}
		consola = cual;
		arriba = true;
	}

	public static void bajar() {
		arriba = false;
	}

	/** Botones y palanca circular que se ven apretados en la consola. */
	static void setEstado(int mascara, int[] palancaCircular) {
		botones = mascara;
		palanca = palancaCircular;
	}

	static Matrix4f matrizGui() {
		return matrizGui;
	}

	/** Campo de visión mientras se sostiene la consola: pasa suave del normal al de la barra de FOV. */
	public static double fov(double normal) {
		return normal + (EmuladorCliente.de(consola.sistema).fovConsola() - normal) * suavizado();
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
		PortatilDoble p = consola;
		// Arranque y llegada suaves
		float bajo = 1 - suavizado();
		pose.pushPose();
		pose.translate(0, p.manosY - bajo * BAJADA, p.manosZ);
		pose.mulPose(Axis.XP.rotationDegrees(bajo * INCLINACION_ABAJO));
		if (!jugador.isInvisible()) {
			for (HumanoidArm lado : HumanoidArm.values()) {
				ManosGba.brazo(pose, buffers, luz, jugador, lado, p.agarre[0], p.agarre[1], p.agarre[2], p.brazo[0], p.brazo[1], p.brazo[2]);
			}
		}
		pose.pushPose();
		// La consola se gira hacia los ojos: la tapa queda casi de frente y la pantalla táctil, inclinada hacia abajo
		pose.mulPose(Axis.XP.rotationDegrees(p.inclinacion));
		pose.scale(p.escalaManos, p.escalaManos, p.escalaManos);
		pose.translate(-0.5f, -0.5f, -0.5f);
		EmuladorCliente emulador = EmuladorCliente.de(p.sistema);
		ModeloDoble.dibujar(p, pose, buffers, luz, OverlayTexture.NO_OVERLAY, botones, palanca, emulador.hayJuego() ? emulador.textura() : null,
				emulador.pausadoPorJugador());
		Window ventana = Minecraft.getInstance().getWindow();
		float ancho = ventana.getGuiScaledWidth(), alto = ventana.getGuiScaledHeight();
		matrizGui = new Matrix4f().translate(ancho / 2, alto / 2, 0).scale(ancho / 2, -alto / 2, 1)
				.mul(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
		pose.popPose();
		pose.popPose();
		buffers.endBatch();
	}
}
