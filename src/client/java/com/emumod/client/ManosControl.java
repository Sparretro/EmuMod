package com.emumod.client;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.emumod.EmuMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Primera persona jugando a la N64 o a la PS2: el control (un modelo de una sola pieza) en las dos manos, abajo, para ver
 * el televisor por encima. Los botones apretados se marcan con una sombra encima y las palancas con un punto que se corre
 * hacia donde se las empuja. Los botones y las palancas se pueden usar con el mouse.
 */
public final class ManosControl {
	private static final ResourceLocation BLANCO = EmuMod.id("textures/modelo/blanco.png");
	// Más hondos que el de la NES (los mangos vienen hacia el jugador): chicos y lejos para que no tapen la tele
	private static final float Y = -0.245f, Z = -0.5f, INCLINACION = 55, BAJADA = 0.7f;
	private static final float BRAZO_X = 0.3f, BRAZO_Y = 0.94f, BRAZO_Z = -0.2f;

	/** Botón redondo sobre las tapas, en coordenadas del modelo (x a la derecha, -z hacia el borde de lejos). */
	private record BotonControl(int mascara, float x, float z, float radio) {
	}

	/** Palanca analógica: centro, cuánto se mueve la punta y la altura de su tapa. */
	private record Palanca(float x, float z, float radio, float alto) {
	}

	// Control de N64 de synthetic worlds (CC BY 4.0)
	static final ManosControl N64 = new ManosControl("control_n64", 0.3f, 0.12f, -0.04f, 0.03f, 6.1f,
			-4.8f, -2.4f, 1.05f, new int[] {BotonN64.ARRIBA.mascara, BotonN64.ABAJO.mascara, BotonN64.IZQUIERDA.mascara, BotonN64.DERECHA.mascara},
			new BotonControl[] {
					new BotonControl(BotonN64.A.mascara, 3.9f, -0.78f, 0.62f),
					new BotonControl(BotonN64.B.mascara, 2.77f, -1.98f, 0.58f),
					new BotonControl(BotonN64.START.mascara, 0, -1.87f, 0.52f),
					new BotonControl(BotonN64.C_ARRIBA.mascara, 5.13f, -3.83f, 0.45f),
					new BotonControl(BotonN64.C_IZQUIERDA.mascara, 4.27f, -3.0f, 0.45f),
					new BotonControl(BotonN64.C_DERECHA.mascara, 6.15f, -2.95f, 0.45f),
					new BotonControl(BotonN64.C_ABAJO.mascara, 5.18f, -2.1f, 0.45f),
			},
			new Palanca[] {new Palanca(0, 1.3f, 1.6f, 6.3f)});

	// DualShock 2 de rhcreations (CC BY 4.0); las palancas son más altas que los botones
	static final ManosControl PS2 = new ManosControl("control_ps2", 0.3f, 0.12f, -0.06f, 0.03f, 0.25f,
			-2.77f, -0.53f, 0.85f, new int[] {BotonPs2.ARRIBA.mascara, BotonPs2.ABAJO.mascara, BotonPs2.IZQUIERDA.mascara, BotonPs2.DERECHA.mascara},
			new BotonControl[] {
					new BotonControl(BotonPs2.TRIANGULO.mascara, 2.93f, -1.38f, 0.36f),
					new BotonControl(BotonPs2.CUADRADO.mascara, 2.18f, -0.54f, 0.36f),
					new BotonControl(BotonPs2.CIRCULO.mascara, 3.7f, -0.53f, 0.36f),
					new BotonControl(BotonPs2.EQUIS.mascara, 2.89f, 0.3f, 0.36f),
					new BotonControl(BotonPs2.SELECT.mascara, -0.75f, -0.58f, 0.22f),
					new BotonControl(BotonPs2.START.mascara, 0.79f, -0.58f, 0.22f),
			},
			new Palanca[] {new Palanca(-1.51f, 0.89f, 0.75f, 0.85f), new Palanca(1.5f, 0.9f, 0.75f, 0.85f)});

