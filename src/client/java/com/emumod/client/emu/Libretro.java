package com.emumod.client.emu;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

// Mapeo JNA de la API libretro (libretro.h) que usa el núcleo de SkyEmu
interface Libretro extends Library {
	int RETRO_API_VERSION = 1;

	int ENV_EXPERIMENTAL = 0x10000;
	int ENV_GET_SYSTEM_DIRECTORY = 9;
	int ENV_SET_PIXEL_FORMAT = 10;
	int ENV_SET_INPUT_DESCRIPTORS = 11;
	int ENV_GET_VARIABLE = 15;
	int ENV_GET_VARIABLE_UPDATE = 17;
	int ENV_GET_LOG_INTERFACE = 27;
	int ENV_GET_SAVE_DIRECTORY = 31;
	int ENV_SET_MEMORY_MAPS = 36;
	// Citra avisa el tamaño de sus pantallas al armar el cuadro; si se le contesta que no, no dibuja nada
	int ENV_SET_GEOMETRY = 37;
	int ENV_GET_LANGUAGE = 39;
	int ENV_GET_AUDIO_VIDEO_ENABLE = 47;
	int ENV_GET_CORE_OPTIONS_VERSION = 52;
	int ENV_SET_CORE_OPTIONS_V2_INTL = 68;

	int PIXEL_FORMAT_0RGB1555 = 0;
	int PIXEL_FORMAT_XRGB8888 = 1;
	int PIXEL_FORMAT_RGB565 = 2;
	int LANGUAGE_ENGLISH = 0;

	int DEVICE_JOYPAD = 1;
	int DEVICE_MOUSE = 2;
	int DEVICE_ANALOG = 5;
	int DEVICE_POINTER = 6;
	int DEVICE_ID_MOUSE_LEFT = 2;
	int DEVICE_ID_MOUSE_RIGHT = 3;
	int DEVICE_ID_MOUSE_MIDDLE = 6;
	int DEVICE_ID_POINTER_X = 0;
	int DEVICE_ID_POINTER_Y = 1;
	int DEVICE_ID_POINTER_PRESSED = 2;
	int DEVICE_ID_POINTER_COUNT = 3;
	int DEVICE_INDEX_ANALOG_LEFT = 0;
	int DEVICE_INDEX_ANALOG_RIGHT = 1;
	int DEVICE_ID_ANALOG_X = 0;
	int DEVICE_ID_ANALOG_Y = 1;
	int DEVICE_ID_JOYPAD_MASK = 256;

	int MEMORY_SAVE_RAM = 0;

	int retro_api_version();

	void retro_set_environment(Entorno cb);

	void retro_set_video_refresh(Video cb);

	void retro_set_audio_sample(Muestra cb);

	void retro_set_audio_sample_batch(LoteAudio cb);

	void retro_set_input_poll(SondeoEntrada cb);

	void retro_set_input_state(EstadoEntrada cb);

	void retro_init();

	void retro_deinit();

	void retro_get_system_av_info(InfoAv info);

	void retro_set_controller_port_device(int puerto, int dispositivo);

	void retro_reset();

	void retro_run();

	boolean retro_load_game(InfoJuego juego);

	void retro_unload_game();

	long retro_serialize_size();

	boolean retro_serialize(Pointer data, long size);

	boolean retro_unserialize(Pointer data, long size);

	Pointer retro_get_memory_data(int id);

	long retro_get_memory_size(int id);

	interface Entorno extends Callback {
		boolean invoke(int cmd, Pointer data);
	}

	interface Video extends Callback {
		void invoke(Pointer data, int ancho, int alto, long pitch);
	}

	interface Muestra extends Callback {
		void invoke(short izquierda, short derecha);
	}

	interface LoteAudio extends Callback {
		long invoke(Pointer data, long cuadros);
	}

	interface SondeoEntrada extends Callback {
		void invoke();
	}

	interface EstadoEntrada extends Callback {
		short invoke(int puerto, int dispositivo, int indice, int id);
	}

	// retro_log_printf_t es variádica; en x64 los argumentos extra se ignoran sin problema
	interface Log extends Callback {
		void invoke(int nivel, String formato);
	}

	@Structure.FieldOrder({"path", "data", "size", "meta"})
	class InfoJuego extends Structure {
		public String path;
		public Pointer data;
		public long size;
		public String meta;
	}

	@Structure.FieldOrder({"baseWidth", "baseHeight", "maxWidth", "maxHeight", "aspectRatio"})
	class Geometria extends Structure {
		public int baseWidth;
		public int baseHeight;
		public int maxWidth;
		public int maxHeight;
		public float aspectRatio;
	}

	@Structure.FieldOrder({"fps", "sampleRate"})
	class Tiempos extends Structure {
		public double fps;
		public double sampleRate;
	}

	@Structure.FieldOrder({"geometry", "timing"})
	class InfoAv extends Structure {
		public Geometria geometry = new Geometria();
		public Tiempos timing = new Tiempos();
	}
}
