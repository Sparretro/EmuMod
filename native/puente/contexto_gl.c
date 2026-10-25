// Contexto OpenGL propio del puente, para núcleos que solo dibujan con la placa de video (Dolphin, para la Wii).
//
// Minecraft no le puede prestar su contexto al núcleo (lo usa él, en otro hilo), así que el puente crea uno aparte en
// una ventana oculta, en el hilo que maneja el núcleo. El núcleo dibuja en un framebuffer del puente y, en cada cuadro,
// el puente lo lee de vuelta a memoria (glReadPixels) y se lo pasa a Java como un cuadro común XRGB8888.

#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <windows.h>

#include "contexto_gl.h"

typedef unsigned int GLenum;
typedef unsigned int GLuint;
typedef int GLint;
typedef int GLsizei;
typedef void GLvoid;

#define GL_TEXTURE_2D 0x0DE1
#define GL_RGBA8 0x8058
#define GL_RGBA 0x1908
#define GL_BGRA 0x80E1
#define GL_UNSIGNED_BYTE 0x1401
#define GL_FRAMEBUFFER 0x8D40
#define GL_READ_FRAMEBUFFER 0x8CA8
#define GL_DRAW_FRAMEBUFFER 0x8CA9
#define GL_READ_FRAMEBUFFER_BINDING 0x8CAA
#define GL_DRAW_FRAMEBUFFER_BINDING 0x8CA6
#define GL_RENDERBUFFER 0x8D41
#define GL_COLOR_ATTACHMENT0 0x8CE0
#define GL_DEPTH_STENCIL_ATTACHMENT 0x821A
#define GL_DEPTH24_STENCIL8 0x88F0
#define GL_FRAMEBUFFER_COMPLETE 0x8CD5
#define GL_PACK_ALIGNMENT 0x0D05
#define GL_PACK_ROW_LENGTH 0x0D02
#define GL_PIXEL_PACK_BUFFER 0x88EB
#define GL_PIXEL_PACK_BUFFER_BINDING 0x88ED

#define WGL_CONTEXT_MAJOR_VERSION_ARB 0x2091
#define WGL_CONTEXT_MINOR_VERSION_ARB 0x2092
#define WGL_CONTEXT_PROFILE_MASK_ARB 0x9126
#define WGL_CONTEXT_CORE_PROFILE_BIT_ARB 0x00000001
#define WGL_CONTEXT_COMPATIBILITY_PROFILE_BIT_ARB 0x00000002

typedef HGLRC(WINAPI *crear_contexto_t)(HDC, HGLRC, const int *);

static void(APIENTRY *p_glGenFramebuffers)(GLsizei, GLuint *);
static void(APIENTRY *p_glDeleteFramebuffers)(GLsizei, const GLuint *);
static void(APIENTRY *p_glBindFramebuffer)(GLenum, GLuint);
static void(APIENTRY *p_glFramebufferTexture2D)(GLenum, GLenum, GLenum, GLuint, GLint);
static void(APIENTRY *p_glFramebufferRenderbuffer)(GLenum, GLenum, GLenum, GLuint);
static GLenum(APIENTRY *p_glCheckFramebufferStatus)(GLenum);
static void(APIENTRY *p_glGenRenderbuffers)(GLsizei, GLuint *);
static void(APIENTRY *p_glDeleteRenderbuffers)(GLsizei, const GLuint *);
static void(APIENTRY *p_glBindRenderbuffer)(GLenum, GLuint);
static void(APIENTRY *p_glRenderbufferStorage)(GLenum, GLenum, GLsizei, GLsizei);
static void(APIENTRY *p_glBindBuffer)(GLenum, GLuint);
// Las de OpenGL 1.1 salen de opengl32.dll directamente
static void(APIENTRY *p_glGenTextures)(GLsizei, GLuint *);
static void(APIENTRY *p_glDeleteTextures)(GLsizei, const GLuint *);
static void(APIENTRY *p_glBindTexture)(GLenum, GLuint);
static void(APIENTRY *p_glTexImage2D)(GLenum, GLint, GLint, GLsizei, GLsizei, GLint, GLenum, GLenum, const GLvoid *);
static void(APIENTRY *p_glReadPixels)(GLint, GLint, GLsizei, GLsizei, GLenum, GLenum, GLvoid *);
static void(APIENTRY *p_glPixelStorei)(GLenum, GLint);
static void(APIENTRY *p_glGetIntegerv)(GLenum, GLint *);

static HMODULE opengl32;
static HWND ventana;
static HDC dc;
static HGLRC contexto;
static GLuint fbo, color, profundidad;
static unsigned fbo_ancho, fbo_alto;
/* Último paso que falló, para el registro */
const char *gl_error = "";
static uint8_t *lectura;
static size_t lectura_capacidad;

