package com.emumod.client;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

import com.emumod.client.emu.Nucleo.Boton;
import com.emumod.client.emu.Sistema;

/**
 * Joystick o gamepad (Xbox, PlayStation, Switch Pro y los genéricos que GLFW reconoce con su base de mapeos de SDL)
 * -> botones y palancas de cada consola. Se usa el primero conectado; se suma al teclado y al mouse, no los reemplaza.
 *
 * <p>Los botones van por posición, no por nombre: el de abajo del gamepad (A en Xbox, Equis en PlayStation) es el de
 * abajo de la consola, y así con los cuatro. Back + Start juntos abren o cierran las opciones del mod.
 */
public final class Joystick {
	private static final float ZONA_MUERTA = 0.2f;
	// Gatillos analógicos (-1 suelto a 1 a fondo) y palancas usadas como botones (cruz de la GBA, botones C de la N64)
	private static final float GATILLO = 0.3f;
	private static final float PALANCA_COMO_BOTON = 0.5f;

	private static final GLFWGamepadState ESTADO = GLFWGamepadState.create();
	private static boolean combinacionAntes;

	private Joystick() {
	}

	/** El primer gamepad conectado (número de joystick de GLFW), o -1. */
	static int conectado() {
		for (int j = GLFW.GLFW_JOYSTICK_1; j <= GLFW.GLFW_JOYSTICK_LAST; j++) {
			if (GLFW.glfwJoystickIsGamepad(j)) {
				return j;
			}
		}
		return -1;
	}

	/** Nombre del gamepad conectado (para avisarlo en pantalla), o null. */
	static String nombre() {
		int j = conectado();
		return j < 0 ? null : GLFW.glfwGetGamepadName(j);
	}

	/** Lee el gamepad; false si no hay ninguno. Se llama una vez por cuadro, antes de {@link #mascara} y {@link #palancas}. */
	static boolean leer() {
		int j = conectado();
		if (j < 0 || !GLFW.glfwGetGamepadState(j, ESTADO)) {
			ESTADO.buttons().put(0, new byte[ESTADO.buttons().capacity()]);
			FloatBuffer ejes = ESTADO.axes();
			for (int i = 0; i < ejes.capacity(); i++) {
				// Los gatillos sueltos valen -1
				ejes.put(i, i == GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER || i == GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER ? -1 : 0);
			}
			combinacionAntes = false;
			return false;
		}
		return true;
	}

	/** Back + Start recién apretados juntos (una vez por apretada): abrir o cerrar las opciones. */
	static boolean combinacionOpciones() {
		boolean ahora = apretado(GLFW.GLFW_GAMEPAD_BUTTON_BACK) && apretado(GLFW.GLFW_GAMEPAD_BUTTON_START);
		boolean nueva = ahora && !combinacionAntes;
		combinacionAntes = ahora;
		return nueva;
	}

	private static boolean apretado(int boton) {
		ByteBuffer b = ESTADO.buttons();
		return b.get(boton) == GLFW.GLFW_PRESS;
	}

	private static float eje(int eje) {
		float v = ESTADO.axes().get(eje);
		return Math.abs(v) < ZONA_MUERTA ? 0 : v;
	}

	private static boolean gatillo(int eje) {
		return ESTADO.axes().get(eje) > GATILLO;
	}

