package com.emumod.client;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.IntPredicate;

import org.lwjgl.glfw.GLFW;

import com.emumod.client.emu.Nucleo.Boton;
import com.emumod.client.emu.Sistema;

/**
 * Teclado -> botones y palancas de cada consola. Cada consola tiene su lista de acciones con una tecla cada una, que el
 * jugador puede cambiar en «Keybinds» (TeclasScreen); se guardan en opciones.properties.
 */
public final class Controles {
	/**
	 * Una acción del control: un botón (mascara) o una dirección de una palanca (palanca 0 = izquierda, 1 = derecha;
	 * direccion 0 arriba, 1 abajo, 2 izquierda, 3 derecha).
	 */
	public record Accion(String nombre, int teclaPorDefecto, int mascara, int palanca, int direccion) {
		static Accion boton(String nombre, int tecla, int mascara) {
			return new Accion(nombre, tecla, mascara, -1, -1);
		}

		static Accion palanca(String nombre, int tecla, int palanca, int direccion) {
			return new Accion(nombre, tecla, 0, palanca, direccion);
		}

		boolean esPalanca() {
			return palanca >= 0;
		}
	}

	// GBA y NES
	private static final List<Accion> GBA_NES = List.of(
			Accion.boton("Arriba", GLFW.GLFW_KEY_UP, Boton.ARRIBA.mascara()),
			Accion.boton("Abajo", GLFW.GLFW_KEY_DOWN, Boton.ABAJO.mascara()),
			Accion.boton("Izquierda", GLFW.GLFW_KEY_LEFT, Boton.IZQUIERDA.mascara()),
			Accion.boton("Derecha", GLFW.GLFW_KEY_RIGHT, Boton.DERECHA.mascara()),
			Accion.boton("A", GLFW.GLFW_KEY_Z, Boton.A.mascara()),
			Accion.boton("B", GLFW.GLFW_KEY_X, Boton.B.mascara()),
			Accion.boton("Start", GLFW.GLFW_KEY_ENTER, Boton.START.mascara()),
			Accion.boton("Start (2)", GLFW.GLFW_KEY_KP_ENTER, Boton.START.mascara()),
			Accion.boton("Select", GLFW.GLFW_KEY_LEFT_SHIFT, Boton.SELECT.mascara()),
			Accion.boton("Select (2)", GLFW.GLFW_KEY_RIGHT_SHIFT, Boton.SELECT.mascara()),
			Accion.boton("L", GLFW.GLFW_KEY_A, Boton.L.mascara()),
			Accion.boton("R", GLFW.GLFW_KEY_S, Boton.R.mascara()));

	// N64: las flechas son la palanca y la cruz va en T F G H
	private static final List<Accion> N64 = List.of(
			Accion.palanca("Palanca arriba", GLFW.GLFW_KEY_UP, 0, 0),
			Accion.palanca("Palanca abajo", GLFW.GLFW_KEY_DOWN, 0, 1),
			Accion.palanca("Palanca izquierda", GLFW.GLFW_KEY_LEFT, 0, 2),
			Accion.palanca("Palanca derecha", GLFW.GLFW_KEY_RIGHT, 0, 3),
			Accion.boton("A", GLFW.GLFW_KEY_Z, BotonN64.A.mascara),
			Accion.boton("B", GLFW.GLFW_KEY_X, BotonN64.B.mascara),
			Accion.boton("Z", GLFW.GLFW_KEY_C, BotonN64.Z.mascara),
			Accion.boton("Z (2)", GLFW.GLFW_KEY_SPACE, BotonN64.Z.mascara),
			Accion.boton("Start", GLFW.GLFW_KEY_ENTER, BotonN64.START.mascara),
			Accion.boton("Start (2)", GLFW.GLFW_KEY_KP_ENTER, BotonN64.START.mascara),
			Accion.boton("L", GLFW.GLFW_KEY_A, BotonN64.L.mascara),
			Accion.boton("R", GLFW.GLFW_KEY_S, BotonN64.R.mascara),
			Accion.boton("C arriba", GLFW.GLFW_KEY_I, BotonN64.C_ARRIBA.mascara),
			Accion.boton("C abajo", GLFW.GLFW_KEY_K, BotonN64.C_ABAJO.mascara),
			Accion.boton("C izquierda", GLFW.GLFW_KEY_J, BotonN64.C_IZQUIERDA.mascara),
			Accion.boton("C derecha", GLFW.GLFW_KEY_L, BotonN64.C_DERECHA.mascara),
			Accion.boton("Cruz arriba", GLFW.GLFW_KEY_T, BotonN64.ARRIBA.mascara),
			Accion.boton("Cruz abajo", GLFW.GLFW_KEY_G, BotonN64.ABAJO.mascara),
			Accion.boton("Cruz izquierda", GLFW.GLFW_KEY_F, BotonN64.IZQUIERDA.mascara),
			Accion.boton("Cruz derecha", GLFW.GLFW_KEY_H, BotonN64.DERECHA.mascara));

