package com.emumod.client;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.emumod.EmuMod;
import com.emumod.client.emu.Nucleo.Boton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Modelo 3D de la Game Boy Advance: «Game Boy Retreat» de ForkyForklift (CC-BY-4.0), convertido con
 * {@code tools/ConvertirGlb.java}. Se usa en la mano y en la pantalla de juego; en su pantalla se dibuja el juego y
 * sus botones se hunden al apretarlos.
 *
 * <p>Coordenadas del modelo: x a la derecha, z hacia arriba y el frente mirando a -y. {@link #dibujar} lo deja de
 * pie en el cubo 0..1 de un ítem, con el frente hacia +z, como los modelos de ítem de Minecraft.
 */
final class ModeloGba3D {
	private static final ResourceLocation MALLA = EmuMod.id("modelos/gba.bin");
	private static final ResourceLocation TEXTURA_CUERPO = EmuMod.id("textures/modelo/gba_cuerpo.png");
	private static final ResourceLocation TEXTURA_CARTUCHO = EmuMod.id("textures/modelo/gba_cartucho.png");

	// Escala del modelo (2,48 de ancho) al cubo de un ítem
	static final float ESCALA = 1 / 2.6f;

	// Rectángulo de la pantalla en coordenadas del modelo, apenas delante del vidrio
	static final float PANTALLA_IZQ = -0.5410f, PANTALLA_DER = 0.5481f;
	static final float PANTALLA_ARRIBA = 0.4743f, PANTALLA_ABAJO = -0.2496f;
	static final float PANTALLA_Y = -0.1265f;

	// Grupos de vértices que escribe el conversor
	private static final int GRUPO_A = 1, GRUPO_B = 2, GRUPO_CRUZ = 3, GRUPO_START = 4, GRUPO_SELECT = 5;
	private static final float HUNDIDO = 0.025f;
	static final float CRUZ_X = -0.8727f, CRUZ_Z = 0.1340f, CRUZ_BRAZO = 0.16f;

	/** Botones clicables: máscara del GBA, centro (x, z) en el modelo y radio. */
	record BotonModelo(int mascara, float x, float z, float radio) {
	}

	static final BotonModelo[] BOTONES = {
			new BotonModelo(Boton.A.mascara(), 1.0058f, 0.1713f, 0.12f),
			new BotonModelo(Boton.B.mascara(), 0.7709f, 0.1160f, 0.12f),
			new BotonModelo(Boton.START.mascara(), -0.7374f, -0.2424f, 0.06f),
			new BotonModelo(Boton.SELECT.mascara(), -0.7374f, -0.3424f, 0.06f),
			// Gatillos: las esquinas de arriba
			new BotonModelo(Boton.L.mascara(), -0.95f, 0.62f, 0.16f),
			new BotonModelo(Boton.R.mascara(), 0.95f, 0.62f, 0.16f),
	};

	private record Malla(float[] datos, byte[] grupos, int[] indices) {
		// datos por vértice: x y z u v nx ny nz
	}

	private static Malla consola, cartucho;

	private ModeloGba3D() {
	}

	private static void cargar() {
		if (consola != null) {
			return;
		}
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(
				Minecraft.getInstance().getResourceManager().getResourceOrThrow(MALLA).open()))) {
			if (in.readInt() != 0x47424133) {
				throw new IOException("Formato desconocido");
			}
			consola = leerMalla(in);
			cartucho = leerMalla(in);
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo leer " + MALLA, e);
		}
	}

	private static Malla leerMalla(DataInputStream in) throws IOException {
		int n = in.readInt();
		float[] datos = new float[n * 8];
		byte[] grupos = new byte[n];
		for (int i = 0; i < n; i++) {
			for (int c = 0; c < 8; c++) {
				datos[i * 8 + c] = in.readFloat();
			}
			grupos[i] = in.readByte();
		}
		int[] indices = new int[in.readInt()];
		for (int i = 0; i < indices.length; i++) {
			indices[i] = in.readInt();
		}
		return new Malla(datos, grupos, indices);
	}

	/**
	 * Dibuja la consola en el cubo 0..1 de la pose actual.
	 *
	 * @param botones máscara de botones del GBA apretados (se dibujan hundidos)
	 * @param pantalla textura del juego para la pantalla, o null para dejarla apagada
	 * @param pausado oscurece la pantalla
	 */
	static void dibujar(PoseStack pose, MultiBufferSource buffers, int luz, int overlay, int botones, ResourceLocation pantalla, boolean pausado) {
		cargar();
		pose.pushPose();
		aplicarOrientacion(pose);
		Matrix4f matriz = pose.last().pose();
		Matrix3f normales = pose.last().normal();
		dibujarMalla(consola, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURA_CUERPO)), matriz, normales, luz, overlay, botones);
		dibujarMalla(cartucho, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURA_CARTUCHO)), matriz, normales, luz, overlay, 0);
		if (pantalla != null) {
			// La pantalla brilla sola: sin sombreado y a luz máxima
			VertexConsumer v = buffers.getBuffer(tipoPantalla(pantalla, EmuladorCliente.get().filtro().escala > 1));
			int brillo = LightTexture.FULL_BRIGHT;
			int c = pausado ? 90 : 255;
			v.vertex(matriz, PANTALLA_IZQ, PANTALLA_Y, PANTALLA_ARRIBA).color(c, c, c, 255).uv(0, 0).uv2(brillo).endVertex();
			v.vertex(matriz, PANTALLA_IZQ, PANTALLA_Y, PANTALLA_ABAJO).color(c, c, c, 255).uv(0, 1).uv2(brillo).endVertex();
			v.vertex(matriz, PANTALLA_DER, PANTALLA_Y, PANTALLA_ABAJO).color(c, c, c, 255).uv(1, 1).uv2(brillo).endVertex();
			v.vertex(matriz, PANTALLA_DER, PANTALLA_Y, PANTALLA_ARRIBA).color(c, c, c, 255).uv(1, 0).uv2(brillo).endVertex();
		}
		pose.popPose();
	}

	// Como RenderType.text (sin sombreado, con lightmap) pero eligiendo el filtrado: con la grilla LCD la textura se ve
	// achicada y sin filtrado lineal hace franjas de colores
	private static RenderType pantallaNitida, pantallaSuave;

	private static RenderType tipoPantalla(ResourceLocation textura, boolean suave) {
		if (pantallaNitida == null) {
			pantallaNitida = crearTipoPantalla("televisor_pantalla", textura, false);
			pantallaSuave = crearTipoPantalla("emumod_pantalla_suave", textura, true);
		}
		return suave ? pantallaSuave : pantallaNitida;
	}

	private static RenderType crearTipoPantalla(String nombre, ResourceLocation textura, boolean suave) {
		return TiposRender.pantalla(nombre, textura, suave);
	}

	/** Del cubo 0..1 a coordenadas del modelo: centrado, escalado y con el frente (-y) mirando a +z. */
	static void aplicarOrientacion(PoseStack pose) {
		pose.translate(0.5f, 0.5f, 0.5f);
		pose.scale(ESCALA, ESCALA, ESCALA);
		pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90));
	}

	/** Posición en pantalla de un punto del modelo (x, y, z) con la pose y la orientación, para ubicar botones con el mouse. */
	static Vector4f transformar(Matrix4f pose, float x, float y, float z) {
		PoseStack temporal = new PoseStack();
		temporal.last().pose().set(pose);
		aplicarOrientacion(temporal);
		Vector4f punto = temporal.last().pose().transform(new Vector4f(x, y, z, 1));
		// División de perspectiva: en primera persona la matriz incluye la proyección (en la GUI w queda en 1)
		return punto.div(punto.w);
	}

	private static void dibujarMalla(Malla malla, VertexConsumer v, Matrix4f matriz, Matrix3f normales, int luz, int overlay, int botones) {
		float[] d = malla.datos;
		int[] indices = malla.indices;
		// Las RenderType de entidades son de cuadriláteros: cada triángulo va como un cuadrilátero con el último vértice repetido
		for (int t = 0; t < indices.length; t += 3) {
			for (int k = 0; k < 4; k++) {
				int i = indices[t + Math.min(k, 2)];
				int o = i * 8;
				float hundido = hundimiento(malla.grupos[i], d[o], d[o + 2], botones);
				// De frente el hundimiento casi no se ve: el botón apretado además se oscurece
				int c = 255 - (int) (hundido / HUNDIDO * 85);
				v.vertex(matriz, d[o], d[o + 1] + hundido, d[o + 2])
						.color(c, c, c, 255)
						.uv(d[o + 3], d[o + 4])
						.overlayCoords(overlay)
						.uv2(luz)
						.normal(normales, d[o + 5], d[o + 6], d[o + 7])
						.endVertex();
			}
		}
	}

	// Cuánto entra el vértice en la carcasa (+y) según los botones apretados
	private static float hundimiento(int grupo, float x, float z, int botones) {
		return switch (grupo) {
			case GRUPO_A -> (botones & Boton.A.mascara()) != 0 ? HUNDIDO : 0;
			case GRUPO_B -> (botones & Boton.B.mascara()) != 0 ? HUNDIDO : 0;
			case GRUPO_START -> (botones & Boton.START.mascara()) != 0 ? HUNDIDO : 0;
			case GRUPO_SELECT -> (botones & Boton.SELECT.mascara()) != 0 ? HUNDIDO : 0;
			case GRUPO_CRUZ -> {
				// La cruz se inclina hacia el lado apretado
				float dx = (x - CRUZ_X) / CRUZ_BRAZO, dz = (z - CRUZ_Z) / CRUZ_BRAZO, h = 0;
				if ((botones & Boton.DERECHA.mascara()) != 0) h += Math.max(0, dx);
				if ((botones & Boton.IZQUIERDA.mascara()) != 0) h += Math.max(0, -dx);
				if ((botones & Boton.ARRIBA.mascara()) != 0) h += Math.max(0, dz);
				if ((botones & Boton.ABAJO.mascara()) != 0) h += Math.max(0, -dz);
				yield Math.min(1, h) * HUNDIDO;
			}
			default -> 0;
		};
	}

	/** Dirección de la cruz según un punto del modelo, o 0 si el punto no está sobre la cruz. */
	static int mascaraCruz(float x, float z) {
		float dx = x - CRUZ_X, dz = z - CRUZ_Z;
		if (dx * dx + dz * dz > CRUZ_BRAZO * CRUZ_BRAZO * 1.2f) {
			return 0;
		}
		float zonaMuerta = CRUZ_BRAZO * 0.3f;
		int mascara = 0;
		if (dx < -zonaMuerta) mascara |= Boton.IZQUIERDA.mascara();
		if (dx > zonaMuerta) mascara |= Boton.DERECHA.mascara();
		if (dz > zonaMuerta) mascara |= Boton.ARRIBA.mascara();
		if (dz < -zonaMuerta) mascara |= Boton.ABAJO.mascara();
		return mascara;
	}
}
