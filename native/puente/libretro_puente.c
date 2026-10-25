// Puente libretro de EmuMod (Spar Retro): una DLL que se hace pasar por un núcleo libretro y le pasa todo al núcleo de verdad.
//
// Para qué: Mupen64Plus-Next corre la emulación en una corrutina (libco) con su propia pila, y desde ahí llama a los
// callbacks del frontend (audio, controles, logs). Si el frontend es Java (JNA), la JVM ve una pila que no es la del
// hilo y se rompe (IllegalMonitorStateException y similares). Este puente atiende esos callbacks en C: si el núcleo
// llama desde la pila del hilo los pasa a Java directamente; si llama desde otra pila guarda lo que hace falta (audio,
// último cuadro, mensajes) y lo entrega a Java cuando el núcleo vuelve, ya en la pila normal. Los controles y las
// opciones se le preguntan a Java antes de cada cuadro.
//
// LRPS2 (PS2) además llama desde sus propios hilos (el del EE, el del audio): todo lo que no llega desde el hilo que llama
// a retro_run, en su propia pila, se guarda igual (con un cerrojo) y se entrega después.
//
// Java le pasa el texto ya formateado como formato (su callback de log no formatea).
//
// Uso desde Java: cargar esta DLL con la misma interfaz libretro de siempre y llamar primero a
// puente_cargar("<ruta al núcleo>.dll").

#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <windows.h>

#include "contexto_gl.h"

#define EXPORTAR __declspec(dllexport)

// ---- Lo justo de libretro.h ----
#define RETRO_ENVIRONMENT_EXPERIMENTAL 0x10000
#define RETRO_ENVIRONMENT_GET_VARIABLE 15
#define RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE 17
#define RETRO_ENVIRONMENT_GET_LOG_INTERFACE 27
#define RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE 47
#define RETRO_DEVICE_JOYPAD 1
#define RETRO_DEVICE_ANALOG 5
#define RETRO_DEVICE_MOUSE 2
#define RETRO_DEVICE_POINTER 6
#define RETRO_DEVICE_ID_JOYPAD_MASK 256

typedef bool (*entorno_t)(unsigned cmd, void *data);
typedef void (*video_t)(const void *data, unsigned ancho, unsigned alto, size_t pitch);
typedef void (*muestra_t)(int16_t izquierda, int16_t derecha);
typedef size_t (*lote_audio_t)(const int16_t *data, size_t cuadros);
typedef void (*sondeo_t)(void);
typedef int16_t (*estado_entrada_t)(unsigned puerto, unsigned dispositivo, unsigned indice, unsigned id);
typedef void (*log_t)(int nivel, const char *formato, ...);

struct variable {
	const char *clave;
	const char *valor;
};

struct interfaz_log {
	log_t log;
};

// ---- El núcleo de verdad ----
static HMODULE nucleo;
static unsigned (*n_api_version)(void);
static void (*n_set_environment)(entorno_t);
static void (*n_set_video_refresh)(video_t);
static void (*n_set_audio_sample)(muestra_t);
static void (*n_set_audio_sample_batch)(lote_audio_t);
static void (*n_set_input_poll)(sondeo_t);
static void (*n_set_input_state)(estado_entrada_t);
static void (*n_init)(void);
static void (*n_deinit)(void);
static void (*n_get_system_info)(void *);
static void (*n_get_system_av_info)(void *);
static void (*n_set_controller_port_device)(unsigned, unsigned);
static void (*n_reset)(void);
static void (*n_run)(void);
static size_t (*n_serialize_size)(void);
static bool (*n_serialize)(void *, size_t);
static bool (*n_unserialize)(const void *, size_t);
static bool (*n_load_game)(const void *);
static void (*n_unload_game)(void);
static void *(*n_get_memory_data)(unsigned);
static size_t (*n_get_memory_size)(unsigned);

// ---- Los callbacks de Java ----
static entorno_t j_entorno;
static video_t j_video;
static muestra_t j_muestra;
static lote_audio_t j_lote_audio;
static sondeo_t j_sondeo;
static estado_entrada_t j_estado_entrada;
static log_t j_log;

// ---- Lo guardado mientras el núcleo corre en otra pila ----
static int16_t *audio;
static size_t audio_cuadros, audio_capacidad;
static const void *video_datos;
static unsigned video_ancho, video_alto;
static size_t video_pitch;
static bool video_pendiente;
static char *mensajes;
static size_t mensajes_largo, mensajes_capacidad;