	// PS2 y PS3: las flechas son la cruz (los menús se manejan con ella), W A S D la palanca izquierda e I J K L la derecha
	private static final List<Accion> PS = List.of(
			Accion.boton("Cruz arriba", GLFW.GLFW_KEY_UP, BotonPs2.ARRIBA.mascara),
			Accion.boton("Cruz abajo", GLFW.GLFW_KEY_DOWN, BotonPs2.ABAJO.mascara),
			Accion.boton("Cruz izquierda", GLFW.GLFW_KEY_LEFT, BotonPs2.IZQUIERDA.mascara),
			Accion.boton("Cruz derecha", GLFW.GLFW_KEY_RIGHT, BotonPs2.DERECHA.mascara),
			Accion.boton("Equis", GLFW.GLFW_KEY_Z, BotonPs2.EQUIS.mascara),
			Accion.boton("Círculo", GLFW.GLFW_KEY_X, BotonPs2.CIRCULO.mascara),
			Accion.boton("Cuadrado", GLFW.GLFW_KEY_C, BotonPs2.CUADRADO.mascara),
			Accion.boton("Triángulo", GLFW.GLFW_KEY_V, BotonPs2.TRIANGULO.mascara),
			Accion.boton("L1", GLFW.GLFW_KEY_Q, BotonPs2.L1.mascara),
			Accion.boton("R1", GLFW.GLFW_KEY_E, BotonPs2.R1.mascara),
			Accion.boton("L2", GLFW.GLFW_KEY_1, BotonPs2.L2.mascara),
			Accion.boton("R2", GLFW.GLFW_KEY_3, BotonPs2.R2.mascara),
			Accion.boton("L3", GLFW.GLFW_KEY_N, BotonPs2.L3.mascara),
			Accion.boton("R3", GLFW.GLFW_KEY_M, BotonPs2.R3.mascara),
			Accion.boton("Start", GLFW.GLFW_KEY_ENTER, BotonPs2.START.mascara),
			Accion.boton("Start (2)", GLFW.GLFW_KEY_KP_ENTER, BotonPs2.START.mascara),
			Accion.boton("Select", GLFW.GLFW_KEY_BACKSPACE, BotonPs2.SELECT.mascara),
			Accion.palanca("Palanca izq. arriba", GLFW.GLFW_KEY_W, 0, 0),
			Accion.palanca("Palanca izq. abajo", GLFW.GLFW_KEY_S, 0, 1),
			Accion.palanca("Palanca izq. izquierda", GLFW.GLFW_KEY_A, 0, 2),
			Accion.palanca("Palanca izq. derecha", GLFW.GLFW_KEY_D, 0, 3),
			Accion.palanca("Palanca der. arriba", GLFW.GLFW_KEY_I, 1, 0),
			Accion.palanca("Palanca der. abajo", GLFW.GLFW_KEY_K, 1, 1),
			Accion.palanca("Palanca der. izquierda", GLFW.GLFW_KEY_J, 1, 2),
			Accion.palanca("Palanca der. derecha", GLFW.GLFW_KEY_L, 1, 3));

