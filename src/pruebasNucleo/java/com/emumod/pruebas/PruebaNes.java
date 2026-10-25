package com.emumod.pruebas;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import com.emumod.client.emu.Nucleo;
import com.emumod.client.emu.RomDePrueba;
import com.emumod.client.emu.Sistema;

/**
 * Prueba headless del núcleo de NES (QuickNES), sin Minecraft ni juegos de terceros: corre la ROM propia de
 * {@link RomDePrueba} (pantalla roja y un tono) y revisa video, audio y estados.
 * Uso: ./gradlew probarNes
 */
public final class PruebaNes {
	private PruebaNes() {
	}

	public static void main(String[] args) throws Exception {
		Path salida = Path.of(args[0]);
		Files.createDirectories(salida);
		Nucleo nucleo = Nucleo.obtener(Sistema.NES, salida.resolve("nativos"), salida.resolve("sistema"));
		long[] muestrasAudio = {0};
		long[] energia = {0};
		nucleo.setSalidaAudio((muestras, cuadros) -> {
			muestrasAudio[0] += cuadros;
			for (int i = 0; i < cuadros * 2; i++) {
				energia[0] += Math.abs(muestras[i]);
			}
		});
		nucleo.cargarJuego(RomDePrueba.roja(), "prueba_roja.nes");
		System.out.printf("NES: %.4f fps, audio a %.0f Hz%n", nucleo.fps(), nucleo.frecuenciaAudio());

		int[] cuadro = new int[Sistema.NES.ancho * Sistema.NES.alto];
		for (int i = 0; i < 60; i++) {
			nucleo.correrCuadro();
		}
		nucleo.copiarCuadro(cuadro);
		guardar(cuadro, salida.resolve("nes_rojo.png"));
		int centro = cuadro[(Sistema.NES.alto / 2) * Sistema.NES.ancho + Sistema.NES.ancho / 2];
		int r = (centro >> 16) & 0xFF, g = (centro >> 8) & 0xFF, b = centro & 0xFF;
		System.out.printf("Color del centro: #%06X%n", centro);
		if (!(r > 120 && g < 90 && b < 90)) {
			throw new AssertionError("La pantalla no quedó roja");
		}

		double promedio = muestrasAudio[0] == 0 ? 0 : energia[0] / (double) (muestrasAudio[0] * 2);
		System.out.printf("Audio: %d cuadros de audio en 60 cuadros de video, amplitud media %.0f%n", muestrasAudio[0], promedio);
		if (muestrasAudio[0] < nucleo.frecuenciaAudio() * 0.9 || promedio < 200) {
			throw new AssertionError("No se oyó el tono");
		}

		byte[] estado = nucleo.guardarEstado();
		if (estado == null || !nucleo.cargarEstado(estado)) {
			throw new AssertionError("Fallaron los estados guardados");
		}
		System.out.println("Estado guardado y cargado: " + estado.length + " bytes");
		System.out.println("Prueba de NES OK");
	}

	private static void guardar(int[] cuadro, Path archivo) throws Exception {
		BufferedImage img = new BufferedImage(Sistema.NES.ancho, Sistema.NES.alto, BufferedImage.TYPE_INT_RGB);
		img.setRGB(0, 0, Sistema.NES.ancho, Sistema.NES.alto, cuadro, 0, Sistema.NES.ancho);
		ImageIO.write(img, "png", archivo.toFile());
	}
}
