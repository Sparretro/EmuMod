import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Convierte un modelo GLB (glTF 2.0 binario) al formato liviano que lee Modelo3D del mod.
// Uso: java ConvertirModelo.java <modelo.glb> <carpeta assets/emumod> <nombre> <lado máximo de textura> [carpeta de vistas previas]
//        [--pantalla-oscura]  (pinta de vidrio oscuro el verde croma que algunos modelos usan para la pantalla)
//        [--simplificar <fracción>]  (junta los vértices más cercanos que esa fracción del tamaño del modelo; para modelos
//        con muchísimo detalle que en Minecraft no se ve y que, dibujado cuadro por cuadro, baja los FPS)
//        [--quitar-menores <fracción>]  (descarta las piezas más chicas que esa fracción del modelo, como tornillos)
//        [--simplificar-texturas]  (también simplifica piezas con textura, sin mezclar coordenadas de textura distintas)
//        [--quitar <regex>]  (descarta las piezas cuyo nombre coincide, como un control o cables que vienen con la consola)
//        [--separar <regex>]  (parte esas piezas en sus partes sueltas, <nombre>_0, _1...: para hundir botones que vienen juntos)
//        [--girar-x <grados>]  (gira el modelo alrededor del eje x, para modelos exportados inclinados)
//        [--opaco]  (dibuja todas las piezas opacas, para modelos que marcan como translúcida la carcasa entera)
//        [--z-arriba]  (para modelos exportados con z hacia arriba: queda y arriba y lo que estaba en -y, hacia +z)
//        [--ejes <x,y,z>]  (reacomoda los ejes: por ejemplo "-y,x,z" pone el eje y viejo, dado vuelta, como el x nuevo;
//        tiene que ser un giro, sin espejar)
//
// Si la malla se llama "defaultMaterial" (exportaciones de Sketchfab desde Collada) la pieza toma el nombre de su nodo
// padre, que es el que dice qué es.
//
// Escribe modelos/<nombre>.bin y textures/modelo/<nombre>/<n>.png. Aplica las transformaciones de los nodos de la
// escena, así que los vértices quedan en coordenadas del modelo completo (metros, y hacia arriba). Cada malla del GLB
// queda como una pieza con su nombre, para que el mod pueda mover piezas sueltas (por ejemplo, hundir un botón).
//
// Formato (DataOutputStream, big endian): int "MOD3"; int texturas; por textura un UTF con el nombre del png;
// int piezas; por pieza: UTF nombre, int textura (-1 = sin textura), int color ARGB, boolean translúcida,
// int vértices, por vértice x y z u v nx ny nz (float), int índices, índices (int).
public class ConvertirModelo {
	static ByteBuffer bin;
	static Map<String, Object> gltf;

	public static void main(String[] args) throws Exception {
		byte[] glb = Files.readAllBytes(Path.of(args[0]));
		ByteBuffer bb = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN);
		int largoJson = bb.getInt(12);
		gltf = obj(new Json(new String(glb, 20, largoJson, StandardCharsets.UTF_8)).valor());
		int inicioBin = 20 + largoJson + 8;
		bin = ByteBuffer.wrap(glb, inicioBin, glb.length - inicioBin).slice().order(ByteOrder.LITTLE_ENDIAN);
		File assets = new File(args[1]);
		String nombre = args[2];
		int ladoMaximo = Integer.parseInt(args[3]);
		List<String> resto = new ArrayList<>(List.of(args).subList(4, args.length));
		boolean pantallaOscura = resto.remove("--pantalla-oscura");
		boolean opaco = resto.remove("--opaco");
		boolean zArriba = resto.remove("--z-arriba");
		String[] ejes = null;
		int posEjes = resto.indexOf("--ejes");
		if (posEjes >= 0) {
			ejes = resto.get(posEjes + 1).split(",");
			resto.remove(posEjes + 1);
			resto.remove(posEjes);
		}
		double simplificar = 0;
		int posSimplificar = resto.indexOf("--simplificar");
		if (posSimplificar >= 0) {
			simplificar = Double.parseDouble(resto.get(posSimplificar + 1));
			resto.remove(posSimplificar + 1);
			resto.remove(posSimplificar);
		}

		// Texturas: una por imagen usada como baseColor
		List<Object> materiales = lista(gltf.get("materials"));
		Map<Integer, Integer> texturaDeImagen = new LinkedHashMap<>();
		File carpetaTexturas = new File(assets, "textures/modelo/" + nombre);
		carpetaTexturas.mkdirs();
		List<String> nombresTexturas = new ArrayList<>();

