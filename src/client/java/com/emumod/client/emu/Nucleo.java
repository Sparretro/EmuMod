package com.emumod.client.emu;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import com.sun.jna.CallbackReference;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Frontend libretro mínimo para los núcleos del mod (SkyEmu para GBA, QuickNES para NES). No depende de Minecraft.
 * Cada núcleo guarda su estado en variables globales de su DLL, así que hay una sola instancia por consola
 * y todas las llamadas a una instancia tienen que hacerse desde el mismo hilo.
 */
public final class Nucleo {
	private static final Logger LOGGER = LoggerFactory.getLogger("emumod/nucleo");
	// -Demumod.depurar=true muestra los mensajes de los núcleos en la consola (para las pruebas fuera de Minecraft)
	private static final boolean DEPURAR = Boolean.getBoolean("emumod.depurar");

	public enum Boton {
		// Y, X, L2, R2, L3 y R3 solo los usan la N64 y la PS2 (ver BotonN64 y BotonPs2)
		B(0), Y(1), SELECT(2), START(3), ARRIBA(4), ABAJO(5), IZQUIERDA(6), DERECHA(7), A(8), X(9), L(10), R(11), L2(12), R2(13),
		L3(14), R3(15);

		final int id;

		Boton(int id) {
			this.id = id;
		}

		public int mascara() {
			return 1 << id;
		}
	}

	private static final Map<Sistema, Nucleo> INSTANCIAS = new EnumMap<>(Sistema.class);

	private final Sistema sistema;

	private final Libretro api;
	private final Memory carpetaSistema;
	private final Path rutaSistema;
	private final Map<String, Memory> cadenas = new HashMap<>();
	private final int[] cuadro;
	// Formato de píxel que pidió el núcleo: SkyEmu usa XRGB8888 y QuickNES RGB565
	private int formatoPixel = Libretro.PIXEL_FORMAT_0RGB1555;
	private short[] fila565 = new short[0];
	private volatile int botones;
	// Palancas analógicas (N64 y PS2), de -32768 a 32767; y positiva es para abajo, como en libretro
	private volatile int palancaX, palancaY, palancaDerechaX, palancaDerechaY;
	// Mouse (puntero del Wiimote): posición en la pantalla de -32767 a 32767 y botones (1 izquierdo, 2 derecho, 4 medio)
	private volatile int punteroX, punteroY, botonesMouse;
	// Opciones que se cambian mientras corre (la BIOS de PS2 elegida); pisan las de Sistema
	private final Map<String, String> opcionesCambiadas = new java.util.concurrent.ConcurrentHashMap<>();
	// Cuadro tal como lo entrega el núcleo, cuando mide distinto que la consola (Angrylion da 640x480)
	private int[] filaGrande = new int[0];
	private int anchoVisto, altoVisto;
	private boolean juegoCargado;
	private double fps = 59.7275;
	private double frecuenciaAudio;
	private SalidaAudio salidaAudio;
	private short[] muestras = new short[0];
	private boolean videoActivo = true;
	private boolean audioActivo = true;

	// Referencias fuertes: si el GC las junta, la DLL llamaría a memoria liberada
	private final Libretro.Entorno entorno = this::entorno;
	private final Libretro.Log log = (nivel, formato) -> {
		LOGGER.debug("[{}] {}", nivel, formato.strip());
		if (DEPURAR) {
			System.err.println("[" + nivel + "] " + formato.strip());
		}
	};
	private final Libretro.Video video = this::video;
	private final Libretro.Muestra muestra = (izquierda, derecha) -> {
	};
	private final Libretro.LoteAudio loteAudio = this::loteAudio;
	private final Libretro.SondeoEntrada sondeo = () -> {
	};
	private final Libretro.EstadoEntrada estadoEntrada = this::estadoEntrada;

	// Solo la función propia del puente; el resto de la API es la de libretro
	private interface Puente extends Library {
		boolean puente_cargar(String ruta);

		void puente_permitir_gl(boolean permitir);
	}

