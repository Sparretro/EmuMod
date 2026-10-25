package com.emumod.client;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

import com.emumod.EmuMod;
import com.emumod.client.emu.Nucleo;

/**
 * Reproduce el audio del núcleo. Escribir bloquea cuando el buffer está lleno: eso marca el ritmo de la
 * emulación a x1, así el juego va exactamente a la velocidad de la placa de sonido y no hay cortes.
 * Solo se usa desde el hilo del emulador.
 */
final class SalidaSonido implements Nucleo.SalidaAudio {
	private static final double LATENCIA_SEGUNDOS = 0.08;
	// Al prender, cargar un juego o volver de una pausa: un rato en silencio y después sube suave. Los primeros cuadros
	// del núcleo y los cortes mientras la emulación arranca sonaban como un zumbido trabado
	private static final double SILENCIO_INICIAL_SEGUNDOS = 0.3;
	private static final double FUNDIDO_SEGUNDOS = 0.15;

	private final int frecuencia;
	private SourceDataLine linea;
	private boolean disponible = true;
	private byte[] bytes = new byte[0];
	private volatile float volumen = 1;
	// Cuadros de audio escritos desde el último arranque
	private long cuadrosDesdeArranque;

	SalidaSonido(int frecuencia) {
		this.frecuencia = frecuencia;
	}

	/** False si no hay dispositivo de audio: la emulación se sincroniza con el reloj y sin sonido. */
	boolean disponible() {
		if (linea == null && disponible) {
			abrir();
		}
		return linea != null;
	}

	void setVolumen(float volumen) {
		this.volumen = volumen;
	}

	@Override
	public void escribir(short[] muestras, int cuadros) {
		if (!disponible()) {
			return;
		}
		int cantidad = cuadros * 2;
		if (bytes.length < cantidad * 2) {
			bytes = new byte[cantidad * 2];
		}
		float v = volumen;
		long silencio = (long) (frecuencia * SILENCIO_INICIAL_SEGUNDOS);
		double fundido = frecuencia * FUNDIDO_SEGUNDOS;
		for (int i = 0; i < cantidad; i++) {
			long cuadro = cuadrosDesdeArranque + i / 2;
			float entrada = cuadro < silencio ? 0 : (float) Math.min(1, (cuadro - silencio) / fundido);
			int muestra = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(muestras[i] * v * entrada)));
			bytes[2 * i] = (byte) muestra;
			bytes[2 * i + 1] = (byte) (muestra >> 8);
		}
		cuadrosDesdeArranque += cuadros;
		// Con la placa detenida, write se trabaría si el bloque no entra en el buffer
		if (!linea.isRunning() && linea.available() < cantidad * 2) {
			linea.start();
		}
		linea.write(bytes, 0, cantidad * 2);
		// Arranca la placa recién con medio buffer lleno, así no se queda sin datos en los primeros cuadros
		if (!linea.isRunning() && linea.available() <= linea.getBufferSize() / 2) {
			linea.start();
		}
	}

	/** Corta lo que quedaba en el buffer (al pausar, acelerar o cambiar de juego); al seguir vuelve a entrar suave. */
	void detener() {
		cuadrosDesdeArranque = 0;
		if (linea != null) {
			linea.stop();
			linea.flush();
		}
	}

	void cerrar() {
		if (linea != null) {
			linea.close();
			linea = null;
		}
	}

	private void abrir() {
		try {
			AudioFormat formato = new AudioFormat(frecuencia, 16, 2, true, false);
			int tamanioBuffer = (int) (frecuencia * LATENCIA_SEGUNDOS) * formato.getFrameSize();
			linea = AudioSystem.getSourceDataLine(formato);
			linea.open(formato, tamanioBuffer);
		} catch (Exception | LinkageError e) {
			EmuMod.LOGGER.warn("Sin salida de audio para el televisor; el juego va a sonar mudo", e);
			linea = null;
			disponible = false;
		}
	}
}
