package com.emumod.client.emu;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * ROM de N64 propia, escrita a mano en MIPS, para probar el núcleo sin juegos de terceros: pinta la pantalla de azul
 * (320x240 a 32 bits) y hace sonar un tono cuadrado por el AI. Mupen64Plus emula el arranque del PIF y salta al código
 * de arranque del cartucho (0x40 de la ROM, copiado a la DMEM del RSP); ese código es este programa, no el de Nintendo.
 */
public final class RomDePruebaN64 {
	private static final int ZERO = 0, T0 = 8, T1 = 9, T2 = 10, T3 = 11;
	/** Color de la pantalla de {@link #azul()}, RGBA8888. */
	public static final int COLOR = 0x2050E0FF;

	// Registros de la VI para 320x240 NTSC sin entrelazar, 32 bits por píxel (los valores de libdragon)
	private static final int[][] VI = {
			{0x04, 0x00100000}, // origen del cuadro en la RDRAM
			{0x08, 320}, // ancho
			{0x0C, 0x200}, // interrupción vertical
			{0x10, 0},
			{0x14, 0x03E52239}, // burst
			{0x18, 0x20D}, // v_sync: 525 líneas
			{0x1C, 0x0C15}, // h_sync
			{0x20, 0x0C150C15}, // leap
			{0x24, 0x006C02EC}, // h_start
			{0x28, 0x002501FF}, // v_start
			{0x2C, 0x000E0204}, // v_burst
			{0x30, 0x200}, // x_scale: 320/640
			{0x34, 0x400}, // y_scale: 240/240
			{0x00, 0x00003203}, // control: 32 bits, remuestreo y avance de píxel estándar (al final, ya configurado)
	};

	private RomDePruebaN64() {
	}

	public static byte[] azul() {
		return deColor(COLOR);
	}

	/** ROM de 1 MB en formato .z64 (big endian) que pinta la pantalla de ese color (RGBA8888). */
	public static byte[] deColor(int color) {
		Asm a = new Asm();
		// Cuadro: 320 x 240 píxeles de 32 bits desde 0xA0100000 (sin caché)
		a.lui(T0, 0xA010);
		a.lui(T2, color >>> 16);
		a.ori(T2, T2, color & 0xFFFF);
		a.lui(T1, (320 * 240) >>> 16);
		a.ori(T1, T1, (320 * 240) & 0xFFFF);
		a.etiqueta("pintar");
		a.sw(T2, 0, T0);
		a.addiu(T0, T0, 4);
		a.addiu(T1, T1, -1);
		a.bne(T1, ZERO, "pintar");
		a.nop();
		// Sonido: 2048 cuadros estéreo de 16 bits en 0xA0200000, onda cuadrada de período 64 (500 Hz a 32 kHz)
		a.lui(T0, 0xA020);
		a.ori(T1, ZERO, 2048);
		a.etiqueta("onda");
		a.andi(T3, T1, 0x20);
		a.lui(T2, 0x3000);
		a.bne(T3, ZERO, "escribir");
		a.ori(T2, T2, 0x3000);
		a.lui(T2, 0xD000);
		a.ori(T2, T2, 0xD000);
		a.etiqueta("escribir");
		a.sw(T2, 0, T0);
		a.addiu(T0, T0, 4);
		a.addiu(T1, T1, -1);
		a.bne(T1, ZERO, "onda");
		a.nop();
		// VI
		a.lui(T0, 0xA440);
		for (int[] registro : VI) {
			a.lui(T1, registro[1] >>> 16);
			a.ori(T1, T1, registro[1] & 0xFFFF);
			a.sw(T1, registro[0], T0);
		}
		// AI a 32 kHz: dacrate = reloj de la VI (48,681812 MHz) / 32000 - 1
		a.lui(T0, 0xA450);
		a.ori(T1, ZERO, 48681812 / 32000 - 1);
		a.sw(T1, 0x10, T0);
		a.ori(T1, ZERO, 15);
		a.sw(T1, 0x14, T0);
		a.ori(T1, ZERO, 1);
		a.sw(T1, 0x08, T0);
		// Bucle: cada vez que la cola del AI tiene lugar, se vuelve a mandar el mismo tono
		a.etiqueta("bucle");
		a.lw(T1, 0x0C, T0);
		a.nop();
		a.bltz(T1, "bucle");
		a.nop();
		a.lui(T2, 0x0020);
		a.sw(T2, 0x00, T0);
		a.ori(T2, ZERO, 2048 * 4);
		a.sw(T2, 0x04, T0);
		a.beq(ZERO, ZERO, "bucle");
		a.nop();

		byte[] codigo = a.terminar();
		if (codigo.length > 0x1000 - 0x40) {
			throw new IllegalStateException("El programa no entra en el código de arranque");
		}
		byte[] rom = new byte[1024 * 1024];
		escribir(rom, 0x00, 0x80371240); // configuración del PI; también marca el orden de bytes .z64
		escribir(rom, 0x04, 0x0000000F); // reloj
		escribir(rom, 0x08, 0x80000400); // punto de entrada (no se usa: el código de arranque es este programa)
		byte[] nombre = "TELEVISOR PRUEBA    ".getBytes(StandardCharsets.US_ASCII);
		System.arraycopy(nombre, 0, rom, 0x20, nombre.length);
		rom[0x3B] = 'N';
		rom[0x3C] = 'T';
		rom[0x3D] = 'V';
		rom[0x3E] = 'E';
		System.arraycopy(codigo, 0, rom, 0x40, codigo.length);
		return rom;
	}