	private Nucleo(Sistema consola, Path dll, Path puente, Path sistema) {
		this.sistema = consola;
		cuadro = new int[consola.ancho * consola.alto];
		if (puente != null) {
			String ruta = puente.toAbsolutePath().toString();
			Puente p = Native.load(ruta, Puente.class);
			if (!p.puente_cargar(dll.toAbsolutePath().toString())) {
				throw new IllegalStateException("No se pudo cargar el núcleo " + dll.getFileName());
			}
			p.puente_permitir_gl(consola.usaGl());
			api = Native.load(ruta, Libretro.class);
		} else {
			api = Native.load(dll.toAbsolutePath().toString(), Libretro.class);
		}
		if (api.retro_api_version() != Libretro.RETRO_API_VERSION) {
			throw new IllegalStateException("Versión de API libretro inesperada: " + api.retro_api_version());
		}
		rutaSistema = sistema;
		carpetaSistema = cadena(sistema.toAbsolutePath().toString());
		api.retro_set_environment(entorno);
		api.retro_set_video_refresh(video);
		api.retro_set_audio_sample(muestra);
		api.retro_set_audio_sample_batch(loteAudio);
		api.retro_set_input_poll(sondeo);
		api.retro_set_input_state(estadoEntrada);
		api.retro_init();
	}

	/**
	 * @param carpetaNativos dónde extraer la DLL del núcleo
	 * @param carpetaSistema carpeta de sistema libretro; la BIOS de GBA va en {@code <carpetaSistema>/SkyEmu/gba_bios.bin}.
	 *                       Si no está, SkyEmu usa su BIOS libre de reemplazo. La NES no usa BIOS.
	 */
	public static synchronized Nucleo obtener(Sistema consola, Path carpetaNativos, Path carpetaSistema) throws IOException {
		Nucleo nucleo = INSTANCIAS.get(consola);
		if (nucleo == null) {
			// Citra crea su subcarpeta adentro y no puede si la carpeta de sistema todavía no existe
			java.nio.file.Files.createDirectories(carpetaSistema);
			// Una copia del puente por consola: cada copia maneja un solo núcleo
			Path puente = consola.usaPuente()
					? Nativos.extraer(carpetaNativos, "libretro_puente", "libretro_puente_" + consola.name().toLowerCase(java.util.Locale.ROOT)) : null;
			nucleo = new Nucleo(consola, Nativos.extraer(carpetaNativos, consola.biblioteca), puente, carpetaSistema);
			INSTANCIAS.put(consola, nucleo);
		}
		return nucleo;
	}

	public Sistema sistema() {
		return sistema;
	}

	public void cargarJuego(byte[] rom, String nombre) {
		descargarJuego();
		Memory datos = new Memory(rom.length);
		datos.write(0, rom, 0, rom.length);
		Libretro.InfoJuego info = new Libretro.InfoJuego();
		// SkyEmu elige la consola por la extensión del path; el contenido va en data
		info.path = nombre.toLowerCase().endsWith(sistema.extension) ? nombre : nombre + sistema.extension;
		info.data = datos;
		info.size = rom.length;
		if (!api.retro_load_game(info)) {
			throw new IllegalArgumentException("El núcleo rechazó la ROM " + nombre);
		}
		despuesDeCargar();
	}

	/**
	 * Carga un juego que el núcleo lee él mismo desde el disco (PS2: las imágenes de disco pesan gigas). Con
	 * {@code archivo} null arranca la consola sin juego (en la PS2, el menú de la BIOS).
	 */
	public void cargarArchivo(Path archivo) {
		descargarJuego();
		// RPCS3 instala el firmware al iniciarse: si se lo dieron después, se reinicia el núcleo para que lo instale
		if (sistema == Sistema.PS3 && !java.nio.file.Files.isRegularFile(rutaSistema.resolve("rpcs3/dev_flash/vsh/module/vsh.self"))
				&& java.nio.file.Files.isRegularFile(rutaSistema.resolve("rpcs3/PS3UPDAT.PUP"))) {
			api.retro_deinit();
			api.retro_init();
		}
		Libretro.InfoJuego info = null;
		if (archivo != null) {
			info = new Libretro.InfoJuego();
			info.path = archivo.toAbsolutePath().toString();
		}
		if (!api.retro_load_game(info)) {
			throw new IllegalArgumentException("El núcleo rechazó " + (archivo == null ? "arrancar sin juego" : archivo.getFileName()));
		}
		despuesDeCargar();
	}

	private void despuesDeCargar() {
		juegoCargado = true;
		// Como RetroArch: se le dice qué control hay en el puerto 1. Dolphin arma el Wiimote recién con este aviso
		if (sistema == Sistema.WII) {
			api.retro_set_controller_port_device(0, Libretro.DEVICE_JOYPAD);
		}
		Libretro.InfoAv av = new Libretro.InfoAv();
		api.retro_get_system_av_info(av);
		fps = av.timing.fps;
		frecuenciaAudio = av.timing.sampleRate;
	}

	public void descargarJuego() {
		if (juegoCargado) {
			api.retro_unload_game();
			juegoCargado = false;
			if (sistema.reiniciarAlDescargar()) {
				api.retro_deinit();
				api.retro_init();
			}
		}
	}

