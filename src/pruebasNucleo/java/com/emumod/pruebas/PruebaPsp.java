package com.emumod.pruebas;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import com.emumod.client.emu.Nucleo;
import com.emumod.client.emu.RomDePruebaPsp;
import com.emumod.client.emu.Sistema;

/**
 * Prueba headless de la PSP, sin Minecraft: PPSSPP con el contexto OpenGL propio del puente. Con el programa de prueba
 * propio (RomDePruebaPsp, escrito a mano) revisa que se vea la pantalla azul, que ✕ la ponga roja y que el cuadrado
 * blanco se mueva. Con otro juego solo corre y guarda capturas. Uso: ./gradlew probarPsp [-Pjuego=<ruta>]
 */
public final class PruebaPsp {
	private static final int ANCHO = Sistema.PSP.ancho, ALTO = Sistema.PSP.alto;

	private PruebaPsp() {
	}

	public static void main(String[] args) throws Exception {
		// Como la Wii y la 3DS: pila grande en el hilo que maneja el núcleo
		Throwable[] error = {null};
		Thread hilo = new Thread(null, () -> {
			try {
				probar(args);
			} catch (Throwable e) {
				error[0] = e;
			}
		}, "Prueba PSP", 256L * 1024 * 1024);
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
		boolean propio = args.length < 2 || args[1].isEmpty();
		Path juego = propio ? salida.resolve("pruebapsp.elf") : Path.of(args[1]);
		if (propio) {
			Files.write(juego, RomDePruebaPsp.elf());
		}
		java.util.Set<String> errores = java.util.concurrent.ConcurrentHashMap.newKeySet();
		com.sun.jna.Native.setCallbackExceptionHandler((cb, e) -> {
			if (errores.add(e.toString())) {
				e.printStackTrace();
			}
		});
		Nucleo nucleo = Nucleo.obtener(Sistema.PSP, salida.resolve("nativos"), salida.resolve("sistema"));
		long[] muestrasAudio = {0};
		nucleo.setSalidaAudio((muestras, cuadros) -> muestrasAudio[0] += cuadros);
		nucleo.cargarArchivo(juego);
		System.out.printf("PSP: %.4f fps, audio a %.0f Hz%n", nucleo.fps(), nucleo.frecuenciaAudio());

		int[] cuadro = new int[ANCHO * ALTO];
		int total = Integer.getInteger("emumod.cuadros", 0);
		if (total <= 0) {
			total = propio ? 400 : 1800;
		}
		long inicio = System.nanoTime(), tramo = inicio;
		long periodo = (long) (1e9 / nucleo.fps());
		int azul = 0, rojo = 0;
		int[] cuadradoX = new int[2];
		for (int i = 1; i <= total; i++) {
			// Cuadros 200 a 260: ✕ apretado (el botón de abajo del RetroPad)
			nucleo.setBotones(i >= 200 && i < 260 ? Nucleo.Boton.B.mascara() : 0);
			nucleo.correrCuadro();
			long espera = inicio + i * periodo - System.nanoTime();
			if (espera > 0) {
				Thread.sleep(espera / 1_000_000, (int) (espera % 1_000_000));
			}
			if (i == 180 || i == 190 || i == 250 || i % 300 == 0) {
				nucleo.copiarCuadro(cuadro);
				guardar(cuadro, salida.resolve("psp_" + i + ".png"));
				// El fondo, abajo a la izquierda (lejos del cuadrado), y dónde está el cuadrado en la fila 116
				int fondo = cuadro[(ALTO - 20) * ANCHO + 20] & 0xFFFFFF;
				int x = -1;
				for (int c = 0; c < ANCHO && x < 0; c++) {
					if ((cuadro[116 * ANCHO + c] & 0xFFFFFF) > 0xE0E0E0) {
						x = c;
					}
				}
				System.out.printf("Cuadro %d: fondo %06X, cuadrado en x=%d%n", i, fondo, x);
				if (i == 180) {
					azul += parecido(fondo, RomDePruebaPsp.AZUL) ? 1 : 0;
					cuadradoX[0] = x;
				}
				if (i == 190) {
					cuadradoX[1] = x;
				}
				if (i == 250) {
					rojo += parecido(fondo, RomDePruebaPsp.ROJO) ? 1 : 0;
				}
			}
			if (i % 150 == 0) {
				long ahora = System.nanoTime();
				System.out.printf("Cuadro %d: últimos 150 a x%.2f%n", i, 150 / nucleo.fps() / ((ahora - tramo) / 1e9));
				tramo = ahora;
			}
		}
		nucleo.copiarCuadro(cuadro);
		long noNegros = java.util.Arrays.stream(cuadro).filter(p -> (p & 0xFFFFFF) > 0x101010).count();
		System.out.printf("Píxeles no negros: %d de %d; audio: %d cuadros%n", noNegros, cuadro.length, muestrasAudio[0]);
		nucleo.apagar();
		if (noNegros < 1000) {
			throw new AssertionError("La pantalla quedó negra");
		}
		if (propio && (azul != 1 || rojo != 1)) {
			throw new AssertionError("El botón ✕ no llegó al juego (azul antes: " + azul + ", rojo con ✕: " + rojo + ")");
		}
		if (propio && (cuadradoX[0] < 0 || cuadradoX[1] <= cuadradoX[0])) {
			throw new AssertionError("El cuadrado no se movió: x=" + cuadradoX[0] + " y después x=" + cuadradoX[1]);
		}
		if (!errores.isEmpty()) {
			throw new AssertionError("Hubo errores en los callbacks: " + errores);
		}
		System.out.println("Prueba de PSP OK");
		System.exit(0);
	}

	private static boolean parecido(int a, int b) {
		for (int s = 0; s < 24; s += 8) {
			if (Math.abs((a >> s & 0xFF) - (b >> s & 0xFF)) > 24) {
				return false;
			}
		}
		return true;
	}

	private static void guardar(int[] cuadro, Path archivo) throws Exception {
		BufferedImage img = new BufferedImage(ANCHO, ALTO, BufferedImage.TYPE_INT_RGB);
		img.setRGB(0, 0, ANCHO, ALTO, cuadro, 0, ANCHO);
		ImageIO.write(img, "png", archivo.toFile());
	}
}