	// Wii (Wiimote de costado): el puntero, A, B y sacudir también van con el mouse (ver TelevisorScreen); la palanca
	// izquierda de libretro es la inclinación del Wiimote
	private static final List<Accion> WII = List.of(
			Accion.boton("Cruz arriba", GLFW.GLFW_KEY_UP, BotonWii.ARRIBA.mascara),
			Accion.boton("Cruz abajo", GLFW.GLFW_KEY_DOWN, BotonWii.ABAJO.mascara),
			Accion.boton("Cruz izquierda", GLFW.GLFW_KEY_LEFT, BotonWii.IZQUIERDA.mascara),
			Accion.boton("Cruz derecha", GLFW.GLFW_KEY_RIGHT, BotonWii.DERECHA.mascara),
			Accion.boton("2", GLFW.GLFW_KEY_Z, BotonWii.DOS.mascara),
			Accion.boton("1", GLFW.GLFW_KEY_X, BotonWii.UNO.mascara),
			Accion.boton("A", GLFW.GLFW_KEY_C, BotonWii.A.mascara),
			Accion.boton("B", GLFW.GLFW_KEY_V, BotonWii.B.mascara),
			Accion.boton("Sacudir", GLFW.GLFW_KEY_SPACE, BotonWii.SACUDIR.mascara),
			Accion.boton("+", GLFW.GLFW_KEY_ENTER, BotonWii.MAS.mascara),
			Accion.boton("-", GLFW.GLFW_KEY_BACKSPACE, BotonWii.MENOS.mascara),
			Accion.boton("Home", GLFW.GLFW_KEY_H, BotonWii.HOME.mascara),
			Accion.palanca("Inclinar adelante", GLFW.GLFW_KEY_W, 0, 0),
			Accion.palanca("Inclinar atrás", GLFW.GLFW_KEY_S, 0, 1),
			Accion.palanca("Inclinar a la izquierda", GLFW.GLFW_KEY_A, 0, 2),
			Accion.palanca("Inclinar a la derecha", GLFW.GLFW_KEY_D, 0, 3));

	// 3DS: las flechas son la palanca circular y la cruz va en T F G H, como en la N64. A, B, X e Y como en RetroArch (X,
	// Z, S y A); Citra lee cada botón de la 3DS del botón del RetroPad con el mismo nombre (ZL y ZR son L2 y R2). Home no
	// se usa: en Citra para libretro también cambia de lugar las pantallas
	private static final List<Accion> N3DS = List.of(
			Accion.palanca("Palanca arriba", GLFW.GLFW_KEY_UP, 0, 0),
			Accion.palanca("Palanca abajo", GLFW.GLFW_KEY_DOWN, 0, 1),
			Accion.palanca("Palanca izquierda", GLFW.GLFW_KEY_LEFT, 0, 2),
			Accion.palanca("Palanca derecha", GLFW.GLFW_KEY_RIGHT, 0, 3),
			Accion.boton("A", GLFW.GLFW_KEY_X, Boton.A.mascara()),
			Accion.boton("B", GLFW.GLFW_KEY_Z, Boton.B.mascara()),
			Accion.boton("X", GLFW.GLFW_KEY_S, Boton.X.mascara()),
			Accion.boton("Y", GLFW.GLFW_KEY_A, Boton.Y.mascara()),
			Accion.boton("L", GLFW.GLFW_KEY_Q, Boton.L.mascara()),
			Accion.boton("R", GLFW.GLFW_KEY_E, Boton.R.mascara()),
			Accion.boton("ZL", GLFW.GLFW_KEY_1, Boton.L2.mascara()),
			Accion.boton("ZR", GLFW.GLFW_KEY_3, Boton.R2.mascara()),
			Accion.boton("Start", GLFW.GLFW_KEY_ENTER, Boton.START.mascara()),
			Accion.boton("Start (2)", GLFW.GLFW_KEY_KP_ENTER, Boton.START.mascara()),
			Accion.boton("Select", GLFW.GLFW_KEY_BACKSPACE, Boton.SELECT.mascara()),
			Accion.boton("Cruz arriba", GLFW.GLFW_KEY_T, Boton.ARRIBA.mascara()),
			Accion.boton("Cruz abajo", GLFW.GLFW_KEY_G, Boton.ABAJO.mascara()),
			Accion.boton("Cruz izquierda", GLFW.GLFW_KEY_F, Boton.IZQUIERDA.mascara()),
			Accion.boton("Cruz derecha", GLFW.GLFW_KEY_H, Boton.DERECHA.mascara()));


