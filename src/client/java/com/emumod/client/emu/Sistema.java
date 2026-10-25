package com.emumod.client.emu;

import java.util.Map;
import static java.util.Map.entry;

/** Consolas que emula el mod: cada una es un núcleo libretro distinto, en su propia DLL. */
public enum Sistema {
	// SkyEmu (MIT): opciones de su libretro.c
	GBA("skyemu_libretro", 240, 160, ".gba", Map.of(
			"system_core_override", "Game Boy Advance",
			"system_gb_bios_enable", "ON",
			"system_gba_bios_enable", "ON",
			"system_nds_bios_enable", "ON")),
	// QuickNES (adaptador libretro GPL-2, emulador LGPL-2.1): se recorta el borde de arriba y abajo como un televisor,
	// pero no los costados, así sale siempre 256x224
	NES("quicknes_libretro", 256, 224, ".nes", Map.of(
			"quicknes_use_overscan_h", "enabled",
			"quicknes_use_overscan_v", "disabled")),
	// Mupen64Plus-Next (GPL-2): Angrylion dibuja por software (sin OpenGL ni Vulkan, que Minecraft no le puede prestar) a
	// 640x480 y se promedia a 320x240, la resolución de casi todos los juegos. El RSP va con ParaLLEl (recompilador),
	// que Angrylion necesita porque no usa el HLE. Botones sin modos raros: cada botón del N64 es uno del RetroPad (alt-map)
	N64("mupen64plus_next_libretro", 320, 240, ".z64", Map.ofEntries(
			entry("mupen64plus-rdp-plugin", "angrylion"),
			entry("mupen64plus-rsp-plugin", "parallel"),
			entry("mupen64plus-cpucore", "dynamic_recompiler"),
			entry("mupen64plus-angrylion-vioverlay", "Filtered"),
			entry("mupen64plus-angrylion-sync", "Low"),
			entry("mupen64plus-angrylion-multithread", "all threads"),
			entry("mupen64plus-angrylion-overscan", "disabled"),
			entry("mupen64plus-aspect", "4:3"),
			entry("mupen64plus-43screensize", "640x480"),
			entry("mupen64plus-FrameDuping", "True"),
			entry("mupen64plus-Framerate", "Original"),
			entry("mupen64plus-virefresh", "Auto"),
			entry("mupen64plus-astick-deadzone", "15"),
			entry("mupen64plus-astick-sensitivity", "100"),
			entry("mupen64plus-alt-map", "True"),
			entry("mupen64plus-pak1", "memory"),
			entry("mupen64plus-pak2", "none"),
			entry("mupen64plus-pak3", "none"),
			entry("mupen64plus-pak4", "none"))),
	// LRPS2 (PCSX2 de libretro, GPL-3): el renderizador «Software (SW)» dibuja todo con la CPU, sin contexto de video del
	// frontend. Lee el juego él mismo desde el disco. La BIOS la elige el jugador (Nucleo#setOpcion "pcsx2_bios").
	// 640x448 es la resolución de casi todos los juegos NTSC; los PAL (512 líneas) se promedian
	PS2("pcsx2_libretro", 640, 448, ".iso", Map.ofEntries(
			entry("pcsx2_renderer", "Software (SW)"),
			entry("pcsx2_mtvu", "enabled"),
			entry("pcsx2_fastboot", "enabled"),
			entry("pcsx2_fastcdvd", "disabled"),
			entry("pcsx2_shared_memory_cards", "enabled"),
			entry("pcsx2_upscale_multiplier", "1x (Native)"),
			entry("pcsx2_deinterlace_mode", "Automatic"))),
	// RPCS3 para libretro (GPL-2): sin contexto de video del frontend dibuja con Vulkan en un dispositivo propio (la placa
	// de video de la PC) y devuelve cada cuadro por memoria. Instala el firmware (sistema/rpcs3/PS3UPDAT.PUP) al iniciarse
	// y lee los juegos del disco. La PS3 dibuja casi siempre a 1280x720
	// Shaders sincrónicos: con la compilación en paralelo (async, la de fábrica) el driver Vulkan de AMD se cierra solo la
	// primera vez que compila los shaders de un juego (amdvlk64.dll, 0xC0000409), también sin Minecraft. Sincrónico puede
	// trabar un instante cuando aparece un efecto nuevo, que después queda en caché
	PS3("rpcs3_libretro", 1280, 720, ".iso", Map.ofEntries(
			entry("rpcs3_renderer", "vulkan"),
			entry("rpcs3_shader_mode", "sync"))),
	// Dolphin para libretro (GPL-2), Wii y GameCube: solo dibuja con la placa de video, así que el puente le da un contexto
	// OpenGL propio y lee cada cuadro (ver native/puente/contexto_gl.c). Lee los juegos del disco (.iso, .wbfs, .rvz...)
	WII("dolphin_libretro", 640, 528, ".iso", Map.ofEntries(
			entry("dolphin_renderer", "Hardware"),
			// Wiimote de costado (como New Super Mario Bros.); el puntero va con el mouse (izquierdo A, derecho B, rueda sacudir)
			entry("dolphin_wiimote_default_device", "sideways"),
			entry("dolphin_ir_mode", "2"),
			entry("dolphin_default_mouse_bindings_enabled", "enabled"))),
	// Citra para libretro (GPL-2), Nintendo 3DS: dibuja con OpenGL (el contexto propio del puente, como Dolphin) y lee el
	// juego del disco. Las dos pantallas salen una arriba de la otra en un cuadro de 400x480: la de arriba (400x240) y la
	// táctil (320x240, centrada). La táctil se toca con el mouse (puntero de libretro + botón izquierdo); la palanca
	// derecha es el C-Stick
	N3DS("citra_libretro", 400, 480, ".3ds", Map.ofEntries(
			entry("citra_use_cpu_jit", "enabled"),
			entry("citra_use_shader_jit", "enabled"),
			entry("citra_use_hw_shaders", "enabled"),
			entry("citra_use_hw_shader_cache", "enabled"),
			entry("citra_resolution_factor", "1x (Native)"),
			entry("citra_layout_option", "Default Top-Bottom Screen"),
			entry("citra_swap_screen", "Top"),
			entry("citra_analog_function", "C-Stick"),
			entry("citra_mouse_touchscreen", "enabled"),
			entry("citra_touch_touchscreen", "disabled"),
			entry("citra_render_touchscreen", "disabled"),
			entry("citra_is_new_3ds", "Old 3DS"),
			entry("citra_region_value", "Auto"),
			entry("citra_language", "Spanish"),
			entry("citra_use_virtual_sd", "enabled"),
			entry("citra_use_libretro_save_path", "LibRetro Default")));