void *gl_funcion(const char *nombre) {
	void *f = (void *)wglGetProcAddress(nombre);
	// wglGetProcAddress devuelve 0, 1, 2, 3 o -1 para lo que no tiene (lo de OpenGL 1.1 está en opengl32.dll)
	if (f == NULL || f == (void *)1 || f == (void *)2 || f == (void *)3 || f == (void *)-1) {
		f = (void *)GetProcAddress(opengl32, nombre);
	}
	return f;
}

#define CARGAR_GL(n) (*(void **)&p_##n = gl_funcion(#n)) != NULL

bool gl_crear(unsigned version_mayor, unsigned version_menor, bool nucleo) {
	if (contexto) {
		return wglMakeCurrent(dc, contexto);
	}
	opengl32 = LoadLibraryA("opengl32.dll");
	if (!opengl32) {
		gl_error = "opengl32.dll";
		return false;
	}
	WNDCLASSA clase = {0};
	clase.style = CS_OWNDC;
	clase.lpfnWndProc = DefWindowProcA;
	clase.hInstance = GetModuleHandleA(NULL);
	clase.lpszClassName = "EmuModContextoGL";
	RegisterClassA(&clase);
	// Nunca se muestra: solo hace falta para tener un contexto de OpenGL
	ventana = CreateWindowExA(0, clase.lpszClassName, "", WS_POPUP, 0, 0, 16, 16, NULL, NULL, clase.hInstance, NULL);
	if (!ventana) {
		gl_error = "ventana";
		return false;
	}
	dc = GetDC(ventana);
	PIXELFORMATDESCRIPTOR pfd = {0};
	pfd.nSize = sizeof pfd;
	pfd.nVersion = 1;
	pfd.dwFlags = PFD_DRAW_TO_WINDOW | PFD_SUPPORT_OPENGL | PFD_DOUBLEBUFFER;
	pfd.iPixelType = PFD_TYPE_RGBA;
	pfd.cColorBits = 32;
	pfd.cDepthBits = 24;
	pfd.cStencilBits = 8;
	int formato = ChoosePixelFormat(dc, &pfd);
	if (!formato || !SetPixelFormat(dc, formato, &pfd)) {
		gl_error = "formato de píxel";
		return false;
	}
	HGLRC temporal = wglCreateContext(dc);
	if (!temporal || !wglMakeCurrent(dc, temporal)) {
		gl_error = "contexto temporal";
		return false;
	}
	crear_contexto_t crear = (crear_contexto_t)(void *)wglGetProcAddress("wglCreateContextAttribsARB");
	if (crear) {
		// La versión más nueva que haya, sin bajar de la que pidió el núcleo
		static const int versiones[][2] = {{4, 6}, {4, 5}, {4, 4}, {4, 3}, {4, 2}, {4, 1}, {4, 0}, {3, 3}, {3, 2}};
		for (size_t i = 0; i < sizeof versiones / sizeof versiones[0] && !contexto; i++) {
			if (versiones[i][0] < (int)version_mayor || (versiones[i][0] == (int)version_mayor && versiones[i][1] < (int)version_menor)) {
				break;
			}
			int atributos[] = {WGL_CONTEXT_MAJOR_VERSION_ARB, versiones[i][0], WGL_CONTEXT_MINOR_VERSION_ARB, versiones[i][1],
					WGL_CONTEXT_PROFILE_MASK_ARB, nucleo ? WGL_CONTEXT_CORE_PROFILE_BIT_ARB : WGL_CONTEXT_COMPATIBILITY_PROFILE_BIT_ARB, 0};
			contexto = crear(dc, NULL, atributos);
		}
	}
	if (contexto) {
		wglMakeCurrent(dc, contexto);
		wglDeleteContext(temporal);
	} else if (!nucleo) {
		contexto = temporal;
	} else {
		wglMakeCurrent(NULL, NULL);
		gl_error = "contexto de la versión pedida";
		wglDeleteContext(temporal);
		return false;
	}
	bool ok = CARGAR_GL(glGenFramebuffers) && CARGAR_GL(glDeleteFramebuffers) && CARGAR_GL(glBindFramebuffer)
			&& CARGAR_GL(glFramebufferTexture2D) && CARGAR_GL(glFramebufferRenderbuffer) && CARGAR_GL(glCheckFramebufferStatus)
			&& CARGAR_GL(glGenRenderbuffers) && CARGAR_GL(glDeleteRenderbuffers) && CARGAR_GL(glBindRenderbuffer)
			&& CARGAR_GL(glRenderbufferStorage) && CARGAR_GL(glBindBuffer) && CARGAR_GL(glGenTextures) && CARGAR_GL(glDeleteTextures)
			&& CARGAR_GL(glBindTexture) && CARGAR_GL(glTexImage2D) && CARGAR_GL(glReadPixels) && CARGAR_GL(glPixelStorei)
			&& CARGAR_GL(glGetIntegerv);
	if (!ok) {
		gl_error = "funciones de OpenGL";
		gl_destruir();
	}
	return ok;
}