// Respuestas de Java que se repiten desde la otra pila
#define MAX_VARIABLES 256
static char *variables_clave[MAX_VARIABLES];
static char *variables_valor[MAX_VARIABLES];
static int variables_cantidad;
static unsigned audio_video_activo = 3;
static int16_t botones;
static int16_t palancas[2][2];
// Mouse y puntero (Wii): se le preguntan a Java antes de cada cuadro
static int16_t puntero[3], botones_mouse[3];

// El hilo de Java que maneja el núcleo (el que llama a todo lo de la API); se anota en cada llamada
static volatile DWORD hilo_java;
static CRITICAL_SECTION cerrojo;
static bool cerrojo_listo;
// Copia del cuadro cuando llega desde otro hilo (el puntero del núcleo puede no valer más tarde)
static uint8_t *video_copia;
static size_t video_copia_capacidad;

static void entrar(void) {
	hilo_java = GetCurrentThreadId();
}

static bool en_pila_del_hilo(void) {
	ULONG_PTR bajo, alto;
	GetCurrentThreadStackLimits(&bajo, &alto);
	ULONG_PTR aca = (ULONG_PTR)&bajo;
	return aca >= bajo && aca < alto;
}

// Se puede llamar a Java ahora mismo: es el hilo de Java y está en su pila (no en una corrutina)
static bool directo(void) {
	return GetCurrentThreadId() == hilo_java && en_pila_del_hilo();
}

static void bloquear(void) {
	EnterCriticalSection(&cerrojo);
}

static void desbloquear(void) {
	LeaveCriticalSection(&cerrojo);
}

static void *crecer(void *bloque, size_t *capacidad, size_t necesaria, size_t tamanio) {
	if (necesaria <= *capacidad) {
		return bloque;
	}
	size_t nueva = *capacidad ? *capacidad : 4096;
	while (nueva < necesaria) {
		nueva *= 2;
	}
	void *otro = realloc(bloque, nueva * tamanio);
	if (otro) {
		*capacidad = nueva;
	}
	return otro ? otro : bloque;
}

// Guarda la respuesta de Java para repetirla desde otro hilo. Los valores viejos no se liberan: otro hilo puede
// estar leyéndolos (son pocos y cortos)
static void guardar_variable(const char *clave, const char *valor) {
	bloquear();
	for (int i = 0; i < variables_cantidad; i++) {
		if (strcmp(variables_clave[i], clave) == 0) {
			if (strcmp(variables_valor[i], valor) != 0) {
				variables_valor[i] = _strdup(valor);
			}
			desbloquear();
			return;
		}
	}
	if (variables_cantidad < MAX_VARIABLES) {
		variables_clave[variables_cantidad] = _strdup(clave);
		variables_valor[variables_cantidad] = _strdup(valor);
		variables_cantidad++;
	}
	desbloquear();
}

static const char *buscar_variable(const char *clave) {
	const char *valor = NULL;
	bloquear();
	for (int i = 0; i < variables_cantidad; i++) {
		if (strcmp(variables_clave[i], clave) == 0) {
			valor = variables_valor[i];
			break;
		}
	}
	desbloquear();
	return valor;
}

// ---- Video por hardware (OpenGL propio, ver contexto_gl.c) ----

#define RETRO_ENVIRONMENT_SET_HW_RENDER 14
#define RETRO_ENVIRONMENT_SET_PIXEL_FORMAT 10
#define RETRO_ENVIRONMENT_SET_HW_SHARED_CONTEXT 44
#define RETRO_ENVIRONMENT_GET_PREFERRED_HW_RENDER 56
#define RETRO_HW_CONTEXT_NONE 0
#define RETRO_HW_CONTEXT_OPENGL 1
#define RETRO_HW_CONTEXT_OPENGL_CORE 3
#define RETRO_HW_FRAME_BUFFER_VALID ((void *)-1)

typedef void (*hw_reinicio_t)(void);
struct hw_render {
	int tipo;
	hw_reinicio_t contexto_reiniciado;
	uintptr_t (*framebuffer_actual)(void);
	void *(*funcion)(const char *);
	bool profundidad;
	bool stencil;
	bool origen_abajo;
	unsigned version_mayor;
	unsigned version_menor;
	bool guardar_contexto;
	hw_reinicio_t contexto_destruido;
	bool depuracion;
};