	// PSP: como la PS2 (flechas = cruz, W A S D = palanca analógica, Z X C V = ✕ ◯ □ △) con un solo gatillo por lado.
	// PPSSPP lee ✕ ◯ □ △ de B, A, Y y X del RetroPad (por posición)
	private static final List<Accion> PSP = List.of(
			Accion.boton("Cruz arriba", GLFW.GLFW_KEY_UP, Boton.ARRIBA.mascara()),
			Accion.boton("Cruz abajo", GLFW.GLFW_KEY_DOWN, Boton.ABAJO.mascara()),
			Accion.boton("Cruz izquierda", GLFW.GLFW_KEY_LEFT, Boton.IZQUIERDA.mascara()),
			Accion.boton("Cruz derecha", GLFW.GLFW_KEY_RIGHT, Boton.DERECHA.mascara()),
			Accion.boton("Equis", GLFW.GLFW_KEY_Z, Boton.B.mascara()),
			Accion.boton("Círculo", GLFW.GLFW_KEY_X, Boton.A.mascara()),
			Accion.boton("Cuadrado", GLFW.GLFW_KEY_C, Boton.Y.mascara()),
			Accion.boton("Triángulo", GLFW.GLFW_KEY_V, Boton.X.mascara()),
			Accion.boton("L", GLFW.GLFW_KEY_Q, Boton.L.mascara()),
			Accion.boton("R", GLFW.GLFW_KEY_E, Boton.R.mascara()),
			Accion.boton("Start", GLFW.GLFW_KEY_ENTER, Boton.START.mascara()),
			Accion.boton("Start (2)", GLFW.GLFW_KEY_KP_ENTER, Boton.START.mascara()),
			Accion.boton("Select", GLFW.GLFW_KEY_BACKSPACE, Boton.SELECT.mascara()),
			Accion.palanca("Palanca arriba", GLFW.GLFW_KEY_W, 0, 0),
			Accion.palanca("Palanca abajo", GLFW.GLFW_KEY_S, 0, 1),
			Accion.palanca("Palanca izquierda", GLFW.GLFW_KEY_A, 0, 2),
			Accion.palanca("Palanca derecha", GLFW.GLFW_KEY_D, 0, 3));

	// DS: como la 3DS pero sin palanca: las flechas son la cruz. melonDS DS lee cada botón del RetroPad con su nombre
	private static final List<Accion> NDS = List.of(
			Accion.boton("Cruz arriba", GLFW.GLFW_KEY_UP, Boton.ARRIBA.mascara()),
			Accion.boton("Cruz abajo", GLFW.GLFW_KEY_DOWN, Boton.ABAJO.mascara()),
			Accion.boton("Cruz izquierda", GLFW.GLFW_KEY_LEFT, Boton.IZQUIERDA.mascara()),
			Accion.boton("Cruz derecha", GLFW.GLFW_KEY_RIGHT, Boton.DERECHA.mascara()),
			Accion.boton("A", GLFW.GLFW_KEY_X, Boton.A.mascara()),
			Accion.boton("B", GLFW.GLFW_KEY_Z, Boton.B.mascara()),
			Accion.boton("X", GLFW.GLFW_KEY_S, Boton.X.mascara()),
			Accion.boton("Y", GLFW.GLFW_KEY_A, Boton.Y.mascara()),
			Accion.boton("L", GLFW.GLFW_KEY_Q, Boton.L.mascara()),
			Accion.boton("R", GLFW.GLFW_KEY_E, Boton.R.mascara()),
			Accion.boton("Start", GLFW.GLFW_KEY_ENTER, Boton.START.mascara()),
			Accion.boton("Start (2)", GLFW.GLFW_KEY_KP_ENTER, Boton.START.mascara()),
			Accion.boton("Select", GLFW.GLFW_KEY_BACKSPACE, Boton.SELECT.mascara()));

	/** Palanca a fondo; con Shift, a la mitad (para caminar). */
	static final int PALANCA_MAXIMA = 32767;

	// Tecla de cada acción, por consola (la GBA y la NES comparten la lista pero no las teclas)
	private static final Map<Sistema, int[]> TECLAS = new EnumMap<>(Sistema.class);

	private Controles() {
	}

	public static List<Accion> acciones(Sistema consola) {
		return switch (consola) {
			case N64 -> N64;
			case PS2, PS3 -> PS;
			case WII -> WII;
			case N3DS -> N3DS;
			case PSP -> PSP;
			case NDS -> NDS;
			default -> GBA_NES;
		};
	}

