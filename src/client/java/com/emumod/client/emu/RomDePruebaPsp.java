package com.emumod.client.emu;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Programa de PSP propio, escrito a mano en MIPS, para probar el núcleo sin juegos de terceros: un ejecutable ELF que
 * pinta la pantalla de azul (de rojo mientras se aprieta ✕) con un cuadrado blanco que se mueve. Usa tres funciones del
 * sistema de la PSP, que PPSSPP emula (HLE) sin BIOS: sceDisplaySetFrameBuf, sceDisplayWaitVblankStart y
 * sceCtrlReadBufferPositive. PPSSPP enlaza cada una por su NID: la tabla de importaciones (los «stubs») la arma este
 * archivo y el emulador reemplaza cada stub por la llamada al sistema.
 */
public final class RomDePruebaPsp {
	/** Colores de la prueba en el cuadro que entrega el núcleo (0xRRGGBB). */
	public static final int AZUL = 0x2050E0, ROJO = 0xE02020;

	private static final int ZERO = 0, A0 = 4, A1 = 5, A2 = 6, A3 = 7, T0 = 8, T1 = 9, T2 = 10, T3 = 11, T4 = 12,
			T5 = 13, T6 = 14, T7 = 15, S0 = 16, S1 = 17, T8 = 24, T9 = 25;
	// Donde se carga el programa: el comienzo de la memoria de usuario que usan los ejecutables no reubicables
	private static final int BASE = 0x08804000;
	private static final int BOTON_CRUZ = 0x4000;

	private RomDePruebaPsp() {
	}

	public static byte[] elf() {
		// ---- Código ----
		Asm a = new Asm();
		a.lui(S0, 0x4400); // VRAM sin caché
		a.ori(S1, ZERO, 0); // cuadros
		a.etiqueta("bucle");
		// sceCtrlReadBufferPositive(&datos, 1): los botones quedan en datos + 4
		a.la(A0, "datos");
		a.ori(A1, ZERO, 1);
		a.jal("sceCtrlReadBufferPositive");
		a.nop();
		a.la(T0, "datos");
		a.lw(T1, 4, T0);
		// Color en ABGR: azul, o rojo con ✕
		a.li(T2, 0xFF000000 | rgbAAbgr(AZUL));
		a.andi(T3, T1, BOTON_CRUZ);
		a.beq(T3, ZERO, "pintar");
		a.nop();
		a.li(T2, 0xFF000000 | rgbAAbgr(ROJO));
		a.etiqueta("pintar");
		// Fondo: 512 (ancho de la memoria) x 272 palabras
		a.addu(T4, S0, ZERO);
		a.li(T5, 512 * 272);
		a.etiqueta("fondo");
		a.sw(T2, 0, T4);
		a.addiu(T4, T4, 4);
		a.addiu(T5, T5, -1);
		a.bne(T5, ZERO, "fondo");
		a.nop();
		// Cuadrado blanco de 32x32 en (100 + cuadro % 256, 100)
		a.andi(T6, S1, 0xFF);
		a.addiu(T6, T6, 100);
		a.sll(T6, T6, 2);
		a.li(T7, 100 * 512 * 4);
		a.addu(T7, T7, S0);
		a.addu(T7, T7, T6);
		a.ori(T8, ZERO, 32);
		a.addiu(T9, ZERO, -1);
		a.etiqueta("fila");
		a.addu(A0, T7, ZERO);
		a.ori(A1, ZERO, 32);
		a.etiqueta("columna");
		a.sw(T9, 0, A0);
		a.addiu(A0, A0, 4);
		a.addiu(A1, A1, -1);
		a.bne(A1, ZERO, "columna");
		a.nop();
		a.addiu(T7, T7, 512 * 4);
		a.addiu(T8, T8, -1);
		a.bne(T8, ZERO, "fila");
		a.nop();
		// sceDisplaySetFrameBuf(VRAM, 512, 32 bits, en el próximo cuadro) y esperar el vblank
		a.lui(A0, 0x0400);
		a.ori(A1, ZERO, 512);
		a.ori(A2, ZERO, 3);
		a.ori(A3, ZERO, 1);
		a.jal("sceDisplaySetFrameBuf");
		a.nop();
		a.jal("sceDisplayWaitVblankStart");
		a.nop();
		a.addiu(S1, S1, 1);
		a.beq(ZERO, ZERO, "bucle");
		a.nop();

		// ---- Stubs: 8 bytes cada uno, PPSSPP los reemplaza por la llamada al sistema ----
		a.alinear(16);
		a.etiqueta("stubsDisplay");
		a.etiqueta("sceDisplaySetFrameBuf");
		a.jr(31);
		a.nop();
		a.etiqueta("sceDisplayWaitVblankStart");
		a.jr(31);
		a.nop();
		a.etiqueta("stubsCtrl");
		a.etiqueta("sceCtrlReadBufferPositive");
		a.jr(31);
		a.nop();

		// ---- Datos: NIDs, nombres de las bibliotecas, tabla de importaciones e información del módulo ----
		a.etiqueta("nidsDisplay");
		a.palabra(0x289D82FE); // sceDisplaySetFrameBuf
		a.palabra(0x984C27E7); // sceDisplayWaitVblankStart
		a.etiqueta("nidsCtrl");
		a.palabra(0x1F803938); // sceCtrlReadBufferPositive
		a.etiqueta("nombreDisplay");
		a.texto("sceDisplay", 12);
		a.etiqueta("nombreCtrl");
		a.texto("sceCtrl", 8);
		a.etiqueta("datos");
		a.ceros(16);
		a.alinear(16);
		a.etiqueta("importaciones");
		importacion(a, "nombreDisplay", 2, "nidsDisplay", "stubsDisplay");
		importacion(a, "nombreCtrl", 1, "nidsCtrl", "stubsCtrl");
		a.etiqueta("finImportaciones");
		a.alinear(16);
		a.etiqueta("modulo");
		a.media(0); // atributos: modo usuario
		a.media(0x0101); // versión
		a.texto("EmuMod prueba", 28);
		a.palabra(0); // gp
		a.palabra(0); // exportaciones
		a.palabra(0);
		a.direccion("importaciones");
		a.direccion("finImportaciones");
		a.etiqueta("fin");

		byte[] imagen = a.terminar();
		int modulo = a.posicion("modulo");
		return armarElf(imagen, modulo);
	}

