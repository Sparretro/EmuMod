package com.emumod.client.emu;

/**
 * ROM de NES propia, escrita a mano en 6502, para probar el núcleo sin juegos de terceros: pinta toda la pantalla de
 * rojo y hace sonar un tono con el canal de pulso. La usan la prueba del núcleo (probarNes) y las capturas de desarrollo.
 */
public final class RomDePrueba {
	// $C000: inicializa, espera dos vblank, pone rojo ($16) en toda la paleta (la CHR-RAM empieza con basura, así que
	// el fondo puede usar cualquier entrada), prende el fondo y un tono
	private static final int[] PROGRAMA = {
			0x78, 0xD8, // SEI, CLD
			0xA2, 0x40, 0x8E, 0x17, 0x40, // LDX #$40, STX $4017
			0xA2, 0xFF, 0x9A, 0xE8, // LDX #$FF, TXS, INX
			0x8E, 0x00, 0x20, 0x8E, 0x01, 0x20, // STX $2000, STX $2001
			0x2C, 0x02, 0x20, 0x10, 0xFB, // espera vblank
			0x2C, 0x02, 0x20, 0x10, 0xFB, // espera vblank
			0xA9, 0x3F, 0x8D, 0x06, 0x20, 0xA9, 0x00, 0x8D, 0x06, 0x20, // $2006 = $3F00
			0xA9, 0x16, 0xA2, 0x20, 0x8D, 0x07, 0x20, 0xCA, 0xD0, 0xFA, // las 32 entradas de la paleta en rojo ($16)
			0xA9, 0x00, 0x8D, 0x06, 0x20, 0x8D, 0x06, 0x20, // $2006 = $0000
			0xA9, 0x0A, 0x8D, 0x01, 0x20, // $2001 = fondo visible
			0xA9, 0x01, 0x8D, 0x15, 0x40, // $4015 = pulso 1 activo
			0xA9, 0xBF, 0x8D, 0x00, 0x40, // $4000 = ciclo 50%, volumen constante 15
			0xA9, 0xFD, 0x8D, 0x02, 0x40, // $4002 = período bajo (~440 Hz)
			0xA9, 0x00, 0x8D, 0x03, 0x40, // $4003 = período alto
			0x4C, 0x50, 0xC0, // bucle infinito: JMP $C050
	};

	private RomDePrueba() {
	}

	// iNES: 16 KB de PRG (en $C000, espejado en $8000), sin CHR-ROM (usa CHR-RAM), mapper 0
	public static byte[] roja() {
		// El JMP del bucle y los vectores apuntan a $C050, el final del programa
		if (PROGRAMA.length != 0x53) {
			throw new IllegalStateException("El programa cambió de largo: corregir el JMP y los vectores");
		}
		byte[] rom = new byte[16 + 16 * 1024];
		rom[0] = 'N';
		rom[1] = 'E';
		rom[2] = 'S';
		rom[3] = 0x1A;
		rom[4] = 1;
		for (int i = 0; i < PROGRAMA.length; i++) {
			rom[16 + i] = (byte) PROGRAMA[i];
		}
		// Vectores NMI, RESET e IRQ al final del banco
		int vectores = 16 + 0x3FFA;
		int[] direcciones = {0xC050, 0xC000, 0xC050};
		for (int i = 0; i < 3; i++) {
			rom[vectores + i * 2] = (byte) direcciones[i];
			rom[vectores + i * 2 + 1] = (byte) (direcciones[i] >> 8);
		}
		return rom;
	}
}
