// Programa de prueba de Nintendo DS (homebrew propio, sin nada de Nintendo) para probar el núcleo melonDS DS sin juegos
// de terceros: la pantalla de arriba se pinta de un color según el botón apretado (nada: azul, A: rojo, B: verde, X:
// amarillo, Y: magenta, otro: blanco) y la de abajo es gris oscuro con un cuadrado blanco donde se toca. Igual que el
// de la 3DS (native/prueba3ds).
#include <nds.h>

static void pintar(u16 *fb, int x0, int y0, int x1, int y1, u16 color) {
	for (int y = y0; y < y1; y++) {
		if (y < 0 || y >= 192) continue;
		for (int x = x0; x < x1; x++) {
			if (x < 0 || x >= 256) continue;
			fb[y * 256 + x] = color;
		}
	}
}

int main(void) {
	// Las dos pantallas en modo mapa de bits de 16 bits
	videoSetMode(MODE_5_2D);
	videoSetModeSub(MODE_5_2D);
	vramSetBankA(VRAM_A_MAIN_BG);
	vramSetBankC(VRAM_C_SUB_BG);
	int fondo = bgInit(3, BgType_Bmp16, BgSize_B16_256x256, 0, 0);
	int fondoSub = bgInitSub(3, BgType_Bmp16, BgSize_B16_256x256, 0, 0);
	u16 *arriba = bgGetGfxPtr(fondo), *abajo = bgGetGfxPtr(fondoSub);
	while (pmMainLoop()) {
		swiWaitForVBlank();
		scanKeys();
		u32 teclas = keysHeld();
		u16 color = RGB15(0, 10, 25);
		if (teclas & KEY_A) color = RGB15(27, 4, 4);
		else if (teclas & KEY_B) color = RGB15(4, 25, 7);
		else if (teclas & KEY_X) color = RGB15(30, 27, 4);
		else if (teclas & KEY_Y) color = RGB15(27, 5, 27);
		else if (teclas & ~KEY_TOUCH) color = RGB15(31, 31, 31);
		color |= BIT(15);
		pintar(arriba, 0, 0, 256, 192, color);
		// Franja blanca arriba a la izquierda para saber que la imagen no está dada vuelta
		pintar(arriba, 0, 0, 64, 16, RGB15(31, 31, 31) | BIT(15));
		pintar(abajo, 0, 0, 256, 192, RGB15(5, 5, 5) | BIT(15));
		if (teclas & KEY_TOUCH) {
			touchPosition t;
			touchRead(&t);
			pintar(abajo, t.px - 8, t.py - 8, t.px + 8, t.py + 8, RGB15(31, 31, 31) | BIT(15));
		}
	}
	return 0;
}
