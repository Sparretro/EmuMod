package com.emumod.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

import com.emumod.EmuMod;
import com.emumod.client.emu.Sistema;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/**
 * Lado de Minecraft del emulador de una consola (GBA, NES o N64): valida archivos, manda órdenes a su {@link HiloEmulador}
 * y vuelca su último cuadro en una textura dinámica. El juego solo avanza mientras se está jugando.
 */
public final class EmuladorCliente {
	public static final int[] VELOCIDADES = {1, 2, 4, 6, 8, 16};
	public static final int RANURAS = 5;

	private static final int TAMANIO_CABECERA = 0xC0;
	private static final int OFFSET_VALOR_FIJO = 0xB2;
	private static final int VALOR_FIJO = 0x96;
	private static final long TAMANIO_MAXIMO_ROM = 32L * 1024 * 1024;
	private static final long TAMANIO_BIOS = 16 * 1024;

	private static final Map<Sistema, EmuladorCliente> INSTANCIAS = new EnumMap<>(Sistema.class);
	// Cabecera de toda ROM de NES en formato iNES
	private static final byte[] CABECERA_NES = {0x4E, 0x45, 0x53, 0x1A};
	private static final long TAMANIO_MAXIMO_ROM_NES = 4L * 1024 * 1024;
	// Los cartuchos de N64 van de 4 a 64 MB; los tres órdenes de bytes de las ROMs (.z64, .v64, .n64) los entiende el núcleo
	private static final long TAMANIO_MAXIMO_ROM_N64 = 64L * 1024 * 1024;
	private static final int[] CABECERAS_N64 = {0x80371240, 0x37804012, 0x40123780};
	// Imágenes de disco de PS2 que entiende LRPS2; la BIOS es una imagen de 4 MB (las más nuevas, hasta 8)
	private static final java.util.Set<String> EXTENSIONES_PS2 = java.util.Set.of(".iso", ".bin", ".cue", ".chd", ".cso", ".zso", ".gz", ".m3u", ".elf");
	// PS3: imagen de disco, juego en carpeta (su PS3_DISC.SFB o PARAM.SFO), EBOOT.BIN, paquete .pkg o ejecutable
	private static final java.util.Set<String> EXTENSIONES_PS3 = java.util.Set.of(".iso", ".sfb", ".sfo", ".pkg", ".bin", ".self", ".elf");
	// Wii y GameCube: imágenes de disco en cualquiera de los formatos de Dolphin, canales .wad y ejecutables
	private static final java.util.Set<String> EXTENSIONES_WII = java.util.Set.of(".iso", ".gcm", ".wbfs", ".rvz", ".wia", ".gcz", ".ciso", ".wad", ".dol", ".elf", ".m3u");
	// 3DS: juegos desencriptados (Citra no abre los cifrados) y programas caseros
	private static final java.util.Set<String> EXTENSIONES_3DS = java.util.Set.of(".3ds", ".cci", ".cxi", ".cia", ".3dsx", ".app", ".elf", ".axf");
	// PSP: imágenes de UMD (.iso, comprimidas .cso o .chd), juegos de la tienda y caseros (.pbp) y ejecutables
	private static final java.util.Set<String> EXTENSIONES_PSP = java.util.Set.of(".iso", ".cso", ".chd", ".pbp", ".elf", ".prx");
	// El firmware de PS3 (PS3UPDAT.PUP) pesa unos 200 MB y empieza con "SCEUF"
	private static final long TAMANIO_MINIMO_FIRMWARE_PS3 = 50L * 1024 * 1024;
	private static final long TAMANIO_MINIMO_BIOS_PS2 = 4L * 1024 * 1024, TAMANIO_MAXIMO_BIOS_PS2 = 8L * 1024 * 1024;

	private final Sistema consola;
	private final ResourceLocation idTextura;

	private HiloEmulador hilo;
	private DynamicTexture textura;
	private final int[] cuadro;
	private long cuadroVisto;
	private int velocidad = 1;
	private boolean pausadoPorJugador;
	// Volumen propio del mod (0 a 1), compartido por las dos consolas; se guarda en opciones.properties
	private static double volumen = 1;
	// Filtro de la pantalla y campo de visión mientras se juega, uno por consola, también en opciones.properties
	private FiltroPantalla filtro;
	private int fovConsola = 70;
	private int punteroX, punteroY, botonesMouse;
	// Solo PS2: archivo de la BIOS elegida, en opciones.properties
	private String biosPs2;
	static final int FOV_MINIMO = 30, FOV_MAXIMO = 110;
	// Escala con la que se creó la textura (el filtro LCD la triplica)
	private int escalaTextura;

