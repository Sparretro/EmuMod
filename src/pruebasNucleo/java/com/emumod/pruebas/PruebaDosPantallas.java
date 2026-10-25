package com.emumod.pruebas;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import com.emumod.client.emu.Nucleo;
import com.emumod.client.emu.Sistema;

/**
 * Prueba headless de las portátiles de dos pantallas, sin Minecraft: la 3DS (Citra con el contexto OpenGL propio del
 * puente) y la DS (melonDS DS, por software). Con el programa de prueba propio de cada una (native/prueba3ds y
 * native/pruebands, homebrew sin nada de Nintendo) revisa que se vean las dos pantallas, que lleguen
 * los botones (A pinta la de arriba de rojo) y que el mouse toque la pantalla de abajo (aparece un cuadrado blanco).
 * Con otro juego solo corre y guarda capturas. Uso: ./gradlew probarN3ds o probarNds [-Pjuego=<ruta>]
 */
public final class PruebaDosPantallas {
	private static Sistema sistema;
	private static int ANCHO, ALTO;

	private PruebaDosPantallas() {
	}

	public static void main(String[] args) throws Exception {
		// Como la Wii: pila grande en el hilo que maneja el núcleo
		Throwable[] error = {null};
		Thread hilo = new Thread(null, () -> {
			try {
				probar(args);
			} catch (Throwable e) {
				error[0] = e;
			}
		}, "Prueba " + args[2], 256L * 1024 * 1024);
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
		Path juego = Path.of(args[1]);
		sistema = Sistema.valueOf(args[2]);
		ANCHO = sistema.ancho;
		ALTO = sistema.alto;
		String nombre = juego.getFileName().toString();
		boolean propio = nombre.equals("prueba3ds.3dsx") || nombre.equals("pruebands.nds");
		java.util.Set<String> errores = java.util.concurrent.ConcurrentHashMap.newKeySet();
		com.sun.jna.Native.setCallbackExceptionHandler((cb, e) -> {
			if (errores.add(e.toString())) {
				e.printStackTrace();
			}
		});
		Nucleo nucleo = Nucleo.obtener(sistema, salida.resolve("nativos"), salida.resolve("sistema"));
		long[] muestrasAudio = {0};
		nucleo.setSalidaAudio((muestras, cuadros) -> muestrasAudio[0] += cuadros);
		if (sistema.leeDelDisco()) {
			nucleo.cargarArchivo(juego);
		} else {
			nucleo.cargarJuego(Files.readAllBytes(juego), nombre);
		}
		System.out.printf(sistema + ": %.4f fps, audio a %.0f Hz%n", nucleo.fps(), nucleo.frecuenciaAudio());

		int[] cuadro = new int[ANCHO * ALTO];
		int total = Integer.getInteger("emumod.cuadros", 0);
		if (total <= 0) {
			total = propio ? 400 : 1800;
		}
		long inicio = System.nanoTime(), tramo = inicio;
		long periodo = (long) (1e9 / nucleo.fps());
		int conA = 0, sinA = 0, tocado = 0;
		for (int i = 1; i <= total; i++) {
			// Cuadros 200 a 260: A apretado; 300 a 360: toque en el centro de la pantalla de abajo (a tres cuartos de alto
			// del cuadro, en el medio)
			boolean a = i >= 200 && i < 260, toque = i >= 300 && i < 360;
			nucleo.setBotones(a ? Nucleo.Boton.A.mascara() : 0);
			nucleo.setPuntero(0, toque ? 16383 : 0, toque ? 1 : 0);
			nucleo.correrCuadro();
			long espera = inicio + i * periodo - System.nanoTime();
			if (espera > 0) {
				Thread.sleep(espera / 1_000_000, (int) (espera % 1_000_000));
			}
			if (i == 180 || i == 250 || i == 350 || i % 300 == 0) {
				nucleo.copiarCuadro(cuadro);
				guardar(cuadro, salida.resolve(sistema.name().toLowerCase() + "_" + i + ".png"));
				// La pantalla de arriba en el medio y la de abajo donde se tocó (corrido 4 píxeles: melonDS DS marca el toque con
				// un punto justo en el centro)
				int arriba = cuadro[ALTO / 4 * ANCHO + ANCHO / 2], abajo = cuadro[(ALTO * 3 / 4 + 4) * ANCHO + ANCHO / 2 + 4];
				System.out.printf("Cuadro %d: arriba %06X, abajo %06X%n", i, arriba & 0xFFFFFF, abajo & 0xFFFFFF);
				if (i == 180 && esAzul(arriba)) sinA++;
				if (i == 250 && esRojo(arriba)) conA++;
				if (i == 350 && (abajo & 0xFFFFFF) > 0xE0E0E0) tocado++;
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
		if (propio && (sinA != 1 || conA != 1)) {
			throw new AssertionError("El botón A no llegó al juego (azul antes: " + sinA + ", rojo con A: " + conA + ")");
		}
		if (propio && tocado != 1) {
			throw new AssertionError("El mouse no tocó la pantalla de abajo");
		}
		if (!errores.isEmpty()) {
			throw new AssertionError("Hubo errores en los callbacks: " + errores);
		}
		System.out.println("Prueba de " + sistema + " OK");
		System.exit(0);
	}

	private static boolean esAzul(int p) {
		return (p & 0xFF) > 150 && ((p >> 16) & 0xFF) < 60;
	}

	private static boolean esRojo(int p) {
		return ((p >> 16) & 0xFF) > 150 && (p & 0xFF) < 80;
	}

	private static void guardar(int[] cuadro, Path archivo) throws Exception {
		BufferedImage img = new BufferedImage(ANCHO, ALTO, BufferedImage.TYPE_INT_RGB);
		img.setRGB(0, 0, ANCHO, ALTO, cuadro, 0, ANCHO);
		ImageIO.write(img, "png", archivo.toFile());
	}
}