// Como RetroArch, el framebuffer lleva profundidad y stencil solo si el núcleo los pide: Citra no los pide, deja la
// prueba de profundidad prendida y nunca limpia la profundidad, así que con un búfer de profundidad no dibuja nada
bool gl_tamanio(unsigned ancho, unsigned alto, bool con_profundidad) {
	if (!contexto) {
		gl_error = "sin contexto";
	}
	if (!contexto || (ancho <= fbo_ancho && alto <= fbo_alto && fbo)) {
		return contexto != NULL;
	}
	if (ancho < fbo_ancho) ancho = fbo_ancho;
	if (alto < fbo_alto) alto = fbo_alto;
	if (fbo) {
		p_glDeleteFramebuffers(1, &fbo);
		p_glDeleteTextures(1, &color);
		if (profundidad) {
			p_glDeleteRenderbuffers(1, &profundidad);
			profundidad = 0;
		}
	}
	GLint antes_lectura = 0, antes_dibujo = 0;
	p_glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING, &antes_lectura);
	p_glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &antes_dibujo);
	p_glGenTextures(1, &color);
	p_glBindTexture(GL_TEXTURE_2D, color);
	p_glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, (GLsizei)ancho, (GLsizei)alto, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
	p_glBindTexture(GL_TEXTURE_2D, 0);
	if (con_profundidad) {
		p_glGenRenderbuffers(1, &profundidad);
		p_glBindRenderbuffer(GL_RENDERBUFFER, profundidad);
		p_glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, (GLsizei)ancho, (GLsizei)alto);
		p_glBindRenderbuffer(GL_RENDERBUFFER, 0);
	}
	p_glGenFramebuffers(1, &fbo);
	p_glBindFramebuffer(GL_FRAMEBUFFER, fbo);
	p_glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0);
	if (con_profundidad) {
		p_glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, profundidad);
	}
	GLenum estado = p_glCheckFramebufferStatus(GL_FRAMEBUFFER);
	bool ok = estado == GL_FRAMEBUFFER_COMPLETE;
	if (!ok) {
		static char texto[64];
		snprintf(texto, sizeof texto, "framebuffer incompleto 0x%X", estado);
		gl_error = texto;
	}
	p_glBindFramebuffer(GL_READ_FRAMEBUFFER, (GLuint)antes_lectura);
	p_glBindFramebuffer(GL_DRAW_FRAMEBUFFER, (GLuint)antes_dibujo);
	fbo_ancho = ancho;
	fbo_alto = alto;
	return ok;
}

uintptr_t gl_framebuffer(void) {
	return fbo;
}

bool gl_activo(void) {
	return contexto != NULL;
}

const uint8_t *gl_leer(unsigned ancho, unsigned alto, bool invertir, size_t *pitch) {
	if (!fbo || ancho == 0 || alto == 0) {
		return NULL;
	}
	if (ancho > fbo_ancho) ancho = fbo_ancho;
	if (alto > fbo_alto) alto = fbo_alto;
	size_t fila = (size_t)ancho * 4, tamanio = fila * alto;
	if (lectura_capacidad < tamanio * 2) {
		free(lectura);
		lectura = malloc(tamanio * 2);
		lectura_capacidad = lectura ? tamanio * 2 : 0;
		if (!lectura) {
			return NULL;
		}
	}
	GLint antes = 0, buffer = 0;
	p_glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING, &antes);
	p_glGetIntegerv(GL_PIXEL_PACK_BUFFER_BINDING, &buffer);
	p_glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
	p_glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
	p_glPixelStorei(GL_PACK_ALIGNMENT, 4);
	p_glPixelStorei(GL_PACK_ROW_LENGTH, 0);
	// BGRA en bytes es el XRGB8888 de libretro en memoria (little endian)
	p_glReadPixels(0, 0, (GLsizei)ancho, (GLsizei)alto, GL_BGRA, GL_UNSIGNED_BYTE, lectura);
	p_glBindFramebuffer(GL_READ_FRAMEBUFFER, (GLuint)antes);
	p_glBindBuffer(GL_PIXEL_PACK_BUFFER, (GLuint)buffer);
	*pitch = fila;
	if (!invertir) {
		return lectura;
	}
	// OpenGL guarda la primera fila abajo: se da vuelta en la segunda mitad del búfer
	uint8_t *derecho = lectura + tamanio;
	for (unsigned y = 0; y < alto; y++) {
		memcpy(derecho + (size_t)y * fila, lectura + (size_t)(alto - 1 - y) * fila, fila);
	}
	return derecho;
}

void gl_destruir(void) {
	if (contexto) {
		if (fbo && p_glDeleteFramebuffers) {
			p_glDeleteFramebuffers(1, &fbo);
			p_glDeleteTextures(1, &color);
			p_glDeleteRenderbuffers(1, &profundidad);
		}
		wglMakeCurrent(NULL, NULL);
		wglDeleteContext(contexto);
		contexto = NULL;
	}
	fbo = color = profundidad = 0;
	fbo_ancho = fbo_alto = 0;
	if (ventana) {
		ReleaseDC(ventana, dc);
		DestroyWindow(ventana);
		ventana = NULL;
	}
}