	// Entrada de la tabla de importaciones (20 bytes): biblioteca, versión, banderas, tamaño en palabras, variables,
	// funciones, NIDs y stubs
	private static void importacion(Asm a, String nombre, int funciones, String nids, String stubs) {
		a.direccion(nombre);
		a.media(0x0011);
		a.media(0x4001);
		a.byteSuelto(5);
		a.byteSuelto(0);
		a.media(funciones);
		a.direccion(nids);
		a.direccion(stubs);
	}

	private static int rgbAAbgr(int rgb) {
		return (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16) & 0xFF;
	}

	// ELF de 32 bits, MIPS little endian, no reubicable: un solo segmento con todo. PPSSPP busca la información del
	// módulo en la sección .rodata.sceModuleInfo
	private static byte[] armarElf(byte[] imagen, int offsetModulo) {
		byte[] nombres = "\0.text\0.rodata.sceModuleInfo\0.shstrtab\0".getBytes(StandardCharsets.US_ASCII);
		int inicioDatos = 0x100;
		int inicioNombres = inicioDatos + imagen.length;
		int inicioSecciones = (inicioNombres + nombres.length + 3) & ~3;
		ByteBuffer b = ByteBuffer.allocate(inicioSecciones + 4 * 40).order(ByteOrder.LITTLE_ENDIAN);
		b.put(new byte[] {0x7F, 'E', 'L', 'F', 1, 1, 1, 0});
		b.position(16);
		b.putShort((short) 2); // ET_EXEC
		b.putShort((short) 8); // EM_MIPS
		b.putInt(1);
		b.putInt(BASE); // entrada: el comienzo del código
		b.putInt(52); // tabla de segmentos
		b.putInt(inicioSecciones);
		b.putInt(0x10A23001); // banderas de MIPS de la Allegrex (las de los ejecutables de la PSP)
		b.putShort((short) 52);
		b.putShort((short) 32);
		b.putShort((short) 1);
		b.putShort((short) 40);
		b.putShort((short) 4);
		b.putShort((short) 3); // índice de .shstrtab
		// Segmento: todo cargado en BASE, lectura, escritura y ejecución
		b.putInt(1);
		b.putInt(inicioDatos);
		b.putInt(BASE);
		b.putInt(BASE);
		b.putInt(imagen.length);
		b.putInt(imagen.length);
		b.putInt(7);
		b.putInt(16);
		b.position(inicioDatos);
		b.put(imagen);
		b.put(nombres);
		b.position(inicioSecciones);
		b.put(new byte[40]);
		seccion(b, 1, 1, 6, BASE, inicioDatos, offsetModulo); // .text
		seccion(b, 7, 1, 2, BASE + offsetModulo, inicioDatos + offsetModulo, 52); // .rodata.sceModuleInfo
		seccion(b, 29, 3, 0, 0, inicioNombres, nombres.length); // .shstrtab
		return b.array();
	}