	/** Nombre de la DLL dentro de {@code natives/<plataforma>/}, sin extensión. */
	public final String biblioteca;
	public final int ancho;
	public final int alto;
	/** Extensión de las ROMs, con punto. */
	public final String extension;
	final Map<String, String> opciones;

	Sistema(String biblioteca, int ancho, int alto, String extension, Map<String, String> opciones) {
		this.biblioteca = biblioteca;
		this.ancho = ancho;
		this.alto = alto;
		this.extension = extension;
		this.opciones = opciones;
	}

	/**
	 * El núcleo llama a los callbacks desde una corrutina (N64) o desde sus propios hilos (PS2, PS3): se carga a través de
	 * native/puente.
	 */
	boolean usaPuente() {
		return this == N64 || this == PS2 || this == PS3 || this == WII || this == N3DS;
	}

	/** El núcleo lee el juego del disco (retro_game_info sin datos): las imágenes de PS2 y PS3 pesan gigas. */
	public boolean leeDelDisco() {
		return this == PS2 || this == PS3 || this == WII || this == N3DS;
	}

	/** El núcleo dibuja con OpenGL: el puente le crea un contexto propio. */
	boolean usaGl() {
		return this == WII || this == N3DS;
	}

	/** Mupen64Plus-Next sigue corriendo el juego anterior en su corrutina hasta retro_deinit: para cambiar de juego se reinicia. */
	boolean reiniciarAlDescargar() {
		return this == N64;
	}
}
