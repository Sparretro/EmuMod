package com.emumod.client;

import com.emumod.ConsolaItem;
import com.emumod.client.emu.Nucleo.Boton;
import com.emumod.client.emu.Sistema;

/**
 * Las portátiles con modelo 3D propio (la 3DS, la DS, la Game Boy Advance SP y la PSP): se juegan en las manos como la GBA; en
 * la 3DS y la DS la pantalla de abajo es táctil.
 * Cada una trae su modelo 3D, dónde están sus pantallas y botones en ese modelo y cómo se sostiene (ver
 * {@link ModeloDoble} y {@link ManosDoble}).
 *
 * <p>Coordenadas de cada modelo tal como salen de {@code tools/ConvertirModelo.java}: y hacia arriba y la base acostada;
 * la pantalla táctil es plana. Citra entrega las dos pantallas una arriba de la otra en un solo cuadro.
 */
enum PortatilDoble {
	/**
	 * «Nintendo 3DS» de Broeach8972 (CC-BY-4.0, Sketchfab), sin las piezas repetidas que trae el GLB. El jugador está del
	 * lado de -z (la tapa se abre hacia +z) y +x es su izquierda. Citra arma un cuadro de 400x480: la de arriba ocupa la
	 * mitad de arriba y la táctil (320x240) va centrada abajo.
	 */
	N3DS(Sistema.N3DS, ConsolaItem.Modelo.N3DS, "n3ds", 12.31f, 2.24f, -0.42f, -0.027f, true,
			// Pantalla de arriba (en la tapa, 5:3 como la de verdad): esquinas arriba izquierda, arriba derecha, abajo
			// derecha y abajo izquierda vistas por el jugador, y la normal
			esquinas(0, 2.684f, 2.261f, 3.476f, 2.086f, 0.5028f, 0.8644f, true), new float[] {0, 0.8644f, -0.5028f},
			new float[] {0, 0, 1, 0.5f},
			// Pantalla táctil, plana en la base: arriba es el lado de la bisagra
			new float[][] {{2.825f, 0.896f, -0.880f}, {-2.825f, 0.896f, -0.880f}, {-2.825f, 0.896f, -5.212f}, {2.825f, 0.896f, -5.212f}},
			new float[] {40 / 400f, 0.5f, 360 / 400f, 1},
			new BotonModelo[] {
					new BotonModelo(Boton.A.mascara(), "Object_1", -5.234f, -2.346f, 0.42f),
					new BotonModelo(Boton.B.mascara(), "Object_3", -4.535f, -3.045f, 0.42f),
					new BotonModelo(Boton.X.mascara(), "Object_8", -4.535f, -1.646f, 0.42f),
					new BotonModelo(Boton.Y.mascara(), "Object_9", -3.835f, -2.346f, 0.42f),
					// Gatillos: las esquinas de atrás de la base
					new BotonModelo(Boton.L.mascara(), "Object_5", 5.33f, 0.0f, 0.75f),
					new BotonModelo(Boton.R.mascara(), "Object_7", -5.33f, 0.0f, 0.75f)},
			new BotonModelo(0, "Object_4", 4.563f, -4.434f, 0.79f),
			new BotonModelo(0, "Object_2", 4.569f, -2.162f, 0.72f),
			// Derecha del jugador y hacia la bisagra (arriba en la cruz), en (x, z)
			new float[] {-1, 0}, new float[] {0, 1},
			// En las manos: alto y distancia a los ojos, inclinación hacia los ojos, escala y agarre (como en ManosGba)
			0.0f, -0.62f, 50, 0.88f, new float[] {0.43f, -0.27f, 0.05f}, new float[] {0.30f, 0.93f, -0.20f}),