	private EmuladorCliente(Sistema consola) {
		this.consola = consola;
		idTextura = EmuMod.id("pantalla_" + consola.name().toLowerCase(java.util.Locale.ROOT));
		cuadro = new int[consola.ancho * consola.alto];
		filtro = FiltroPantalla.porDefecto(consola);
		// Con la NES y la N64 se mira un televisor a unos metros: más cerrado se ve más grande; las portátiles van a los ojos
		fovConsola = consola == Sistema.GBA || consola == Sistema.N3DS || consola == Sistema.PSP ? 70 : 50;
		cargarOpciones();
	}

	public record Mensaje(Component texto, boolean esError) {
		static Mensaje ok(String clave, Object... args) {
			return new Mensaje(Component.translatable(clave, args), false);
		}

		static Mensaje error(String clave, Object... args) {
			return new Mensaje(Component.translatable(clave, args), true);
		}
	}

	/** La Game Boy Advance (el televisor también la usa). */
	public static EmuladorCliente get() {
		return de(Sistema.GBA);
	}

	public static EmuladorCliente nes() {
		return de(Sistema.NES);
	}

	public static synchronized EmuladorCliente de(Sistema consola) {
		return INSTANCIAS.computeIfAbsent(consola, EmuladorCliente::new);
	}

	public Sistema consola() {
		return consola;
	}

	/** Todas las consolas que ya se usaron, para guardar al cerrar Minecraft. */
	public static synchronized java.util.List<EmuladorCliente> todas() {
		return java.util.List.copyOf(INSTANCIAS.values());
	}

	public static Path carpeta() {
		return FabricLoader.getInstance().getConfigDir().resolve(EmuMod.MOD_ID);
	}

	/**
	 * El mod se llamaba «Televisor» y guardaba todo en config/televisor: la primera vez se mueve esa carpeta (BIOS, firmware,
	 * partidas, estados y opciones) a la nueva, así no se pierde nada. Las DLL extraídas se vuelven a sacar del jar.
	 */
	public static void migrarCarpetaVieja() {
		Path vieja = FabricLoader.getInstance().getConfigDir().resolve("televisor");
		Path nueva = carpeta();
		if (!Files.isDirectory(vieja) || Files.exists(nueva)) {
			return;
		}
		try {
			Files.move(vieja, nueva);
			EmuMod.LOGGER.info("Se movió la carpeta del mod Televisor a {}", nueva);
		} catch (IOException e) {
			EmuMod.LOGGER.error("No se pudo mover {} a {}", vieja, nueva, e);
		}
	}

	/** Carpeta de sistema libretro: la BIOS va en {@code sistema/SkyEmu/gba_bios.bin}. */
	public static Path carpetaSistema() {
		return carpeta().resolve("sistema");
	}

	public static Path rutaBios() {
		return carpetaSistema().resolve("SkyEmu").resolve("gba_bios.bin");
	}

	public static boolean hayBios() {
		return Files.isRegularFile(rutaBios());
	}

	private HiloEmulador hilo() {
		if (hilo == null) {
			// La GBA guarda donde siempre; la NES y la N64 en subcarpetas, para que no se mezclen juegos con el mismo nombre
			Path partidas = carpeta().resolve("partidas"), estados = carpeta().resolve("estados");
			if (consola != Sistema.GBA) {
				String sub = consola.name().toLowerCase(java.util.Locale.ROOT);
				partidas = partidas.resolve(sub);
				estados = estados.resolve(sub);
			}
			hilo = new HiloEmulador(consola, carpeta().resolve("nativos"), carpetaSistema(), partidas, estados);
			hilo.setVelocidad(velocidad);
		}
		return hilo;
	}