// Java lo habilita para los núcleos que lo necesitan (puente_permitir_gl)
static bool gl_permitido;
// El pedido del núcleo; contexto_reiniciado se llama al terminar de cargar el juego
static struct hw_render *hw_pedido;
static bool hw_en_uso;

static uintptr_t p_framebuffer_actual(void) {
	return gl_framebuffer();
}

static void *p_funcion_gl(const char *nombre) {
	return gl_funcion(nombre);
}

// JNA pasa el boolean de Java como un int (no como el bool de un byte de C)
EXPORTAR void puente_permitir_gl(int permitir) {
	gl_permitido = permitir != 0;
}

// Atiende los pedidos de video por hardware del núcleo; devuelve false si no es uno de esos
static bool hw_entorno(unsigned base, void *data, bool *respuesta) {
	switch (base) {
		case RETRO_ENVIRONMENT_GET_PREFERRED_HW_RENDER:
			if (!gl_permitido) {
				return false;
			}
			*(int *)data = RETRO_HW_CONTEXT_OPENGL_CORE;
			*respuesta = true;
			return true;
		case RETRO_ENVIRONMENT_SET_HW_RENDER: {
			struct hw_render *hw = data;
			if (hw->tipo == RETRO_HW_CONTEXT_NONE) {
				hw_en_uso = false;
				*respuesta = true;
				return true;
			}
			bool tipo_ok = gl_permitido && (hw->tipo == RETRO_HW_CONTEXT_OPENGL_CORE || hw->tipo == RETRO_HW_CONTEXT_OPENGL);
			bool creado = tipo_ok && gl_crear(hw->version_mayor ? hw->version_mayor : 3, hw->version_menor, hw->tipo == RETRO_HW_CONTEXT_OPENGL_CORE);
			bool ok = creado && gl_tamanio(1280, 1056, hw->profundidad || hw->stencil);
			if (ok) {
				hw->framebuffer_actual = p_framebuffer_actual;
				hw->funcion = p_funcion_gl;
				hw_pedido = hw;
				hw_en_uso = true;
				// Java recibe los cuadros leídos como XRGB8888
				unsigned formato = 1;
				if (j_entorno) {
					j_entorno(RETRO_ENVIRONMENT_SET_PIXEL_FORMAT, &formato);
				}
			}
			if (!ok && j_log) {
				char texto[256];
				snprintf(texto, sizeof texto, "Puente: no se pudo dar el contexto OpenGL %u.%u (tipo %d, permitido %d, creado %d): %s, error %lu", hw->version_mayor, hw->version_menor, hw->tipo, gl_permitido, creado, gl_error, GetLastError());
				j_log(3, texto);
			}
			*respuesta = ok;
			return true;
		}
		case RETRO_ENVIRONMENT_SET_HW_SHARED_CONTEXT:
			// El contexto es solo del núcleo: no lo comparte con nadie
			*respuesta = gl_permitido;
			return gl_permitido;
		default:
			return false;
	}
}

// ---- Callbacks que ve el núcleo ----

static void p_log(int nivel, const char *formato, ...) {
	char texto[1024];
	va_list args;
	va_start(args, formato);
	vsnprintf(texto, sizeof texto, formato, args);
	va_end(args);
	if (directo()) {
		if (j_log) {
			j_log(nivel, texto);
		}
		return;
	}
	size_t largo = strlen(texto);
	bloquear();
	// Si nadie los retira (el núcleo escribe mucho entre cuadros) no se acumulan sin fin
	if (mensajes_largo < 1024 * 1024) {
		mensajes = crecer(mensajes, &mensajes_capacidad, mensajes_largo + largo + 3, 1);
		if (mensajes_largo + largo + 3 <= mensajes_capacidad) {
			mensajes[mensajes_largo++] = (char)('0' + (nivel & 7));
			memcpy(mensajes + mensajes_largo, texto, largo);
			mensajes_largo += largo;
			mensajes[mensajes_largo++] = '\0';
		}
	}
	desbloquear();
}

