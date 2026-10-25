package com.emumod.pruebas;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import com.emumod.client.emu.Nucleo;
import com.emumod.client.emu.RomDePruebaN64;
import com.emumod.client.emu.Sistema;

/**
 * Prueba headless del núcleo de N64 (Mupen64Plus-Next con Angrylion), sin Minecraft ni juegos de terceros: corre la ROM
 * propia de {@link RomDePruebaN64} (pantalla azul y un tono) y revisa video, audio, guardado, estados y velocidad.
 * Uso: ./gradlew probarN64
 */
public final class PruebaN64 {
	private PruebaN64() {
	}

	public static void main(String[] args) throws Exception {
		java.util.Set<String> errores = java.util.concurrent.ConcurrentHashMap.newKeySet();
		Path salida = Path.of(args[0]);
		Files.createDirectories(salida);
		// Un error en un callback de JNA no corta la prueba: se muestra una vez y se falla al final
		com.sun.jna.Native.setCallbackExceptionHandler((cb, e) -> {
			if (errores.add(e.toString())) {
				e.printStackTrace();
			}
		});
		Nucleo nucleo = Nucleo.obtener(Sistema.N64, salida.resolve("nativos"), salida.resolve("sistema"));
		long[] muestrasAudio = {0};
		long[] energia = {0};
		nucleo.setSalidaAudio((muestras, cuadros) -> {
			muestrasAudio[0] += cuadros;
			for (int i = 0; i < cuadros * 2; i++) {
				energia[0] += Math.abs(muestras[i]);
			}
		});
		nucleo.cargarJuego(RomDePruebaN64.azul(), "prueba_azul.z64");
		System.out.printf("N64: %.4f fps, audio a %.0f Hz%n", nucleo.fps(), nucleo.frecuenciaAudio());

		int[] cuadro = new int[Sistema.N64.ancho * Sistema.N64.alto];
		long inicio = System.nanoTime();
		int cuadros = 120;
		for (int i = 0; i < cuadros; i++) {
			nucleo.setPalanca(i % 2 == 0 ? 20000 : -20000, 0);
			nucleo.correrCuadro();
		}
		double segundos = (System.nanoTime() - inicio) / 1e9;
		System.out.printf("%d cuadros en %.2f s: x%.2f de tiempo real%n", cuadros, segundos, cuadros / nucleo.fps() / segundos);
		nucleo.copiarCuadro(cuadro);
		guardar(cuadro, salida.resolve("n64_azul.png"));
		int centro = cuadro[(Sistema.N64.alto / 2) * Sistema.N64.ancho + Sistema.N64.ancho / 2];
		int r = (centro >> 16) & 0xFF, g = (centro >> 8) & 0xFF, b = centro & 0xFF;
		System.out.printf("Color del centro: #%06X%n", centro);
		if (!(b > 150 && r < 100 && g < 130)) {
			throw new AssertionError("La pantalla no quedó azul");
		}

		double promedio = muestrasAudio[0] == 0 ? 0 : energia[0] / (double) (muestrasAudio[0] * 2);
		System.out.printf("Audio: %d cuadros de audio en %d cuadros de video, amplitud media %.0f%n", muestrasAudio[0], cuadros, promedio);
		if (muestrasAudio[0] < nucleo.frecuenciaAudio() * 1.5 || promedio < 200) {
			throw new AssertionError("No se oyó el tono");
		}

		byte[] guardado = nucleo.leerGuardado();
		System.out.println("Memoria de guardado: " + (guardado == null ? "ninguna" : guardado.length + " bytes"));
		byte[] estado = nucleo.guardarEstado();
		if (estado == null || !nucleo.cargarEstado(estado)) {
			throw new AssertionError("Fallaron los estados guardados");
		}
		nucleo.correrCuadro();
		System.out.println("Estado guardado y cargado: " + estado.length + " bytes");
		nucleo.reiniciar();
		for (int i = 0; i < 30; i++) {
			nucleo.correrCuadro();
		}
		System.out.println("Reinicio OK");

		// Otro juego: el núcleo se reinicia entero (ver Sistema.reiniciarAlDescargar)
		nucleo.cargarJuego(RomDePruebaN64.deColor(0xE02020FF), "prueba_roja.z64");
		for (int i = 0; i < 30; i++) {
			nucleo.correrCuadro();
		}
		nucleo.copiarCuadro(cuadro);
		centro = cuadro[(Sistema.N64.alto / 2) * Sistema.N64.ancho + Sistema.N64.ancho / 2];
		System.out.printf("Segundo juego, color del centro: #%06X%n", centro);
		if (((centro >> 16) & 0xFF) < 150 || (centro & 0xFF) > 100) {
			throw new AssertionError("El segundo juego no se ve");
		}
		if (!errores.isEmpty()) {
			throw new AssertionError("Hubo errores en los callbacks: " + errores);
		}
		System.out.println("Prueba de N64 OK");
		nucleo.apagar();
		System.out.println("Núcleo apagado");
		// Los hilos del núcleo (Angrylion) no terminan solos
		System.exit(0);
	}

	private static void guardar(int[] cuadro, Path archivo) throws Exception {
		BufferedImage img = new BufferedImage(Sistema.N64.ancho, Sistema.N64.alto, BufferedImage.TYPE_INT_RGB);
		img.setRGB(0, 0, Sistema.N64.ancho, Sistema.N64.alto, cuadro, 0, Sistema.N64.ancho);
		ImageIO.write(img, "png", archivo.toFile());
	}
}
