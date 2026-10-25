package com.emumod.client;

import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.platform.NativeImage;
import com.emumod.client.emu.Sistema;

/**
 * Filtros de la pantalla, pasados a Java desde los shaders de libretro (glsl-shaders/handheld, dominio público):
 * <ul>
 * <li>{@code gba-color.glsl} (hunterk, valores de Pokefan531): colores como en la pantalla real de la GBA, sin
 * retroiluminación, más apagados y cálidos.</li>
 * <li>{@code lcd3x.glsl} (Gigaherz): cada píxel del GBA se dibuja en 3x3 con la grilla de subpíxeles RGB y las líneas del
 * LCD.</li>
 * </ul>
 * Para la NES y la N64 hay un filtro CRT propio del mod (líneas de barrido y máscara de fósforo RGB, cada píxel en 3x3).
 * Se aplican al subir cada cuadro a la textura, así no hace falta cambiar los shaders de Minecraft.
 */
public enum FiltroPantalla {
	ORIGINAL(1, false, false),
	COLOR_GBA(1, true, false),
	LCD(3, true, true),
	CRT(3, false, true);

	/** Píxeles de la textura por píxel del GBA. */
	final int escala;
	private final boolean color;
	private final boolean grilla;

	FiltroPantalla(int escala, boolean color, boolean grilla) {
		this.escala = escala;
		this.color = color;
		this.grilla = grilla;
	}

	// gba-color.glsl: el GBA tiene 15 bits de color, así que se precalcula una tabla con los 32768 colores
	private static final int[] COLOR = new int[1 << 15];
	// lcd3x.glsl: factor de cada canal en cada uno de los 3x3 subpíxeles
	private static final float[][] GRILLA = new float[9][3];
	// CRT: la tercera fila de cada píxel es el hueco oscuro entre líneas de barrido y cada columna realza un fósforo
	private static final float[][] GRILLA_CRT = new float[9][3];

	static {
		double gamma = 2.2 + 1.0; // target_gamma + darken_screen
		double lum = 0.94;
		double r = 0.82, g = 0.665, b = 0.73, rg = 0.125, rb = 0.195, gr = 0.24, gb = 0.075, br = -0.06, bg = 0.21;
		for (int i = 0; i < COLOR.length; i++) {
			double rojo = Math.min(1, Math.pow(((i >> 10) & 31) / 31.0, gamma) * lum);
			double verde = Math.min(1, Math.pow(((i >> 5) & 31) / 31.0, gamma) * lum);
			double azul = Math.min(1, Math.pow((i & 31) / 31.0, gamma) * lum);
			double sr = r * rojo + gr * verde + br * azul;
			double sg = rg * rojo + g * verde + bg * azul;
			double sb = rb * rojo + gb * verde + b * azul;
			COLOR[i] = (canal(sr) << 16) | (canal(sg) << 8) | canal(sb);
		}
		// brighten_lcd al máximo del shader (12, el original usa 4): la grilla más suave no hace moiré cuando la pantalla
		// se ve a menos de 3 píxeles por píxel del GBA, como en las manos
		double brillo = 16, brilloLcd = 12;
		double[] desfases = {Math.PI * 0.5, Math.PI * (0.5 - 2.0 / 3), Math.PI * (0.5 - 4.0 / 3)};
		for (int sy = 0; sy < 3; sy++) {
			double fy = (brillo + Math.sin(2 * Math.PI * (sy + 0.5) / 3)) / (brillo + 1);
			for (int sx = 0; sx < 3; sx++) {
				double angulo = 2 * Math.PI * (sx + 0.5) / 3;
				for (int c = 0; c < 3; c++) {
					GRILLA[sy * 3 + sx][c] = (float) (fy * (brilloLcd + Math.sin(angulo + desfases[c])) / (brilloLcd + 1));
				}
			}
		}
		float[] lineas = {1.08f, 1.0f, 0.55f};
		for (int sy = 0; sy < 3; sy++) {
			for (int sx = 0; sx < 3; sx++) {
				for (int c = 0; c < 3; c++) {
					GRILLA_CRT[sy * 3 + sx][c] = lineas[sy] * (sx == c ? 1.06f : 0.94f);
				}
			}
		}
	}