static bool p_entorno(unsigned cmd, void *data) {
	unsigned base = cmd & ~RETRO_ENVIRONMENT_EXPERIMENTAL;
	if (base == RETRO_ENVIRONMENT_GET_LOG_INTERFACE) {
		((struct interfaz_log *)data)->log = p_log;
		if (directo() && j_entorno) {
			// Java guarda su función de log para recibir los mensajes ya formateados
			struct interfaz_log de_java = {0};
			if (j_entorno(cmd, &de_java)) {
				j_log = de_java.log;
			}
		}
		return true;
	}
	bool respuesta_hw = false;
	if (directo() && hw_entorno(base, data, &respuesta_hw)) {
		return respuesta_hw;
	}
	if (directo()) {
		bool r = j_entorno && j_entorno(cmd, data);
		if (base == RETRO_ENVIRONMENT_GET_VARIABLE && r) {
			struct variable *v = data;
			if (v->clave && v->valor) {
				guardar_variable(v->clave, v->valor);
			}
		} else if (base == RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE && r) {
			audio_video_activo = *(unsigned *)data;
		}
		return r;
	}
	switch (base) {
		case RETRO_ENVIRONMENT_GET_VARIABLE: {
			struct variable *v = data;
			v->valor = v->clave ? buscar_variable(v->clave) : NULL;
			return v->valor != NULL;
		}
		case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
			*(bool *)data = false;
			return true;
		case RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE:
			*(unsigned *)data = audio_video_activo;
			return true;
		default:
			return false;
	}
}

static void p_video(const void *data, unsigned ancho, unsigned alto, size_t pitch) {
	if (data == RETRO_HW_FRAME_BUFFER_VALID) {
		// Lo dibujó en el framebuffer del puente: se lee a memoria y sigue como un cuadro común
		data = hw_en_uso ? gl_leer(ancho, alto, hw_pedido && hw_pedido->origen_abajo, &pitch) : NULL;
	}
	if (directo()) {
		if (j_video) {
			j_video(data, ancho, alto, pitch);
		}
		return;
	}
	bloquear();
	video_ancho = ancho;
	video_alto = alto;
	video_pitch = pitch;
	video_datos = NULL;
	if (data && GetCurrentThreadId() != hilo_java) {
		// Desde otro hilo el puntero puede dejar de valer: se copia
		size_t tamanio = pitch * alto;
		video_copia = crecer(video_copia, &video_copia_capacidad, tamanio, 1);
		if (tamanio <= video_copia_capacidad) {
			memcpy(video_copia, data, tamanio);
			video_datos = video_copia;
		}
	} else {
		// Desde la corrutina, en el mismo hilo, el cuadro del núcleo sigue valiendo hasta el próximo retro_run
		video_datos = data;
	}
	video_pendiente = true;
	desbloquear();
}

static size_t p_lote_audio(const int16_t *data, size_t cuadros) {
	if (!data || cuadros == 0) {
		return cuadros;
	}
	bloquear();
	audio = crecer(audio, &audio_capacidad, (audio_cuadros + cuadros) * 2, sizeof(int16_t));
	if ((audio_cuadros + cuadros) * 2 <= audio_capacidad) {
		memcpy(audio + audio_cuadros * 2, data, cuadros * 2 * sizeof(int16_t));
		audio_cuadros += cuadros;
	}
	desbloquear();
	return cuadros;
}

static void p_muestra(int16_t izquierda, int16_t derecha) {
	int16_t par[2] = {izquierda, derecha};
	p_lote_audio(par, 1);
}

static void p_sondeo(void) {
}

static int16_t p_estado_entrada(unsigned puerto, unsigned dispositivo, unsigned indice, unsigned id) {
	// El núcleo puede preguntar con un subtipo (Dolphin: el Wiimote de costado es un joypad con subtipo 2); como en
	// RetroArch, cuenta solo el tipo base
	dispositivo &= 0xFF;
	if (puerto != 0) {
		return 0;
	}
	if (dispositivo == RETRO_DEVICE_JOYPAD) {
		return id == RETRO_DEVICE_ID_JOYPAD_MASK ? botones : (int16_t)((botones >> id) & 1);
	}
	if (dispositivo == RETRO_DEVICE_ANALOG && indice < 2 && id < 2) {
		return palancas[indice][id];
	}
	if (dispositivo == RETRO_DEVICE_POINTER && id < 4) {
		return id == 3 ? 1 : puntero[id];
	}
	if (dispositivo == RETRO_DEVICE_MOUSE) {
		return id == 2 ? botones_mouse[0] : id == 3 ? botones_mouse[1] : id == 6 ? botones_mouse[2] : 0;
	}
	return 0;
}