	private static void escribir(byte[] rom, int offset, int valor) {
		rom[offset] = (byte) (valor >>> 24);
		rom[offset + 1] = (byte) (valor >>> 16);
		rom[offset + 2] = (byte) (valor >>> 8);
		rom[offset + 3] = (byte) valor;
	}

	// Ensamblador MIPS mínimo: solo las instrucciones que usa el programa
	private static final class Asm {
		private final ByteArrayOutputStream salida = new ByteArrayOutputStream();
		private final Map<String, Integer> etiquetas = new HashMap<>();
		private final Map<Integer, String> saltos = new HashMap<>();
		private int[] palabras = new int[0];
		private int cantidad;

		void etiqueta(String nombre) {
			etiquetas.put(nombre, cantidad);
		}

		private void palabra(int p) {
			if (cantidad == palabras.length) {
				palabras = java.util.Arrays.copyOf(palabras, Math.max(64, cantidad * 2));
			}
			palabras[cantidad++] = p;
		}

		private void tipoI(int op, int rs, int rt, int inmediato) {
			palabra(op << 26 | rs << 21 | rt << 16 | (inmediato & 0xFFFF));
		}

		void lui(int rt, int valor) {
			tipoI(0x0F, 0, rt, valor);
		}

		void ori(int rt, int rs, int valor) {
			tipoI(0x0D, rs, rt, valor);
		}

		void andi(int rt, int rs, int valor) {
			tipoI(0x0C, rs, rt, valor);
		}

		void addiu(int rt, int rs, int valor) {
			tipoI(0x09, rs, rt, valor);
		}

		void sw(int rt, int offset, int base) {
			tipoI(0x2B, base, rt, offset);
		}

		void lw(int rt, int offset, int base) {
			tipoI(0x23, base, rt, offset);
		}

		void beq(int rs, int rt, String destino) {
			saltos.put(cantidad, destino);
			tipoI(0x04, rs, rt, 0);
		}

		void bne(int rs, int rt, String destino) {
			saltos.put(cantidad, destino);
			tipoI(0x05, rs, rt, 0);
		}

		void bltz(int rs, String destino) {
			saltos.put(cantidad, destino);
			tipoI(0x01, rs, 0, 0);
		}

		void nop() {
			palabra(0);
		}

		byte[] terminar() {
			for (Map.Entry<Integer, String> salto : saltos.entrySet()) {
				int desde = salto.getKey();
				int desplazamiento = etiquetas.get(salto.getValue()) - (desde + 1);
				palabras[desde] |= desplazamiento & 0xFFFF;
			}
			for (int i = 0; i < cantidad; i++) {
				int p = palabras[i];
				salida.write(p >>> 24);
				salida.write(p >>> 16);
				salida.write(p >>> 8);
				salida.write(p);
			}
			return salida.toByteArray();
		}
	}
}
