package com.emumod.client;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.emumod.client.emu.Nucleo.Boton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Primera persona jugando a la NES: el control de NES en las dos manos, abajo, para ver el televisor por encima.
 * Los botones apretados se hunden y oscurecen, y se pueden clickear con el mouse.
 */
public final class ManosNes {
	private static final String MODELO = "control_nes";
	// El control mide 0,36 de ancho, abajo y un poco inclinado hacia la cámara
	private static final float ANCHO = 0.34f, Y = -0.21f, Z = -0.45f, INCLINACION = 50, BAJADA = 0.7f;
	// Agarre de la mano derecha respecto del centro del control (la izquierda es el espejo) y hacia dónde sube el brazo
	private static final float AGARRE_X = 0.16f, AGARRE_Y = -0.05f, AGARRE_Z = 0.03f;
	private static final float BRAZO_X = 0.3f, BRAZO_Y = 0.94f, BRAZO_Z = -0.2f;
	// Coordenadas del modelo: x a la derecha, y hacia arriba (las tapas de los botones) y -z hacia el borde de lejos
	private static final float TAPAS_Y = 1.94f;
	private static final float CRUZ_X = -4.029f, CRUZ_Z = 0.333f, CRUZ_BRAZO = 1.05f;
	private static final float HUNDIDO = 0.3f;

	private record BotonControl(String pieza, int mascara, float x, float z, float radio) {
	}

	private static final BotonControl[] BOTONES = {
			new BotonControl("BtnA_Controller_0", Boton.A.mascara(), 4.473f, 0.873f, 0.62f),
			new BotonControl("BtnB_Controller_0", Boton.B.mascara(), 2.85f, 0.873f, 0.62f),
			new BotonControl("BtnSelect_Controller_0", Boton.SELECT.mascara(), -1.056f, 0.92f, 0.55f),
			new BotonControl("BtnStart_Controller_0", Boton.START.mascara(), 0.41f, 0.92f, 0.55f),
	};
	private static final int CRUZ = Boton.ARRIBA.mascara() | Boton.ABAJO.mascara() | Boton.IZQUIERDA.mascara() | Boton.DERECHA.mascara();

	private static int botones;
	// Del modelo del control a coordenadas de GUI, para clickear sus botones; null si no se dibujó
	private static Matrix4f matrizGui;

	private ManosNes() {
	}

	static void setBotones(int mascara) {
		botones = mascara;
	}

	public static void dibujar(PoseStack pose, MultiBufferSource.BufferSource buffers, LocalPlayer jugador, int luz) {
		float bajo = 1 - SesionNes.avanzar();
		pose.pushPose();
		pose.translate(0, Y - bajo * BAJADA, Z);
		if (!jugador.isInvisible()) {
			ManosGba.brazo(pose, buffers, luz, jugador, HumanoidArm.RIGHT, AGARRE_X, AGARRE_Y, AGARRE_Z, BRAZO_X, BRAZO_Y, BRAZO_Z);
			ManosGba.brazo(pose, buffers, luz, jugador, HumanoidArm.LEFT, AGARRE_X, AGARRE_Y, AGARRE_Z, BRAZO_X, BRAZO_Y, BRAZO_Z);
		}
		pose.mulPose(Axis.XP.rotationDegrees(INCLINACION));
		Modelo3D control = Modelo3D.de(MODELO);
		control.centrar(pose, ANCHO);
		control.dibujar(pose, buffers, luz, OverlayTexture.NO_OVERLAY, ManosNes::ajuste);
		Window ventana = Minecraft.getInstance().getWindow();
		float ancho = ventana.getGuiScaledWidth(), alto = ventana.getGuiScaledHeight();
		matrizGui = !SesionNes.activa() ? null : new Matrix4f().translate(ancho / 2, alto / 2, 0).scale(ancho / 2, -alto / 2, 1)
				.mul(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
		pose.popPose();
		buffers.endBatch();
	}

	// Botón apretado: se hunde y se oscurece
	private static float[] ajuste(String pieza) {
		if (pieza.equals("DPad_Controller_0")) {
			return (botones & CRUZ) != 0 ? new float[] {0, -HUNDIDO, 0, 0.6f} : null;
		}
		for (BotonControl b : BOTONES) {
			if (b.pieza.equals(pieza)) {
				return (botones & b.mascara) != 0 ? new float[] {0, -HUNDIDO, 0, 0.55f} : null;
			}
		}
		return null;
	}

	/** Botón del control bajo el mouse (coordenadas de GUI), proyectado con la misma pose con la que se dibujó. */
	static int mascaraEn(double mouseX, double mouseY) {
		Matrix4f m = matrizGui;
		if (m == null) {
			return 0;
		}
		// Cruz: el mouse se pasa al plano de las tapas resolviendo con los dos ejes proyectados
		Vector4f centro = proyectar(m, CRUZ_X, CRUZ_Z);
		Vector4f ejeX = proyectar(m, CRUZ_X + 1, CRUZ_Z).sub(centro);
		Vector4f ejeZ = proyectar(m, CRUZ_X, CRUZ_Z + 1).sub(centro);
		double mx = mouseX - centro.x, my = mouseY - centro.y;
		double det = ejeX.x * ejeZ.y - ejeZ.x * ejeX.y;
		if (Math.abs(det) > 1e-6) {
			double a = (mx * ejeZ.y - ejeZ.x * my) / det;
			double b = (ejeX.x * my - mx * ejeX.y) / det;
			if (a * a + b * b <= CRUZ_BRAZO * CRUZ_BRAZO * 1.3) {
				double muerta = CRUZ_BRAZO * 0.3;
				int mascara = 0;
				if (a < -muerta) mascara |= Boton.IZQUIERDA.mascara();
				if (a > muerta) mascara |= Boton.DERECHA.mascara();
				// -z es el borde de lejos del control: arriba en la cruz
				if (b < -muerta) mascara |= Boton.ARRIBA.mascara();
				if (b > muerta) mascara |= Boton.ABAJO.mascara();
				if (mascara != 0) {
					return mascara;
				}
			}
		}
		for (BotonControl boton : BOTONES) {
			Vector4f c = proyectar(m, boton.x, boton.z);
			Vector4f borde = proyectar(m, boton.x + boton.radio, boton.z);
			if (Math.hypot(mouseX - c.x, mouseY - c.y) <= Math.hypot(borde.x - c.x, borde.y - c.y)) {
				return boton.mascara;
			}
		}
		return 0;
	}

	private static Vector4f proyectar(Matrix4f m, float x, float z) {
		Vector4f p = m.transform(new Vector4f(x, TAPAS_Y, z, 1));
		return p.div(p.w);
	}
}