	/** Máscara de botones de la consola según el gamepad (en el formato de cada una, como {@link Controles#mascara}). */
	static int mascara(Sistema consola) {
		if (combinacionAntes) {
			// Back + Start están abriendo el menú: no llegan al juego
			return 0;
		}
		boolean abajo = apretado(GLFW.GLFW_GAMEPAD_BUTTON_A), derecha = apretado(GLFW.GLFW_GAMEPAD_BUTTON_B);
		boolean izquierda = apretado(GLFW.GLFW_GAMEPAD_BUTTON_X), arriba = apretado(GLFW.GLFW_GAMEPAD_BUTTON_Y);
		boolean lb = apretado(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER), rb = apretado(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER);
		boolean lt = gatillo(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER), rt = gatillo(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER);
		boolean start = apretado(GLFW.GLFW_GAMEPAD_BUTTON_START), back = apretado(GLFW.GLFW_GAMEPAD_BUTTON_BACK);
		boolean l3 = apretado(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_THUMB), r3 = apretado(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_THUMB);
		boolean cruzArriba = apretado(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP), cruzAbajo = apretado(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN);
		boolean cruzIzquierda = apretado(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT), cruzDerecha = apretado(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT);
		float lx = eje(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X), ly = eje(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y);
		float rx = eje(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X), ry = eje(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y);

		int m = 0;
		switch (consola) {
			case N64 -> {
				// A abajo y B a la izquierda, como en el control de N64; los botones C en la palanca derecha y también en
				// B e Y; Z en el gatillo izquierdo
				m |= pon(abajo, BotonN64.A.mascara) | pon(izquierda, BotonN64.B.mascara) | pon(lt || rt, BotonN64.Z.mascara)
						| pon(lb, BotonN64.L.mascara) | pon(rb, BotonN64.R.mascara) | pon(start, BotonN64.START.mascara)
						| pon(derecha || ry > PALANCA_COMO_BOTON, BotonN64.C_ABAJO.mascara)
						| pon(arriba || rx < -PALANCA_COMO_BOTON, BotonN64.C_IZQUIERDA.mascara)
						| pon(ry < -PALANCA_COMO_BOTON, BotonN64.C_ARRIBA.mascara) | pon(rx > PALANCA_COMO_BOTON, BotonN64.C_DERECHA.mascara)
						| pon(cruzArriba, BotonN64.ARRIBA.mascara) | pon(cruzAbajo, BotonN64.ABAJO.mascara)
						| pon(cruzIzquierda, BotonN64.IZQUIERDA.mascara) | pon(cruzDerecha, BotonN64.DERECHA.mascara);
			}
			case WII -> {
				// Wiimote de costado: 2 y 1 son los botones grandes, A y B los otros dos; RB sacude; la palanca izquierda
				// también mueve la cruz
				m |= pon(abajo, BotonWii.DOS.mascara) | pon(izquierda, BotonWii.UNO.mascara) | pon(derecha, BotonWii.A.mascara)
						| pon(arriba || lt, BotonWii.B.mascara) | pon(rb || rt, BotonWii.SACUDIR.mascara)
						| pon(start, BotonWii.MAS.mascara) | pon(back, BotonWii.MENOS.mascara) | pon(lb, BotonWii.HOME.mascara)
						| pon(cruzArriba || ly < -PALANCA_COMO_BOTON, BotonWii.ARRIBA.mascara)
						| pon(cruzAbajo || ly > PALANCA_COMO_BOTON, BotonWii.ABAJO.mascara)
						| pon(cruzIzquierda || lx < -PALANCA_COMO_BOTON, BotonWii.IZQUIERDA.mascara)
						| pon(cruzDerecha || lx > PALANCA_COMO_BOTON, BotonWii.DERECHA.mascara);
			}
			default -> {
				// El RetroPad de libretro ya va por posición (B abajo, A a la derecha, Y a la izquierda, X arriba), y la PS2,
				// la PS3, la 3DS, la PSP, la DS, la GBA y la NES lo leen así
				boolean sinPalancas = consola == Sistema.GBA || consola == Sistema.NES || consola == Sistema.NDS;
				m |= pon(abajo, Boton.B.mascara()) | pon(derecha, Boton.A.mascara()) | pon(izquierda, Boton.Y.mascara())
						| pon(arriba, Boton.X.mascara()) | pon(lb, Boton.L.mascara()) | pon(rb, Boton.R.mascara())
						| pon(lt, Boton.L2.mascara()) | pon(rt, Boton.R2.mascara()) | pon(l3, Boton.L3.mascara())
						| pon(r3, Boton.R3.mascara()) | pon(start, Boton.START.mascara()) | pon(back, Boton.SELECT.mascara())
						// En la GBA, la NES y la DS la palanca izquierda también es la cruz
						| pon(cruzArriba || sinPalancas && ly < -PALANCA_COMO_BOTON, Boton.ARRIBA.mascara())
						| pon(cruzAbajo || sinPalancas && ly > PALANCA_COMO_BOTON, Boton.ABAJO.mascara())
						| pon(cruzIzquierda || sinPalancas && lx < -PALANCA_COMO_BOTON, Boton.IZQUIERDA.mascara())
						| pon(cruzDerecha || sinPalancas && lx > PALANCA_COMO_BOTON, Boton.DERECHA.mascara());
			}
		}
		return m;
	}

	private static int pon(boolean apretado, int mascara) {
		return apretado ? mascara : 0;
	}

	/**
	 * Suma las palancas del gamepad a las del teclado ({@link Controles#palancas}): la que se mueve más allá de la zona
	 * muerta manda. En la Wii la palanca derecha inclina el Wiimote; en la N64 la derecha son los botones C (ver
	 * {@link #mascara}).
	 */
	static void palancas(Sistema consola, int[][] palancas) {
		if (consola == Sistema.GBA || consola == Sistema.NES || consola == Sistema.NDS) {
			return;
		}
		float lx = eje(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X), ly = eje(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y);
		float rx = eje(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X), ry = eje(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y);
		if (consola == Sistema.WII) {
			poner(palancas, 0, rx, ry);
			return;
		}
		poner(palancas, 0, lx, ly);
		if (consola != Sistema.N64) {
			poner(palancas, 1, rx, ry);
		}
	}

	private static void poner(int[][] palancas, int cual, float x, float y) {
		if (x != 0 || y != 0) {
			palancas[cual] = new int[] {Math.round(x * Controles.PALANCA_MAXIMA), Math.round(y * Controles.PALANCA_MAXIMA)};
		}
	}
}