	/** Descarga el juego y apaga el núcleo (al cerrar Minecraft); después no se puede volver a usar. */
	public void apagar() {
		if (juegoCargado) {
			api.retro_unload_game();
			juegoCargado = false;
		}
		api.retro_deinit();
	}

	public boolean hayJuego() {
		return juegoCargado;
	}

	public void reiniciar() {
		if (juegoCargado) {
			api.retro_reset();
		}
	}

	/** Emula un cuadro completo (1/fps segundos de consola). */
	public void correrCuadro() {
		if (juegoCargado) {
			api.retro_run();
		}
	}

	/** Copia el último cuadro en formato 0x00RRGGBB, fila por fila. */
	public void copiarCuadro(int[] destino) {
		System.arraycopy(cuadro, 0, destino, 0, cuadro.length);
	}

	/** Botones apretados como máscara de {@link Boton#mascara()}. Se puede llamar desde cualquier hilo. */
	public void setBotones(int mascara) {
		botones = mascara;
	}

	/** Palanca analógica, de -32768 a 32767 en cada eje (y positiva = abajo). Se puede llamar desde cualquier hilo. */
	public void setPalanca(int x, int y) {
		palancaX = x;
		palancaY = y;
	}

	/** Puntero (Wii): posición en la pantalla de -32767 a 32767 y botones del mouse (1 izquierdo, 2 derecho, 4 medio). */
	public void setPuntero(int x, int y, int botones) {
		punteroX = x;
		punteroY = y;
		botonesMouse = botones;
	}

	/** Palanca derecha (PS2), igual que {@link #setPalanca}. */
	public void setPalancaDerecha(int x, int y) {
		palancaDerechaX = x;
		palancaDerechaY = y;
	}

	/** Cambia una opción del núcleo; se lee al cargar el próximo juego. */
	public void setOpcion(String clave, String valor) {
		opcionesCambiadas.put(clave, valor);
	}

	public double fps() {
		return fps;
	}

	public double frecuenciaAudio() {
		return frecuenciaAudio;
	}

	/** Recibe el audio del núcleo: estéreo intercalado, 16 bits con signo, a {@link #frecuenciaAudio()} Hz. */
	public interface SalidaAudio {
		void escribir(short[] muestras, int cuadros);
	}

	public void setSalidaAudio(SalidaAudio salida) {
		salidaAudio = salida;
	}

	/** Sin video el núcleo no dibuja el cuadro (más rápido); sirve para saltear cuadros al acelerar. */
	public void setVideoActivo(boolean activo) {
		videoActivo = activo;
	}

	public void setAudioActivo(boolean activo) {
		audioActivo = activo;
	}

	/** Copia de la memoria de guardado del cartucho (SRAM/Flash/EEPROM), o null si no hay juego. */
	public byte[] leerGuardado() {
		long tamanio = juegoCargado ? api.retro_get_memory_size(Libretro.MEMORY_SAVE_RAM) : 0;
		if (tamanio == 0) {
			return null;
		}
		return api.retro_get_memory_data(Libretro.MEMORY_SAVE_RAM).getByteArray(0, (int) tamanio);
	}

	/** Restaura la memoria de guardado; lo que no cubre el archivo queda como memoria borrada (0xFF). */
	public void escribirGuardado(byte[] datos) {
		long tamanio = juegoCargado ? api.retro_get_memory_size(Libretro.MEMORY_SAVE_RAM) : 0;
		if (tamanio == 0) {
			return;
		}
		Pointer memoria = api.retro_get_memory_data(Libretro.MEMORY_SAVE_RAM);
		int copiar = (int) Math.min(tamanio, datos.length);
		memoria.write(0, datos, 0, copiar);
		if (copiar < tamanio) {
			memoria.setMemory(copiar, tamanio - copiar, (byte) 0xFF);
		}
	}

	/** Estado completo de la consola (estado guardado), o null si no hay juego. */
	public byte[] guardarEstado() {
		long tamanio = juegoCargado ? api.retro_serialize_size() : 0;
		if (tamanio == 0) {
			return null;
		}
		Memory memoria = new Memory(tamanio);
		if (!api.retro_serialize(memoria, tamanio)) {
			return null;
		}
		return memoria.getByteArray(0, (int) tamanio);
	}

	public boolean cargarEstado(byte[] estado) {
		if (!juegoCargado || estado.length != api.retro_serialize_size()) {
			return false;
		}
		Memory memoria = new Memory(estado.length);
		memoria.write(0, estado, 0, estado.length);
		return api.retro_unserialize(memoria, estado.length);
	}

