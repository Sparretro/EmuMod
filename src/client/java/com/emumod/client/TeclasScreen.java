package com.emumod.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.emumod.client.emu.Sistema;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Keybinds: las teclas de cada acción del control de la consola. Se toca una acción y se aprieta la tecla nueva (Esc
 * cancela, Supr la deja sin tecla). Se guardan con las opciones de la consola al volver.
 */
public class TeclasScreen extends Screen {
	private static final int ANCHO_BOTON = 150, ALTO_BOTON = 20, SEPARACION = 4, POR_PAGINA = 16;

	private final Screen anterior;
	private final EmuladorCliente emulador;
	private final Sistema consola;
	private final List<Button> botones = new ArrayList<>();
	// Acción que espera su tecla nueva, o -1
	private int esperando = -1;
	private int pagina;

	TeclasScreen(Screen anterior, EmuladorCliente emulador) {
		super(Component.literal("Keybinds"));
		this.anterior = anterior;
		this.emulador = emulador;
		consola = emulador.consola();
	}

	@Override
	protected void init() {
		botones.clear();
		List<Controles.Accion> acciones = Controles.acciones(consola);
		int paginas = (acciones.size() + POR_PAGINA - 1) / POR_PAGINA;
		pagina = Math.min(pagina, paginas - 1);
		int desde = pagina * POR_PAGINA, hasta = Math.min(acciones.size(), desde + POR_PAGINA);
		// Dos columnas de hasta 8 filas
		int columnas = 2, filas = (POR_PAGINA + 1) / 2;
		int ancho = columnas * ANCHO_BOTON + SEPARACION;
		int x0 = (width - ancho) / 2, y0 = 34;
		for (int i = desde; i < hasta; i++) {
			int indice = i, j = i - desde;
			int x = x0 + (j / filas) * (ANCHO_BOTON + SEPARACION), y = y0 + (j % filas) * (ALTO_BOTON + SEPARACION);
			Button b = addRenderableWidget(Button.builder(texto(indice), boton -> {
				esperando = indice;
				actualizarTextos();
			}).bounds(x, y, ANCHO_BOTON, ALTO_BOTON).build());
			botones.add(b);
		}
		int abajo = height - ALTO_BOTON - 8;
		int anchoAbajo = 100;
		int x = (width - (paginas > 1 ? 4 : 2) * (anchoAbajo + SEPARACION)) / 2;
		if (paginas > 1) {
			addRenderableWidget(Button.builder(Component.literal("<"), b -> cambiarPagina(-1)).bounds(x, abajo, anchoAbajo, ALTO_BOTON).build());
			x += anchoAbajo + SEPARACION;
		}
		addRenderableWidget(Button.builder(Component.literal("Restablecer"), b -> {
			Controles.restablecer(consola);
			esperando = -1;
			actualizarTextos();
		}).bounds(x, abajo, anchoAbajo, ALTO_BOTON).build());
		x += anchoAbajo + SEPARACION;
		addRenderableWidget(Button.builder(Component.literal("Listo"), b -> onClose()).bounds(x, abajo, anchoAbajo, ALTO_BOTON).build());
		x += anchoAbajo + SEPARACION;
		if (paginas > 1) {
			addRenderableWidget(Button.builder(Component.literal(">"), b -> cambiarPagina(1)).bounds(x, abajo, anchoAbajo, ALTO_BOTON).build());
		}
	}

	private void cambiarPagina(int paso) {
		int paginas = (Controles.acciones(consola).size() + POR_PAGINA - 1) / POR_PAGINA;
		pagina = (pagina + paso + paginas) % paginas;
		esperando = -1;
		rebuildWidgets();
	}

	private Component texto(int indice) {
		Controles.Accion accion = Controles.acciones(consola).get(indice);
		if (indice == esperando) {
			return Component.literal("> " + accion.nombre() + ": ... <");
		}
		int tecla = Controles.teclas(consola)[indice];
		String nombre = tecla == GLFW.GLFW_KEY_UNKNOWN ? "-" : InputConstants.Type.KEYSYM.getOrCreate(tecla).getDisplayName().getString();
		return Component.literal(accion.nombre() + ": " + nombre);
	}

	private void actualizarTextos() {
		int desde = pagina * POR_PAGINA;
		for (int i = 0; i < botones.size(); i++) {
			botones.get(i).setMessage(texto(desde + i));
		}
	}

	@Override
	public boolean keyPressed(int tecla, int scanCode, int modificadores) {
		if (esperando >= 0) {
			if (tecla != GLFW.GLFW_KEY_ESCAPE) {
				Controles.teclas(consola)[esperando] = tecla == GLFW.GLFW_KEY_DELETE ? GLFW.GLFW_KEY_UNKNOWN : tecla;
			}
			esperando = -1;
			actualizarTextos();
			return true;
		}
		return super.keyPressed(tecla, scanCode, modificadores);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics);
		graphics.drawCenteredString(font, Component.literal("Keybinds - " + consola.name()), width / 2, 10, 0xFFFFFF);
		graphics.drawCenteredString(font, Component.literal(esperando >= 0 ? "Pulsa la tecla nueva (Esc cancela, Supr la deja sin tecla)"
				: "Toca una acción para cambiar su tecla"), width / 2, 22, 0xA0A0A0);
		if (consola == Sistema.WII) {
			graphics.drawCenteredString(font, Component.literal("Mouse: apuntar con el Wiimote, click izquierdo A, derecho B, rueda sacudir"),
					width / 2, height - ALTO_BOTON - 20, 0xA0A0A0);
		}
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void onClose() {
		emulador.guardarOpciones();
		minecraft.setScreen(anterior);
	}
}
