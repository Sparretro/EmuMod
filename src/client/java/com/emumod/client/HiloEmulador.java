package com.emumod.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.emumod.EmuMod;
import com.emumod.client.EmuladorCliente.Mensaje;
import com.emumod.client.emu.Nucleo;
import com.emumod.client.emu.Sistema;

/**
 * Hilo dueño del núcleo: todas las llamadas a la DLL se hacen acá. Minecraft le manda órdenes por una cola
 * y toma el último cuadro terminado. Guarda la partida del cartucho (.sav) sola y maneja los estados guardados.
 */
final class HiloEmulador {
	private static final long ESPERA_EN_PAUSA_MS = 50;
	private static final long PILA = 64L * 1024 * 1024;
	// Cada cuántos cuadros emulados se revisa si el juego escribió en su memoria de guardado
	private static final int CUADROS_ENTRE_REVISIONES_DE_GUARDADO = 120;
	// Acelerado, se muestra imagen al menos tantas veces por segundo
	private static final int MUESTRAS_POR_SEGUNDO_ACELERADO = 30;

	private final Sistema consola;
	private final Path carpetaNativos;
	private final Path carpetaSistema;
	private final Path carpetaPartidas;
	private final Path carpetaEstados;
	private final LinkedBlockingQueue<Runnable> ordenes = new LinkedBlockingQueue<>();
	private final Thread hilo;

	private volatile boolean corriendo = true;
	private volatile boolean pausado = true;
	private volatile int velocidad = 1;
	private volatile int botones;
	private volatile int palancaX, palancaY, palancaDerechaX, palancaDerechaY;
	private volatile int punteroX, punteroY, botonesMouse;
	private volatile boolean hayJuego;
	private volatile String error;
	private volatile double velocidadReal;

	private final Object cerrojoCuadro = new Object();
	private final int[] cuadroPublicado;
	private long numeroCuadro;

	// Solo desde el hilo del emulador
	private Nucleo nucleo;
	private SalidaSonido sonido;
	private int frecuenciaSonido;
	private String nombreJuego;
	private byte[] ultimoGuardado;
	private int cuadrosDesdeRevision;
	private long ultimoNanos;
	private double atraso;
	private long inicioMedicion;
	private int cuadrosMedidos;
	private boolean enPausaYa;

	HiloEmulador(Sistema consola, Path carpetaNativos, Path carpetaSistema, Path carpetaPartidas, Path carpetaEstados) {
		this.consola = consola;
		cuadroPublicado = new int[consola.ancho * consola.alto];
		this.carpetaNativos = carpetaNativos;
		this.carpetaSistema = carpetaSistema;
		this.carpetaPartidas = carpetaPartidas;
		this.carpetaEstados = carpetaEstados;
		// Pila grande: RPCS3 (PS3) se desborda con el megabyte que Java les da a los hilos
		hilo = new Thread(null, this::bucle, "EmuMod emulador " + consola, PILA);
		hilo.setDaemon(true);
		hilo.start();
	}

	// ---- Desde Minecraft (cualquier hilo) ----

	void cargar(byte[] rom, String nombre, Consumer<Mensaje> alTerminar) {
		ordenes.add(() -> alTerminar.accept(cargarAhora(n -> n.cargarJuego(rom, nombre), nombre, rom.length / 1024)));
	}

	/** Como {@link #cargar}, pasándole antes opciones al núcleo (las BIOS del jugador en la DS). */
	void cargar(byte[] rom, String nombre, java.util.Map<String, String> opciones, Consumer<Mensaje> alTerminar) {
		ordenes.add(() -> alTerminar.accept(cargarAhora(n -> {
			opciones.forEach(n::setOpcion);
			n.cargarJuego(rom, nombre);
		}, nombre, rom.length / 1024)));
	}

	/**
	 * Carga un juego que el núcleo lee del disco (PS2), o arranca sin juego si {@code archivo} es null. {@code opciones}
	 * se le pasan al núcleo antes (la BIOS elegida).
	 */
	void cargarArchivo(Path archivo, String nombre, java.util.Map<String, String> opciones, Consumer<Mensaje> alTerminar) {
		ordenes.add(() -> alTerminar.accept(cargarAhora(n -> {
			opciones.forEach(n::setOpcion);
			n.cargarArchivo(archivo);
		}, nombre, 0)));
	}