// Antes de cada cuadro, en la pila normal: controles y opciones desde Java
static void antes(void) {
	if (j_sondeo) {
		j_sondeo();
	}
	if (j_estado_entrada) {
		botones = j_estado_entrada(0, RETRO_DEVICE_JOYPAD, 0, RETRO_DEVICE_ID_JOYPAD_MASK);
		for (unsigned i = 0; i < 2; i++) {
			for (unsigned eje = 0; eje < 2; eje++) {
				palancas[i][eje] = j_estado_entrada(0, RETRO_DEVICE_ANALOG, i, eje);
			}
		}
		for (unsigned id = 0; id < 3; id++) {
			puntero[id] = j_estado_entrada(0, RETRO_DEVICE_POINTER, 0, id);
		}
		static const unsigned ids_mouse[3] = {2, 3, 6};
		for (unsigned i = 0; i < 3; i++) {
			botones_mouse[i] = j_estado_entrada(0, RETRO_DEVICE_MOUSE, 0, ids_mouse[i]);
		}
	}
	unsigned av = 3;
	if (j_entorno && j_entorno(RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE, &av)) {
		audio_video_activo = av;
	}
}

// Después de cada llamada al núcleo, ya en la pila normal: se entrega lo guardado. Lo guardado se saca con el cerrojo
// tomado pero a Java se le entrega sin él, para que los hilos del núcleo no esperen a Java
static void despues(void) {
	char *lista = NULL;
	size_t largo = 0;
	bool hay_video = false;
	const void *datos = NULL;
	unsigned ancho = 0, alto = 0;
	size_t pitch = 0;
	int16_t *muestras = NULL;
	uint8_t *copia = NULL;
	size_t cuadros = 0;

	bloquear();
	if (mensajes_largo > 0) {
		lista = malloc(mensajes_largo);
		if (lista) {
			memcpy(lista, mensajes, mensajes_largo);
			largo = mensajes_largo;
		}
		mensajes_largo = 0;
	}
	if (video_pendiente) {
		video_pendiente = false;
		hay_video = true;
		datos = video_datos;
		ancho = video_ancho;
		alto = video_alto;
		pitch = video_pitch;
		if (datos != NULL && datos == video_copia) {
			// Java se lleva la copia: otro hilo puede escribir el próximo cuadro mientras la lee
			copia = video_copia;
			video_copia = NULL;
			video_copia_capacidad = 0;
		}
	}
	if (audio_cuadros > 0) {
		muestras = malloc(audio_cuadros * 2 * sizeof(int16_t));
		if (muestras) {
			memcpy(muestras, audio, audio_cuadros * 2 * sizeof(int16_t));
			cuadros = audio_cuadros;
		}
		audio_cuadros = 0;
	}
	desbloquear();

	for (size_t i = 0; i < largo;) {
		int nivel = lista[i] - '0';
		const char *texto = lista + i + 1;
		if (j_log) {
			j_log(nivel, texto);
		}
		i += strlen(texto) + 2;
	}
	free(lista);
	if (hay_video && j_video) {
		j_video(datos, ancho, alto, pitch);
	}
	free(copia);
	if (cuadros > 0 && j_lote_audio) {
		j_lote_audio(muestras, cuadros);
	}
	free(muestras);
}

// ---- Pila de los hilos del núcleo ----
//
// Un hilo de Windows creado sin tamaño de pila toma el que dice el ejecutable, y java.exe/javaw.exe dicen 1 MB (RetroArch,
// 8 o más). RPCS3 arranca hilos así que se desbordan con 1 MB y matan el proceso (STATUS_STACK_OVERFLOW). Se parchea la
// tabla de importaciones del núcleo para que todo hilo que cree tenga al menos PILA_MINIMA de reserva (solo se reserva
// espacio de direcciones; la memoria se usa a medida que hace falta).

#define PILA_MINIMA (16u * 1024 * 1024)

typedef HANDLE(WINAPI *crear_hilo_t)(LPSECURITY_ATTRIBUTES, SIZE_T, LPTHREAD_START_ROUTINE, LPVOID, DWORD, LPDWORD);
typedef uintptr_t(__cdecl *beginthreadex_t)(void *, unsigned, unsigned(__stdcall *)(void *), void *, unsigned, unsigned *);
static crear_hilo_t crear_hilo_original;
static beginthreadex_t beginthreadex_original;

