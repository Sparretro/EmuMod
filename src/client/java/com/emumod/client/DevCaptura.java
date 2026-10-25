package com.emumod.client;

import java.nio.file.Path;

import com.emumod.ConsolaItem;
import com.emumod.EmuMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

// Solo para desarrollo (-Demumod.dev.captura): abre la pantalla del televisor,
// guarda una captura en run/screenshots y cierra el juego. Permite revisar la UI sin intervención manual.
// Con -Demumod.dev.rom=vacio carga un cartucho vacío (sin código) para ver correr la BIOS en el televisor;
// con una ruta, la carga como si se hubiera elegido con el botón "Cargar juego".
// Con -Demumod.dev.velocidad=N arranca acelerado y registra la velocidad lograda.
// Con -Demumod.dev.estados guarda un estado en la ranura 5 y después lo carga.
// Con -Demumod.dev.pausa reinicia el juego y después lo pausa.
// Con -Demumod.dev.consola=gba|gba_sp abre la pantalla de esa consola en vez del televisor, y con
// -Demumod.dev.botones=N la dibuja con esos botones del GBA apretados (máscara de Nucleo.Boton).
// Con -Demumod.dev.mundo crea un mundo y saca capturas de las consolas en la mano (ver DevMundo).
// Con -Demumod.dev.solo=n3ds o gba_sp además, solo la 3DS o la SP (ver DevDoble).
final class DevCaptura {
	private static final int TICKS_ANTES_DE_CAPTURAR = 80;

	private static int ticks = -1;

	private DevCaptura() {
	}

	static void registrarSiCorresponde() {
		if (!Boolean.getBoolean("emumod.dev.captura")) {
			return;
		}
		PortatilDoble solo = switch (System.getProperty("emumod.dev.solo", "")) {
			case "n3ds" -> PortatilDoble.N3DS;
			case "gba_sp" -> PortatilDoble.GBA_SP;
			default -> null;
		};
		if (Boolean.getBoolean("emumod.dev.mundo") && solo != null) {
			ClientTickEvents.END_CLIENT_TICK.register(new DevDoble(solo)::tick);
			return;
		}
		if (Boolean.getBoolean("emumod.dev.mundo")) {
			ClientTickEvents.END_CLIENT_TICK.register(new DevMundo()::tick);
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(DevCaptura::tick);
	}

	static TelevisorScreen pantallaPedida() {
		return switch (System.getProperty("emumod.dev.consola", "")) {
			case "gba" -> new TelevisorScreen(ConsolaItem.Modelo.GBA);
			case "gba_sp" -> new TelevisorScreen(ConsolaItem.Modelo.GBA_SP);
			default -> new TelevisorScreen();
		};
	}

	private static void tick(Minecraft mc) {
		EmuladorCliente emulador = EmuladorCliente.get();
		if (ticks < 0) {
			// Menú principal o, en el primer arranque, la pantalla de accesibilidad
			if (mc.screen != null && mc.getOverlay() == null) {
				TelevisorScreen pantalla = pantallaPedida();
				pantalla.apretarParaCaptura(Integer.getInteger("emumod.dev.botones", 0));
				emulador.setVelocidad(Integer.getInteger("emumod.dev.velocidad", 1));
				String rom = System.getProperty("emumod.dev.rom", "");
				if (rom.equals("vacio")) {
					emulador.cargarJuego(new byte[0x200], "cartucho_vacio.gba", pantalla::mostrar);
				} else if (!rom.isEmpty()) {
					// Mismo camino que el botón "Cargar juego" después de elegir el archivo
					emulador.cargarRom(Path.of(rom), pantalla::mostrar);
				}
				mc.setScreen(pantalla);
				ticks = 0;
			}
			return;
		}
		if (Boolean.getBoolean("emumod.dev.estados") && mc.screen instanceof TelevisorScreen pantalla) {
			if (ticks == 30) {
				emulador.guardarEstado(5, m -> {
					pantalla.mostrar(m);
					EmuMod.LOGGER.info("Captura de desarrollo: {}", m.texto().getString());
				});
			} else if (ticks == 50) {
				emulador.cargarEstado(5, m -> {
					pantalla.mostrar(m);
					EmuMod.LOGGER.info("Captura de desarrollo: {}", m.texto().getString());
				});
			}
		}
		if (Boolean.getBoolean("emumod.dev.pausa") && mc.screen instanceof TelevisorScreen pantalla) {
			if (ticks == 30) {
				emulador.reiniciar(m -> {
					pantalla.mostrar(m);
					EmuMod.LOGGER.info("Captura de desarrollo: {}", m.texto().getString());
				});
			} else if (ticks == 60) {
				emulador.setPausadoPorJugador(true);
				EmuMod.LOGGER.info("Captura de desarrollo: pausado");
			}
		}
		if (++ticks == TICKS_ANTES_DE_CAPTURAR) {
			EmuMod.LOGGER.info("Captura de desarrollo: velocidad elegida x{}, lograda x{}",
					emulador.velocidad(), String.format("%.1f", emulador.velocidadReal()));
			Screenshot.grab(mc.gameDirectory, "emumod_dev.png", mc.getMainRenderTarget(),
					mensaje -> EmuMod.LOGGER.info("Captura de desarrollo: {}", mensaje.getString()));
			// Inicializar la clase carga sus nativos sin abrir ningún diálogo
			try {
				Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs", true, DevCaptura.class.getClassLoader());
				EmuMod.LOGGER.info("Captura de desarrollo: TinyFileDialogs disponible");
			} catch (Throwable e) {
				EmuMod.LOGGER.error("Captura de desarrollo: TinyFileDialogs no disponible", e);
			}
			mc.stop();
		}
	}
}
