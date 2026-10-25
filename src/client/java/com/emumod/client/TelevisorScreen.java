package com.emumod.client;

import java.util.function.IntPredicate;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import com.mojang.blaze3d.platform.InputConstants;
import com.emumod.ConsolaItem;
import com.emumod.client.emu.Sistema;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TelevisorScreen extends Screen {
	public static final int ANCHO_GBA = 240;
	public static final int ALTO_GBA = 160;

	// Espacio reservado en coordenadas de GUI: botones y título arriba; mensajes y botones abajo
	private static final int MARGEN_SUPERIOR = 28;
	private static final int MARGEN_INFERIOR = 36;
	private static final int MARCO = 6;
	private static final int ALTO_BOTON = 20;
	private static final int SEPARACION = 4;

	private static final int COLOR_MARCO = 0xFF2B2D31;
	private static final int COLOR_MARCO_BORDE = 0xFF111214;
	private static final int COLOR_PANTALLA = 0xFF0B1214;
	private static final int COLOR_OK = 0x7FD08A;
	private static final int COLOR_ERROR = 0xE06C5F;
	private static final int COLOR_TENUE = 0x8FA3A6;
	private static final int COLOR_VELO_PAUSA = 0x99000000;
	private static final long DURACION_MENSAJE_MS = 5000;

	private final EmuladorCliente emulador;
	// Se juega con la consola en las manos y el mundo de fondo: la GBA delante de los ojos o el control de la NES
	private final boolean enManos;
	// La consola de televisor que se está jugando (NES o N64), o null con la GBA o el televisor de la GBA
	private final ConsolaTele tele;
	// La 3DS o la SP en las manos (como la GBA, pero con su modelo de tapa; la 3DS con pantalla táctil, ver ManosDoble), o null
	private final PortatilDoble doble;
	// Toque en la pantalla táctil de la 3DS con el mouse: posición del puntero en el cuadro de Citra y si se está tocando
	private int tactilX, tactilY;
	private boolean tocando;
	// Con la GBA en las manos: menú visible (Tab), se recuerda al volver a abrir
	private static boolean menuAbierto;

	// Área de juego en píxeles reales de la ventana
	private int pantallaX, pantallaY, escala, anchoPantallaPx, altoPantallaPx;
	// Botones de la consola apretados con el mouse
	private int mascaraMouse;
	// Palanca (N64 o PS2) empujada con el mouse: índice, x e y; o null
	private int[] palancaMouse;
	// Botones del mouse que se le pasan al Wiimote (1 izquierdo, 2 derecho, 4 medio)
	private int botonesMouseWii;
	private boolean apretandoConMouse;

	private Button botonRom;
	private Button botonPausa;
	private Button botonReiniciar;
	private Button botonBios;
	private BarraVolumen barraVolumen;
	private Button botonTeclas;
	// Se va a Keybinds y se vuelve: no termina la partida
	private boolean abriendoTeclas;
	// El título se dibuja solo si entra entre los botones de arriba
	private int finBotonesIzquierda, inicioBotonesDerecha;
	private Button botonVelocidad;
	private Button botonRanura;
	private Button botonGuardarEstado;
	private Button botonCargarEstado;
	// Estática: se recuerda al cerrar y volver a abrir la pantalla
	private static int ranura = 1;
	private EmuladorCliente.Mensaje mensaje;
	private long mensajeHasta;

	public TelevisorScreen() {
		super(Component.translatable("screen.emumod.televisor"));
		emulador = EmuladorCliente.get();
		enManos = false;
		tele = null;
		doble = null;
	}

	public TelevisorScreen(ConsolaItem.Modelo modelo) {
		super(Component.translatable("item.emumod." + modelo.name().toLowerCase(java.util.Locale.ROOT)));
		doble = PortatilDoble.de(modelo);
		emulador = doble != null ? EmuladorCliente.de(doble.sistema) : EmuladorCliente.get();
		enManos = modelo == ConsolaItem.Modelo.GBA || doble != null;
		tele = null;
	}

	/** Jugando a la NES o la N64, sentado frente al televisor CRT con el control en las manos (ver SesionNes). */
	static TelevisorScreen tele(ConsolaTele consola) {
		return new TelevisorScreen(consola);
	}

	private TelevisorScreen(ConsolaTele tele) {
		super(Component.translatable("screen.emumod." + tele.modelo));
		emulador = tele.emulador();
		enManos = true;
		this.tele = tele;
		doble = null;
	}

	@Override
	protected void init() {
		if (tele != null) {
			SesionNes.subir();
		} else if (doble != null) {
			ManosDoble.subir(doble);
		} else if (enManos) {
			ManosGba.subir();
		} else {
			calcularArea();
		}

		// Arriba a la izquierda: juego, pausa y reinicio; a la derecha: volumen y BIOS
		int arriba = 4;
		int izquierda = 8;
		botonRom = addRenderableWidget(Button.builder(Component.translatable("screen.emumod.cargar_rom" + sufijoTextos()), b -> elegirRom())
				.bounds(izquierda, arriba, 110, ALTO_BOTON).build());
		izquierda += 110 + SEPARACION;
		botonPausa = addRenderableWidget(Button.builder(textoPausa(), b -> alternarPausa())
				.bounds(izquierda, arriba, 50, ALTO_BOTON).build());
		izquierda += 50 + SEPARACION;
		botonReiniciar = addRenderableWidget(Button.builder(Component.translatable("screen.emumod.reiniciar"),
				b -> {
					setFocused(null);
					// La PS2 sin disco se puede prender igual: arranca el menú de la BIOS
					if (!emulador.hayJuego() && emulador.consola() == Sistema.PS2) {
						emulador.encenderSinJuego(this::mostrar);
					} else {
						emulador.reiniciar(this::mostrar);
					}
					botonPausa.setMessage(textoPausa());
				}).bounds(izquierda, arriba, 60, ALTO_BOTON).build());
		finBotonesIzquierda = izquierda + 60;

		int derecha = width - 8 - 80;
		botonBios = addRenderableWidget(Button.builder(textoBotonBios(), b -> elegirBios())
				.bounds(derecha, arriba, 80, ALTO_BOTON).build());
		// La 3DS no usa BIOS: su botón no se muestra y no ocupa lugar
		if (doble != PortatilDoble.N3DS) {
			derecha -= SEPARACION + 80;
		}
		barraVolumen = addRenderableWidget(new BarraVolumen(derecha, arriba, 80, ALTO_BOTON));
		derecha -= SEPARACION + 64;
		botonTeclas = addRenderableWidget(Button.builder(Component.literal("Keybinds"), b -> {
			// Sin levantarse ni bajar la consola: al volver sigue la misma partida
			abriendoTeclas = true;
			minecraft.setScreen(new TeclasScreen(this, emulador));
		}).bounds(derecha, arriba, 64, ALTO_BOTON).build());
		inicioBotonesDerecha = derecha;

		int[] anchos = {90, 64, 96, 96};
		int total = anchos[0] + anchos[1] + anchos[2] + anchos[3] + 3 * SEPARACION;
		int x = (width - total) / 2;
		int abajo = height - ALTO_BOTON - 4;
		botonVelocidad = addRenderableWidget(Button.builder(textoVelocidad(), b -> cambiarVelocidad())
				.bounds(x, abajo, anchos[0], ALTO_BOTON).build());
		x += anchos[0] + SEPARACION;
		botonRanura = addRenderableWidget(Button.builder(textoRanura(), b -> cambiarRanura())
				.bounds(x, abajo, anchos[1], ALTO_BOTON).build());
		x += anchos[1] + SEPARACION;
		botonGuardarEstado = addRenderableWidget(Button.builder(Component.translatable("screen.emumod.guardar_estado"),
				b -> {
					setFocused(null);
					emulador.guardarEstado(ranura, this::mostrar);
				}).bounds(x, abajo, anchos[2], ALTO_BOTON).build());
		x += anchos[2] + SEPARACION;
		botonCargarEstado = addRenderableWidget(Button.builder(Component.translatable("screen.emumod.cargar_estado"),
				b -> {
					setFocused(null);
					emulador.cargarEstado(ranura, this::mostrar);
				}).bounds(x, abajo, anchos[3], ALTO_BOTON).build());

		if (enManos) {
			// Con la GBA en las manos: filtro de la pantalla y campo de visión, en una fila arriba de la de abajo
			int fila = abajo - ALTO_BOTON - SEPARACION;
			int inicio = (width - 2 * 120 - SEPARACION) / 2;
			addRenderableWidget(Button.builder(textoFiltro(), b -> {
				setFocused(null);
				emulador.setFiltro(emulador.filtro().siguiente(emulador.consola()));
				b.setMessage(textoFiltro());
			}).bounds(inicio, fila, 120, ALTO_BOTON).build());
			addRenderableWidget(new BarraFov(inicio + 120 + SEPARACION, fila, 120, ALTO_BOTON));
		}

	}

	// Escala entera sobre píxeles reales para que los píxeles del GBA queden nítidos
	private void calcularArea() {
		double guiScale = minecraft.getWindow().getGuiScale();
		int disponibleAncho = (int) ((width - 2 * MARCO - 8) * guiScale);
		int disponibleAlto = (int) ((height - MARGEN_SUPERIOR - MARGEN_INFERIOR - 2 * MARCO) * guiScale);
		escala = Math.max(1, Math.min(disponibleAncho / ANCHO_GBA, disponibleAlto / ALTO_GBA));

		anchoPantallaPx = ANCHO_GBA * escala;
		altoPantallaPx = ALTO_GBA * escala;
		int arribaPx = (int) (MARGEN_SUPERIOR * guiScale);
		int altoUtilPx = (int) ((height - MARGEN_SUPERIOR - MARGEN_INFERIOR) * guiScale);
		pantallaX = ((int) (width * guiScale) - anchoPantallaPx) / 2;
		pantallaY = arribaPx + (altoUtilPx - altoPantallaPx) / 2;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// Con la GBA en las manos se sigue viendo el mundo detrás
		if (!enManos) {
			renderBackground(graphics);
		}

		long ventana = minecraft.getWindow().getWindow();
		IntPredicate apretada = tecla -> InputConstants.isKeyDown(ventana, tecla);
		// El joystick se suma al teclado y al mouse; Back + Start abren las opciones como la tecla O
		boolean hayJoystick = Joystick.leer();
		if (hayJoystick && Joystick.combinacionOpciones() && enManos) {
			menuAbierto = !menuAbierto;
			setFocused(null);
		}
		int botones = Controles.mascara(emulador.consola(), apretada) | mascaraMouse | (hayJoystick ? Joystick.mascara(emulador.consola()) : 0);
		int[][] palancas = Controles.palancas(emulador.consola(), apretada);
		if (hayJoystick) {
			Joystick.palancas(emulador.consola(), palancas);
		}
		if (palancaMouse != null) {
			palancas[palancaMouse[0]] = new int[] {palancaMouse[1], palancaMouse[2]};
		}
		if (emulador.consola() == Sistema.WII) {
			// El puntero del Wiimote sigue al mouse por toda la ventana (la tele queda en el medio de la vista)
			int px = (int) Math.round(Math.max(-1, Math.min(1, mouseX * 2.0 / width - 1)) * Controles.PALANCA_MAXIMA);
			int py = (int) Math.round(Math.max(-1, Math.min(1, mouseY * 2.0 / height - 1)) * Controles.PALANCA_MAXIMA);
			emulador.setPuntero(px, py, botonesMouseWii);
		} else if (doble != null && doble.abajo != null) {
			// Pantalla táctil: el mouse sobre la pantalla de abajo del modelo, con el botón izquierdo apretado
			emulador.setPuntero(tactilX, tactilY, tocando ? 1 : 0);
		}
		emulador.actualizar(botones, palancas[0], palancas[1]);

		if (tele != null) {
			if (tele.manos != null) {
				tele.manos.setEstado(botones, palancas);
			} else {
				ManosNes.setBotones(botones);
			}
			// Minecraft avisa «Shift para bajarse» al montar el asiento; acá Shift es del juego y para levantarse se usa Esc
			minecraft.gui.setOverlayMessage(Component.empty(), false);
		} else if (doble != null) {
			ManosDoble.setEstado(botones, palancas[0]);
			actualizarPantallaProyectada();
		} else if (enManos) {
			ManosGba.setBotones(botones);
			actualizarPantallaProyectada();
		}
		double guiScale = minecraft.getWindow().getGuiScale();
		int marcoPx = (int) Math.round(MARCO * guiScale);
		int anchoPx = anchoPantallaPx;
		int altoPx = altoPantallaPx;

		if (!enManos) {
			// Dibujar en píxeles reales de la ventana
			graphics.pose().pushPose();
			graphics.pose().scale((float) (1 / guiScale), (float) (1 / guiScale), 1);
			graphics.fill(pantallaX - marcoPx - 1, pantallaY - marcoPx - 1, pantallaX + anchoPx + marcoPx + 1, pantallaY + altoPx + marcoPx + 1, COLOR_MARCO_BORDE);
			graphics.fill(pantallaX - marcoPx, pantallaY - marcoPx, pantallaX + anchoPx + marcoPx, pantallaY + altoPx + marcoPx, COLOR_MARCO);
			if (emulador.hayJuego()) {
				graphics.blit(emulador.textura(), pantallaX, pantallaY, anchoPx, altoPx, 0, 0, ANCHO_GBA, ALTO_GBA, ANCHO_GBA, ALTO_GBA);
				if (emulador.pausadoPorJugador()) {
					graphics.fill(pantallaX, pantallaY, pantallaX + anchoPx, pantallaY + altoPx, COLOR_VELO_PAUSA);
				}
			} else {
				graphics.fill(pantallaX, pantallaY, pantallaX + anchoPx, pantallaY + altoPx, COLOR_PANTALLA);
			}
			graphics.pose().popPose();
		}

		int centroX = width / 2;
		int centroPantallaX = (int) ((pantallaX + anchoPx / 2) / guiScale);
		if (emulador.hayJuego() && emulador.pausadoPorJugador() && anchoPx > 0) {
			int centroY = (int) ((pantallaY + altoPx / 2) / guiScale);
			graphics.drawCenteredString(font, Component.translatable("screen.emumod.en_pausa"), centroPantallaX, centroY - 4, 0xFFFFFF);
		}
		if (!emulador.hayJuego() && anchoPx > 0) {
			int centroY = (int) ((pantallaY + altoPx / 2) / guiScale);
			graphics.drawCenteredString(font, Component.translatable("screen.emumod.sin_juego"), centroPantallaX, centroY - 4, COLOR_TENUE);
			if (emulador.error() != null) {
				graphics.drawCenteredString(font, Component.literal(emulador.error()), centroPantallaX, centroY + 8, COLOR_ERROR);
			}
		}
		int anchoTitulo = font.width(title);
		if (!enManos && centroX - anchoTitulo / 2 > finBotonesIzquierda + 4 && centroX + anchoTitulo / 2 < inicioBotonesDerecha - 4) {
			graphics.drawCenteredString(font, title, centroX, 10, 0xFFFFFF);
		}

		int lineaMensaje = height - MARGEN_INFERIOR + 1;
		if (enManos && verMenuGba()) {
			// Arriba de la fila de filtro y FOV
			lineaMensaje -= ALTO_BOTON + SEPARACION + 2;
		}
		if (mensaje != null && System.currentTimeMillis() < mensajeHasta) {
			graphics.drawCenteredString(font, mensaje.texto(), centroX, lineaMensaje, mensaje.esError() ? COLOR_ERROR : COLOR_OK);
		} else if (emulador.hayJuego() && !emulador.pausadoPorJugador() && emulador.velocidadReal() > 0
				&& emulador.velocidadReal() < emulador.velocidad() * 0.9) {
			// Avisar si la PC no llega a la velocidad elegida (también a x1: el juego va lento)
			graphics.drawCenteredString(font, Component.translatable("screen.emumod.velocidad_real",
					String.format("%.1f", emulador.velocidadReal())), centroX, lineaMensaje, COLOR_TENUE);
		}

		// Con la GBA en las manos el menú se esconde para ver la consola; la tecla de opciones (O) lo muestra
		boolean verMenu = !enManos || verMenuGba();
		for (GuiEventListener hijo : children()) {
			if (hijo instanceof AbstractWidget widget) {
				widget.visible = verMenu;
			}
		}
		if (!verMenu) {
			dibujarAyuda(graphics);
		}
		// La NES, la N64 y la 3DS no usan BIOS
		if (doble == PortatilDoble.N3DS) {
			botonBios.visible = false;
		}
		if (tele != null) {
			botonBios.visible = verMenu && (emulador.consola() == Sistema.PS2 || emulador.consola() == Sistema.PS3);
			if (!emulador.hayJuego()) {
				graphics.drawCenteredString(font, Component.translatable("screen.emumod.sin_juego"), centroX, height / 3, 0xFFFFFF);
				if (emulador.consola() == Sistema.PS2) {
					graphics.drawCenteredString(font, Component.translatable(emulador.biosPs2() == null ? "screen.emumod.ayuda_bios_ps2"
							: "screen.emumod.ayuda_encender_ps2"), centroX, height / 3 + 24, COLOR_TENUE);
				} else if (emulador.consola() == Sistema.PS3) {
					graphics.drawCenteredString(font, Component.translatable(EmuladorCliente.hayFirmwarePs3() ? "screen.emumod.ayuda_juego_ps3"
							: "screen.emumod.ayuda_firmware_ps3"), centroX, height / 3 + 24, COLOR_TENUE);
				}
				if (emulador.error() != null) {
					graphics.drawCenteredString(font, Component.literal(emulador.error()), centroX, height / 3 + 12, COLOR_ERROR);
				}
			}
		}
		// Mientras el diálogo de archivos está abierto no se puede abrir otro
		botonRom.active = botonBios.active = !SelectorArchivos.abierto();
		botonGuardarEstado.active = botonCargarEstado.active = botonPausa.active = emulador.hayJuego();
		boolean encender = !emulador.hayJuego() && emulador.consola() == Sistema.PS2;
		botonReiniciar.active = emulador.hayJuego() || (encender && emulador.biosPs2() != null);
		botonReiniciar.setMessage(Component.translatable(encender ? "screen.emumod.encender" : "screen.emumod.reiniciar"));
		botonPausa.setMessage(textoPausa());
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	// ---- Game Boy Advance: se juega con la consola en las manos, delante de los ojos (ver ManosGba) ----

	// Altura de las tapas de los botones (el frente del modelo mira a -y)
	private static final float FRENTE_BOTONES = -0.15f;

	// Recuadro de la pantalla en píxeles reales, para centrar los textos de pausa y sin juego
	private void actualizarPantallaProyectada() {
		double guiScale = minecraft.getWindow().getGuiScale();
		Matrix4f matriz = doble != null ? ManosDoble.matrizGui() : ManosGba.matrizGui();
		if (matriz == null) {
			anchoPantallaPx = altoPantallaPx = 0;
			return;
		}
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		java.util.List<Vector4f> esquinas = new java.util.ArrayList<>();
		if (doble != null) {
			// Los textos van en la pantalla de arriba
			for (float[] e : doble.arriba) {
				esquinas.add(ModeloDoble.transformar(doble, matriz, e[0], e[1], e[2]));
			}
		} else {
			for (float x : new float[] {ModeloGba3D.PANTALLA_IZQ, ModeloGba3D.PANTALLA_DER}) {
				for (float z : new float[] {ModeloGba3D.PANTALLA_ABAJO, ModeloGba3D.PANTALLA_ARRIBA}) {
					esquinas.add(ModeloGba3D.transformar(matriz, x, ModeloGba3D.PANTALLA_Y, z));
				}
			}
		}
		for (Vector4f punto : esquinas) {
			minX = Math.min(minX, punto.x);
			maxX = Math.max(maxX, punto.x);
			minY = Math.min(minY, punto.y);
			maxY = Math.max(maxY, punto.y);
		}
		pantallaX = (int) (minX * guiScale);
		pantallaY = (int) (minY * guiScale);
		anchoPantallaPx = (int) ((maxX - minX) * guiScale);
		altoPantallaPx = (int) ((maxY - minY) * guiScale);
	}

	/** Botón del modelo bajo el mouse: se proyecta cada botón con la misma pose con la que se dibuja. */
	private int mascaraModelo(double mouseX, double mouseY) {
		Matrix4f matriz = ManosGba.matrizGui();
		if (matriz == null) {
			return 0;
		}
		// Cruz: se pasa el mouse al plano de la cruz resolviendo con sus dos ejes proyectados
		Vector4f centro = ModeloGba3D.transformar(matriz, ModeloGba3D.CRUZ_X, FRENTE_BOTONES, ModeloGba3D.CRUZ_Z);
		Vector4f ejeX = ModeloGba3D.transformar(matriz, ModeloGba3D.CRUZ_X + 1, FRENTE_BOTONES, ModeloGba3D.CRUZ_Z).sub(centro);
		Vector4f ejeZ = ModeloGba3D.transformar(matriz, ModeloGba3D.CRUZ_X, FRENTE_BOTONES, ModeloGba3D.CRUZ_Z + 1).sub(centro);
		double mx = mouseX - centro.x, my = mouseY - centro.y;
		double determinante = ejeX.x * ejeZ.y - ejeZ.x * ejeX.y;
		if (Math.abs(determinante) > 1e-6) {
			float a = (float) ((mx * ejeZ.y - ejeZ.x * my) / determinante);
			float b = (float) ((ejeX.x * my - mx * ejeX.y) / determinante);
			int cruz = ModeloGba3D.mascaraCruz(ModeloGba3D.CRUZ_X + a, ModeloGba3D.CRUZ_Z + b);
			if (cruz != 0) {
				return cruz;
			}
		}
		for (ModeloGba3D.BotonModelo boton : ModeloGba3D.BOTONES) {
			Vector4f c = ModeloGba3D.transformar(matriz, boton.x(), FRENTE_BOTONES, boton.z());
			Vector4f borde = ModeloGba3D.transformar(matriz, boton.x() + boton.radio(), FRENTE_BOTONES, boton.z());
			double radio = Math.hypot(borde.x - c.x, borde.y - c.y);
			if (Math.hypot(mouseX - c.x, mouseY - c.y) <= radio) {
				return boton.mascara();
			}
		}
		return 0;
	}

	// Abajo a la derecha, en chiquito: cómo abrir las opciones y cómo guardar la consola
	private void dibujarAyuda(GuiGraphics graphics) {
		float escalaTexto = 0.75f;
		Component opciones = Component.translatable("screen.emumod.ayuda_opciones", EmuModClient.TECLA_OPCIONES.getTranslatedKeyMessage());
		Component salir = Component.translatable(tele != null ? "screen.emumod.ayuda_levantarse" : "screen.emumod.ayuda_guardar");
		Component[] lineas = doble != null && doble.abajo != null ? new Component[] {Component.translatable("screen.emumod.ayuda_tocar"), opciones, salir}
				: new Component[] {opciones, salir};
		String joystick = Joystick.nombre();
		if (joystick != null) {
			lineas = java.util.Arrays.copyOf(lineas, lineas.length + 1);
			lineas[lineas.length - 1] = Component.translatable("screen.emumod.joystick", joystick);
		}
		graphics.pose().pushPose();
		graphics.pose().scale(escalaTexto, escalaTexto, 1);
		int derecha = (int) ((width - 4) / escalaTexto);
		int y = (int) ((height - 4) / escalaTexto) - lineas.length * 10;
		for (Component linea : lineas) {
			graphics.drawString(font, linea, derecha - font.width(linea), y, 0xFFFFFF);
			y += 10;
		}
		graphics.pose().popPose();
	}

	private boolean verMenuGba() {
		return menuAbierto || !emulador.hayJuego();
	}

	/** Abre la GBA con el menú de opciones visible (tecla de opciones con la consola en la mano). */
	void abrirConOpciones() {
		menuAbierto = true;
	}

	// Solo para las capturas de desarrollo: muestra u oculta el menú de opciones
	void mostrarMenuParaCaptura() {
		menuAbierto = true;
	}

	void ocultarMenuParaCaptura() {
		menuAbierto = false;
	}

	// Solo para las capturas de desarrollo: dibuja la consola con esos botones apretados
	void apretarParaCaptura(int mascara) {
		mascaraMouse = mascara;
	}

	private int mascaraBajo(double mouseX, double mouseY) {
		if (tele != null && tele.manos != null) {
			return tele.manos.mascaraEn(mouseX, mouseY);
		}
		if (tele != null) {
			return ManosNes.mascaraEn(mouseX, mouseY);
		}
		return enManos ? mascaraModelo(mouseX, mouseY) : 0;
	}

	// Los botones dibujados de la consola también se aprietan con el mouse, y arrastrando se puede pasar de uno a otro.
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int boton) {
		if (super.mouseClicked(mouseX, mouseY, boton)) {
			return true;
		}
		// Wii: los clicks que no tocan un botón del menú van al Wiimote (izquierdo A, derecho B, rueda sacudir)
		if (emulador.consola() == Sistema.WII && boton <= 2) {
			botonesMouseWii |= 1 << boton;
			return true;
		}
		// 3DS: sobre la pantalla de abajo se toca; sobre la palanca circular se la empuja; sobre un botón, se aprieta
		if (doble != null && boton == 0) {
			float[] p = puntoDoble(mouseX, mouseY);
			if (p == null) {
				return false;
			}
			int[] toque = ModeloDoble.puntoTactil(doble, p[0], p[1]);
			if (toque != null) {
				tactilX = toque[0];
				tactilY = toque[1];
				tocando = apretandoConMouse = true;
				return true;
			}
			if (ModeloDoble.sobrePalanca(doble, p[0], p[1])) {
				int[] palanca = ModeloDoble.palancaDesde(doble, p[0], p[1]);
				palancaMouse = new int[] {0, palanca[0], palanca[1]};
				apretandoConMouse = true;
				return true;
			}
			mascaraMouse = ModeloDoble.mascaraEn(doble, p[0], p[1]);
			apretandoConMouse = mascaraMouse != 0;
			return apretandoConMouse;
		}
		if (enManos && boton == 0) {
			if (tele != null && tele.manos != null && (palancaMouse = tele.manos.palancaEn(mouseX, mouseY)) != null) {
				apretandoConMouse = true;
				return true;
			}
			mascaraMouse = mascaraBajo(mouseX, mouseY);
			apretandoConMouse = mascaraMouse != 0;
			return apretandoConMouse;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int boton, double dx, double dy) {
		if (doble != null && apretandoConMouse) {
			float[] p = puntoDoble(mouseX, mouseY);
			if (p != null) {
				if (tocando) {
					// Arrastrando fuera de la pantalla el stylus se queda en el último punto de adentro
					int[] toque = ModeloDoble.puntoTactil(doble, p[0], p[1]);
					if (toque != null) {
						tactilX = toque[0];
						tactilY = toque[1];
					}
				} else if (palancaMouse != null) {
					int[] palanca = ModeloDoble.palancaDesde(doble, p[0], p[1]);
					palancaMouse = new int[] {0, palanca[0], palanca[1]};
				} else {
					mascaraMouse = ModeloDoble.mascaraEn(doble, p[0], p[1]);
				}
			}
			return true;
		}
		if (palancaMouse != null) {
			// Arrastrando fuera de la palanca se la sigue empujando a fondo hacia ese lado
			int[] nueva = tele.manos.arrastrarPalanca(palancaMouse[0], mouseX, mouseY);
			if (nueva != null) {
				palancaMouse = nueva;
			}
			return true;
		}
		if (apretandoConMouse) {
			mascaraMouse = mascaraBajo(mouseX, mouseY);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, boton, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int boton) {
		if (emulador.consola() == Sistema.WII && boton <= 2) {
			botonesMouseWii &= ~(1 << boton);
		}
		if (apretandoConMouse) {
			apretandoConMouse = false;
			tocando = false;
			mascaraMouse = 0;
			palancaMouse = null;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, boton);
	}

	// 3DS o SP: punto (x, z) de la base de la consola bajo el mouse, o null si todavía no se dibujó
	private float[] puntoDoble(double mouseX, double mouseY) {
		Matrix4f matriz = ManosDoble.matrizGui();
		return matriz == null ? null : ModeloDoble.puntoEnLaBase(doble, matriz, mouseX, mouseY);
	}

	private void elegirRom() {
		setFocused(null);
		String[] patrones = switch (emulador.consola()) {
			case NES -> new String[] {"*.nes"};
			case N64 -> new String[] {"*.z64", "*.n64", "*.v64"};
			case PS2 -> new String[] {"*.iso", "*.bin", "*.cue", "*.chd", "*.cso", "*.zso", "*.m3u"};
			case PS3 -> new String[] {"*.iso", "*.SFB", "*.sfb", "*.SFO", "*.sfo", "*.pkg", "*.BIN", "*.bin", "*.self", "*.elf"};
			case WII -> new String[] {"*.iso", "*.wbfs", "*.rvz", "*.gcm", "*.gcz", "*.ciso", "*.wia", "*.wad", "*.dol", "*.elf"};
			case N3DS -> new String[] {"*.3ds", "*.cci", "*.cxi", "*.cia", "*.3dsx"};
			default -> new String[] {"*.gba"};
		};
		SelectorArchivos.abrir(Component.translatable("screen.emumod.elegir_rom" + sufijoTextos()).getString(),
				patrones, Component.translatable("screen.emumod.filtro_rom" + sufijoTextos()).getString(),
				archivo -> emulador.cargarRom(archivo, this::mostrar));
	}

	private void elegirBios() {
		setFocused(null);
		boolean ps2 = emulador.consola() == Sistema.PS2, ps3 = emulador.consola() == Sistema.PS3;
		String clave = ps3 ? "_ps3" : ps2 ? "_ps2" : "";
		SelectorArchivos.abrir(Component.translatable("screen.emumod.elegir_bios" + clave).getString(),
				ps3 ? new String[] {"*.PUP", "*.pup"} : new String[] {"*.bin", "*.BIN"},
				Component.translatable("screen.emumod.filtro_bios" + clave).getString(),
				archivo -> {
					EmuladorCliente.Mensaje resultado = emulador.instalarBios(archivo);
					mostrar(resultado);
					botonBios.setMessage(textoBotonBios());
					// Con la BIOS lista y sin disco, la PS2 se prende sola en su menú
					if (ps2 && !resultado.esError() && !emulador.hayJuego()) {
						emulador.encenderSinJuego(this::mostrar);
					}
				});
	}

	private void cambiarVelocidad() {
		setFocused(null);
		int[] velocidades = EmuladorCliente.VELOCIDADES;
		int actual = 0;
		while (actual < velocidades.length && velocidades[actual] != emulador.velocidad()) {
			actual++;
		}
		emulador.setVelocidad(velocidades[(actual + 1) % velocidades.length]);
		botonVelocidad.setMessage(textoVelocidad());
	}

	private void alternarPausa() {
		setFocused(null);
		emulador.setPausadoPorJugador(!emulador.pausadoPorJugador());
		botonPausa.setMessage(textoPausa());
	}

	private Component textoPausa() {
		return Component.translatable(emulador.pausadoPorJugador() ? "screen.emumod.seguir" : "screen.emumod.pausa");
	}

	private void cambiarRanura() {
		setFocused(null);
		ranura = ranura % EmuladorCliente.RANURAS + 1;
		botonRanura.setMessage(textoRanura());
	}

	void mostrar(EmuladorCliente.Mensaje nuevo) {
		mensaje = nuevo;
		mensajeHasta = System.currentTimeMillis() + DURACION_MENSAJE_MS;
	}

	private Component textoVelocidad() {
		return Component.translatable("screen.emumod.velocidad", emulador.velocidad());
	}

	private Component textoRanura() {
		return Component.translatable("screen.emumod.ranura", ranura);
	}

	private Component textoFiltro() {
		return Component.translatable("screen.emumod.filtro",
				Component.translatable("screen.emumod.filtro." + emulador.filtro().name().toLowerCase(java.util.Locale.ROOT)));
	}

	// Los textos de cargar juego de la GBA no llevan sufijo; los de las otras consolas, el nombre de la consola
	private String sufijoTextos() {
		return emulador.consola() == Sistema.GBA ? "" : "_" + emulador.consola().name().toLowerCase(java.util.Locale.ROOT);
	}

	private Component textoBotonBios() {
		if (emulador.consola() == Sistema.PS2) {
			return Component.translatable(emulador.biosPs2() != null ? "screen.emumod.bios_ps2_lista" : "screen.emumod.bios_ps2_falta");
		}
		if (emulador.consola() == Sistema.PS3) {
			return Component.translatable(EmuladorCliente.hayFirmwarePs3() ? "screen.emumod.firmware_ps3_lista" : "screen.emumod.firmware_ps3_falta");
		}
		return Component.translatable(EmuladorCliente.hayBios() ? "screen.emumod.bios_oficial" : "screen.emumod.bios_libre");
	}

	// Las teclas del juego no deben mover el foco entre botones ni activar nada de la interfaz
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (enManos && EmuModClient.TECLA_OPCIONES.matches(keyCode, scanCode)) {
			menuAbierto = !menuAbierto;
			setFocused(null);
			return true;
		}
		if (emulador.hayJuego() && Controles.esTeclaDelJuego(emulador.consola(), keyCode)) {
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void removed() {
		if (abriendoTeclas) {
			abriendoTeclas = false;
			emulador.pausar();
			return;
		}
		if (tele != null) {
			SesionNes.terminar();
		} else if (doble != null) {
			ManosDoble.bajar();
		} else if (enManos) {
			ManosGba.bajar();
		}
		palancaMouse = null;
		mascaraMouse = 0;
		apretandoConMouse = false;
		tocando = false;
		emulador.pausar();
		emulador.guardarOpciones();
	}

	/** Campo de visión con la GBA delante de los ojos (más chico = la consola se ve más grande). */
	private final class BarraFov extends AbstractSliderButton {
		BarraFov(int x, int y, int ancho, int alto) {
			super(x, y, ancho, alto, Component.empty(), aValor(emulador.fovConsola()));
			updateMessage();
		}

		private static double aValor(int fov) {
			return (fov - EmuladorCliente.FOV_MINIMO) / (double) (EmuladorCliente.FOV_MAXIMO - EmuladorCliente.FOV_MINIMO);
		}

		private int fov() {
			return (int) Math.round(EmuladorCliente.FOV_MINIMO + value * (EmuladorCliente.FOV_MAXIMO - EmuladorCliente.FOV_MINIMO));
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable("screen.emumod.fov", fov()));
		}

		@Override
		protected void applyValue() {
			emulador.setFovConsola(fov());
		}

		// Soltar la barra le saca el foco, así las flechas vuelven a ser del juego
		@Override
		public void onRelease(double mouseX, double mouseY) {
			super.onRelease(mouseX, mouseY);
			TelevisorScreen.this.setFocused(null);
		}
	}

	/** Volumen propio del televisor, que se multiplica por el volumen general de Minecraft. */
	private final class BarraVolumen extends AbstractSliderButton {
		BarraVolumen(int x, int y, int ancho, int alto) {
			super(x, y, ancho, alto, Component.empty(), emulador.volumen());
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable("screen.emumod.volumen", (int) Math.round(value * 100)));
		}

		@Override
		protected void applyValue() {
			emulador.setVolumen(value);
		}

		// Soltar la barra le saca el foco, así las flechas vuelven a ser del juego
		@Override
		public void onRelease(double mouseX, double mouseY) {
			super.onRelease(mouseX, mouseY);
			TelevisorScreen.this.setFocused(null);
		}
	}
}