	// DualShock 3 de DarkFire (CC BY 4.0), con los mismos botones que el de PS2
	static final ManosControl PS3 = new ManosControl("control_ps3", 0.3f, 0.12f, -0.07f, 0.04f, 3.0f,
			-3.18f, -1.05f, 0.86f, new int[] {BotonPs2.ARRIBA.mascara, BotonPs2.ABAJO.mascara, BotonPs2.IZQUIERDA.mascara, BotonPs2.DERECHA.mascara},
			new BotonControl[] {
					new BotonControl(BotonPs2.TRIANGULO.mascara, 3.13f, -1.9f, 0.3f),
					new BotonControl(BotonPs2.CUADRADO.mascara, 2.3f, -1.0f, 0.3f),
					new BotonControl(BotonPs2.CIRCULO.mascara, 4.05f, -1.04f, 0.3f),
					new BotonControl(BotonPs2.EQUIS.mascara, 3.21f, -0.25f, 0.3f),
					new BotonControl(BotonPs2.SELECT.mascara, -1.0f, -1.04f, 0.25f),
					new BotonControl(BotonPs2.START.mascara, 0.95f, -1.04f, 0.25f),
			},
			new Palanca[] {new Palanca(-1.72f, 0.4f, 0.69f, 3.68f), new Palanca(1.63f, 0.41f, 0.69f, 3.68f)});

	// Wiimote de Twisty_z (CC BY 4.0), de costado: la cruz a la izquierda y el 1 y el 2 a la derecha. No tiene palancas
	static final ManosControl WII = new ManosControl("control_wii", 0.27f, 0.11f, -0.02f, -0.01f, 0.24f,
			-1.54f, -0.03f, 0.23f, new int[] {BotonWii.ARRIBA.mascara, BotonWii.ABAJO.mascara, BotonWii.IZQUIERDA.mascara, BotonWii.DERECHA.mascara},
			new BotonControl[] {
					new BotonControl(BotonWii.A.mascara, -0.82f, -0.03f, 0.18f),
					new BotonControl(BotonWii.MAS.mascara, -0.02f, -0.34f, 0.1f),
					new BotonControl(BotonWii.HOME.mascara, -0.02f, -0.03f, 0.1f),
					new BotonControl(BotonWii.MENOS.mascara, -0.02f, 0.3f, 0.1f),
					new BotonControl(BotonWii.UNO.mascara, 1.22f, -0.03f, 0.15f),
					new BotonControl(BotonWii.DOS.mascara, 1.66f, -0.03f, 0.15f),
			},
			new Palanca[0]);

	static {
		WII.subida = 0.07f;
	}

	private final String modelo;
	private final float ancho;
	// Cuánto más arriba que los otros va este control (el Wiimote es finito y abajo no se ve)
	private float subida;
	// Agarre de la mano derecha respecto del centro del control (la izquierda es el espejo)
	private final float agarreX, agarreY, agarreZ;
	private final float tapasY;
	private final float cruzX, cruzZ, cruzBrazo;
	// Arriba, abajo, izquierda y derecha de la cruz
	private final int[] cruz;
	private final int mascaraCruz;
	private final BotonControl[] botones;
	private final Palanca[] palancas;

	private int apretados;
	private final int[][] posicionPalancas;
	// Del modelo del control a coordenadas de GUI, para clickear sus botones; null si no se dibujó
	private Matrix4f matrizGui;

	private ManosControl(String modelo, float ancho, float agarreX, float agarreY, float agarreZ, float tapasY, float cruzX, float cruzZ,
			float cruzBrazo, int[] cruz, BotonControl[] botones, Palanca[] palancas) {
		this.modelo = modelo;
		this.ancho = ancho;
		this.agarreX = agarreX;
		this.agarreY = agarreY;
		this.agarreZ = agarreZ;
		this.tapasY = tapasY;
		this.cruzX = cruzX;
		this.cruzZ = cruzZ;
		this.cruzBrazo = cruzBrazo;
		this.cruz = cruz;
		mascaraCruz = cruz[0] | cruz[1] | cruz[2] | cruz[3];
		this.botones = botones;
		this.palancas = palancas;
		posicionPalancas = new int[palancas.length][2];
	}

	String modelo() {
		return modelo;
	}

	/** Botones apretados y posición de cada palanca (x e y de -32767 a 32767, y positiva = hacia el jugador). */
	void setEstado(int mascara, int[]... palancas) {
		apretados = mascara;
		for (int i = 0; i < posicionPalancas.length && i < palancas.length; i++) {
			posicionPalancas[i][0] = palancas[i][0];
			posicionPalancas[i][1] = palancas[i][1];
		}
	}

