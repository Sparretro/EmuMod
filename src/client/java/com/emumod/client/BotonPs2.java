package com.emumod.client;

import com.emumod.client.emu.Nucleo.Boton;

/** Botones del DualShock 2: LRPS2 lee cada uno de un botón del RetroPad (las palancas van aparte, como analógicos). */
enum BotonPs2 {
	EQUIS(Boton.B), CIRCULO(Boton.A), CUADRADO(Boton.Y), TRIANGULO(Boton.X),
	L1(Boton.L), R1(Boton.R), L2(Boton.L2), R2(Boton.R2), L3(Boton.L3), R3(Boton.R3),
	SELECT(Boton.SELECT), START(Boton.START),
	ARRIBA(Boton.ARRIBA), ABAJO(Boton.ABAJO), IZQUIERDA(Boton.IZQUIERDA), DERECHA(Boton.DERECHA);

	final int mascara;

	BotonPs2(Boton retroPad) {
		mascara = retroPad.mascara();
	}
}
