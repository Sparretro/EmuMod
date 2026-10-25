package com.emumod.client;

import com.emumod.ModBlocks;
import com.emumod.N64BlockEntity;
import com.emumod.ConsolaTeleBlockEntity;
import com.emumod.client.emu.Sistema;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Consolas que se juegan sentado frente al televisor CRT: su modelo 3D apoyado en el bloque, dónde tiene los conectores
 * (en coordenadas del modelo) para dibujar los cables y el control que se tiene en las manos.
 */
enum ConsolaTele {
	// NES de A.stark (CC BY 4.0): el frente del modelo mira a -z; la salida de video atrás y el puerto del control 1 adelante.
	// Su control se dibuja aparte (ManosNes), con los botones que se hunden
	NES(Sistema.NES, "nes", 0.75f, 180, new float[] {-1.47f, 0.5f, 1.84f}, new float[] {-1.43f, 0.46f, -1.92f}, null),
	// Nintendo 64 de Ethanboor (CC BY 4.0): el frente mira a +z; la salida AV atrás, al costado de la fuente, y el puerto 1
	// es el de más a la izquierda mirando de frente
	N64(Sistema.N64, "n64", 0.78f, 0, new float[] {0.42f, -0.12f, -0.84f}, new float[] {-0.19f, -0.1f, -0.1f}, ManosControl.N64),
	// PS2 clásica de jei3323 y PS2 Slim de alexdperdomo (CC BY 4.0), acostadas, con el frente a +z: la salida AV atrás a la
	// izquierda (mirando de frente) y el puerto 1 adelante a la izquierda
	PS2(Sistema.PS2, "ps2", 0.88f, 0, new float[] {-8f, 0, -9.3f}, new float[] {-10.4f, 0.67f, 9.4f}, ManosControl.PS2),
	PS2_SLIM(Sistema.PS2, "ps2_slim", 0.67f, 0, new float[] {-1.19f, 1.09f, -2.23f}, new float[] {-2.56f, 0.91f, 2.23f}, ManosControl.PS2),
	// PS3 clásica de jei3323 y PS3 Slim de Vasily (CC BY 4.0): el frente (la ranura del disco) mira a -z; la salida HDMI
	// atrás al medio y el control por USB adelante a la izquierda (mirando de frente, +x)
	PS3(Sistema.PS3, "ps3", 0.95f, 180, new float[] {-0.018f, -0.01f, 0.15f}, new float[] {0.14f, -0.03f, -0.128f}, ManosControl.PS3),
	PS3_SLIM(Sistema.PS3, "ps3_slim", 0.85f, 180, new float[] {0.019f, -0.019f, 0.184f}, new float[] {0.15f, -0.01f, -0.202f}, ManosControl.PS3),
	// Wii de elouisetrewartha (CC BY-NC 4.0), parada en su base con el frente a +z; la salida AV atrás. El Wiimote es
	// inalámbrico: no hay cable del control
	WII(Sistema.WII, "wii", 0.13f, 0, new float[] {0.5f, 3f, -10.3f}, null, ManosControl.WII);

	final Sistema sistema;
	final String modelo;
	/** Ancho de la consola en bloques. */
	final float ancho;
	/** Giro para que el frente del modelo quede para el lado del bloque que mira al jugador. */
	final float giroFrente;
	final float[] salidaVideo;
	final float[] puertoControl;
	/** El control en las manos, o null para la NES. */
	final ManosControl manos;

	ConsolaTele(Sistema sistema, String modelo, float ancho, float giroFrente, float[] salidaVideo, float[] puertoControl, ManosControl manos) {
		this.sistema = sistema;
		this.modelo = modelo;
		this.ancho = ancho;
		this.giroFrente = giroFrente;
		this.salidaVideo = salidaVideo;
		this.puertoControl = puertoControl;
		this.manos = manos;
	}

	static ConsolaTele de(BlockEntity consola) {
		if (consola instanceof N64BlockEntity) {
			return N64;
		}
		if (consola instanceof ConsolaTeleBlockEntity) {
			BlockState estado = consola.getBlockState();
			if (estado.is(ModBlocks.PS3)) {
				return PS3;
			}
			if (estado.is(ModBlocks.PS3_SLIM)) {
				return PS3_SLIM;
			}
			if (estado.is(ModBlocks.WII)) {
				return WII;
			}
			return estado.is(ModBlocks.PS2_SLIM) ? PS2_SLIM : PS2;
		}
		return NES;
	}

	EmuladorCliente emulador() {
		return EmuladorCliente.de(sistema);
	}
}