	/**
	 * «Gameboy Advance SP» de Smoggybeard (CC-BY-4.0, Sketchfab), sin el cartucho que trae puesto y con la pantalla en
	 * negro. Los botones vienen juntos en pocas piezas y se separaron (tools/ConvertirModelo.java --separar): A y B se
	 * hunden con su letra. El jugador está del lado de +z (la tapa se abre hacia -z) y +x es su derecha. Usa el mismo
	 * emulador que la GBA; su única pantalla, de 3:2, está en la tapa. Es más larga que ancha: la escala va por el largo.
	 */
	GBA_SP(Sistema.GBA, ConsolaItem.Modelo.GBA_SP, "gba_sp", 5.2f, 0.674f, 0.9495f, -1.218f, false,
			new float[][] {{-1.4627f, 2.0731f, -1.3040f}, {1.3530f, 2.0731f, -1.3040f}, {1.3530f, 0.3650f, -0.4768f}, {-1.4627f, 0.3650f, -0.4768f}},
			new float[] {0, 0.44f, 0.898f}, new float[] {0, 0, 1, 1},
			// Sin pantalla táctil: la superficie de la base, donde se ubica el mouse para los botones
			-0.728f,
			new BotonModelo[] {
					new BotonModelo(Boton.A.mascara(), "Object_4_7|Object_4_6|Object_6_1", 1.185f, 1.316f, 0.2f),
					new BotonModelo(Boton.B.mascara(), "Object_4_3|Object_4_2|Object_4_4|Object_6_0", 0.585f, 1.559f, 0.2f),
					new BotonModelo(Boton.START.mascara(), "Object_7_5", 0.267f, 3.101f, 0.15f),
					new BotonModelo(Boton.SELECT.mascara(), "Object_7_3", -0.364f, 3.101f, 0.15f),
					// Gatillos: las esquinas de atrás de la base, junto a la bisagra
					new BotonModelo(Boton.L.mascara(), "Object_7_1", -1.756f, -0.075f, 0.3f),
					new BotonModelo(Boton.R.mascara(), "Object_7_6", 1.666f, -0.071f, 0.3f)},
			new BotonModelo(0, "Object_4_0", -0.969f, 1.443f, 0.4f),
			new float[] {1, 0}, new float[] {0, -1},
			-0.04f, -0.62f, 45, 0.72f, new float[] {0.43f, -0.27f, 0.05f}, new float[] {0.30f, 0.93f, -0.20f}),
	/**
	 * «Sony PSP» de Ilya Ostrovsky (CC-BY-4.0, Sketchfab), girado 45° para que quede acostado (venía apoyado) y sin el piso
	 * que trae; la imagen de su pantalla se pintó de negro. Cada botón es una pieza: la cruz son cuatro (las flechas van
	 * con radio 0 para que el mouse use la zona de la cruz, que permite diagonales). El jugador está del lado de +z y +x
	 * es su derecha. La pantalla, de 480x272, está en la misma cara que los botones; los gatillos, en las esquinas de atrás.
	 */
	PSP(Sistema.PSP, ConsolaItem.Modelo.PSP, "psp", 16.76f, 2.2455f, -2.2455f, 0.956f, false,
			new float[][] {{-4.726f, 3.246f, -5.141f}, {4.700f, 3.246f, -5.141f}, {4.700f, 3.246f, 0.158f}, {-4.726f, 3.246f, 0.158f}},
			new float[] {0, 1, 0}, new float[] {0, 0, 1, 1}, null, null, 3.30f,
			new BotonModelo[] {
					new BotonModelo(Boton.ARRIBA.mascara(), "button1_low_sony_psp_0", -6.48f, -3.374f, 0),
					new BotonModelo(Boton.ABAJO.mascara(), "button3_low_sony_psp_0", -6.48f, -1.810f, 0),
					new BotonModelo(Boton.DERECHA.mascara(), "button2_low_sony_psp_0", -5.698f, -2.592f, 0),
					new BotonModelo(Boton.IZQUIERDA.mascara(), "button4_low_sony_psp_0", -7.262f, -2.592f, 0),
					// △ ◯ ✕ □: X, A, B e Y del RetroPad, por posición
					new BotonModelo(Boton.X.mascara(), "button5_low_sony_psp_0", 6.521f, -3.663f, 0.5f),
					new BotonModelo(Boton.A.mascara(), "button6_low_sony_psp_0", 7.616f, -2.617f, 0.5f),
					new BotonModelo(Boton.B.mascara(), "button7_low_sony_psp_0", 6.572f, -1.523f, 0.5f),
					new BotonModelo(Boton.Y.mascara(), "button8_low_sony_psp_0", 5.476f, -2.567f, 0.5f),
					new BotonModelo(Boton.SELECT.mascara(), "button14_low_sony_psp_0", 3.647f, 0.910f, 0.45f),
					new BotonModelo(Boton.START.mascara(), "button15_low_sony_psp_0", 4.748f, 0.910f, 0.45f),
					new BotonModelo(Boton.L.mascara(), null, -6.8f, -5.4f, 1.0f),
					new BotonModelo(Boton.R.mascara(), null, 6.8f, -5.4f, 1.0f)},
			new BotonModelo(0, null, -6.48f, -2.592f, 1.25f),
			// La palanca analógica, abajo de la cruz (es parte de la carcasa: no se mueve, pero se arrastra con el mouse)
			new BotonModelo(0, null, -6.44f, -0.38f, 0.6f),
			new float[] {1, 0}, new float[] {0, -1},
			-0.02f, -0.62f, 75, 0.95f, new float[] {0.42f, -0.08f, 0.04f}, new float[] {0.30f, 0.93f, -0.20f}),
	/**
	 * «Nintendo DSi Console» de AlanaSierra (CC-BY-4.0, Sketchfab): una DSi rosa con cada botón como pieza suelta. Emula
	 * una DS (melonDS DS en modo DS), que entrega un cuadro de 256x384 con las dos pantallas enteras, una arriba de la
	 * otra. El jugador está del lado de +z (la tapa se abre hacia -z, a unos 30°) y +x es su derecha. Las dos pantallas
	 * del modelo son algo más anchas que 4:3: el juego va centrado, con la proporción de la DS.
	 */
	NDS(Sistema.NDS, ConsolaItem.Modelo.NDS, "nds", 0.245f, 0.0525f, -0.0525f, 0.009f, false,
			new float[][] {{-0.0646f, 0.0870f, -0.1593f}, {0.0557f, 0.0870f, -0.1593f}, {0.0557f, 0.0422f, -0.0810f}, {-0.0646f, 0.0422f, -0.0810f}},
			new float[] {0, 0.87f, 0.5f}, new float[] {0, 0, 1, 0.5f},
			new float[][] {{-0.0647f, 0.0292f, -0.0359f}, {0.0558f, 0.0292f, -0.0359f}, {0.0558f, 0.0292f, 0.0545f}, {-0.0647f, 0.0292f, 0.0545f}},
			new float[] {0, 0.5f, 1, 1},
			new BotonModelo[] {
					new BotonModelo(Boton.A.mascara(), "A_Button_geo2_aiStandardSurface1_0", 0.104f, -0.0075f, 0.011f),
					new BotonModelo(Boton.B.mascara(), "B_Button_geo_aiStandardSurface1_0", 0.092f, 0.006f, 0.011f),
					new BotonModelo(Boton.X.mascara(), "X_Button_geo1_aiStandardSurface1_0", 0.092f, -0.021f, 0.011f),
					new BotonModelo(Boton.Y.mascara(), "Y_Button_geo3_aiStandardSurface1_0", 0.079f, -0.0075f, 0.011f),
					new BotonModelo(Boton.START.mascara(), "Start_Button_geo4_aiStandardSurface1_0", 0.068f, 0.040f, 0.007f),
					new BotonModelo(Boton.SELECT.mascara(), "Select_Button_geo5_aiStandardSurface1_0", 0.068f, 0.051f, 0.007f),
					// Gatillos: las esquinas de atrás de la base
					new BotonModelo(Boton.L.mascara(), "L_button_geo_aiStandardSurface1_0", -0.109f, -0.065f, 0.02f),
					new BotonModelo(Boton.R.mascara(), "R_button_geo_aiStandardSurface1_0", 0.1015f, -0.065f, 0.02f)},
			new BotonModelo(0, "D_Pad_geo_aiStandardSurface1_0", -0.0985f, -0.0075f, 0.02f),
			null,
			new float[] {1, 0}, new float[] {0, -1},
			0.03f, -0.62f, 50, 0.74f, new float[] {0.37f, -0.16f, 0.05f}, new float[] {0.30f, 0.93f, -0.20f});



