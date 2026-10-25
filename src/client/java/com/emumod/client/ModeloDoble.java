package com.emumod.client;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.emumod.client.emu.Nucleo.Boton;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Dibuja una portátil de dos pantallas (la 3DS o la DS, ver {@link PortatilDoble}) con el juego en sus pantallas y
 * sus botones hundidos, y pasa un punto del mouse a un toque en la pantalla táctil o a un botón. {@link #dibujar} la
 * deja en el cubo 0..1 de un ítem con el lado del jugador hacia +z, como los modelos de Minecraft.
 */
final class ModeloDoble {
	// Un poco por delante de cada pantalla del modelo, para que no se mezclen
	private static final float SEPARACION = 0.012f;
	private static final float HUNDIDO = 0.07f;
	// La palanca circular se inclina hasta este desplazamiento
	private static final float RECORRIDO_PALANCA = 0.22f;

	private ModeloDoble() {
	}

	/**
	 * Dibuja la consola en el cubo 0..1 de la pose actual.
	 *
	 * @param botones máscara del RetroPad apretada (se dibujan hundidos los que son piezas sueltas)
	 * @param palanca palanca circular, x e y de -32767 a 32767 (y positiva = abajo), o null
	 * @param pantalla textura del juego (las dos pantallas, una arriba de la otra), o null para dejarlas apagadas
	 * @param pausado oscurece las pantallas
	 */
	static void dibujar(PortatilDoble p, PoseStack pose, MultiBufferSource buffers, int luz, int overlay, int botones, int[] palanca,
			ResourceLocation pantalla, boolean pausado) {
		pose.pushPose();
		aplicarOrientacion(p, pose);
		Modelo3D.de(p.nombreModelo).dibujar(pose, buffers, luz, overlay, pieza -> ajuste(p, pieza, botones, palanca));
		if (pantalla != null) {
			VertexConsumer v = buffers.getBuffer(tipoPantalla(p, pantalla));
			int c = pausado ? 90 : 255;
			cuadrilatero(v, pose.last().pose(), p.arriba, p.normalArriba, p.uvArriba, c, p.unidad);
			if (p.abajo != null) {
				cuadrilatero(v, pose.last().pose(), p.abajo, new float[] {0, 1, 0}, p.uvAbajo, c, p.unidad);
			}
		}
		pose.popPose();
	}

	private static float[] ajuste(PortatilDoble p, String pieza, int botones, int[] palanca) {
		for (PortatilDoble.BotonModelo b : p.botones) {
			if (PortatilDoble.esPieza(b.pieza(), pieza)) {
				return (botones & b.mascara()) != 0 ? new float[] {0, -HUNDIDO * p.unidad, 0, 0.7f} : null;
			}
		}
		if (PortatilDoble.esPieza(p.cruz.pieza(), pieza)) {
			int cruz = Boton.ARRIBA.mascara() | Boton.ABAJO.mascara() | Boton.IZQUIERDA.mascara() | Boton.DERECHA.mascara();
			return (botones & cruz) != 0 ? new float[] {0, -HUNDIDO * 0.6f * p.unidad, 0, 0.75f} : null;
		}
		if (p.palanca != null && pieza.equals(p.palanca.pieza()) && palanca != null && (palanca[0] != 0 || palanca[1] != 0)) {
			// x de la palanca hacia la derecha del jugador; y positiva, hacia el jugador (lejos de la bisagra)
			float px = palanca[0] / 32767f * RECORRIDO_PALANCA * p.unidad, py = -palanca[1] / 32767f * RECORRIDO_PALANCA * p.unidad;
			return new float[] {px * p.derecha[0] + py * p.haciaBisagra[0], 0, px * p.derecha[1] + py * p.haciaBisagra[1], 1};
		}
		return null;
	}

	// La pantalla brilla sola: sin sombreado y a luz máxima. Esquinas: arriba izquierda, arriba derecha, abajo derecha,
	// abajo izquierda; uv: u0, v0, u1, v1 del cuadro del núcleo
	private static void cuadrilatero(VertexConsumer v, Matrix4f m, float[][] e, float[] normal, float[] uv, int c, float unidad) {
		float s = SEPARACION * unidad;
		int brillo = LightTexture.FULL_BRIGHT;
		float[][] t = {{uv[0], uv[1]}, {uv[2], uv[1]}, {uv[2], uv[3]}, {uv[0], uv[3]}};
		for (int i : new int[] {0, 3, 2, 1}) {
			v.vertex(m, e[i][0] + normal[0] * s, e[i][1] + normal[1] * s, e[i][2] + normal[2] * s)
					.color(c, c, c, 255).uv(t[i][0], t[i][1]).uv2(brillo).endVertex();
		}
	}

	// Un tipo de render por consola (cada una tiene su textura; el de la GBA guarda la suya)
	private static final RenderType[] TIPOS = new RenderType[PortatilDoble.values().length];
	private static final ResourceLocation[] TEXTURAS = new ResourceLocation[PortatilDoble.values().length];

	private static RenderType tipoPantalla(PortatilDoble p, ResourceLocation textura) {
		int i = p.ordinal();
		if (!textura.equals(TEXTURAS[i])) {
			TEXTURAS[i] = textura;
			TIPOS[i] = TiposRender.pantalla("emumod_pantalla_" + p.nombreModelo, textura, false);
		}
		return TIPOS[i];
	}

	/** Del cubo 0..1 a coordenadas del modelo: centrado, escalado y con el lado del jugador mirando a +z. */
	static void aplicarOrientacion(PortatilDoble p, PoseStack pose) {
		pose.translate(0.5f, 0.5f, 0.5f);
		pose.scale(p.escala, p.escala, p.escala);
		if (p.darVuelta) {
			pose.mulPose(Axis.YP.rotationDegrees(180));
		}
		pose.translate(0, -p.centroY, -p.centroZ);
	}

	/** Posición en pantalla de un punto del modelo con la pose (de la GUI o de primera persona) y la orientación. */
	static Vector4f transformar(PortatilDoble p, Matrix4f pose, float x, float y, float z) {
		PoseStack temporal = new PoseStack();
		temporal.last().pose().set(pose);
		aplicarOrientacion(p, temporal);
		Vector4f punto = temporal.last().pose().transform(new Vector4f(x, y, z, 1));
		// División de perspectiva: en primera persona la matriz incluye la proyección (en la GUI w queda en 1)
		return punto.div(punto.w);
	}

	/**
	 * Punto (x, z) de la base de la consola que queda debajo del mouse; null si la base se ve de canto. En primera persona
	 * la consola está muy cerca de los ojos y la perspectiva deforma mucho: no alcanza con proyectar dos ejes desde un
	 * punto, así que se busca el punto con Newton sobre la proyección misma (converge en pocos pasos).
	 */
	static float[] puntoEnLaBase(PortatilDoble p, Matrix4f pose, double mouseX, double mouseY) {
		// Se arranca en el centro de la pantalla táctil, que es donde más se clickea (sin táctil, a la altura de la cruz)
		float y = p.superficieY();
		float x = p.abajo != null ? (p.abajo[0][0] + p.abajo[2][0]) / 2 : 0, z = p.abajo != null ? (p.abajo[0][2] + p.abajo[2][2]) / 2 : p.cruz.z();
		final float h = 0.05f * p.unidad;
		for (int i = 0; i < 12; i++) {
			Vector4f q = transformar(p, pose, x, y, z);
			double ex = mouseX - q.x, ey = mouseY - q.y;
			if (ex * ex + ey * ey < 1e-4) {
				break;
			}
			Vector4f dx = transformar(p, pose, x + h, y, z).sub(q).div(h);
			Vector4f dz = transformar(p, pose, x, y, z + h).sub(q).div(h);
			double determinante = dx.x * dz.y - dz.x * dx.y;
			if (Math.abs(determinante) < 1e-9) {
				return null;
			}
			double pasoX = (ex * dz.y - dz.x * ey) / determinante, pasoZ = (dx.x * ey - ex * dx.y) / determinante;
			// Pasos acotados: lejos de la consola la proyección se dobla y Newton podría saltar al otro lado de la cámara
			double largo = Math.hypot(pasoX, pasoZ);
			if (largo > 4) {
				pasoX *= 4 / largo;
				pasoZ *= 4 / largo;
			}
			x += (float) pasoX;
			z += (float) pasoZ;
		}
		return new float[] {x, z};
	}

	/**
	 * Toque en la pantalla de abajo: posición del puntero de libretro en el cuadro entero del núcleo (-32767 a 32767), o
	 * null si el punto de la base no cae en la pantalla táctil (o la consola no tiene).
	 */
	static int[] puntoTactil(PortatilDoble p, float x, float z) {
		if (p.abajo == null) {
			return null;
		}
		float u = (x - p.abajo[0][0]) / (p.abajo[1][0] - p.abajo[0][0]), v = (z - p.abajo[0][2]) / (p.abajo[3][2] - p.abajo[0][2]);
		if (u < 0 || u > 1 || v < 0 || v > 1) {
			return null;
		}
		float enCuadroX = p.uvAbajo[0] + u * (p.uvAbajo[2] - p.uvAbajo[0]), enCuadroY = p.uvAbajo[1] + v * (p.uvAbajo[3] - p.uvAbajo[1]);
		return new int[] {Math.round((enCuadroX * 2 - 1) * 32767), Math.round((enCuadroY * 2 - 1) * 32767)};
	}

	/** Botones del modelo en un punto de la base (máscara del RetroPad), o 0. */
	static int mascaraEn(PortatilDoble p, float x, float z) {
		for (PortatilDoble.BotonModelo b : p.botones) {
			if (Math.hypot(x - b.x(), z - b.z()) <= b.radio()) {
				return b.mascara();
			}
		}
		float dx = x - p.cruz.x(), dz = z - p.cruz.z();
		if (Math.hypot(dx, dz) > p.cruz.radio()) {
			return 0;
		}
		// Hacia la derecha del jugador y hacia la bisagra (arriba en la cruz)
		float d = dx * p.derecha[0] + dz * p.derecha[1], a = dx * p.haciaBisagra[0] + dz * p.haciaBisagra[1];
		float zonaMuerta = p.cruz.radio() * 0.3f;
		int mascara = 0;
		if (d > zonaMuerta) mascara |= Boton.DERECHA.mascara();
		if (d < -zonaMuerta) mascara |= Boton.IZQUIERDA.mascara();
		if (a > zonaMuerta) mascara |= Boton.ARRIBA.mascara();
		if (a < -zonaMuerta) mascara |= Boton.ABAJO.mascara();
		return mascara;
	}

	static boolean sobrePalanca(PortatilDoble p, float x, float z) {
		return p.palanca != null && Math.hypot(x - p.palanca.x(), z - p.palanca.z()) <= p.palanca.radio();
	}

	/** Palanca circular empujada desde un punto de la base: x e y de -32767 a 32767 (y positiva = abajo). */
	static int[] palancaDesde(PortatilDoble p, float x, float z) {
		float dx = x - p.palanca.x(), dz = z - p.palanca.z();
		double px = (dx * p.derecha[0] + dz * p.derecha[1]) / p.palanca.radio();
		double py = -(dx * p.haciaBisagra[0] + dz * p.haciaBisagra[1]) / p.palanca.radio();
		double largo = Math.hypot(px, py);
		if (largo > 1) {
			px /= largo;
			py /= largo;
		}
		return new int[] {(int) Math.round(px * 32767), (int) Math.round(py * 32767)};
	}
}
