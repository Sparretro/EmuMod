package com.emumod.pruebas;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Set;

import javax.imageio.ImageIO;

import com.emumod.client.Controles;
import com.emumod.client.emu.Nucleo;
import com.emumod.client.emu.Sistema;
import org.lwjgl.glfw.GLFW;

/**
 * Arranca el núcleo con un cartucho vacío (ceros, sin código: no es una ROM) para ver la animación de la BIOS,
 * guarda capturas y el audio, prueba estados guardados y guardado de batería, y mide la velocidad.
 * Uso: PruebaNucleo <carpeta de salida> [ruta a gba_bios.bin]
 */
public final class PruebaNucleo {
	private static final int[] CUADROS_A_CAPTURAR = {30, 90, 150, 240};
	private static final int CUADRO_ESTADO = 90;
	private static final String CARTUCHO = "cartucho_vacio.gba";

	private static int fallos;

	public static void main(String[] args) throws Exception {
		probarControles();

		Path salida = Path.of(args[0]);
		Path sistema = salida.resolve("sistema");
		Files.createDirectories(sistema.resolve("SkyEmu"));
		Path biosDestino = sistema.resolve("SkyEmu/gba_bios.bin");
		if (args.length > 1 && !args[1].isBlank()) {
			Files.copy(Path.of(args[1]), biosDestino, StandardCopyOption.REPLACE_EXISTING);
		} else {
			Files.deleteIfExists(biosDestino);
		}
		System.out.println("BIOS: " + (Files.exists(biosDestino) ? "oficial (del usuario)" : "reemplazo libre de SkyEmu"));

		Nucleo nucleo = Nucleo.obtener(Sistema.GBA, salida.resolve("nativos"), sistema);
		ByteArrayOutputStream audio = new ByteArrayOutputStream();
		nucleo.setSalidaAudio((muestras, cuadros) -> {
			ByteBuffer bytes = ByteBuffer.allocate(cuadros * 4).order(ByteOrder.LITTLE_ENDIAN);
			for (int i = 0; i < cuadros * 2; i++) {
				bytes.putShort(muestras[i]);
			}
			audio.writeBytes(bytes.array());
		});
		nucleo.cargarJuego(new byte[0x200], CARTUCHO);
		System.out.printf("Juego cargado: fps=%.4f audio=%.0f Hz%n", nucleo.fps(), nucleo.frecuenciaAudio());

		int[] cuadro = new int[Sistema.GBA.ancho * Sistema.GBA.alto];
		int[] cuadroTrasEstado = null;
		byte[] estado = null;
		boolean huboImagen = false;
		int siguiente = 0;
		for (int n = 1; n <= CUADROS_A_CAPTURAR[CUADROS_A_CAPTURAR.length - 1]; n++) {
			nucleo.correrCuadro();
			if (n == CUADRO_ESTADO) {
				estado = nucleo.guardarEstado();
			}
			if (n == CUADRO_ESTADO + 1) {
				cuadroTrasEstado = new int[cuadro.length];
				nucleo.copiarCuadro(cuadroTrasEstado);
			}
			if (n == CUADROS_A_CAPTURAR[siguiente]) {
				nucleo.copiarCuadro(cuadro);
				long colores = Arrays.stream(cuadro).distinct().count();
				huboImagen |= colores > 1;
				Path png = salida.resolve("cuadro_" + n + ".png");
				guardar(cuadro, png);
				System.out.printf("Cuadro %d: %d colores distintos -> %s%n", n, colores, png);
				siguiente++;
			}
		}
		nucleo.setSalidaAudio(null);
		comprobar(huboImagen, "Video: hay imagen");
		probarAudio(audio.toByteArray(), (int) nucleo.frecuenciaAudio(), salida.resolve("arranque_bios.wav"));
		probarEstado(nucleo, estado, cuadroTrasEstado);
		probarGuardado(nucleo);
		medirVelocidad(nucleo);

		nucleo.descargarJuego();
		if (fallos > 0) {
			System.out.println("FALLARON " + fallos + " comprobaciones");
			System.exit(1);
		}
		System.out.println("OK");
	}

	private static void probarAudio(byte[] pcm, int frecuencia, Path wav) throws Exception {
		ByteBuffer datos = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
		int pico = 0;
		while (datos.remaining() >= 2) {
			pico = Math.max(pico, Math.abs(datos.getShort()));
		}
		double segundos = pcm.length / 4.0 / frecuencia;
		escribirWav(pcm, frecuencia, wav);
		System.out.printf("Audio: %.2f s, pico %d de 32767 -> %s%n", segundos, pico, wav);
		comprobar(segundos > 3.9 && segundos < 4.1, "Audio: 240 cuadros dan ~4 s de sonido");
		comprobar(pico > 1000, "Audio: el arranque de la BIOS no es silencio");
	}