	/** Botón del modelo: máscara del RetroPad, pieza o piezas que se hunden (separadas por «|», o null), centro (x, z) en la base y radio para el mouse. */
	record BotonModelo(int mascara, String pieza, float x, float z, float radio) {
	}

	final Sistema sistema;
	final ConsolaItem.Modelo modelo;
	final String nombreModelo;
	// Del modelo al cubo 0..1 de un ítem: escala (por el ancho), centro de su caja y piso del modelo
	final float escala, centroY, centroZ, pisoY;
	// Unidad del modelo respecto del de la 3DS (12,31 de ancho): las medidas fijas de ModeloDoble se multiplican por esto
	final float unidad;
	// El lado del jugador del modelo está en -z y hay que darlo vuelta para que mire a +z, como los ítems de Minecraft
	final boolean darVuelta;
	// La pantalla de abajo (táctil) y su parte del cuadro son null en la SP, que tiene una sola
	final float[][] arriba, abajo;
	// Altura de la base donde se ubica el mouse: la pantalla táctil, o la superficie de la base si no hay
	final float superficie;
	final float[] normalArriba, uvArriba, uvAbajo;
	final BotonModelo[] botones;
	final BotonModelo cruz, palanca;
	final float[] derecha, haciaBisagra;
	final float manosY, manosZ, inclinacion, escalaManos;
	final float[] agarre, brazo;