	/**
	 * Lee y valida una ROM (de GBA, NES o N64 según la consola) desde el disco y la carga. El mensaje para el jugador llega por
	 * {@code alTerminar} en el hilo de render.
	 */
	public void cargarRom(Path archivo, Consumer<Mensaje> alTerminar) {
		String nombre = archivo.getFileName().toString();
		if (consola.leeDelDisco()) {
			cargarDisco(archivo, alTerminar);
			return;
		}
		try {
			long tamanio = Files.size(archivo);
			long maximo = switch (consola) {
				case NES -> TAMANIO_MAXIMO_ROM_NES;
				case N64 -> TAMANIO_MAXIMO_ROM_N64;
				default -> TAMANIO_MAXIMO_ROM;
			};
			String sufijo = consola == Sistema.N64 ? "_n64" : "";
			if (tamanio < TAMANIO_CABECERA || tamanio > maximo) {
				alTerminar.accept(Mensaje.error("screen.emumod.rom_tamanio_invalido" + sufijo, nombre));
				return;
			}
			byte[] rom = Files.readAllBytes(archivo);
			if (!romValida(rom)) {
				alTerminar.accept(Mensaje.error("screen.emumod.rom_invalida" + sufijo, nombre));
				return;
			}
			cargarJuego(rom, nombre, alTerminar);
		} catch (IOException e) {
			EmuMod.LOGGER.error("No se pudo leer {}", archivo, e);
			alTerminar.accept(Mensaje.error("screen.emumod.error_lectura", nombre));
		}
	}

	private boolean romValida(byte[] rom) {
		if (consola == Sistema.NES) {
			return java.util.Arrays.equals(rom, 0, 4, CABECERA_NES, 0, 4);
		}
		if (consola == Sistema.N64) {
			int cabecera = java.nio.ByteBuffer.wrap(rom, 0, 4).getInt();
			return java.util.Arrays.stream(CABECERAS_N64).anyMatch(c -> c == cabecera);
		}
		// Byte fijo de la cabecera de todo cartucho de GBA
		return (rom[OFFSET_VALOR_FIJO] & 0xFF) == VALOR_FIJO;
	}

	public void cargarJuego(byte[] rom, String nombre, Consumer<Mensaje> alTerminar) {
		pausadoPorJugador = false;
		hilo().cargar(rom, nombre, enRender(alTerminar));
	}

	/** Reinicia el juego como si se apagara y prendiera la consola. La partida del cartucho se conserva. */
	public void reiniciar(Consumer<Mensaje> alTerminar) {
		pausadoPorJugador = false;
		hilo().reiniciar(enRender(alTerminar));
	}

	public boolean pausadoPorJugador() {
		return pausadoPorJugador;
	}

	public void setPausadoPorJugador(boolean pausado) {
		pausadoPorJugador = pausado;
		if (hilo != null) {
			hilo.setPausado(pausado);
		}
	}

	public double volumen() {
		return volumen;
	}

	public void setVolumen(double volumen) {
		EmuladorCliente.volumen = Math.max(0, Math.min(1, volumen));
	}

	public FiltroPantalla filtro() {
		return filtro;
	}

	public void setFiltro(FiltroPantalla filtro) {
		this.filtro = filtro;
		// Que se vea el cambio aunque el juego esté en pausa
		if (hayJuego()) {
			subirCuadro();
		}
	}

	public int fovConsola() {
		return fovConsola;
	}

	public void setFovConsola(int fov) {
		fovConsola = Math.max(FOV_MINIMO, Math.min(FOV_MAXIMO, fov));
	}