	private static void probarEstado(Nucleo nucleo, byte[] estado, int[] esperado) {
		comprobar(estado != null, "Estado: se pudo guardar (" + (estado == null ? 0 : estado.length / 1024) + " KB)");
		comprobar(nucleo.cargarEstado(estado), "Estado: se pudo cargar");
		nucleo.correrCuadro();
		int[] obtenido = new int[esperado.length];
		nucleo.copiarCuadro(obtenido);
		comprobar(Arrays.equals(esperado, obtenido), "Estado: el cuadro siguiente al estado es idéntico al original");
		comprobar(!nucleo.cargarEstado(new byte[16]), "Estado: rechaza datos de tamaño incorrecto");
	}

	private static void probarGuardado(Nucleo nucleo) {
		byte[] original = nucleo.leerGuardado();
		comprobar(original != null, "Guardado: memoria del cartucho de " + (original == null ? 0 : original.length / 1024) + " KB");
		byte[] patron = new byte[8 * 1024];
		for (int i = 0; i < patron.length; i++) {
			patron[i] = (byte) (i * 31 + 7);
		}
		// Recargar el juego borra la memoria; restaurarla tiene que dejar el patrón y 0xFF en el resto
		nucleo.cargarJuego(new byte[0x200], CARTUCHO);
		nucleo.escribirGuardado(patron);
		byte[] leido = nucleo.leerGuardado();
		comprobar(Arrays.equals(patron, Arrays.copyOf(leido, patron.length)), "Guardado: el patrón se restaura igual");
		boolean restoBorrado = true;
		for (int i = patron.length; i < leido.length; i++) {
			restoBorrado &= leido[i] == (byte) 0xFF;
		}
		comprobar(restoBorrado, "Guardado: el resto queda en 0xFF");
	}

	private static void medirVelocidad(Nucleo nucleo) {
		for (boolean video : new boolean[] {true, false}) {
			nucleo.setVideoActivo(video);
			int cuadros = 600;
			long inicio = System.nanoTime();
			for (int i = 0; i < cuadros; i++) {
				nucleo.correrCuadro();
			}
			double porSegundo = cuadros / ((System.nanoTime() - inicio) / 1e9);
			System.out.printf("Velocidad %s: %.0f cuadros/s (x%.1f)%n", video ? "con video" : "sin video", porSegundo, porSegundo / nucleo.fps());
		}
		nucleo.setVideoActivo(true);
	}

	private static void probarControles() {
		Set<Integer> apretadas = Set.of(GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_RIGHT_SHIFT);
		int esperada = Nucleo.Boton.A.mascara() | Nucleo.Boton.ARRIBA.mascara() | Nucleo.Boton.SELECT.mascara();
		comprobar(Controles.mascara(Sistema.GBA, apretadas::contains) == esperada, "Controles: Z+Arriba+Shift = A+ARRIBA+SELECT");
		comprobar(Controles.mascara(Sistema.GBA, t -> false) == 0 && !Controles.esTeclaDelJuego(Sistema.GBA, GLFW.GLFW_KEY_E), "Controles: sin teclas de más");
		// N64: Z = A (B del RetroPad), C = gatillo Z (L2), I = C arriba (X); las flechas son la palanca
		Set<Integer> n64 = Set.of(GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_I, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_RIGHT);
		int esperadaN64 = Nucleo.Boton.B.mascara() | Nucleo.Boton.L2.mascara() | Nucleo.Boton.X.mascara();
		comprobar(Controles.mascara(Sistema.N64, n64::contains) == esperadaN64, "Controles N64: Z+C+I = A+Z+C arriba");
		int[] palanca = Controles.palanca(n64::contains);
		comprobar(palanca[0] > 20000 && palanca[1] < -20000 && Math.hypot(palanca[0], palanca[1]) <= 32768,
				"Controles N64: arriba+derecha = palanca en diagonal, sin pasarse");
	}

	private static void comprobar(boolean ok, String descripcion) {
		System.out.println((ok ? "  OK    " : "  FALLO ") + descripcion);
		if (!ok) {
			fallos++;
		}
	}

	private static void guardar(int[] cuadro, Path destino) throws Exception {
		BufferedImage img = new BufferedImage(Sistema.GBA.ancho, Sistema.GBA.alto, BufferedImage.TYPE_INT_RGB);
		img.setRGB(0, 0, Sistema.GBA.ancho, Sistema.GBA.alto, cuadro, 0, Sistema.GBA.ancho);
		ImageIO.write(img, "png", destino.toFile());
	}

	private static void escribirWav(byte[] pcm, int frecuencia, Path destino) throws Exception {
		ByteBuffer wav = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
		wav.put("RIFF".getBytes()).putInt(36 + pcm.length).put("WAVE".getBytes());
		wav.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 2)
				.putInt(frecuencia).putInt(frecuencia * 4).putShort((short) 4).putShort((short) 16);
		wav.put("data".getBytes()).putInt(pcm.length).put(pcm);
		Files.write(destino, wav.array());
	}
}