	private static void seccion(ByteBuffer b, int nombre, int tipo, int banderas, int direccion, int offset, int tamanio) {
		b.putInt(nombre);
		b.putInt(tipo);
		b.putInt(banderas);
		b.putInt(direccion);
		b.putInt(offset);
		b.putInt(tamanio);
		b.putInt(0);
		b.putInt(0);
		b.putInt(4);
		b.putInt(0);
	}

	// Ensamblador MIPS mínimo (little endian) con etiquetas y datos
	private static final class Asm {
		private ByteBuffer salida = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN);
		private final Map<String, Integer> etiquetas = new HashMap<>();
		// Lugares a completar con una etiqueta: posición -> (tipo, etiqueta)
		private final Map<Integer, String[]> pendientes = new HashMap<>();

		void etiqueta(String nombre) {
			etiquetas.put(nombre, salida.position());
		}

		int posicion(String nombre) {
			return etiquetas.get(nombre);
		}

		void palabra(int p) {
			salida.putInt(p);
		}

		void media(int m) {
			salida.putShort((short) m);
		}

		void byteSuelto(int v) {
			salida.put((byte) v);
		}

		void ceros(int n) {
			salida.put(new byte[n]);
		}

		void alinear(int n) {
			while (salida.position() % n != 0) {
				salida.put((byte) 0);
			}
		}

		void texto(String s, int largo) {
			byte[] t = s.getBytes(StandardCharsets.US_ASCII);
			salida.put(t);
			salida.put(new byte[largo - t.length]);
		}

		void direccion(String etiqueta) {
			pendientes.put(salida.position(), new String[] {"dir", etiqueta});
			salida.putInt(0);
		}

		private void tipoI(int op, int rs, int rt, int inmediato) {
			palabra(op << 26 | rs << 21 | rt << 16 | (inmediato & 0xFFFF));
		}

		private void tipoR(int rs, int rt, int rd, int sa, int funcion) {
			palabra(rs << 21 | rt << 16 | rd << 11 | sa << 6 | funcion);
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

		void lw(int rt, int offset, int base) {
			tipoI(0x23, base, rt, offset);
		}

		void sw(int rt, int offset, int base) {
			tipoI(0x2B, base, rt, offset);
		}

		void addu(int rd, int rs, int rt) {
			tipoR(rs, rt, rd, 0, 0x21);
		}

		void sll(int rd, int rt, int sa) {
			tipoR(0, rt, rd, sa, 0x00);
		}

		void jr(int rs) {
			tipoR(rs, 0, 0, 0, 0x08);
		}

		void nop() {
			palabra(0);
		}

		/** Carga una constante de 32 bits (lui + ori). */
		void li(int rt, int valor) {
			lui(rt, valor >>> 16);
			ori(rt, rt, valor & 0xFFFF);
		}

		/** Carga la dirección de una etiqueta (lui + ori, se completa al terminar). */
		void la(int rt, String etiqueta) {
			pendientes.put(salida.position(), new String[] {"la", etiqueta});
			lui(rt, 0);
			ori(rt, rt, 0);
		}

		void beq(int rs, int rt, String etiqueta) {
			pendientes.put(salida.position(), new String[] {"rama", etiqueta});
			tipoI(0x04, rs, rt, 0);
		}

		void bne(int rs, int rt, String etiqueta) {
			pendientes.put(salida.position(), new String[] {"rama", etiqueta});
			tipoI(0x05, rs, rt, 0);
		}

		void jal(String etiqueta) {
			pendientes.put(salida.position(), new String[] {"jal", etiqueta});
			palabra(0x03 << 26);
		}

		byte[] terminar() {
			for (Map.Entry<Integer, String[]> p : pendientes.entrySet()) {
				int pos = p.getKey();
				Integer destino = etiquetas.get(p.getValue()[1]);
				if (destino == null) {
					throw new IllegalStateException("Falta la etiqueta " + p.getValue()[1]);
				}
				int dir = BASE + destino;
				int instruccion = salida.getInt(pos);
				switch (p.getValue()[0]) {
					case "dir" -> salida.putInt(pos, dir);
					case "jal" -> salida.putInt(pos, instruccion | (dir >>> 2) & 0x03FFFFFF);
					case "rama" -> salida.putInt(pos, instruccion | ((destino - pos - 4) >> 2) & 0xFFFF);
					case "la" -> {
						salida.putInt(pos, instruccion | dir >>> 16);
						salida.putInt(pos + 4, salida.getInt(pos + 4) | dir & 0xFFFF);
					}
					default -> throw new IllegalStateException();
				}
			}
			byte[] r = new byte[salida.position()];
			salida.get(0, r);
			return r;
		}
	}
}
