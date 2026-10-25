package com.emumod.pruebas;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import com.emumod.client.emu.Nucleo;
import com.emumod.client.emu.Sistema;

/**
 * Prueba headless del núcleo de Wii (Dolphin con el contexto OpenGL propio del puente), sin Minecraft: carga el juego
 * indicado, corre a 60 cuadros por segundo y guarda capturas. El juego es del usuario y no se copia.
 * Uso: ./gradlew probarWii -Pjuego=<ruta>
 */
public final class PruebaWii {
	private PruebaWii() {
	}

	public static void main(String[] args) throws Exception {
		// Como la PS3: pila grande en el hilo que maneja el núcleo
		Throwable[] error = {null};
		Thread hilo = new Thread(null, () -> {
			try {
				probar(args);
			} catch (Throwable e) {
				error[0] = e;
			}
		}, "Prueba Wii", 256L * 1024 * 1024);
		hilo.start();
		hilo.join();
		if (error[0] != null) {
			error[0].printStackTrace();
			System.exit(1);
		}
	}

	private static void probar(String[] args) throws Exception {
		Path salida = Path.of(args[0]);
		Files.createDirectories(salida);
		if (args.length < 2 || args[1].isEmpty()) {
			throw new IllegalArgumentException("Falta el juego: ./gradlew probarWii -Pjuego=<ruta>");
		}
		java.util.Set<String> errores = java.util.concurrent.ConcurrentHashMap.newKeySet();
		com.sun.jna.Native.setCallbackExceptionHandler((cb, e) -> {
			if (errores.add(e.toString())) {
				e.printStackTrace();
			}
		});
		Nucleo nucleo = Nucleo.obtener(Sistema.WII, salida.resolve("nativos"), salida.resolve("sistema"));
		long[] muestrasAudio = {0};
		nucleo.setSalidaAudio((muestras, cuadros) -> muestrasAudio[0] += cuadros);
		nucleo.cargarArchivo(Path.of(args[1]));
		System.out.printf("Wii: %.4f fps, audio a %.0f Hz%n", nucleo.fps(), nucleo.frecuenciaAudio());

		int[] cuadro = new int[Sistema.WII.ancho * Sistema.WII.alto];
		int total = Integer.getInteger("emumod.cuadros", 1800);
		long inicio = System.nanoTime(), tramo = inicio;
		long periodo = (long) (1e9 / nucleo.fps());
		for (int i = 1; i <= total; i++) {
			// Cuadros 1550 y 1900: se aprieta el 2 (RetroPad A), para ver que los botones llegan al juego
			nucleo.setBotones((i >= 1550 && i < 1580) || (i >= 1900 && i < 1930) ? com.emumod.client.emu.Nucleo.Boton.A.mascara() : 0);
			nucleo.correrCuadro();
			long espera = inicio + i * periodo - System.nanoTime();
			if (espera > 0) {
				Thread.sleep(espera / 1_000_000, (int) (espera % 1_000_000));
			}
			if (i % 150 == 0) {
				long ahora = System.nanoTime();
				System.out.printf("Cuadro %d: últimos 150 a x%.2f%n", i, 150 / nucleo.fps() / ((ahora - tramo) / 1e9));
				tramo = ahora;
				nucleo.copiarCuadro(cuadro);
				guardar(cuadro, salida.resolve("wii_" + i + ".png"));
			}
		}
		nucleo.copiarCuadro(cuadro);
		long noNegros = java.util.Arrays.stream(cuadro).filter(p -> (p & 0xFFFFFF) > 0x101010).count();
		System.out.printf("Píxeles no negros: %d de %d; audio: %d cuadros%n", noNegros, cuadro.length, muestrasAudio[0]);
		nucleo.apagar();
		if (noNegros < 1000) {
			throw new AssertionError("La pantalla quedó negra");
		}
		if (!errores.isEmpty()) {
			throw new AssertionError("Hubo errores en los callbacks: " + errores);
		}
		System.out.println("Prueba de Wii OK");
		System.exit(0);
	}

	private static void guardar(int[] cuadro, Path archivo) throws Exception {
		BufferedImage img = new BufferedImage(Sistema.WII.ancho, Sistema.WII.alto, BufferedImage.TYPE_INT_RGB);
		img.setRGB(0, 0, Sistema.WII.ancho, Sistema.WII.alto, cuadro, 0, Sistema.WII.ancho);
		ImageIO.write(img, "png", archivo.toFile());
	}
}