		List<Pieza> piezas = new ArrayList<>();
		List<Object> escenas = lista(gltf.get("scenes"));
		int escena = gltf.containsKey("scene") ? num(gltf.get("scene")).intValue() : 0;
		for (Object n : lista(obj(escenas.get(escena)).get("nodes"))) {
			recorrer(num(n).intValue(), identidad(), piezas, null);
		}
		if (zArriba) {
			for (Pieza p : piezas) {
				for (int i = 0; i < p.datos.length; i += 8) {
					// (x, y, z) -> (x, z, -y), también la normal (giro de -90° alrededor de x: no invierte los triángulos)
					for (int o : new int[] {0, 5}) {
						float y = p.datos[i + o + 1], z = p.datos[i + o + 2];
						p.datos[i + o + 1] = z;
						p.datos[i + o + 2] = -y;
					}
				}
			}
		}
		if (ejes != null) {
			for (Pieza p : piezas) {
				for (int i = 0; i < p.datos.length; i += 8) {
					for (int o : new int[] {0, 5}) {
						float[] viejo = {p.datos[i + o], p.datos[i + o + 1], p.datos[i + o + 2]};
						for (int c = 0; c < 3; c++) {
							String e = ejes[c].trim();
							float v = viejo[e.charAt(e.length() - 1) - 'x'];
							p.datos[i + o + c] = e.startsWith("-") ? -v : v;
						}
					}
				}
			}
		}
		int posGirar = resto.indexOf("--girar-x");
		if (posGirar >= 0) {
			double angulo = Math.toRadians(Double.parseDouble(resto.get(posGirar + 1)));
			float c = (float) Math.cos(angulo), s = (float) Math.sin(angulo);
			for (Pieza p : piezas) {
				for (int i = 0; i < p.datos.length; i += 8) {
					for (int o : new int[] {1, 6}) {
						float y = p.datos[i + o], z = p.datos[i + o + 1];
						p.datos[i + o] = y * c - z * s;
						p.datos[i + o + 1] = y * s + z * c;
					}
				}
			}
			resto.remove(posGirar + 1);
			resto.remove(posGirar);
		}
		int posSeparar = resto.indexOf("--separar");
		if (posSeparar >= 0) {
			String patron = resto.get(posSeparar + 1);
			List<Pieza> separadas = new ArrayList<>();
			for (Pieza p : piezas) {
				separadas.addAll(p.nombre.matches(patron) ? separar(p) : List.of(p));
			}
			piezas = separadas;
			resto.remove(posSeparar + 1);
			resto.remove(posSeparar);
		}
		int posQuitarNombres = resto.indexOf("--quitar");
		if (posQuitarNombres >= 0) {
			String patron = resto.get(posQuitarNombres + 1);
			int antes = piezas.size();
			piezas.removeIf(p -> p.nombre.matches(patron));
			System.out.printf("Piezas quitadas por nombre: %d%n", antes - piezas.size());
			resto.remove(posQuitarNombres + 1);
			resto.remove(posQuitarNombres);
		}

		for (Pieza p : piezas) {
			if (p.material < 0) {
				continue;
			}
			Map<String, Object> material = obj(materiales.get(p.material));
			Map<String, Object> pbr = obj(material.getOrDefault("pbrMetallicRoughness", Map.of()));
			Map<String, Object> especular = obj(obj(material.getOrDefault("extensions", Map.of()))
					.getOrDefault("KHR_materials_pbrSpecularGlossiness", Map.of()));
			Object textura = pbr.containsKey("baseColorTexture") ? pbr.get("baseColorTexture") : especular.get("diffuseTexture");
			List<Object> factor = lista(pbr.containsKey("baseColorFactor") ? pbr.get("baseColorFactor") : especular.get("diffuseFactor"));
			if (factor != null) {
				p.color = (int) Math.round(num(factor.get(3)).doubleValue() * 255) << 24
						| (int) Math.round(num(factor.get(0)).doubleValue() * 255) << 16
						| (int) Math.round(num(factor.get(1)).doubleValue() * 255) << 8
						| (int) Math.round(num(factor.get(2)).doubleValue() * 255);
			}
			p.translucida = "BLEND".equals(material.get("alphaMode")) && !opaco;
			if (textura != null) {
				int indiceTextura = num(obj(textura).get("index")).intValue();
				int imagen = num(obj(lista(gltf.get("textures")).get(indiceTextura)).get("source")).intValue();
				Integer ya = texturaDeImagen.get(imagen);
				if (ya == null) {
					ya = nombresTexturas.size();
					String archivo = ya + ".png";
					BufferedImage png = achicar(leerImagen(imagen), ladoMaximo);
					if (pantallaOscura) {
						apagarCroma(png);
					}
					ImageIO.write(png, "png", new File(carpetaTexturas, archivo));
					nombresTexturas.add(archivo);
					texturaDeImagen.put(imagen, ya);
				}
				p.textura = ya;
			}
		}

