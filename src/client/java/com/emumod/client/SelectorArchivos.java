package com.emumod.client;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import com.emumod.EmuMod;
import net.minecraft.client.Minecraft;

/**
 * Diálogo nativo para elegir un archivo (TinyFileDialogs, incluido en Minecraft).
 * Se abre en otro hilo para no congelar el juego; el resultado se entrega en el hilo de render.
 */
public final class SelectorArchivos {
	private static final AtomicBoolean ABIERTO = new AtomicBoolean();
	private static volatile String ultimaCarpeta = System.getProperty("user.home") + "/";

	private SelectorArchivos() {
	}

	public static boolean abierto() {
		return ABIERTO.get();
	}

	public static void abrir(String titulo, String[] patrones, String descripcion, Consumer<Path> alElegir) {
		if (!ABIERTO.compareAndSet(false, true)) {
			return;
		}
		Thread hilo = new Thread(() -> {
			String ruta = null;
			try (MemoryStack stack = MemoryStack.stackPush()) {
				PointerBuffer filtros = stack.mallocPointer(patrones.length);
				for (String patron : patrones) {
					filtros.put(stack.UTF8(patron));
				}
				filtros.flip();
				ruta = TinyFileDialogs.tinyfd_openFileDialog(titulo, ultimaCarpeta, filtros, descripcion, false);
			} catch (Throwable e) {
				EmuMod.LOGGER.error("No se pudo abrir el selector de archivos", e);
			} finally {
				ABIERTO.set(false);
			}
			if (ruta != null) {
				Path elegido = Path.of(ruta);
				if (elegido.getParent() != null) {
					ultimaCarpeta = elegido.getParent() + "/";
				}
				Minecraft.getInstance().execute(() -> alElegir.accept(elegido));
			}
		}, "Televisor selector de archivos");
		hilo.setDaemon(true);
		hilo.start();
	}
}
