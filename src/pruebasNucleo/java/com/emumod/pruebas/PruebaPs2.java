package com.emumod.pruebas;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javax.imageio.ImageIO;

import com.emumod.client.emu.Nucleo;
import com.emumod.client.emu.Sistema;

/**
 * Prueba headless del núcleo de PS2 (LRPS2 con el renderizador por software), sin Minecraft ni juegos: arranca la BIOS
 * que se le pasa sin disco (el menú de la PS2) y revisa video, audio, estados y velocidad. La BIOS es del usuario y solo
 * se copia a la carpeta de la prueba. Uso: ./gradlew probarPs2 -Pbios=<ruta a la BIOS>
 */
public final class PruebaPs2 {
	private PruebaPs2() {
	}

	public static void main(String[] args) throws Exception {
		Path salida = Path.of(args[0]);
		if (args.length < 2 || args[1].isEmpty()) {
			throw new IllegalArgumentException("Falta la BIOS: ./gradlew probarPs2 -Pbios=<ruta>");
		}
		Path bios = Path.of(args[1]);
		Path carpetaBios = salida.resolve("sistema").resolve("pcsx2").resolve("bios");
		Files.createDirectories(carpetaBios);
		Files.copy(bios, carpetaBios.resolve(bios.getFileName()), StandardCopyOption.REPLACE_EXISTING);

		java.util.Set<String> errores = java.util.concurrent.ConcurrentHashMap.newKeySet();
		com.sun.jna.Native.setCallbackExceptionHandler((cb, e) -> {
			if (errores.add(e.toString())) {
				e.printStackTrace();
			}
		});
		Nucleo nucleo = Nucleo.obtener(Sistema.PS2, salida.resolve("nativos"), salida.resolve("sistema"));
		long[] muestrasAudio = {0};
		long[] energia = {0};
		nucleo.setSalidaAudio((muestras, cuadros) -> {
			muestrasAudio[0] += cuadros;
			for (int i = 0; i < cuadros * 2; i++) {
				energia[0] += Math.abs(muestras[i]);
			}
		});
		nucleo.setOpcion("pcsx2_bios", bios.getFileName().toString());
		// -Demumod.opcion.<clave>=<valor> cambia opciones del núcleo, para comparar velocidades
		for (String propiedad : System.getProperties().stringPropertyNames()) {
			if (propiedad.startsWith("emumod.opcion.")) {
				nucleo.setOpcion(propiedad.substring("emumod.opcion.".length()), System.getProperty(propiedad));
			}
		}
		// Con un juego (tercer argumento) se mide la velocidad con ese juego; sin él, se arranca el menú de la BIOS
		Path juego = args.length > 2 && !args[2].isEmpty() ? Path.of(args[2]) : null;
		nucleo.cargarArchivo(juego);
		System.out.printf("PS2: %.4f fps, audio a %.0f Hz%n", nucleo.fps(), nucleo.frecuenciaAudio());

		int[] cuadro = new int[Sistema.PS2.ancho * Sistema.PS2.alto];
		int total = Integer.getInteger("emumod.cuadros", 900);
		long inicio = System.nanoTime(), tramo = inicio;
		for (int i = 1; i <= total; i++) {
			nucleo.correrCuadro();
			if (i % 150 == 0) {
				long ahora = System.nanoTime();
				System.out.printf("Cuadro %d: x%.2f de tiempo real (últimos 150: x%.2f)%n", i, i / nucleo.fps() / ((ahora - inicio) / 1e9),
						150 / nucleo.fps() / ((ahora - tramo) / 1e9));
				tramo = ahora;
				nucleo.copiarCuadro(cuadro);
				guardar(cuadro, salida.resolve("ps2_" + i + ".png"));
			}
		}
		nucleo.copiarCuadro(cuadro);
		long noNegros = java.util.Arrays.stream(cuadro).filter(p -> (p & 0xFFFFFF) > 0x101010).count();
		System.out.printf("Píxeles no negros en el último cuadro: %d de %d%n", noNegros, cuadro.length);
		double promedio = muestrasAudio[0] == 0 ? 0 : energia[0] / (double) (muestrasAudio[0] * 2);
		System.out.printf("Audio: %d cuadros de audio, amplitud media %.0f%n", muestrasAudio[0], promedio);
		if (noNegros < cuadro.length / 50) {
			throw new AssertionError("La pantalla quedó negra");
		}
		if (juego != null) {
			nucleo.apagar();
			System.out.println("Medición con juego terminada");
			System.exit(0);
		}

		byte[] estado = nucleo.guardarEstado();
		if (estado == null || !nucleo.cargarEstado(estado)) {
			throw new AssertionError("Fallaron los estados guardados");
		}
		nucleo.correrCuadro();
		System.out.println("Estado guardado y cargado: " + estado.length + " bytes");

		// Cambiar de juego: se descarga y se vuelve a arrancar en el mismo proceso
		nucleo.cargarArchivo(null);
		for (int i = 0; i < 900; i++) {
			nucleo.correrCuadro();
		}
		nucleo.copiarCuadro(cuadro);
		noNegros = java.util.Arrays.stream(cuadro).filter(p -> (p & 0xFFFFFF) > 0x101010).count();
		System.out.printf("Segundo arranque: %d píxeles no negros%n", noNegros);
		guardar(cuadro, salida.resolve("ps2_segundo.png"));
		if (noNegros < cuadro.length / 50) {
			throw new AssertionError("El segundo arranque quedó negro");
		}
		if (!errores.isEmpty()) {
			throw new AssertionError("Hubo errores en los callbacks: " + errores);
		}
		nucleo.apagar();
		System.out.println("Prueba de PS2 OK");
		System.exit(0);
	}

	private static void guardar(int[] cuadro, Path archivo) throws Exception {
		BufferedImage img = new BufferedImage(Sistema.PS2.ancho, Sistema.PS2.alto, BufferedImage.TYPE_INT_RGB);
		img.setRGB(0, 0, Sistema.PS2.ancho, Sistema.PS2.alto, cuadro, 0, Sistema.PS2.ancho);
		ImageIO.write(img, "png", archivo.toFile());
	}
}
