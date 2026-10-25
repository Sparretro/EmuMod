package com.emumod.client;

import com.emumod.client.emu.Nucleo.Boton;

/** Botones del Wiimote de costado: Dolphin lee cada uno de un botón del RetroPad (su mapeo «sideways»). */
enum BotonWii {
	UNO(Boton.B), DOS(Boton.A), A(Boton.X), B(Boton.Y), SACUDIR(Boton.R2), MAS(Boton.START), MENOS(Boton.SELECT), HOME(Boton.R3),
	ARRIBA(Boton.ARRIBA), ABAJO(Boton.ABAJO), IZQUIERDA(Boton.IZQUIERDA), DERECHA(Boton.DERECHA);

	final int mascara;

	BotonWii(Boton retroPad) {
		mascara = retroPad.mascara();
	}
}
