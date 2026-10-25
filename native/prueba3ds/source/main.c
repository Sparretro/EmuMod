// Programa de prueba de 3DS (homebrew propio, sin nada de Nintendo) para probar el núcleo de Citra sin juegos de
// terceros: la pantalla de arriba se pinta de un color según el botón apretado (nada: azul, A: rojo, B: verde, X:
// amarillo, Y: magenta, otro: blanco) y la de abajo es gris oscuro con un cuadrado blanco donde se toca.
#include <3ds.h>
#include <string.h>

// El framebuffer de la 3DS está girado: columnas de 240 píxeles BGR, de abajo hacia arriba
static void pintar(u8 *fb, int ancho, int x0, int y0, int x1, int y1, u8 r, u8 g, u8 b) {
	for (int x = x0; x < x1; x++) {
		if (x < 0 || x >= ancho) continue;
		for (int y = y0; y < y1; y++) {
			if (y < 0 || y >= 240) continue;
			u8 *p = fb + (x * 240 + (239 - y)) * 3;
			p[0] = b;
			p[1] = g;
			p[2] = r;
		}
	}
}

int main(void) {
	gfxInitDefault();
	while (aptMainLoop()) {
		hidScanInput();
		u32 teclas = hidKeysHeld();
		u8 r = 0, g = 80, b = 200;
		if (teclas & KEY_A) { r = 220; g = 30; b = 30; }
		else if (teclas & KEY_B) { r = 30; g = 200; b = 60; }
		else if (teclas & KEY_X) { r = 240; g = 220; b = 30; }
		else if (teclas & KEY_Y) { r = 220; g = 40; b = 220; }
		else if (teclas & ~KEY_TOUCH) { r = 255; g = 255; b = 255; }
		u16 w, h;
		u8 *arriba = gfxGetFramebuffer(GFX_TOP, GFX_LEFT, &w, &h);
		pintar(arriba, 400, 0, 0, 400, 240, r, g, b);
		// Una franja blanca arriba a la izquierda para saber que la imagen no está dada vuelta
		pintar(arriba, 400, 0, 0, 100, 20, 255, 255, 255);
		u8 *abajo = gfxGetFramebuffer(GFX_BOTTOM, GFX_LEFT, &w, &h);
		pintar(abajo, 320, 0, 0, 320, 240, 40, 40, 40);
		if (teclas & KEY_TOUCH) {
			touchPosition t;
			hidTouchRead(&t);
			pintar(abajo, 320, t.px - 10, t.py - 10, t.px + 10, t.py + 10, 255, 255, 255);
		}
		gfxFlushBuffers();
		gfxSwapBuffers();
		gspWaitForVBlank();
	}
	gfxExit();
	return 0;
}
