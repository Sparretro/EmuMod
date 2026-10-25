package com.emumod.pruebas;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javax.imageio.ImageIO;

import com.emumod.client.emu.Nucleo;
import com.emumod.client.emu.Sistema;

/**
 * Prueba headless del núcleo de PS3 (RPCS3 con Vulkan propio y el cuadro por memoria), sin Minecraft: instala el
 * firmware que se le pasa y arranca el menú de la consola (vsh.self del firmware) o el juego indicado, y mide la
 * velocidad. El firmware es del usuario y solo se copia a la carpeta de la prueba.
 * Uso: ./gradlew probarPs3 -Pfirmware=<PS3UPDAT.PUP> [-Pjuego=<ruta>]
 */
public final class PruebaPs3 {
	private PruebaPs3() {
	}

	public static void main(String[] args) throws Exception {
		// RPCS3 necesita mucha pila en el hilo que lo maneja (con la de Java, 1 MB, se desborda al arrancar)
		Throwable[] error = {null};
		Thread hilo = new Thread(null, () -> {
			try {
				probar(args);
			} catch (Throwable e) {
				error[0] = e;
			}
		}, "Prueba PS3", 256L * 1024 * 1024);
		hilo.start();
		hilo.join();
		if (error[0] != null) {
			error[0].printStackTrace();
			System.exit(1);
		}
	}

	private static void probar(String[] args) throws Exception {
		Path salida = Path.of(args[0]);
		if (args.length < 2 || args[1].isEmpty()) {
			throw new IllegalArgumentException("Falta el firmware: ./gradlew probarPs3 -Pfirmware=<PS3UPDAT.PUP>");
		}
		Path sistema = salida.resolve("sistema");
		Path carpeta = sistema.resolve("rpcs3");
		Path vsh = carpeta.resolve("dev_flash").resolve("vsh").resolve("module").resolve("vsh.self");
		if (!Files.isRegularFile(vsh)) {
			Files.createDirectories(carpeta);
			Files.copy(Path.of(args[1]), carpeta.resolve("PS3UPDAT.PUP"), StandardCopyOption.REPLACE_EXISTING);
		}

		java.util.Set<String> errores = java.util.concurrent.ConcurrentHashMap.newKeySet();
		com.sun.jna.Native.setCallbackExceptionHandler((cb, e) -> {
			if (errores.add(e.toString())) {
				e.printStackTrace();
			}
		});
		long inicioFirmware = System.nanoTime();
		Nucleo nucleo = Nucleo.obtener(Sistema.PS3, salida.resolve("nativos"), sistema);
		System.out.printf("Núcleo iniciado en %.1f s; firmware instalado: %s%n", (System.nanoTime() - inicioFirmware) / 1e9, Files.isRegularFile(vsh));
		long[] muestrasAudio = {0};
		nucleo.setSalidaAudio((muestras, cuadros) -> muestrasAudio[0] += cuadros);
		Path juego = args.length > 2 && !args[2].isEmpty() ? Path.of(args[2]) : vsh;
		nucleo.cargarArchivo(juego);
		System.out.printf("PS3: %.4f fps, audio a %.0f Hz, cargado %s%n", nucleo.fps(), nucleo.frecuenciaAudio(), juego.getFileName());

		int[] cuadro = new int[Sistema.PS3.ancho * Sistema.PS3.alto];
		int total = Integer.getInteger("emumod.cuadros", 1200);
		long inicio = System.nanoTime(), tramo = inicio;
		// RPCS3 corre en sus propios hilos y retro_run no espera: se va a 60 cuadros por segundo como un frontend
		long periodo = (long) (1e9 / nucleo.fps());
		for (int i = 1; i <= total; i++) {
			nucleo.correrCuadro();
			long espera = inicio + i * periodo - System.nanoTime();
			if (espera > 0) {
				Thread.sleep(espera / 1_000_000, (int) (espera % 1_000_000));
			}
			if (i % 150 == 0) {
				long ahora = System.nanoTime();
				System.out.printf("Cuadro %d: x%.2f de tiempo real (últimos 150: x%.2f)%n", i, i / nucleo.fps() / ((ahora - inicio) / 1e9),
						150 / nucleo.fps() / ((ahora - tramo) / 1e9));
				tramo = ahora;
				nucleo.copiarCuadro(cuadro);
				guardar(cuadro, salida.resolve("ps3_" + i + ".png"));
			}
		}
		nucleo.copiarCuadro(cuadro);
		long noNegros = java.util.Arrays.stream(cuadro).filter(p -> (p & 0xFFFFFF) > 0x101010).count();
		System.out.printf("Píxeles no negros en el último cuadro: %d de %d; audio: %d cuadros%n", noNegros, cuadro.length, muestrasAudio[0]);
		// La pantalla de "Compiling PPU Modules" (la primera vez) es casi toda negra: basta con que haya algo dibujado
		if (noNegros < 1000) {
			nucleo.apagar();
			throw new AssertionError("La pantalla quedó negra");
		}
		if (!errores.isEmpty()) {
			throw new AssertionError("Hubo errores en los callbacks: " + errores);
		}
		nucleo.apagar();
		System.out.println("Prueba de PS3 OK");
		System.exit(0);
	}

	private static void guardar(int[] cuadro, Path archivo) throws Exception {
		BufferedImage img = new BufferedImage(Sistema.PS3.ancho, Sistema.PS3.alto, BufferedImage.TYPE_INT_RGB);
		img.setRGB(0, 0, Sistema.PS3.ancho, Sistema.PS3.alto, cuadro, 0, Sistema.PS3.ancho);
		ImageIO.write(img, "png", archivo.toFile());
	}
}