		int posQuitar = resto.indexOf("--quitar-menores");
		if (posQuitar >= 0) {
			quitarMenores(piezas, Double.parseDouble(resto.get(posQuitar + 1)));
			resto.remove(posQuitar + 1);
			resto.remove(posQuitar);
		}
		if (simplificar > 0) {
			simplificarPiezas(piezas, simplificar, resto.remove("--simplificar-texturas"));
		}

		new File(assets, "modelos").mkdirs();
		try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(assets, "modelos/" + nombre + ".bin"))))) {
			out.writeInt(0x4D4F4433);
			out.writeInt(nombresTexturas.size());
			for (String t : nombresTexturas) {
				out.writeUTF(t);
			}
			out.writeInt(piezas.size());
			for (Pieza p : piezas) {
				out.writeUTF(p.nombre);
				out.writeInt(p.textura);
				out.writeInt(p.color);
				out.writeBoolean(p.translucida);
				out.writeInt(p.datos.length / 8);
				for (float f : p.datos) {
					out.writeFloat(f);
				}
				out.writeInt(p.indices.length);
				for (int i : p.indices) {
					out.writeInt(i);
				}
			}
		}

		float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		for (Pieza p : piezas) {
			for (int i = 0; i < p.datos.length; i += 8) {
				for (int c = 0; c < 3; c++) {
					min[c] = Math.min(min[c], p.datos[i + c]);
					max[c] = Math.max(max[c], p.datos[i + c]);
				}
			}
		}
		System.out.printf("%s: %d piezas, %d texturas, caja (%.3f %.3f %.3f) a (%.3f %.3f %.3f)%n", nombre, piezas.size(),
				nombresTexturas.size(), min[0], min[1], min[2], max[0], max[1], max[2]);
		for (Pieza p : piezas) {
			float[] pmin = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, pmax = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
			for (int i = 0; i < p.datos.length; i += 8) {
				for (int c = 0; c < 3; c++) {
					pmin[c] = Math.min(pmin[c], p.datos[i + c]);
					pmax[c] = Math.max(pmax[c], p.datos[i + c]);
				}
			}
			System.out.printf("  %-28s tex %2d %s %5d vért  (%.3f %.3f %.3f)-(%.3f %.3f %.3f)%n", p.nombre, p.textura, p.translucida ? "transl" : "      ",
					p.datos.length / 8, pmin[0], pmin[1], pmin[2], pmax[0], pmax[1], pmax[2]);
		}
		if (!resto.isEmpty()) {
			vistasPrevias(piezas, nombresTexturas, carpetaTexturas, min, max, new File(resto.get(0)), nombre);
		}
	}

	static void quitarMenores(List<Pieza> piezas, double fraccion) {
		double tam = 0;
		double[][] cajas = new double[piezas.size()][];
		for (int k = 0; k < piezas.size(); k++) {
			Pieza p = piezas.get(k);
			double[] caja = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
			for (int i = 0; i < p.datos.length; i += 8) {
				for (int c = 0; c < 3; c++) {
					caja[c] = Math.min(caja[c], p.datos[i + c]);
					caja[c + 3] = Math.max(caja[c + 3], p.datos[i + c]);
				}
			}
			cajas[k] = caja;
			for (int c = 0; c < 3; c++) {
				tam = Math.max(tam, caja[c + 3] - caja[c]);
			}
		}
		List<Pieza> quedan = new ArrayList<>();
		for (int k = 0; k < piezas.size(); k++) {
			double[] caja = cajas[k];
			double lado = Math.max(caja[3] - caja[0], Math.max(caja[4] - caja[1], caja[5] - caja[2]));
			if (lado >= tam * fraccion) {
				quedan.add(piezas.get(k));
			}
		}
		System.out.printf("Piezas chicas quitadas: %d%n", piezas.size() - quedan.size());
		piezas.clear();
		piezas.addAll(quedan);
	}

	// Agrupamiento de vértices: cada vértice se pasa al de su celda (posición cuantizada a la celda y normal cuantizada,
	// para no mezclar caras con distinto sombreado); se descartan los triángulos que quedan degenerados o repetidos
	static void simplificarPiezas(List<Pieza> piezas, double fraccion, boolean conTexturas) {
		double tam = 0;
		float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		for (Pieza p : piezas) {
			for (int i = 0; i < p.datos.length; i += 8) {
				for (int c = 0; c < 3; c++) {
					min[c] = Math.min(min[c], p.datos[i + c]);
					max[c] = Math.max(max[c], p.datos[i + c]);
				}
			}
		}
		for (int c = 0; c < 3; c++) {
			tam = Math.max(tam, max[c] - min[c]);
		}
		double celda = tam * fraccion;
		int antes = 0, despues = 0;
		for (Pieza p : piezas) {
			// Las piezas con textura (etiquetas, logos) son livianas y se deformarían: quedan como están, pero se separan un
			// poco hacia afuera porque la carcasa simplificada puede taparlas (estaban pegadas a ella)
			if (p.textura >= 0 && !conTexturas) {
				for (int i = 0; i < p.datos.length; i += 8) {
					for (int c = 0; c < 3; c++) {
						p.datos[i + c] += (float) (p.datos[i + 5 + c] * celda * 0.6);
					}
				}
				antes += p.indices.length / 3;
				despues += p.indices.length / 3;
				continue;
			}
			int n = p.datos.length / 8;
			Map<String, Integer> nuevoDe = new java.util.HashMap<>();
			int[] mapa = new int[n];
			List<Integer> elegidos = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				String clave = Math.round(p.datos[i * 8] / celda) + "," + Math.round(p.datos[i * 8 + 1] / celda) + "," + Math.round(p.datos[i * 8 + 2] / celda)
						+ "|" + Math.round(p.datos[i * 8 + 5] * 2) + "," + Math.round(p.datos[i * 8 + 6] * 2) + "," + Math.round(p.datos[i * 8 + 7] * 2)
						+ (p.textura >= 0 ? "|" + Math.round(p.datos[i * 8 + 3] * 256) + "," + Math.round(p.datos[i * 8 + 4] * 256) : "");
				Integer ya = nuevoDe.get(clave);
				if (ya == null) {
					ya = elegidos.size();
					nuevoDe.put(clave, ya);
					elegidos.add(i);
				}
				mapa[i] = ya;
			}
			float[] datos = new float[elegidos.size() * 8];
			for (int j = 0; j < elegidos.size(); j++) {
				System.arraycopy(p.datos, elegidos.get(j) * 8, datos, j * 8, 8);
			}
			java.util.Set<String> vistos = new java.util.HashSet<>();
			List<Integer> indices = new ArrayList<>();
			for (int t = 0; t + 2 < p.indices.length; t += 3) {
				int a = mapa[p.indices[t]], b = mapa[p.indices[t + 1]], c = mapa[p.indices[t + 2]];
				if (a == b || b == c || a == c) {
					continue;
				}
				int[] orden = {a, b, c};
				java.util.Arrays.sort(orden);
				if (!vistos.add(orden[0] + "," + orden[1] + "," + orden[2])) {
					continue;
				}
				indices.add(a);
				indices.add(b);
				indices.add(c);
			}
			antes += p.indices.length / 3;
			despues += indices.size() / 3;
			p.datos = datos;
			p.indices = indices.stream().mapToInt(Integer::intValue).toArray();
		}
		piezas.removeIf(p -> p.indices.length == 0);
		System.out.printf("Simplificado: %d -> %d triángulos%n", antes, despues);
	}

	// Parte una pieza en sus partes sueltas (las que no comparten vértices; dos vértices en el mismo lugar cuentan como el
	// mismo, porque las costuras de la textura los duplican). Se llaman <nombre>_0, _1... de izquierda a derecha y de atrás
	// hacia adelante, por el centro de su caja
	static List<Pieza> separar(Pieza p) {
		int n = p.datos.length / 8;
		int[] padre = new int[n];
		for (int i = 0; i < n; i++) {
			padre[i] = i;
		}
		Map<String, Integer> lugar = new java.util.HashMap<>();
		for (int i = 0; i < n; i++) {
			String clave = Math.round(p.datos[i * 8] * 1e4) + "," + Math.round(p.datos[i * 8 + 1] * 1e4) + "," + Math.round(p.datos[i * 8 + 2] * 1e4);
			Integer otro = lugar.putIfAbsent(clave, i);
			if (otro != null) {
				unir(padre, i, otro);
			}
		}
		for (int t = 0; t + 2 < p.indices.length; t += 3) {
			unir(padre, p.indices[t], p.indices[t + 1]);
			unir(padre, p.indices[t], p.indices[t + 2]);
		}
		Map<Integer, List<Integer>> grupos = new LinkedHashMap<>();
		for (int t = 0; t + 2 < p.indices.length; t += 3) {
			grupos.computeIfAbsent(raiz(padre, p.indices[t]), k -> new ArrayList<>()).add(t);
		}
		List<Pieza> partes = new ArrayList<>();
		for (List<Integer> triangulos : grupos.values()) {
			Map<Integer, Integer> nuevo = new LinkedHashMap<>();
			int[] indices = new int[triangulos.size() * 3];
			int k = 0;
			for (int t : triangulos) {
				for (int j = 0; j < 3; j++) {
					indices[k++] = nuevo.computeIfAbsent(p.indices[t + j], v -> nuevo.size());
				}
			}
			float[] datos = new float[nuevo.size() * 8];
			for (var e : nuevo.entrySet()) {
				System.arraycopy(p.datos, e.getKey() * 8, datos, e.getValue() * 8, 8);
			}
			Pieza parte = new Pieza();
			parte.material = p.material;
			parte.textura = p.textura;
			parte.color = p.color;
			parte.translucida = p.translucida;
			parte.datos = datos;
			parte.indices = indices;
			partes.add(parte);
		}
		partes.sort(java.util.Comparator.<Pieza>comparingDouble(q -> Math.round(centro(q, 0) * 10)).thenComparingDouble(q -> centro(q, 2)));
		for (int i = 0; i < partes.size(); i++) {
			partes.get(i).nombre = p.nombre + "_" + i;
		}
		return partes;
	}

	static double centro(Pieza p, int eje) {
		double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
		for (int i = eje; i < p.datos.length; i += 8) {
			min = Math.min(min, p.datos[i]);
			max = Math.max(max, p.datos[i]);
		}
		return (min + max) / 2;
	}

	static int raiz(int[] padre, int i) {
		while (padre[i] != i) {
			padre[i] = padre[padre[i]];
			i = padre[i];
		}
		return i;
	}

	static void unir(int[] padre, int a, int b) {
		padre[raiz(padre, a)] = raiz(padre, b);
	}

	static final class Pieza {
		String nombre;
		int material = -1, textura = -1, color = 0xFFFFFFFF;
		boolean translucida;
		float[] datos;
		int[] indices;
	}

	static void recorrer(int indiceNodo, double[] padre, List<Pieza> piezas, String nombrePadre) {
		Map<String, Object> nodo = obj(lista(gltf.get("nodes")).get(indiceNodo));
		double[] m = multiplicar(padre, local(nodo));
		if (nodo.containsKey("mesh")) {
			Map<String, Object> malla = obj(lista(gltf.get("meshes")).get(num(nodo.get("mesh")).intValue()));
			String nombre = String.valueOf(malla.getOrDefault("name", nodo.getOrDefault("name", "pieza")));
			if (nombre.equals("defaultMaterial") && nombrePadre != null) {
				nombre = nombrePadre;
			}
			for (Object primitiva : lista(malla.get("primitives"))) {
				Map<String, Object> prim = obj(primitiva);
				Map<String, Object> atributos = obj(prim.get("attributes"));
				float[] pos = leerFloats(num(atributos.get("POSITION")).intValue(), 3);
				float[] nor = atributos.containsKey("NORMAL") ? leerFloats(num(atributos.get("NORMAL")).intValue(), 3) : new float[pos.length];
				float[] uv = atributos.containsKey("TEXCOORD_0") ? leerFloats(num(atributos.get("TEXCOORD_0")).intValue(), 2) : new float[pos.length / 3 * 2];
				int n = pos.length / 3;
				Pieza p = new Pieza();
				p.nombre = nombre;
				p.material = prim.containsKey("material") ? num(prim.get("material")).intValue() : -1;
				p.datos = new float[n * 8];
				for (int i = 0; i < n; i++) {
					double x = pos[i * 3], y = pos[i * 3 + 1], z = pos[i * 3 + 2];
					p.datos[i * 8] = (float) (m[0] * x + m[4] * y + m[8] * z + m[12]);
					p.datos[i * 8 + 1] = (float) (m[1] * x + m[5] * y + m[9] * z + m[13]);
					p.datos[i * 8 + 2] = (float) (m[2] * x + m[6] * y + m[10] * z + m[14]);
					p.datos[i * 8 + 3] = uv[i * 2];
					p.datos[i * 8 + 4] = uv[i * 2 + 1];
					// Normales con la parte de rotación (las escalas de estos modelos son uniformes)
					double nx = nor[i * 3], ny = nor[i * 3 + 1], nz = nor[i * 3 + 2];
					double tx = m[0] * nx + m[4] * ny + m[8] * nz, ty = m[1] * nx + m[5] * ny + m[9] * nz, tz = m[2] * nx + m[6] * ny + m[10] * nz;
					double largo = Math.sqrt(tx * tx + ty * ty + tz * tz);
					if (largo > 0) {
						tx /= largo;
						ty /= largo;
						tz /= largo;
					}
					p.datos[i * 8 + 5] = (float) tx;
					p.datos[i * 8 + 6] = (float) ty;
					p.datos[i * 8 + 7] = (float) tz;
				}
				if (prim.containsKey("indices")) {
					p.indices = leerIndices(num(prim.get("indices")).intValue());
				} else {
					p.indices = new int[n];
					for (int i = 0; i < n; i++) {
						p.indices[i] = i;
					}
				}
				// Si la transformación invierte (escala negativa), se da vuelta el orden de los triángulos
				if (determinante(m) < 0) {
					for (int t = 0; t + 2 < p.indices.length; t += 3) {
						int aux = p.indices[t + 1];
						p.indices[t + 1] = p.indices[t + 2];
						p.indices[t + 2] = aux;
					}
				}
				piezas.add(p);
			}
		}
		for (Object hijo : lista(nodo.getOrDefault("children", List.of()))) {
			recorrer(num(hijo).intValue(), m, piezas, String.valueOf(nodo.getOrDefault("name", nombrePadre)));
		}
	}

	// ---- Accessors ----

	static float[] leerFloats(int accessor, int componentes) {
		Map<String, Object> a = obj(lista(gltf.get("accessors")).get(accessor));
		Map<String, Object> vista = obj(lista(gltf.get("bufferViews")).get(num(a.get("bufferView")).intValue()));
		int cantidad = num(a.get("count")).intValue();
		int base = num(vista.getOrDefault("byteOffset", 0)).intValue() + num(a.getOrDefault("byteOffset", 0)).intValue();
		int tipo = num(a.get("componentType")).intValue();
		int tamanio = tipo == 5126 ? 4 : tipo == 5123 || tipo == 5122 ? 2 : 1;
		int paso = vista.containsKey("byteStride") ? num(vista.get("byteStride")).intValue() : tamanio * componentes;
		boolean normalizado = Boolean.TRUE.equals(a.get("normalized"));
		float[] r = new float[cantidad * componentes];
		for (int i = 0; i < cantidad; i++) {
			for (int c = 0; c < componentes; c++) {
				int o = base + i * paso + c * tamanio;
				r[i * componentes + c] = switch (tipo) {
					case 5126 -> bin.getFloat(o);
					case 5123 -> normalizado ? (bin.getShort(o) & 0xFFFF) / 65535f : bin.getShort(o) & 0xFFFF;
					case 5122 -> normalizado ? Math.max(bin.getShort(o) / 32767f, -1) : bin.getShort(o);
					case 5121 -> normalizado ? (bin.get(o) & 0xFF) / 255f : bin.get(o) & 0xFF;
					default -> normalizado ? Math.max(bin.get(o) / 127f, -1) : bin.get(o);
				};
			}
		}
		return r;
	}

	static int[] leerIndices(int accessor) {
		Map<String, Object> a = obj(lista(gltf.get("accessors")).get(accessor));
		Map<String, Object> vista = obj(lista(gltf.get("bufferViews")).get(num(a.get("bufferView")).intValue()));
		int cantidad = num(a.get("count")).intValue();
		int base = num(vista.getOrDefault("byteOffset", 0)).intValue() + num(a.getOrDefault("byteOffset", 0)).intValue();
		int tipo = num(a.get("componentType")).intValue();
		int[] r = new int[cantidad];
		for (int i = 0; i < cantidad; i++) {
			r[i] = switch (tipo) {
				case 5125 -> bin.getInt(base + i * 4);
				case 5123 -> bin.getShort(base + i * 2) & 0xFFFF;
				default -> bin.get(base + i) & 0xFF;
			};
		}
		return r;
	}

	static BufferedImage leerImagen(int imagen) throws Exception {
		Map<String, Object> img = obj(lista(gltf.get("images")).get(imagen));
		Map<String, Object> vista = obj(lista(gltf.get("bufferViews")).get(num(img.get("bufferView")).intValue()));
		int inicio = num(vista.getOrDefault("byteOffset", 0)).intValue();
		byte[] datos = new byte[num(vista.get("byteLength")).intValue()];
		bin.get(inicio, datos);
		BufferedImage leida = ImageIO.read(new ByteArrayInputStream(datos));
		BufferedImage argb = new BufferedImage(leida.getWidth(), leida.getHeight(), BufferedImage.TYPE_INT_ARGB);
		argb.getGraphics().drawImage(leida, 0, 0, null);
		return argb;
	}

	static void apagarCroma(BufferedImage textura) {
		for (int y = 0; y < textura.getHeight(); y++) {
			for (int x = 0; x < textura.getWidth(); x++) {
				int c = textura.getRGB(x, y);
				int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
				if (g > 150 && g > r + 60 && g > b + 60) {
					textura.setRGB(x, y, 0xFF141816);
				}
			}
		}
	}

	static BufferedImage achicar(BufferedImage original, int ladoMaximo) {
		int ancho = original.getWidth(), alto = original.getHeight();
		double escala = Math.min(1, ladoMaximo / (double) Math.max(ancho, alto));
		if (escala >= 1) {
			return original;
		}
		BufferedImage chica = new BufferedImage((int) (ancho * escala), (int) (alto * escala), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = chica.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(original, 0, 0, chica.getWidth(), chica.getHeight(), null);
		g.dispose();
		return chica;
	}

	// ---- Matrices 4x4 (orden de columnas, como glTF) ----

	static double[] identidad() {
		return new double[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
	}

	static double[] local(Map<String, Object> nodo) {
		if (nodo.containsKey("matrix")) {
			List<Object> l = lista(nodo.get("matrix"));
			double[] m = new double[16];
			for (int i = 0; i < 16; i++) {
				m[i] = num(l.get(i)).doubleValue();
			}
			return m;
		}
		double[] t = vector(nodo.get("translation"), new double[] {0, 0, 0});
		double[] q = vector(nodo.get("rotation"), new double[] {0, 0, 0, 1});
		double[] s = vector(nodo.get("scale"), new double[] {1, 1, 1});
		double x = q[0], y = q[1], z = q[2], w = q[3];
		double[] r = {
				1 - 2 * (y * y + z * z), 2 * (x * y + z * w), 2 * (x * z - y * w), 0,
				2 * (x * y - z * w), 1 - 2 * (x * x + z * z), 2 * (y * z + x * w), 0,
				2 * (x * z + y * w), 2 * (y * z - x * w), 1 - 2 * (x * x + y * y), 0,
				t[0], t[1], t[2], 1};
		for (int c = 0; c < 3; c++) {
			for (int f = 0; f < 3; f++) {
				r[c * 4 + f] *= s[c];
			}
		}
		return r;
	}

	static double[] vector(Object valor, double[] porDefecto) {
		if (valor == null) {
			return porDefecto;
		}
		List<Object> l = lista(valor);
		double[] r = new double[l.size()];
		for (int i = 0; i < r.length; i++) {
			r[i] = num(l.get(i)).doubleValue();
		}
		return r;
	}

	static double[] multiplicar(double[] a, double[] b) {
		double[] r = new double[16];
		for (int c = 0; c < 4; c++) {
			for (int f = 0; f < 4; f++) {
				double suma = 0;
				for (int k = 0; k < 4; k++) {
					suma += a[k * 4 + f] * b[c * 4 + k];
				}
				r[c * 4 + f] = suma;
			}
		}
		return r;
	}

	static double determinante(double[] m) {
		return m[0] * (m[5] * m[10] - m[9] * m[6]) - m[4] * (m[1] * m[10] - m[9] * m[2]) + m[8] * (m[1] * m[6] - m[5] * m[2]);
	}

	// ---- Vistas previas por software, para revisar la orientación ----

	static void vistasPrevias(List<Pieza> piezas, List<String> texturas, File carpetaTexturas, float[] min, float[] max, File salida,
			String nombre) throws Exception {
		salida.mkdirs();
		List<BufferedImage> imagenes = new ArrayList<>();
		for (String t : texturas) {
			imagenes.add(ImageIO.read(new File(carpetaTexturas, t)));
		}
		// Frente (+z hacia la cámara), costado derecho (+x), arriba (+y)
		String[] vistas = {"frente", "derecha", "arriba", "atras"};
		for (String vista : vistas) {
			int lado = 600;
			BufferedImage out = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_RGB);
			double[] z = new double[lado * lado];
			java.util.Arrays.fill(z, -1e30);
			for (int i = 0; i < z.length; i++) {
				out.setRGB(i % lado, i / lado, 0x404040);
			}
			double tam = 0;
			for (int c = 0; c < 3; c++) {
				tam = Math.max(tam, max[c] - min[c]);
			}
			double escala = lado * 0.9 / tam;
			double cx = (min[0] + max[0]) / 2, cy = (min[1] + max[1]) / 2, cz = (min[2] + max[2]) / 2;
			for (Pieza p : piezas) {
				int n = p.datos.length / 8;
				double[][] s = new double[n][3];
				for (int i = 0; i < n; i++) {
					double x = p.datos[i * 8] - cx, y = p.datos[i * 8 + 1] - cy, zz = p.datos[i * 8 + 2] - cz;
					double sx, sy, sz;
					switch (vista) {
						case "frente" -> { sx = x; sy = y; sz = zz; }
						case "atras" -> { sx = -x; sy = y; sz = -zz; }
						case "derecha" -> { sx = -zz; sy = y; sz = x; }
						default -> { sx = x; sy = -zz; sz = y; }
					}
					s[i][0] = lado / 2.0 + sx * escala;
					s[i][1] = lado / 2.0 - sy * escala;
					s[i][2] = sz;
				}
				BufferedImage tex = p.textura >= 0 ? imagenes.get(p.textura) : null;
				for (int t = 0; t + 2 < p.indices.length; t += 3) {
					int a = p.indices[t], b = p.indices[t + 1], c = p.indices[t + 2];
					double x0 = s[a][0], y0 = s[a][1], x1 = s[b][0], y1 = s[b][1], x2 = s[c][0], y2 = s[c][1];
					double area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
					if (Math.abs(area) < 1e-9) {
						continue;
					}
					int minx = (int) Math.max(0, Math.floor(Math.min(x0, Math.min(x1, x2)))), maxx = (int) Math.min(lado - 1, Math.ceil(Math.max(x0, Math.max(x1, x2))));
					int miny = (int) Math.max(0, Math.floor(Math.min(y0, Math.min(y1, y2)))), maxy = (int) Math.min(lado - 1, Math.ceil(Math.max(y0, Math.max(y1, y2))));
					for (int py = miny; py <= maxy; py++) {
						for (int px = minx; px <= maxx; px++) {
							double fx = px + .5, fy = py + .5;
							double w0 = ((x1 - fx) * (y2 - fy) - (x2 - fx) * (y1 - fy)) / area;
							double w1 = ((x2 - fx) * (y0 - fy) - (x0 - fx) * (y2 - fy)) / area;
							double w2 = 1 - w0 - w1;
							if (w0 < 0 || w1 < 0 || w2 < 0) {
								continue;
							}
							double zz = w0 * s[a][2] + w1 * s[b][2] + w2 * s[c][2];
							if (zz <= z[py * lado + px]) {
								continue;
							}
							int color = p.color;
							if (tex != null) {
								double u = w0 * p.datos[a * 8 + 3] + w1 * p.datos[b * 8 + 3] + w2 * p.datos[c * 8 + 3];
								double v = w0 * p.datos[a * 8 + 4] + w1 * p.datos[b * 8 + 4] + w2 * p.datos[c * 8 + 4];
								u = u - Math.floor(u);
								v = v - Math.floor(v);
								color = tex.getRGB((int) (u * (tex.getWidth() - 1)), (int) (v * (tex.getHeight() - 1)));
							}
							if ((color >>> 24) < 128) {
								continue;
							}
							z[py * lado + px] = zz;
							out.setRGB(px, py, color);
						}
					}
				}
			}
			ImageIO.write(out, "png", new File(salida, nombre + "_" + vista + ".png"));
		}
	}

	// ---- JSON mínimo (objetos, listas, textos, números, true\/false\/null) ----

	@SuppressWarnings("unchecked")
	static Map<String, Object> obj(Object o) {
		return (Map<String, Object>) o;
	}

	@SuppressWarnings("unchecked")
	static List<Object> lista(Object o) {
		return (List<Object>) o;
	}

	static Number num(Object o) {
		return (Number) o;
	}

	static final class Json {
		final String s;
		int i;

		Json(String s) {
			this.s = s;
		}

		Object valor() {
			espacios();
			char c = s.charAt(i);
			if (c == '{') {
				Map<String, Object> m = new LinkedHashMap<>();
				i++;
				espacios();
				if (s.charAt(i) == '}') {
					i++;
					return m;
				}
				while (true) {
					espacios();
					String clave = (String) valor();
					espacios();
					i++; // :
					m.put(clave, valor());
					espacios();
					if (s.charAt(i++) == '}') {
						return m;
					}
				}
			}
			if (c == '[') {
				List<Object> l = new ArrayList<>();
				i++;
				espacios();
				if (s.charAt(i) == ']') {
					i++;
					return l;
				}
				while (true) {
					l.add(valor());
					espacios();
					if (s.charAt(i++) == ']') {
						return l;
					}
				}
			}
			if (c == '"') {
				StringBuilder b = new StringBuilder();
				i++;
				while (s.charAt(i) != '"') {
					char d = s.charAt(i++);
					if (d == '\\') {
						char e = s.charAt(i++);
						switch (e) {
							case 'n' -> b.append('\n');
							case 't' -> b.append('\t');
							case 'u' -> {
								b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
								i += 4;
							}
							default -> b.append(e);
						}
					} else {
						b.append(d);
					}
				}
				i++;
				return b.toString();
			}
			if (s.startsWith("true", i)) {
				i += 4;
				return Boolean.TRUE;
			}
			if (s.startsWith("false", i)) {
				i += 5;
				return Boolean.FALSE;
			}
			if (s.startsWith("null", i)) {
				i += 4;
				return null;
			}
			int inicio = i;
			while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
				i++;
			}
			return Double.parseDouble(s.substring(inicio, i));
		}

		void espacios() {
			while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
				i++;
			}
		}
	}
}
