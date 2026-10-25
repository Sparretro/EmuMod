package com.emumod.client;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.emumod.EmuMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Modelo 3D convertido con {@code tools/ConvertirModelo.java}: piezas con nombre, cada una con su textura o un color
 * liso. Las coordenadas son las del modelo original (en metros, y hacia arriba); quien lo dibuja elige escala y giro.
 */
final class Modelo3D {
	private static final ResourceLocation BLANCO = EmuMod.id("textures/modelo/blanco.png");
	private static final Map<String, Modelo3D> CARGADOS = new HashMap<>();

	record Pieza(String nombre, ResourceLocation textura, int color, boolean translucida, float[] datos, int[] indices) {
	}

	/** Permite mover y teñir piezas sueltas en un cuadro: por ejemplo, hundir el botón apretado de un control. */
	interface Ajuste {
		Ajuste NINGUNO = pieza -> null;

		/** Desplazamiento (x, y, z) en coordenadas del modelo y tinte (0 a 1) de la pieza, o null para dejarla igual. */
		float[] ajustar(String pieza);
	}

	final List<Pieza> piezas;
	final float minX, minY, minZ, maxX, maxY, maxZ;

	private Modelo3D(List<Pieza> piezas) {
		this.piezas = piezas;
		float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		for (Pieza p : piezas) {
			for (int i = 0; i < p.datos.length; i += 8) {
				for (int c = 0; c < 3; c++) {
					min[c] = Math.min(min[c], p.datos[i + c]);
					max[c] = Math.max(max[c], p.datos[i + c]);
				}
			}
		}
		minX = min[0];
		minY = min[1];
		minZ = min[2];
		maxX = max[0];
		maxY = max[1];
		maxZ = max[2];
	}

	static synchronized Modelo3D de(String nombre) {
		return CARGADOS.computeIfAbsent(nombre, Modelo3D::cargar);
	}

	private static Modelo3D cargar(String nombre) {
		ResourceLocation archivo = EmuMod.id("modelos/" + nombre + ".bin");
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(
				Minecraft.getInstance().getResourceManager().getResourceOrThrow(archivo).open()))) {
			if (in.readInt() != 0x4D4F4433) {
				throw new IOException("Formato desconocido");
			}
			ResourceLocation[] texturas = new ResourceLocation[in.readInt()];
			for (int i = 0; i < texturas.length; i++) {
				texturas[i] = EmuMod.id("textures/modelo/" + nombre + "/" + in.readUTF());
			}
			int cantidad = in.readInt();
			List<Pieza> piezas = new ArrayList<>(cantidad);
			for (int p = 0; p < cantidad; p++) {
				String pieza = in.readUTF();
				int textura = in.readInt();
				int color = in.readInt();
				boolean translucida = in.readBoolean();
				float[] datos = new float[in.readInt() * 8];
				for (int i = 0; i < datos.length; i++) {
					datos[i] = in.readFloat();
				}
				int[] indices = new int[in.readInt()];
				for (int i = 0; i < indices.length; i++) {
					indices[i] = in.readInt();
				}
				piezas.add(new Pieza(pieza, textura >= 0 ? texturas[textura] : BLANCO, color, translucida, datos, indices));
			}
			return new Modelo3D(piezas);
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo leer " + archivo, e);
		}
	}

	float ancho() {
		return maxX - minX;
	}

	float alto() {
		return maxY - minY;
	}

	float profundidad() {
		return maxZ - minZ;
	}

	/** Escala el modelo para que mida {@code ancho} en x y lo apoya en y = 0, centrado en x y z. */
	void apoyar(PoseStack pose, float ancho) {
		float escala = ancho / ancho();
		pose.scale(escala, escala, escala);
		pose.translate(-(minX + maxX) / 2, -minY, -(minZ + maxZ) / 2);
	}

	/** Escala el modelo para que mida {@code ancho} en x y lo centra en los tres ejes. */
	void centrar(PoseStack pose, float ancho) {
		float escala = ancho / ancho();
		pose.scale(escala, escala, escala);
		pose.translate(-(minX + maxX) / 2, -(minY + maxY) / 2, -(minZ + maxZ) / 2);
	}

	void dibujar(PoseStack pose, MultiBufferSource buffers, int luz, int overlay, Ajuste ajuste) {
		Matrix4f matriz = pose.last().pose();
		Matrix3f normales = pose.last().normal();
		Vector3f p3 = new Vector3f(), n3 = new Vector3f();
		for (Pieza p : piezas) {
			float[] cambio = ajuste.ajustar(p.nombre);
			float dx = 0, dy = 0, dz = 0, tinte = 1;
			if (cambio != null) {
				dx = cambio[0];
				dy = cambio[1];
				dz = cambio[2];
				tinte = cambio[3];
			}
			float a = ((p.color >>> 24) & 0xFF) / 255f;
			float r = ((p.color >> 16) & 0xFF) / 255f * tinte, g = ((p.color >> 8) & 0xFF) / 255f * tinte, b = (p.color & 0xFF) / 255f * tinte;
			// Cada vértice se transforma una vez; los triángulos lo reusan por índice
			float[] d = p.datos;
			int n = d.length / 8;
			if (transformados.length < n * 6) {
				transformados = new float[n * 6];
			}
			float[] t = transformados;
			for (int i = 0; i < n; i++) {
				int o = i * 8;
				matriz.transformPosition(d[o] + dx, d[o + 1] + dy, d[o + 2] + dz, p3);
				normales.transform(d[o + 5], d[o + 6], d[o + 7], n3).normalize();
				t[i * 6] = p3.x;
				t[i * 6 + 1] = p3.y;
				t[i * 6 + 2] = p3.z;
				t[i * 6 + 3] = n3.x;
				t[i * 6 + 4] = n3.y;
				t[i * 6 + 5] = n3.z;
			}
			VertexConsumer v = buffers.getBuffer(TiposRender.entidadTriangulos(p.textura, p.translucida));
			for (int indice : p.indices) {
				int o = indice * 6;
				// Escritura de un vértice entero de una vez: BufferBuilder la hace sin pasar por cada elemento
				v.vertex(t[o], t[o + 1], t[o + 2], r, g, b, a, d[indice * 8 + 3], d[indice * 8 + 4], overlay, luz, t[o + 3], t[o + 4], t[o + 5]);
			}
		}
	}

	// Solo se dibuja desde el hilo de render
	private static float[] transformados = new float[0];
}
