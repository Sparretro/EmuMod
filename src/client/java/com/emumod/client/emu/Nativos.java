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

	/**
	 * Descomprime en la carpeta de sistema los archivos que un núcleo necesita además de su DLL (el zip va en el jar, en
	 * {@code /sistema/}). Se hace una vez por versión del zip: deja una marca con su hash y no pisa lo que el núcleo
	 * guardó después (partidas, configuración), solo los archivos que vienen en el zip.
	 */
	static void extraerSistema(Path carpetaSistema, String zip) throws IOException {
		byte[] datos;
		try (InputStream in = Nativos.class.getResourceAsStream("/sistema/" + zip)) {
			if (in == null) {
				throw new IOException("Falta /sistema/" + zip + " en el mod");
			}
			datos = in.readAllBytes();
		}
		Path marca = carpetaSistema.resolve("." + zip + "-" + hash(datos).substring(0, 16));
		if (Files.exists(marca)) {
			return;
		}
		Path raiz = carpetaSistema.toAbsolutePath().normalize();
		try (java.util.zip.ZipInputStream entrada = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(datos))) {
			for (java.util.zip.ZipEntry e; (e = entrada.getNextEntry()) != null;) {
				Path destino = raiz.resolve(e.getName()).normalize();
				if (!destino.startsWith(raiz)) {
					throw new IOException("Ruta inválida en " + zip + ": " + e.getName());
				}
				if (e.isDirectory()) {
					Files.createDirectories(destino);
				} else {
					Files.createDirectories(destino.getParent());
					Files.copy(entrada, destino, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
		Files.createDirectories(carpetaSistema);
		Files.write(marca, new byte[0]);
	}

	private static String hash(byte[] datos) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