	// Con pantalla táctil (la 3DS)
	PortatilDoble(Sistema sistema, ConsolaItem.Modelo modelo, String nombreModelo, float ancho, float centroY, float centroZ, float pisoY,
			boolean darVuelta, float[][] arriba, float[] normalArriba, float[] uvArriba, float[][] abajo, float[] uvAbajo, BotonModelo[] botones,
			BotonModelo cruz, BotonModelo palanca, float[] derecha, float[] haciaBisagra, float manosY, float manosZ, float inclinacion,
			float escalaManos, float[] agarre, float[] brazo) {
		this(sistema, modelo, nombreModelo, ancho, centroY, centroZ, pisoY, darVuelta, arriba, normalArriba, uvArriba, abajo, uvAbajo, abajo[0][1],
				botones, cruz, palanca, derecha, haciaBisagra, manosY, manosZ, inclinacion, escalaManos, agarre, brazo);
	}

	// Con una sola pantalla y sin palanca (la SP)
	PortatilDoble(Sistema sistema, ConsolaItem.Modelo modelo, String nombreModelo, float ancho, float centroY, float centroZ, float pisoY,
			boolean darVuelta, float[][] arriba, float[] normalArriba, float[] uvArriba, float superficie, BotonModelo[] botones,
			BotonModelo cruz, float[] derecha, float[] haciaBisagra, float manosY, float manosZ, float inclinacion,
			float escalaManos, float[] agarre, float[] brazo) {
		this(sistema, modelo, nombreModelo, ancho, centroY, centroZ, pisoY, darVuelta, arriba, normalArriba, uvArriba, null, null, superficie,
				botones, cruz, null, derecha, haciaBisagra, manosY, manosZ, inclinacion, escalaManos, agarre, brazo);
	}

	// Completo (la PSP: una sola pantalla, con palanca)
	PortatilDoble(Sistema sistema, ConsolaItem.Modelo modelo, String nombreModelo, float ancho, float centroY, float centroZ, float pisoY,
			boolean darVuelta, float[][] arriba, float[] normalArriba, float[] uvArriba, float[][] abajo, float[] uvAbajo, float superficie,
			BotonModelo[] botones, BotonModelo cruz, BotonModelo palanca, float[] derecha, float[] haciaBisagra, float manosY, float manosZ,
			float inclinacion, float escalaManos, float[] agarre, float[] brazo) {
		this.superficie = superficie;
		this.sistema = sistema;
		this.modelo = modelo;
		this.nombreModelo = nombreModelo;
		this.escala = 0.95f / ancho;
		this.unidad = ancho / 12.31f;
		this.centroY = centroY;
		this.centroZ = centroZ;
		this.pisoY = pisoY;
		this.darVuelta = darVuelta;
		this.arriba = arriba;
		this.normalArriba = normalArriba;
		this.uvArriba = uvArriba;
		this.abajo = abajo;
		this.uvAbajo = uvAbajo;
		this.botones = botones;
		this.cruz = cruz;
		this.palanca = palanca;
		this.derecha = derecha;
		this.haciaBisagra = haciaBisagra;
		this.manosY = manosY;
		this.manosZ = manosZ;
		this.inclinacion = inclinacion;
		this.escalaManos = escalaManos;
		this.agarre = agarre;
		this.brazo = brazo;
	}

	/** Altura del plano donde se ubica el mouse (los botones sobresalen apenas). */
	float superficieY() {
		return superficie;
	}

	/** La pieza del modelo es (una de) las de ese botón: {@code nombres} las lista separadas por «|». */
	static boolean esPieza(String nombres, String pieza) {
		return nombres != null && (nombres.equals(pieza) || ("|" + nombres + "|").contains("|" + pieza + "|"));
	}

	static PortatilDoble de(ConsolaItem.Modelo modelo) {
		for (PortatilDoble p : values()) {
			if (p.modelo == modelo) {
				return p;
			}
		}
		return null;
	}

	// Esquinas de una pantalla inclinada desde su centro, medio ancho, medio alto y la dirección de la tapa hacia arriba
	// (en y, z); con la izquierda del jugador en +x si {@code izquierdaEnMasX}
	private static float[][] esquinas(float x, float y, float z, float medioAncho, float medioAlto, float tapaY, float tapaZ, boolean izquierdaEnMasX) {
		float izq = izquierdaEnMasX ? medioAncho : -medioAncho;
		float dy = tapaY * medioAlto, dz = tapaZ * medioAlto;
		return new float[][] {{x + izq, y + dy, z + dz}, {x - izq, y + dy, z + dz}, {x - izq, y - dy, z - dz}, {x + izq, y - dy, z - dz}};
	}
}