	/** Tecla de cada acción de la consola, en el orden de {@link #acciones}; se puede modificar (Keybinds). */
	public static synchronized int[] teclas(Sistema consola) {
		return TECLAS.computeIfAbsent(consola, c -> acciones(c).stream().mapToInt(Accion::teclaPorDefecto).toArray());
	}

	public static synchronized void restablecer(Sistema consola) {
		TECLAS.remove(consola);
	}

	/** Lee las teclas guardadas de la consola (clave "teclas_<consola>": códigos separados por comas). */
	static synchronized void cargar(Sistema consola, Properties opciones) {
		String guardadas = opciones.getProperty(clave(consola));
		if (guardadas == null) {
			return;
		}
		int[] teclas = teclas(consola);
		String[] partes = guardadas.split(",");
		for (int i = 0; i < teclas.length && i < partes.length; i++) {
			try {
				teclas[i] = Integer.parseInt(partes[i].trim());
			} catch (NumberFormatException e) {
				// Se queda la de fábrica
			}
		}
	}

	static synchronized void guardar(Sistema consola, Properties opciones) {
		int[] teclas = teclas(consola);
		StringBuilder texto = new StringBuilder();
		for (int i = 0; i < teclas.length; i++) {
			texto.append(i == 0 ? "" : ",").append(teclas[i]);
		}
		opciones.setProperty(clave(consola), texto.toString());
	}

	private static String clave(Sistema consola) {
		return "teclas_" + consola.name().toLowerCase(java.util.Locale.ROOT);
	}

	/** Máscara de botones de la consola según qué teclas están apretadas. */
	public static int mascara(Sistema consola, IntPredicate teclaApretada) {
		List<Accion> acciones = acciones(consola);
		int[] teclas = teclas(consola);
		int mascara = 0;
		for (int i = 0; i < acciones.size(); i++) {
			if (!acciones.get(i).esPalanca() && teclas[i] != GLFW.GLFW_KEY_UNKNOWN && teclaApretada.test(teclas[i])) {
				mascara |= acciones.get(i).mascara();
			}
		}
		return mascara;
	}

	/**
	 * Palancas izquierda y derecha según el teclado: x e y de -32767 a 32767 (y positiva = abajo); en diagonal no se pasan
	 * del círculo. Las que la consola no tiene quedan en el centro.
	 */
	public static int[][] palancas(Sistema consola, IntPredicate teclaApretada) {
		List<Accion> acciones = acciones(consola);
		int[] teclas = teclas(consola);
		int[][] direcciones = new int[2][4];
		for (int i = 0; i < acciones.size(); i++) {
			Accion a = acciones.get(i);
			if (a.esPalanca() && teclas[i] != GLFW.GLFW_KEY_UNKNOWN && teclaApretada.test(teclas[i])) {
				direcciones[a.palanca()][a.direccion()] = 1;
			}
		}
		boolean despacio = teclaApretada.test(GLFW.GLFW_KEY_LEFT_SHIFT) || teclaApretada.test(GLFW.GLFW_KEY_RIGHT_SHIFT);
		int[][] resultado = new int[2][2];
		for (int p = 0; p < 2; p++) {
			int x = direcciones[p][3] - direcciones[p][2];
			int y = direcciones[p][1] - direcciones[p][0];
			double largo = x != 0 && y != 0 ? Math.sqrt(2) : 1;
			double fuerza = PALANCA_MAXIMA * (despacio && consola != Sistema.GBA && consola != Sistema.NES ? 0.5 : 1) / largo;
			resultado[p] = new int[] {(int) Math.round(x * fuerza), (int) Math.round(y * fuerza)};
		}
		return resultado;
	}

	/** La palanca del N64 (ver {@link #palancas}). */
	public static int[] palanca(IntPredicate teclaApretada) {
		return palancas(Sistema.N64, teclaApretada)[0];
	}

	public static boolean esTeclaDelJuego(Sistema consola, int tecla) {
		for (int t : teclas(consola)) {
			if (t == tecla) {
				return true;
			}
		}
		boolean tienePalancas = acciones(consola).stream().anyMatch(Accion::esPalanca);
		return tienePalancas && (tecla == GLFW.GLFW_KEY_LEFT_SHIFT || tecla == GLFW.GLFW_KEY_RIGHT_SHIFT);
	}
}