	private static int canal(double valor) {
		return (int) Math.round(Math.pow(Math.max(0, Math.min(1, valor)), 1 / 2.2) * 255);
	}

	/** Los colores y el LCD son de la pantalla de la GBA; el CRT, del televisor de la NES y la N64. */
	boolean sirvePara(Sistema consola) {
		// En la PS3 (1280x720) ni el CRT: serían 8 millones de píxeles por cuadro. En la 3DS tampoco: sus dos pantallas
		// son LCD retroiluminadas, sin el color apagado de la GBA, y la grilla de 3x3 serían casi 2 millones de píxeles
		if (consola == Sistema.PS3 || consola == Sistema.N3DS || consola == Sistema.PSP) {
			return this == ORIGINAL;
		}
		return this == ORIGINAL || (consola != Sistema.GBA) == (this == CRT);
	}

	static FiltroPantalla porDefecto(Sistema consola) {
		// En la PS2 (640x448) el CRT, de 3x3 por píxel, pesa demasiado para hacerlo en cada cuadro: queda a elección
		return consola == Sistema.GBA ? COLOR_GBA : consola == Sistema.PS2 || consola == Sistema.PS3 || consola == Sistema.WII || consola == Sistema.N3DS || consola == Sistema.PSP ? ORIGINAL : CRT;
	}

	FiltroPantalla siguiente(Sistema consola) {
		FiltroPantalla f = this;
		do {
			f = values()[(f.ordinal() + 1) % values().length];
		} while (!f.sirvePara(consola));
		return f;
	}

	/** Pasa el cuadro del núcleo (0x00RRGGBB) a la imagen, que debe medir ancho x alto por {@link #escala}. */
	void llenar(int[] cuadro, int ancho, int alto, NativeImage imagen) {
		int anchoSalida = ancho * escala;
		int[] salida = buffer(anchoSalida * alto * escala);
		float[][] factores = this == CRT ? GRILLA_CRT : GRILLA;
		for (int y = 0; y < alto; y++) {
			for (int x = 0; x < ancho; x++) {
				int xrgb = cuadro[y * ancho + x];
				if (color) {
					xrgb = COLOR[((xrgb >> 9) & 0x7C00) | ((xrgb >> 6) & 0x3E0) | ((xrgb >> 3) & 0x1F)];
				}
				if (!grilla) {
					salida[y * anchoSalida + x] = EmuladorCliente.aAbgr(xrgb);
					continue;
				}
				int rojo = (xrgb >> 16) & 0xFF, verde = (xrgb >> 8) & 0xFF, azul = xrgb & 0xFF;
				for (int sy = 0; sy < 3; sy++) {
					int fila = (y * 3 + sy) * anchoSalida + x * 3;
					for (int sx = 0; sx < 3; sx++) {
						float[] f = factores[sy * 3 + sx];
						// ABGR, como lo guarda NativeImage
						salida[fila + sx] = 0xFF000000 | (Math.min(255, (int) (azul * f[2])) << 16) | (Math.min(255, (int) (verde * f[1])) << 8)
								| Math.min(255, (int) (rojo * f[0]));
					}
				}
			}
		}
		// De una sola vez a la memoria de la imagen (RGBA, 4 bytes por píxel): pixel por pixel costaba varios ms por cuadro
		MemoryUtil.memIntBuffer(imagen.pixels, salida.length).put(0, salida, 0, salida.length);
	}

	// Solo se usa desde el hilo de render
	private static int[] reusable = new int[0];

	private static int[] buffer(int largo) {
		if (reusable.length != largo) {
			reusable = new int[largo];
		}
		return reusable;
	}
}