	void guardarEstado(int ranura, Consumer<Mensaje> alTerminar) {
		ordenes.add(() -> alTerminar.accept(guardarEstadoAhora(ranura)));
	}

	void cargarEstado(int ranura, Consumer<Mensaje> alTerminar) {
		ordenes.add(() -> alTerminar.accept(cargarEstadoAhora(ranura)));
	}

	void reiniciar(Consumer<Mensaje> alTerminar) {
		ordenes.add(() -> alTerminar.accept(reiniciarAhora()));
	}

	void setPausado(boolean pausado) {
		this.pausado = pausado;
	}

	void setVelocidad(int velocidad) {
		this.velocidad = velocidad;
	}

	void setBotones(int botones) {
		this.botones = botones;
	}

	void setPuntero(int x, int y, int botones) {
		punteroX = x;
		punteroY = y;
		botonesMouse = botones;
	}

	void setPalancas(int[] izquierda, int[] derecha) {
		palancaX = izquierda[0];
		palancaY = izquierda[1];
		palancaDerechaX = derecha[0];
		palancaDerechaY = derecha[1];
	}

	void setVolumen(float volumen) {
		SalidaSonido s = sonido;
		if (s != null) {
			s.setVolumen(volumen);
		}
	}

	boolean hayJuego() {
		return hayJuego;
	}

	String error() {
		return error;
	}

	/** Velocidad lograda respecto de la consola real (1 = tiempo real), medida cada segundo. */
	double velocidadReal() {
		return velocidadReal;
	}

	/** Copia el último cuadro si es más nuevo que {@code visto}; devuelve el número del cuadro copiado. */
	long tomarCuadro(int[] destino, long visto) {
		synchronized (cerrojoCuadro) {
			if (numeroCuadro == visto) {
				return visto;
			}
			System.arraycopy(cuadroPublicado, 0, destino, 0, destino.length);
			return numeroCuadro;
		}
	}

