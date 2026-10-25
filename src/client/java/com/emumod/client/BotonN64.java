package com.emumod.client;

import com.emumod.client.emu.Nucleo.Boton;

/**
 * Botones del control de N64. Mupen64Plus-Next con la opción alt-map lee cada uno de un botón distinto del RetroPad;
 * la palanca va aparte, como analógico (ver Nucleo#setPalanca).
 */
enum BotonN64 {
	A(Boton.B), B(Boton.Y), Z(Boton.L2), START(Boton.START), L(Boton.SELECT), R(Boton.R2),
	C_ARRIBA(Boton.X), C_ABAJO(Boton.A), C_IZQUIERDA(Boton.L), C_DERECHA(Boton.R),
	ARRIBA(Boton.ARRIBA), ABAJO(Boton.ABAJO), IZQUIERDA(Boton.IZQUIERDA), DERECHA(Boton.DERECHA);

	final int mascara;

	BotonN64(Boton retroPad) {
		mascara = retroPad.mascara();
	}
}