	/** Valida la BIOS elegida y la copia a la carpeta de sistema. Se usa al cargar el próximo juego. */
	// PS2: el núcleo lee la imagen del disco él mismo (pesan gigas); acá solo se revisa que exista y que tenga BIOS
	private void cargarDisco(Path archivo, Consumer<Mensaje> alTerminar) {
		String nombre = archivo.getFileName().toString();
		String extension = nombre.contains(".") ? nombre.substring(nombre.lastIndexOf('.')).toLowerCase(java.util.Locale.ROOT) : "";
		boolean ps3 = consola == Sistema.PS3, wii = consola == Sistema.WII, n3ds = consola == Sistema.N3DS, psp = consola == Sistema.PSP;
		if (!(psp ? EXTENSIONES_PSP : n3ds ? EXTENSIONES_3DS : wii ? EXTENSIONES_WII : ps3 ? EXTENSIONES_PS3 : EXTENSIONES_PS2).contains(extension)) {
			alTerminar.accept(Mensaje.error("screen.emumod.rom_invalida_" + consola.name().toLowerCase(java.util.Locale.ROOT), nombre));
			return;
		}
		if (!Files.isRegularFile(archivo)) {
			alTerminar.accept(Mensaje.error("screen.emumod.error_lectura", nombre));
			return;
		}
		// La Wii, la 3DS y la PSP no necesitan BIOS ni firmware
		if (wii || n3ds || psp) {
			pausadoPorJugador = false;
			hilo().cargarArchivo(archivo, nombre, java.util.Map.of(), enRender(alTerminar));
			return;
		}
		if (ps3) {
			if (!hayFirmwarePs3()) {
				alTerminar.accept(Mensaje.error("screen.emumod.falta_firmware_ps3"));
				return;
			}
			pausadoPorJugador = false;
			hilo().cargarArchivo(archivo, nombre, java.util.Map.of(), enRender(alTerminar));
			return;
		}
		if (biosPs2 == null) {
			alTerminar.accept(Mensaje.error("screen.emumod.falta_bios_ps2"));
			return;
		}
		pausadoPorJugador = false;
		hilo().cargarArchivo(archivo, nombre, opcionesPs2(), enRender(alTerminar));
	}

	/** PS2 sin disco: arranca el menú de la BIOS (para ver la tarjeta de memoria o la configuración). */
	public void encenderSinJuego(Consumer<Mensaje> alTerminar) {
		if (biosPs2 == null) {
			alTerminar.accept(Mensaje.error("screen.emumod.falta_bios_ps2"));
			return;
		}
		pausadoPorJugador = false;
		hilo().cargarArchivo(null, "BIOS de PS2", opcionesPs2(), enRender(alTerminar));
	}