static HANDLE WINAPI p_crear_hilo(LPSECURITY_ATTRIBUTES seguridad, SIZE_T pila, LPTHREAD_START_ROUTINE inicio, LPVOID arg, DWORD banderas,
		LPDWORD id) {
	if (pila < PILA_MINIMA) {
		pila = PILA_MINIMA;
		banderas |= STACK_SIZE_PARAM_IS_A_RESERVATION;
	}
	return crear_hilo_original(seguridad, pila, inicio, arg, banderas, id);
}

static uintptr_t __cdecl p_beginthreadex(void *seguridad, unsigned pila, unsigned(__stdcall *inicio)(void *), void *arg, unsigned banderas,
		unsigned *id) {
	if (pila < PILA_MINIMA) {
		pila = PILA_MINIMA;
		banderas |= STACK_SIZE_PARAM_IS_A_RESERVATION;
	}
	return beginthreadex_original(seguridad, pila, inicio, arg, banderas, id);
}

// Reemplaza en la tabla de importaciones del módulo la función importada con ese nombre; devuelve la original o NULL
static void *parchear_importacion(HMODULE modulo, const char *nombre, void *nueva) {
	uint8_t *base = (uint8_t *)modulo;
	IMAGE_DOS_HEADER *dos = (IMAGE_DOS_HEADER *)base;
	IMAGE_NT_HEADERS *nt = (IMAGE_NT_HEADERS *)(base + dos->e_lfanew);
	IMAGE_DATA_DIRECTORY dir = nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_IMPORT];
	if (dir.VirtualAddress == 0) {
		return NULL;
	}
	void *original = NULL;
	for (IMAGE_IMPORT_DESCRIPTOR *imp = (IMAGE_IMPORT_DESCRIPTOR *)(base + dir.VirtualAddress); imp->Name != 0; imp++) {
		if (imp->OriginalFirstThunk == 0) {
			continue;
		}
		IMAGE_THUNK_DATA *nombres = (IMAGE_THUNK_DATA *)(base + imp->OriginalFirstThunk);
		IMAGE_THUNK_DATA *direcciones = (IMAGE_THUNK_DATA *)(base + imp->FirstThunk);
		for (; nombres->u1.AddressOfData != 0; nombres++, direcciones++) {
			if (IMAGE_SNAP_BY_ORDINAL(nombres->u1.Ordinal)) {
				continue;
			}
			IMAGE_IMPORT_BY_NAME *por_nombre = (IMAGE_IMPORT_BY_NAME *)(base + nombres->u1.AddressOfData);
			if (strcmp((const char *)por_nombre->Name, nombre) != 0) {
				continue;
			}
			DWORD protegido;
			if (VirtualProtect(&direcciones->u1.Function, sizeof(void *), PAGE_READWRITE, &protegido)) {
				original = (void *)direcciones->u1.Function;
				direcciones->u1.Function = (ULONG_PTR)nueva;
				VirtualProtect(&direcciones->u1.Function, sizeof(void *), protegido, &protegido);
			}
		}
	}
	return original;
}

static void agrandar_pilas(HMODULE modulo) {
	void *original = parchear_importacion(modulo, "CreateThread", (void *)p_crear_hilo);
	crear_hilo_original = original ? (crear_hilo_t)original : CreateThread;
	original = parchear_importacion(modulo, "_beginthreadex", (void *)p_beginthreadex);
	if (original) {
		beginthreadex_original = (beginthreadex_t)original;
	}
}

// ---- Carga ----

#define CARGAR(f) (*(FARPROC *)&n_##f = GetProcAddress(nucleo, "retro_" #f)) != NULL

