// Contexto OpenGL propio del puente (ver contexto_gl.c)
#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/** Crea el contexto en una ventana oculta y lo deja activo en este hilo (o lo reactiva si ya existe). */
bool gl_crear(unsigned version_mayor, unsigned version_menor, bool nucleo);
/** Prepara el framebuffer donde dibuja el núcleo: crece hasta ese tamaño si hace falta, nunca achica. */
bool gl_tamanio(unsigned ancho, unsigned alto, bool con_profundidad);
uintptr_t gl_framebuffer(void);
void *gl_funcion(const char *nombre);
bool gl_activo(void);
/** Lee lo dibujado (BGRA, filas de arriba a abajo); el puntero vale hasta la próxima lectura. */
const uint8_t *gl_leer(unsigned ancho, unsigned alto, bool invertir, size_t *pitch);
void gl_destruir(void);
extern const char *gl_error;
