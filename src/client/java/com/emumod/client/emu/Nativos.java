package com.emumod.client.emu;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.sun.jna.Platform;

// Extrae la DLL de un núcleo empaquetada en el jar a una carpeta local para poder cargarla
final class Nativos {
	private Nativos() {
	}

	static String plataforma() {
		if (Platform.isWindows() && Platform.is64Bit() && !Platform.isARM()) {
			return "windows-x64";
		}
		throw new UnsupportedOperationException("Los núcleos todavía no están compilados para " + Platform.RESOURCE_PREFIX);
	}

	static Path extraer(Path carpeta, String biblioteca) throws IOException {
		return extraer(carpeta, biblioteca, biblioteca);
	}

	/**
	 * Como {@link #extraer(Path, String)}, pero guardando la DLL con otro nombre: Windows carga una vez cada archivo, así que
	 * dos copias con nombres distintos son dos módulos con su propio estado (el puente atiende un solo núcleo).
	 */
	static Path extraer(Path carpeta, String biblioteca, String nombre) throws IOException {
		String recurso = "/natives/" + plataforma() + "/" + System.mapLibraryName(biblioteca);
		byte[] datos;
		try (InputStream in = Nativos.class.getResourceAsStream(recurso)) {
			if (in == null) {
				throw new IOException("Falta " + recurso + " en el mod");
			}
			datos = in.readAllBytes();
		}
		// El nombre lleva el hash para no pisar una DLL en uso por otra versión del mod
		Path destino = carpeta.resolve(nombre + "-" + hash(datos).substring(0, 16) + ".dll");
		if (!Files.exists(destino)) {
			Files.createDirectories(carpeta);
			Path temporal = Files.createTempFile(carpeta, nombre, ".tmp");
			Files.write(temporal, datos);
			Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
		}
		return destino;
	}

	private static String hash(byte[] datos) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