	private long loteAudio(Pointer data, long cuadros) {
		SalidaAudio salida = salidaAudio;
		if (salida != null && data != null && cuadros > 0) {
			int cantidad = (int) cuadros * 2;
			if (muestras.length < cantidad) {
				muestras = new short[cantidad];
			}
			data.read(0, muestras, 0, cantidad);
			salida.escribir(muestras, (int) cuadros);
		}
		return cuadros;
	}

	private boolean entorno(int cmd, Pointer data) {
		switch (cmd & ~Libretro.ENV_EXPERIMENTAL) {
			case Libretro.ENV_GET_LOG_INTERFACE -> data.setPointer(0, CallbackReference.getFunctionPointer(log));
			case Libretro.ENV_SET_PIXEL_FORMAT -> {
				int pedido = data.getInt(0);
				if (pedido != Libretro.PIXEL_FORMAT_XRGB8888 && pedido != Libretro.PIXEL_FORMAT_RGB565) {
					return false;
				}
				formatoPixel = pedido;
			}
			case Libretro.ENV_GET_SYSTEM_DIRECTORY, Libretro.ENV_GET_SAVE_DIRECTORY -> data.setPointer(0, carpetaSistema);
			case Libretro.ENV_GET_VARIABLE -> {
				String clave = data.getPointer(0).getString(0);
				String valor = opcionesCambiadas.getOrDefault(clave, sistema.opciones.get(clave));
				if (valor == null) {
					data.setPointer(Native.POINTER_SIZE, null);
					return false;
				}
				data.setPointer(Native.POINTER_SIZE, cadena(valor));
			}
			case Libretro.ENV_GET_VARIABLE_UPDATE -> data.setByte(0, (byte) 0);
			case Libretro.ENV_GET_LANGUAGE -> data.setInt(0, Libretro.LANGUAGE_ENGLISH);
			case Libretro.ENV_GET_AUDIO_VIDEO_ENABLE -> data.setInt(0, (videoActivo ? 1 : 0) | (audioActivo ? 2 : 0));
			case Libretro.ENV_GET_CORE_OPTIONS_VERSION -> data.setInt(0, 2);
			case Libretro.ENV_SET_CORE_OPTIONS_V2_INTL, Libretro.ENV_SET_INPUT_DESCRIPTORS, Libretro.ENV_SET_MEMORY_MAPS, Libretro.ENV_SET_GEOMETRY -> {
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	private void video(Pointer data, int ancho, int alto, long pitch) {
		// data es null cuando el núcleo repite el cuadro anterior; sin video activo el cuadro no se dibujó
		if (!videoActivo || data == null || ancho <= 0 || alto <= 0) {
			return;
		}
		if (DEPURAR && (ancho != anchoVisto || alto != altoVisto)) {
			System.err.println("Cuadro de " + ancho + "x" + alto);
		}
		anchoVisto = ancho;
		altoVisto = alto;
		if (ancho != sistema.ancho || alto != sistema.alto) {
			if (formatoPixel == Libretro.PIXEL_FORMAT_XRGB8888) {
				achicar(data, ancho, alto, pitch);
			}
			return;
		}
		if (formatoPixel == Libretro.PIXEL_FORMAT_XRGB8888) {
			for (int y = 0; y < alto; y++) {
				data.read(y * pitch, cuadro, y * ancho, ancho);
			}
			return;
		}
		// 0RGB1555 (el formato por defecto si el núcleo no pide otro) no lo usa ninguno de los núcleos del mod
		if (formatoPixel != Libretro.PIXEL_FORMAT_RGB565) {
			return;
		}
		// RGB565 a 0x00RRGGBB, repitiendo los bits altos en los bajos para que el blanco quede 0xFFFFFF
		if (fila565.length < ancho) {
			fila565 = new short[ancho];
		}
		for (int y = 0; y < alto; y++) {
			data.read(y * pitch, fila565, 0, ancho);
			for (int x = 0; x < ancho; x++) {
				int p = fila565[x] & 0xFFFF;
				int r = (p >> 11) & 0x1F, g = (p >> 5) & 0x3F, b = p & 0x1F;
				cuadro[y * ancho + x] = ((r << 3 | r >> 2) << 16) | ((g << 2 | g >> 4) << 8) | (b << 3 | b >> 2);
			}
		}
	}

	// Lleva un cuadro XRGB8888 de cualquier tamaño al de la consola promediando cada bloque de píxeles (640x480 a 320x240
	// promedia de a 2x2, así no se pierden las líneas finas)
	private void achicar(Pointer data, int ancho, int alto, long pitch) {
		if (filaGrande.length < ancho) {
			filaGrande = new int[ancho];
		}
		int destinoAncho = sistema.ancho, destinoAlto = sistema.alto;
		if (ancho < destinoAncho || alto < destinoAlto) {
			estirar(data, ancho, alto, pitch);
			return;
		}
		int[] r = new int[destinoAncho], g = new int[destinoAncho], b = new int[destinoAncho], n = new int[destinoAncho];
		int filaDestino = 0;
		for (int y = 0; y < alto; y++) {
			data.read(y * pitch, filaGrande, 0, ancho);
			for (int x = 0; x < ancho; x++) {
				int p = filaGrande[x];
				int dx = (int) ((long) x * destinoAncho / ancho);
				r[dx] += (p >> 16) & 0xFF;
				g[dx] += (p >> 8) & 0xFF;
				b[dx] += p & 0xFF;
				n[dx]++;
			}
			// Se vuelca la fila de destino cuando la próxima fila de origen ya cae en otra
			int siguiente = (int) ((long) (y + 1) * destinoAlto / alto);
			if (siguiente != filaDestino || y == alto - 1) {
				for (int x = 0; x < destinoAncho; x++) {
					int c = Math.max(1, n[x]);
					cuadro[filaDestino * destinoAncho + x] = (r[x] / c) << 16 | (g[x] / c) << 8 | b[x] / c;
					r[x] = g[x] = b[x] = n[x] = 0;
				}
				filaDestino = Math.min(siguiente, destinoAlto - 1);
			}
		}
	}

	// Un cuadro más chico que la consola en algún lado (la PS2 cambia de resolución según el juego): el píxel más cercano
	private void estirar(Pointer data, int ancho, int alto, long pitch) {
		int destinoAncho = sistema.ancho, destinoAlto = sistema.alto;
		int filaLeida = -1;
		for (int dy = 0; dy < destinoAlto; dy++) {
			int y = (int) ((long) dy * alto / destinoAlto);
			if (y != filaLeida) {
				data.read(y * pitch, filaGrande, 0, ancho);
				filaLeida = y;
			}
			for (int dx = 0; dx < destinoAncho; dx++) {
				cuadro[dy * destinoAncho + dx] = filaGrande[(int) ((long) dx * ancho / destinoAncho)] & 0xFFFFFF;
			}
		}
	}

	private short estadoEntrada(int puerto, int dispositivo, int indice, int id) {
		// Los subtipos (como el Wiimote de costado de Dolphin) cuentan como su tipo base, igual que en RetroArch
		dispositivo &= 0xFF;
		if (puerto == 0 && dispositivo == Libretro.DEVICE_POINTER) {
			return (short) switch (id) {
				case Libretro.DEVICE_ID_POINTER_X -> punteroX;
				case Libretro.DEVICE_ID_POINTER_Y -> punteroY;
				case Libretro.DEVICE_ID_POINTER_PRESSED -> (botonesMouse & 1) != 0 ? 1 : 0;
				case Libretro.DEVICE_ID_POINTER_COUNT -> 1;
				default -> 0;
			};
		}
		if (puerto == 0 && dispositivo == Libretro.DEVICE_MOUSE) {
			return (short) switch (id) {
				case Libretro.DEVICE_ID_MOUSE_LEFT -> botonesMouse & 1;
				case Libretro.DEVICE_ID_MOUSE_RIGHT -> (botonesMouse >> 1) & 1;
				case Libretro.DEVICE_ID_MOUSE_MIDDLE -> (botonesMouse >> 2) & 1;
				default -> 0;
			};
		}
		if (puerto == 0 && dispositivo == Libretro.DEVICE_ANALOG) {
			int valor;
			if (indice == Libretro.DEVICE_INDEX_ANALOG_LEFT) {
				valor = id == Libretro.DEVICE_ID_ANALOG_X ? palancaX : palancaY;
			} else if (indice == Libretro.DEVICE_INDEX_ANALOG_RIGHT) {
				valor = id == Libretro.DEVICE_ID_ANALOG_X ? palancaDerechaX : palancaDerechaY;
			} else {
				return 0;
			}
			return (short) Math.max(-32768, Math.min(32767, valor));
		}
		if (puerto != 0 || dispositivo != Libretro.DEVICE_JOYPAD) {
			return 0;
		}
		int actuales = botones;
		if (id == Libretro.DEVICE_ID_JOYPAD_MASK) {
			return (short) actuales;
		}
		return (short) ((actuales >> id) & 1);
	}

	private Memory cadena(String texto) {
		return cadenas.computeIfAbsent(texto, t -> {
			Memory m = new Memory(Native.toByteArray(t).length);
			m.setString(0, t);
			return m;
		});
	}
}