EXPORTAR bool puente_cargar(const char *ruta) {
	if (!cerrojo_listo) {
		InitializeCriticalSection(&cerrojo);
		cerrojo_listo = true;
	}
	if (nucleo) {
		return true;
	}
	nucleo = LoadLibraryA(ruta);
	if (!nucleo) {
		return false;
	}
	bool ok = CARGAR(api_version) && CARGAR(set_environment) && CARGAR(set_video_refresh) && CARGAR(set_audio_sample)
			&& CARGAR(set_audio_sample_batch) && CARGAR(set_input_poll) && CARGAR(set_input_state) && CARGAR(init)
			&& CARGAR(deinit) && CARGAR(get_system_info) && CARGAR(get_system_av_info) && CARGAR(set_controller_port_device)
			&& CARGAR(reset) && CARGAR(run) && CARGAR(serialize_size) && CARGAR(serialize) && CARGAR(unserialize)
			&& CARGAR(load_game) && CARGAR(unload_game) && CARGAR(get_memory_data) && CARGAR(get_memory_size);
	if (ok) {
		agrandar_pilas(nucleo);
	}
	if (!ok) {
		FreeLibrary(nucleo);
		nucleo = NULL;
	}
	return ok;
}

// ---- La API libretro, hacia el núcleo ----

EXPORTAR unsigned retro_api_version(void) {
	entrar();
	return n_api_version();
}

EXPORTAR void retro_set_environment(entorno_t cb) {
	entrar();
	j_entorno = cb;
	n_set_environment(p_entorno);
	despues();
}

EXPORTAR void retro_set_video_refresh(video_t cb) {
	entrar();
	j_video = cb;
	n_set_video_refresh(p_video);
}

EXPORTAR void retro_set_audio_sample(muestra_t cb) {
	entrar();
	j_muestra = cb;
	n_set_audio_sample(p_muestra);
}

EXPORTAR void retro_set_audio_sample_batch(lote_audio_t cb) {
	entrar();
	j_lote_audio = cb;
	n_set_audio_sample_batch(p_lote_audio);
}

EXPORTAR void retro_set_input_poll(sondeo_t cb) {
	entrar();
	j_sondeo = cb;
	n_set_input_poll(p_sondeo);
}

EXPORTAR void retro_set_input_state(estado_entrada_t cb) {
	entrar();
	j_estado_entrada = cb;
	n_set_input_state(p_estado_entrada);
}

EXPORTAR void retro_init(void) {
	entrar();
	n_init();
	despues();
}

EXPORTAR void retro_deinit(void) {
	entrar();
	n_deinit();
	gl_destruir();
	despues();
}

EXPORTAR void retro_get_system_info(void *info) {
	entrar();
	n_get_system_info(info);
}

EXPORTAR void retro_get_system_av_info(void *info) {
	entrar();
	n_get_system_av_info(info);
}

EXPORTAR void retro_set_controller_port_device(unsigned puerto, unsigned dispositivo) {
	entrar();
	n_set_controller_port_device(puerto, dispositivo);
}

EXPORTAR void retro_reset(void) {
	entrar();
	n_reset();
	despues();
}

EXPORTAR void retro_run(void) {
	entrar();
	antes();
	n_run();
	despues();
}

EXPORTAR size_t retro_serialize_size(void) {
	entrar();
	return n_serialize_size();
}

EXPORTAR bool retro_serialize(void *data, size_t tamanio) {
	entrar();
	bool r = n_serialize(data, tamanio);
	despues();
	return r;
}

EXPORTAR bool retro_unserialize(const void *data, size_t tamanio) {
	entrar();
	bool r = n_unserialize(data, tamanio);
	despues();
	return r;
}

EXPORTAR bool retro_load_game(const void *juego) {
	entrar();
	bool r = n_load_game(juego);
	// Con el juego cargado, el núcleo prepara lo suyo en el contexto de video (como cuando RetroArch lo crea)
	if (r && hw_en_uso && hw_pedido && hw_pedido->contexto_reiniciado) {
		gl_crear(0, 0, true);
		hw_pedido->contexto_reiniciado();
	}
	despues();
	return r;
}

EXPORTAR void retro_unload_game(void) {
	entrar();
	// Como RetroArch: primero se avisa que se va el contexto de video y después se descarga el juego (PPSSPP libera sus
	// cosas de OpenGL en el aviso y después ya no las tiene)
	if (hw_en_uso && hw_pedido && hw_pedido->contexto_destruido) {
		hw_pedido->contexto_destruido();
	}
	n_unload_game();
	hw_en_uso = false;
	hw_pedido = NULL;
	despues();
}

EXPORTAR void *retro_get_memory_data(unsigned id) {
	entrar();
	return n_get_memory_data(id);
}

EXPORTAR size_t retro_get_memory_size(unsigned id) {
	entrar();
	return n_get_memory_size(id);
}