	/**
	 * La BIOS elegida y los hilos extra del renderizador por software: el que más pesa en la PS2. Medido con God of War en
	 * un Ryzen 7 8700G (16 hilos): con 2 extra va a x1,06, con 6 a x1,75 y con 8 casi igual (x1,8). Se usa la mitad de los
	 * hilos de la PC menos 2 (para Minecraft y el EE), entre 2 y 8.
	 */
	private java.util.Map<String, String> opcionesPs2() {
		int hilos = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors() / 2 - 2));
		return java.util.Map.of("pcsx2_bios", biosPs2, "pcsx2_sw_renderer_threads", Integer.toString(hilos));
	}

	/** Nombre del archivo de la BIOS de PS2 elegida (en {@code sistema/pcsx2/bios}), o null si todavía no se eligió. */
	public String biosPs2() {
		return biosPs2;
	}

	// La BIOS de PS2 se copia con su nombre: el núcleo reconoce la región y la versión por el contenido
	private Mensaje instalarBiosPs2(Path archivo) {
		String nombre = archivo.getFileName().toString();
		try {
			long tamanio = Files.size(archivo);
			if (tamanio < TAMANIO_MINIMO_BIOS_PS2 || tamanio > TAMANIO_MAXIMO_BIOS_PS2) {
				return Mensaje.error("screen.emumod.bios_invalida_ps2", nombre);
			}
			Path destino = carpetaSistema().resolve("pcsx2").resolve("bios").resolve(nombre);
			Files.createDirectories(destino.getParent());
			Files.copy(archivo, destino, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			EmuMod.LOGGER.error("No se pudo copiar la BIOS {}", archivo, e);
			return Mensaje.error("screen.emumod.error_lectura", nombre);
		}
		biosPs2 = nombre;
		guardarOpciones();
		return Mensaje.ok("screen.emumod.bios_ps2_instalada", nombre);
	}

	/** El firmware de PS3 está instalado o listo para instalarse (RPCS3 lo instala al iniciarse, en sistema/rpcs3). */
	public static boolean hayFirmwarePs3() {
		Path rpcs3 = carpetaSistema().resolve("rpcs3");
		return Files.isRegularFile(rpcs3.resolve("dev_flash/vsh/module/vsh.self")) || Files.isRegularFile(rpcs3.resolve("PS3UPDAT.PUP"));
	}

	// Se copia a sistema/rpcs3/PS3UPDAT.PUP; el núcleo lo instala al cargar el próximo juego (la primera vez tarda)
	private Mensaje instalarFirmwarePs3(Path archivo) {
		String nombre = archivo.getFileName().toString();
		try {
			byte[] cabecera = new byte[5];
			try (var entrada = Files.newInputStream(archivo)) {
				if (entrada.readNBytes(cabecera, 0, 5) != 5) {
					return Mensaje.error("screen.emumod.firmware_invalido_ps3", nombre);
				}
			}
			if (Files.size(archivo) < TAMANIO_MINIMO_FIRMWARE_PS3 || !"SCEUF".equals(new String(cabecera, java.nio.charset.StandardCharsets.US_ASCII))) {
				return Mensaje.error("screen.emumod.firmware_invalido_ps3", nombre);
			}
			Path destino = carpetaSistema().resolve("rpcs3").resolve("PS3UPDAT.PUP");
			Files.createDirectories(destino.getParent());
			Files.copy(archivo, destino, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			EmuMod.LOGGER.error("No se pudo copiar el firmware {}", archivo, e);
			return Mensaje.error("screen.emumod.error_lectura", nombre);
		}
		return Mensaje.ok("screen.emumod.firmware_ps3_listo");
	}

	public Mensaje instalarBios(Path archivo) {
		if (consola == Sistema.PS2) {
			return instalarBiosPs2(archivo);
		}
		if (consola == Sistema.PS3) {
			return instalarFirmwarePs3(archivo);
		}
		try {
			if (Files.size(archivo) != TAMANIO_BIOS) {
				return Mensaje.error("screen.emumod.bios_invalida", archivo.getFileName().toString());
			}
			Files.createDirectories(rutaBios().getParent());
			Files.copy(archivo, rutaBios(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			EmuMod.LOGGER.error("No se pudo copiar la BIOS {}", archivo, e);
			return Mensaje.error("screen.emumod.error_lectura", archivo.getFileName().toString());
		}
		return Mensaje.ok(hayJuego() ? "screen.emumod.bios_proximo_juego" : "screen.emumod.bios_instalada");
	}

	public void guardarEstado(int ranura, Consumer<Mensaje> alTerminar) {
		hilo().guardarEstado(ranura, enRender(alTerminar));
	}

	public void cargarEstado(int ranura, Consumer<Mensaje> alTerminar) {
		hilo().cargarEstado(ranura, enRender(alTerminar));
	}

	public boolean hayJuego() {
		return hilo != null && hilo.hayJuego();
	}

	public String error() {
		return hilo == null ? null : hilo.error();
	}

	public int velocidad() {
		return velocidad;
	}

	public void setVelocidad(int velocidad) {
		this.velocidad = velocidad;
		if (hilo != null) {
			hilo.setVelocidad(velocidad);
		}
	}

	/** Velocidad lograda (1 = tiempo real); puede ser menor que la elegida si la PC no da abasto. */
	public double velocidadReal() {
		return hilo == null ? 0 : hilo.velocidadReal();
	}

	/** El juego queda congelado hasta la próxima llamada a {@link #actualizar}. */
	public void pausar() {
		if (hilo != null) {
			hilo.setPausado(true);
		}
	}

	/**
	 * Llamar una vez por cuadro de render con la pantalla abierta: pasa los botones y las palancas analógicas
	 * (izquierda y derecha, x e y de -32767 a 32767; las usan la N64 y la PS2) y sube el último cuadro.
	 */
	/** Puntero de la Wii con el mouse: posición en la pantalla de -32767 a 32767 y botones (1 izquierdo, 2 derecho, 4 medio). */
	public void setPuntero(int x, int y, int botones) {
		punteroX = x;
		punteroY = y;
		botonesMouse = botones;
	}

	public void actualizar(int botones, int[] izquierda, int[] derecha) {
		if (!hayJuego()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		// Volumen general de Minecraft por la barra de volumen del televisor
		hilo.setVolumen((float) (mc.options.getSoundSourceVolume(SoundSource.MASTER) * volumen));
		hilo.setBotones(botones);
		hilo.setPalancas(izquierda, derecha);
		hilo.setPuntero(punteroX, punteroY, botonesMouse);
		hilo.setPausado(pausadoPorJugador);

		long nuevo = hilo.tomarCuadro(cuadro, cuadroVisto);
		if (nuevo != cuadroVisto) {
			cuadroVisto = nuevo;
			subirCuadro();
		}
	}

	/** Al cerrar Minecraft: guarda la partida del cartucho y termina el hilo. */
	public void cerrar() {
		guardarOpciones();
		if (hilo != null) {
			hilo.cerrar();
		}
	}

	public ResourceLocation textura() {
		if (textura == null || escalaTextura != filtro.escala) {
			escalaTextura = filtro.escala;
			textura = new DynamicTexture(consola.ancho * escalaTextura, consola.alto * escalaTextura, false);
			// Con la grilla LCD la textura se ve achicada: el filtrado lineal la promedia en vez de dejar franjas
			textura.setFilter(escalaTextura > 1, false);
			// register cierra la textura anterior con el mismo nombre
			Minecraft.getInstance().getTextureManager().register(idTextura, textura);
		}
		return idTextura;
	}

	private void subirCuadro() {
		textura();
		filtro.llenar(cuadro, consola.ancho, consola.alto, textura.getPixels());
		textura.upload();
	}

	private static Path archivoOpciones() {
		return carpeta().resolve("opciones.properties");
	}

	private void cargarOpciones() {
		Path archivo = archivoOpciones();
		if (!Files.isRegularFile(archivo)) {
			return;
		}
		Properties opciones = new Properties();
		try (var entrada = Files.newBufferedReader(archivo)) {
			opciones.load(entrada);
			setVolumen(Double.parseDouble(opciones.getProperty("volumen", "1")));
			setFovConsola(Integer.parseInt(opciones.getProperty(clave("fov_consola"), Integer.toString(fovConsola))));
			Controles.cargar(consola, opciones);
			if (consola == Sistema.PS2) {
				String bios = opciones.getProperty("ps2_bios");
				biosPs2 = bios != null && Files.isRegularFile(carpetaSistema().resolve("pcsx2").resolve("bios").resolve(bios)) ? bios : null;
			}
			try {
				FiltroPantalla leido = FiltroPantalla.valueOf(opciones.getProperty(clave("filtro"), filtro.name()));
				if (leido.sirvePara(consola)) {
					filtro = leido;
				}
			} catch (IllegalArgumentException e) {
				filtro = FiltroPantalla.porDefecto(consola);
			}
		} catch (IOException | NumberFormatException e) {
			EmuMod.LOGGER.warn("No se pudieron leer las opciones del televisor", e);
		}
	}

	// Las opciones de la GBA conservan sus nombres de antes; las de las otras consolas llevan su nombre adelante
	private String clave(String nombre) {
		return consola == Sistema.GBA ? nombre : consola.name().toLowerCase(java.util.Locale.ROOT) + "_" + nombre;
	}

	public synchronized void guardarOpciones() {
		// Se parte del archivo actual para no pisar las opciones de la otra consola
		Properties opciones = new Properties();
		try {
			if (Files.isRegularFile(archivoOpciones())) {
				try (var entrada = Files.newBufferedReader(archivoOpciones())) {
					opciones.load(entrada);
				}
			}
			opciones.setProperty("volumen", Double.toString(volumen));
			opciones.setProperty(clave("fov_consola"), Integer.toString(fovConsola));
			opciones.setProperty(clave("filtro"), filtro.name());
			Controles.guardar(consola, opciones);
			if (biosPs2 != null) {
				opciones.setProperty("ps2_bios", biosPs2);
			}
			Files.createDirectories(carpeta());
			try (var salida = Files.newBufferedWriter(archivoOpciones())) {
				opciones.store(salida, "Televisor");
			}
		} catch (IOException e) {
			EmuMod.LOGGER.warn("No se pudieron guardar las opciones del televisor", e);
		}
	}

	private static Consumer<Mensaje> enRender(Consumer<Mensaje> alTerminar) {
		return mensaje -> Minecraft.getInstance().execute(() -> alTerminar.accept(mensaje));
	}

	// El núcleo entrega 0x00RRGGBB; NativeImage guarda 0xAABBGGRR
	static int aAbgr(int xrgb) {
		return 0xFF000000 | (xrgb & 0x0000FF00) | ((xrgb & 0xFF) << 16) | ((xrgb >> 16) & 0xFF);
	}
}