	public void dibujar(PoseStack pose, MultiBufferSource.BufferSource buffers, LocalPlayer jugador, int luz) {
		float bajo = 1 - SesionNes.avanzar();
		pose.pushPose();
		pose.translate(0, Y + subida - bajo * BAJADA, Z);
		if (!jugador.isInvisible()) {
			ManosGba.brazo(pose, buffers, luz, jugador, HumanoidArm.RIGHT, agarreX, agarreY, agarreZ, BRAZO_X, BRAZO_Y, BRAZO_Z);
			ManosGba.brazo(pose, buffers, luz, jugador, HumanoidArm.LEFT, agarreX, agarreY, agarreZ, BRAZO_X, BRAZO_Y, BRAZO_Z);
		}
		pose.mulPose(Axis.XP.rotationDegrees(INCLINACION));
		Modelo3D control = Modelo3D.de(modelo);
		control.centrar(pose, ancho);
		control.dibujar(pose, buffers, luz, OverlayTexture.NO_OVERLAY, Modelo3D.Ajuste.NINGUNO);
		dibujarApretados(pose, buffers, luz);
		Window ventana = Minecraft.getInstance().getWindow();
		float anchoGui = ventana.getGuiScaledWidth(), altoGui = ventana.getGuiScaledHeight();
		matrizGui = !SesionNes.activa() ? null : new Matrix4f().translate(anchoGui / 2, altoGui / 2, 0).scale(anchoGui / 2, -altoGui / 2, 1)
				.mul(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
		pose.popPose();
		buffers.endBatch();
	}

	private void dibujarApretados(PoseStack pose, MultiBufferSource buffers, int luz) {
		VertexConsumer v = buffers.getBuffer(TiposRender.entidadTriangulos(BLANCO, true));
		Matrix4f m = pose.last().pose();
		Matrix3f n = pose.last().normal();
		// La sombra va apenas encima de las tapas, para que no se hunda en los botones más altos
		float altura = tapasY + botones[botones.length - 1].radio * 0.6f;
		for (BotonControl b : botones) {
			if ((apretados & b.mascara) != 0) {
				disco(v, m, n, luz, b.x, altura, b.z, b.radio, 0, 0, 0, 120);
			}
		}
		if ((apretados & mascaraCruz) != 0) {
			float dx = ((apretados & cruz[3]) != 0 ? 1 : 0) - ((apretados & cruz[2]) != 0 ? 1 : 0);
			float dz = ((apretados & cruz[1]) != 0 ? 1 : 0) - ((apretados & cruz[0]) != 0 ? 1 : 0);
			disco(v, m, n, luz, cruzX + dx * cruzBrazo * 0.6f, altura, cruzZ + dz * cruzBrazo * 0.6f, cruzBrazo * 0.38f, 0, 0, 0, 130);
		}
		for (int i = 0; i < palancas.length; i++) {
			int[] p = posicionPalancas[i];
			if (p[0] != 0 || p[1] != 0) {
				Palanca palanca = palancas[i];
				float px = palanca.x + p[0] / 32767f * palanca.radio * 0.6f, pz = palanca.z + p[1] / 32767f * palanca.radio * 0.6f;
				disco(v, m, n, luz, px, palanca.alto + 0.05f, pz, palanca.radio * 0.22f, 255, 230, 80, 200);
			}
		}
	}

	private static void disco(VertexConsumer v, Matrix4f m, Matrix3f n, int luz, float x, float y, float z, float radio, int r, int g, int b, int a) {
		int lados = 16;
		for (int i = 0; i < lados; i++) {
			double a0 = 2 * Math.PI * i / lados, a1 = 2 * Math.PI * (i + 1) / lados;
			vertice(v, m, n, luz, x, y, z, r, g, b, a);
			vertice(v, m, n, luz, x + radio * (float) Math.cos(a1), y, z + radio * (float) Math.sin(a1), r, g, b, a);
			vertice(v, m, n, luz, x + radio * (float) Math.cos(a0), y, z + radio * (float) Math.sin(a0), r, g, b, a);
		}
	}

	private static void vertice(VertexConsumer v, Matrix4f m, Matrix3f n, int luz, float x, float y, float z, int r, int g, int b, int a) {
		v.vertex(m, x, y, z).color(r, g, b, a).uv(0.5f, 0.5f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(n, 0, 1, 0).endVertex();
	}

	/** Botón del control bajo el mouse (coordenadas de GUI), proyectado con la misma pose con la que se dibujó. */
	int mascaraEn(double mouseX, double mouseY) {
		Matrix4f m = matrizGui;
		if (m == null) {
			return 0;
		}
		double[] enCruz = enPlano(m, cruzX, cruzZ, mouseX, mouseY);
		if (enCruz != null && enCruz[0] * enCruz[0] + enCruz[1] * enCruz[1] <= cruzBrazo * cruzBrazo * 1.3) {
			double muerta = cruzBrazo * 0.3;
			int mascara = 0;
			if (enCruz[0] < -muerta) mascara |= cruz[2];
			if (enCruz[0] > muerta) mascara |= cruz[3];
			// -z es el borde de lejos del control: arriba en la cruz
			if (enCruz[1] < -muerta) mascara |= cruz[0];
			if (enCruz[1] > muerta) mascara |= cruz[1];
			if (mascara != 0) {
				return mascara;
			}
		}
		for (BotonControl boton : botones) {
			Vector4f c = proyectar(m, boton.x, boton.z);
			Vector4f borde = proyectar(m, boton.x + boton.radio, boton.z);
			if (Math.hypot(mouseX - c.x, mouseY - c.y) <= Math.hypot(borde.x - c.x, borde.y - c.y)) {
				return boton.mascara;
			}
		}
		return 0;
	}

	/**
	 * Palanca empujada con el mouse sobre ella: {índice de la palanca, x, y} con x e y de -32767 a 32767 (y positiva =
	 * hacia el jugador, abajo), o null si el mouse no está sobre ninguna.
	 */
	int[] palancaEn(double mouseX, double mouseY) {
		Matrix4f m = matrizGui;
		if (m == null) {
			return null;
		}
		for (int i = 0; i < palancas.length; i++) {
			Palanca palanca = palancas[i];
			double[] p = enPlano(m, palanca.x, palanca.z, mouseX, mouseY);
			if (p == null) {
				continue;
			}
			double largo = Math.hypot(p[0], p[1]) / palanca.radio;
			if (largo > 1.4) {
				continue;
			}
			double escala = Controles.PALANCA_MAXIMA / palanca.radio / Math.max(1, largo);
			return new int[] {i, (int) Math.round(p[0] * escala), (int) Math.round(p[1] * escala)};
		}
		return null;
	}

	/** Como {@link #palancaEn}, pero para seguir arrastrando una palanca ya agarrada aunque el mouse se salga de ella. */
	int[] arrastrarPalanca(int indice, double mouseX, double mouseY) {
		Matrix4f m = matrizGui;
		if (m == null || indice >= palancas.length) {
			return null;
		}
		Palanca palanca = palancas[indice];
		double[] p = enPlano(m, palanca.x, palanca.z, mouseX, mouseY);
		if (p == null) {
			return null;
		}
		double escala = Controles.PALANCA_MAXIMA / palanca.radio / Math.max(1, Math.hypot(p[0], p[1]) / palanca.radio);
		return new int[] {indice, (int) Math.round(p[0] * escala), (int) Math.round(p[1] * escala)};
	}

	// El mouse pasado al plano de las tapas, relativo a (x, z), resolviendo con los dos ejes proyectados
	private double[] enPlano(Matrix4f m, float x, float z, double mouseX, double mouseY) {
		Vector4f centro = proyectar(m, x, z);
		Vector4f ejeX = proyectar(m, x + 1, z).sub(centro);
		Vector4f ejeZ = proyectar(m, x, z + 1).sub(centro);
		double mx = mouseX - centro.x, my = mouseY - centro.y;
		double det = ejeX.x * ejeZ.y - ejeZ.x * ejeX.y;
		if (Math.abs(det) < 1e-6) {
			return null;
		}
		return new double[] {(mx * ejeZ.y - ejeZ.x * my) / det, (ejeX.x * my - mx * ejeX.y) / det};
	}

	private Vector4f proyectar(Matrix4f m, float x, float z) {
		Vector4f p = m.transform(new Vector4f(x, tapasY, z, 1));
		return p.div(p.w);
	}
}