	/** Guarda la partida y termina el hilo (al cerrar Minecraft). */
	void cerrar() {
		ordenes.add(() -> {
			revisarGuardado(true);
			// Sin apagarlo, Mupen64Plus-Next deja hilos de Angrylion que traban el cierre del proceso
			if (nucleo != null) {
				nucleo.apagar();
			}
			corriendo = false;
		});
		try {
			hilo.join(3000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	// ---- Hilo del emulador ----

	private void bucle() {
		while (corriendo) {
			try {
				Runnable orden;
				while ((orden = ordenes.poll()) != null) {
					orden.run();
				}
				if (!corriendo) {
					break;
				}
				if (pausado || !hayJuego) {
					enPausa();
					orden = ordenes.poll(ESPERA_EN_PAUSA_MS, TimeUnit.MILLISECONDS);
					if (orden != null) {
						orden.run();
					}
					continue;
				}
				enPausaYa = false;
				nucleo.setBotones(botones);
				nucleo.setPalanca(palancaX, palancaY);
				nucleo.setPalancaDerecha(palancaDerechaX, palancaDerechaY);
				nucleo.setPuntero(punteroX, punteroY, botonesMouse);
				if (velocidad == 1 && sonido.disponible()) {
					correrSincronizadoConAudio();
				} else {
					correrConReloj();
				}
				medirVelocidad();
			} catch (InterruptedException e) {
				break;
			} catch (Throwable e) {
				EmuMod.LOGGER.error("Error en el emulador", e);
				error = e.getMessage() != null ? e.getMessage() : e.toString();
				hayJuego = false;
			}
		}
		if (sonido != null) {
			sonido.cerrar();
		}
	}

	private void enPausa() {
		if (enPausaYa) {
			return;
		}
		enPausaYa = true;
		if (sonido != null) {
			sonido.detener();
		}
		revisarGuardado(true);
		ultimoNanos = 0;
		inicioMedicion = 0;
	}

	// x1: escribir el audio bloquea hasta que la placa de sonido hace lugar, y eso marca el ritmo
	private void correrSincronizadoConAudio() {
		ultimoNanos = 0;
		nucleo.setVideoActivo(true);
		nucleo.setAudioActivo(true);
		nucleo.correrCuadro();
		publicarCuadro();
		contarCuadros(1);
	}

	// Acelerado (o sin audio): por reloj, sin sonido, dibujando solo el cuadro que se va a mostrar
	private void correrConReloj() throws InterruptedException {
		if (sonido != null) {
			sonido.detener();
		}
		double cuadrosPorSegundo = nucleo.fps() * velocidad;
		long ahora = System.nanoTime();
		if (ultimoNanos == 0) {
			ultimoNanos = ahora;
			atraso = 1;
		}
		atraso += (ahora - ultimoNanos) * cuadrosPorSegundo / 1e9;
		ultimoNanos = ahora;

		int cuadros = (int) atraso;
		if (cuadros == 0) {
			long faltaNanos = (long) ((1 - atraso) / cuadrosPorSegundo * 1e9);
			TimeUnit.NANOSECONDS.sleep(Math.max(faltaNanos, 200_000));
			return;
		}
		// Si la PC no da abasto no se acumula atraso: corre lo más rápido que puede
		int maximo = Math.max(1, (int) Math.ceil(cuadrosPorSegundo / MUESTRAS_POR_SEGUNDO_ACELERADO));
		if (cuadros > maximo) {
			cuadros = maximo;
			atraso = 0;
		} else {
			atraso -= cuadros;
		}
		nucleo.setAudioActivo(false);
		for (int i = 0; i < cuadros; i++) {
			nucleo.setVideoActivo(i == cuadros - 1);
			nucleo.correrCuadro();
		}
		nucleo.setVideoActivo(true);
		publicarCuadro();
		contarCuadros(cuadros);
	}

	private void contarCuadros(int cuadros) {
		cuadrosMedidos += cuadros;
		cuadrosDesdeRevision += cuadros;
		if (cuadrosDesdeRevision >= CUADROS_ENTRE_REVISIONES_DE_GUARDADO) {
			revisarGuardado(false);
		}
	}

	private void medirVelocidad() {
		long ahora = System.nanoTime();
		if (inicioMedicion == 0) {
			inicioMedicion = ahora;
			cuadrosMedidos = 0;
		} else if (ahora - inicioMedicion >= 1_000_000_000L) {
			velocidadReal = cuadrosMedidos / ((ahora - inicioMedicion) / 1e9) / nucleo.fps();
			inicioMedicion = ahora;
			cuadrosMedidos = 0;
		}
	}

	private void publicarCuadro() {
		synchronized (cerrojoCuadro) {
			nucleo.copiarCuadro(cuadroPublicado);
			numeroCuadro++;
		}
	}

	// tamanioKb solo es para el registro (0 si el núcleo lee el juego del disco)
	private Mensaje cargarAhora(Consumer<Nucleo> cargar, String nombre, int tamanioKb) {
		try {
			revisarGuardado(true);
			if (nucleo == null) {
				nucleo = Nucleo.obtener(consola, carpetaNativos, carpetaSistema);
			}
			if (sonido != null) {
				sonido.detener();
			}
			cargar.accept(nucleo);
			// La frecuencia de audio se conoce recién con el juego cargado (SkyEmu 48 kHz, QuickNES 44,1 kHz)
			int frecuencia = (int) Math.round(nucleo.frecuenciaAudio() > 0 ? nucleo.frecuenciaAudio() : 48000);
			if (sonido == null || frecuencia != frecuenciaSonido) {
				if (sonido != null) {
					sonido.cerrar();
				}
				sonido = new SalidaSonido(frecuencia);
				frecuenciaSonido = frecuencia;
				nucleo.setSalidaAudio(sonido);
			}
			nombreJuego = nombre;
			hayJuego = true;
			error = null;
			cuadrosDesdeRevision = 0;

			boolean habiaPartida = false;
			Path sav = archivoPartida();
			if (Files.isRegularFile(sav)) {
				nucleo.escribirGuardado(Files.readAllBytes(sav));
				habiaPartida = true;
			}
			ultimoGuardado = nucleo.leerGuardado();
			EmuMod.LOGGER.info("Juego cargado: {} ({} KB){}", nombre, tamanioKb, habiaPartida ? ", con partida guardada" : "");
			return Mensaje.ok(habiaPartida ? "screen.emumod.rom_cargada_con_partida" : "screen.emumod.rom_cargada", nombre);
		} catch (Exception | LinkageError e) {
			EmuMod.LOGGER.error("No se pudo cargar {}", nombre, e);
			error = e.getMessage() != null ? e.getMessage() : e.toString();
			return Mensaje.error("screen.emumod.error_nucleo", error);
		}
	}

	/** Si el juego cambió su memoria de guardado, la escribe en el .sav. */
	private void revisarGuardado(boolean forzar) {
		cuadrosDesdeRevision = 0;
		if (!hayJuego || nucleo == null || (!forzar && ultimoGuardado == null)) {
			return;
		}
		byte[] actual = nucleo.leerGuardado();
		if (actual == null || Arrays.equals(actual, ultimoGuardado)) {
			return;
		}
		try {
			escribirAtomico(archivoPartida(), actual);
			ultimoGuardado = actual;
			EmuMod.LOGGER.debug("Partida guardada: {}", archivoPartida());
		} catch (IOException e) {
			EmuMod.LOGGER.error("No se pudo guardar la partida de {}", nombreJuego, e);
		}
	}

	private Mensaje guardarEstadoAhora(int ranura) {
		if (!hayJuego) {
			return Mensaje.error("screen.emumod.sin_juego");
		}
		byte[] estado = nucleo.guardarEstado();
		if (estado == null) {
			return Mensaje.error("screen.emumod.estado_error_guardar", ranura);
		}
		try {
			escribirAtomico(archivoEstado(ranura), estado);
		} catch (IOException e) {
			EmuMod.LOGGER.error("No se pudo escribir el estado {}", ranura, e);
			return Mensaje.error("screen.emumod.estado_error_guardar", ranura);
		}
		return Mensaje.ok("screen.emumod.estado_guardado", ranura);
	}

	private Mensaje cargarEstadoAhora(int ranura) {
		if (!hayJuego) {
			return Mensaje.error("screen.emumod.sin_juego");
		}
		Path archivo = archivoEstado(ranura);
		if (!Files.isRegularFile(archivo)) {
			return Mensaje.error("screen.emumod.estado_vacio", ranura);
		}
		try {
			if (!nucleo.cargarEstado(Files.readAllBytes(archivo))) {
				return Mensaje.error("screen.emumod.estado_incompatible", ranura);
			}
		} catch (IOException e) {
			EmuMod.LOGGER.error("No se pudo leer el estado {}", ranura, e);
			return Mensaje.error("screen.emumod.estado_incompatible", ranura);
		}
		if (sonido != null) {
			sonido.detener();
		}
		publicarCuadro();
		return Mensaje.ok("screen.emumod.estado_cargado", ranura);
	}

	private Mensaje reiniciarAhora() {
		if (!hayJuego) {
			return Mensaje.error("screen.emumod.sin_juego");
		}
		// El reinicio de SkyEmu vuelve a cargar la ROM: los datos de guardado se reponen después
		revisarGuardado(true);
		byte[] guardado = nucleo.leerGuardado();
		nucleo.reiniciar();
		if (guardado != null) {
			nucleo.escribirGuardado(guardado);
		}
		if (sonido != null) {
			sonido.detener();
		}
		return Mensaje.ok("screen.emumod.reiniciado");
	}

	private Path archivoPartida() {
		return carpetaPartidas.resolve(sinExtension(nombreJuego) + ".sav");
	}

	private Path archivoEstado(int ranura) {
		return carpetaEstados.resolve(sinExtension(nombreJuego) + ".ranura" + ranura + ".estado");
	}

	private static String sinExtension(String nombre) {
		int punto = nombre.lastIndexOf('.');
		return punto > 0 ? nombre.substring(0, punto) : nombre;
	}

	// Escribe a un temporal y lo mueve: si el juego se cierra a mitad, no queda un archivo cortado
	private static void escribirAtomico(Path destino, byte[] datos) throws IOException {
		Files.createDirectories(destino.getParent());
		Path temporal = destino.resolveSibling(destino.getFileName() + ".tmp");
		Files.write(temporal, datos);
		Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}
}
