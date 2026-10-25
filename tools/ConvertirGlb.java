import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

// Convierte "Game Boy Retreat" (ForkyForklift, CC-BY-4.0, Sketchfab) de GLB al formato liviano que lee el mod.
// Uso: java ConvertirGlb.java <game_boy_retreat.glb> <carpeta assets/emumod>
//
// Escribe modelos/gba.bin (dos mallas: consola y cartucho) y textures/modelo/gba_cuerpo.png y gba_cartucho.png.
// Los offsets son los del GLB descargado de Sketchfab (ver su JSON). La textura de la consola se achica a 2048,
// se le multiplica la oclusión ambiental y se borra la captura de juego de la pantalla (el mod dibuja ahí el juego).
// Cada vértice lleva un grupo: 0 carcasa, 1 A, 2 B, 3 cruz, 4 Start, 5 Select (los botones se hunden al apretarlos).
public class ConvertirGlb {
	// Botones en coordenadas del modelo: x a la derecha, z hacia arriba, el frente mira a -y
	static final double[][] BOTONES = {
			// grupo, x, z, radio, y máxima (solo la tapa, que sobresale del frente)
			{1, 1.0, 0.17, 0.12, -0.125},
			{2, 0.77, 0.118, 0.12, -0.125},
			{3, -0.8735, 0.135, 0.16, -0.13},
			{4, -0.738, -0.241, 0.05, -0.13},
			{5, -0.738, -0.341, 0.05, -0.13},
	};
	// Captura de juego dentro de la textura (en píxeles de la textura de 4096)
	static final int CAPTURA_X0 = 1285, CAPTURA_Y0 = 606, CAPTURA_X1 = 1949, CAPTURA_Y1 = 1601;

	static ByteBuffer bb;
	static int bin;

	public static void main(String[] args) throws Exception {
		byte[] glb = Files.readAllBytes(Path.of(args[0]));
		bb = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN);
		bin = 20 + bb.getInt(12) + 8;
		File assets = new File(args[1]);
		new File(assets, "modelos").mkdirs();
		new File(assets, "textures/modelo").mkdirs();

		try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(assets, "modelos/gba.bin"))))) {
			out.writeInt(0x47424133); // "GBA3"
			// Consola: posiciones bv2+0, normales bv2+45948, uv bv1+0, índices bv0+0
			malla(out, 3829, 0, 45948, 0, 11544, 0, true);
			// Cartucho: posiciones bv2+91896, normales bv2+94056, uv bv1+30632, índices bv0+46176
			malla(out, 180, 91896, 94056, 30632, 504, 46176, false);
		}

		BufferedImage difusa = imagen(240624, 8648882);
		BufferedImage oclusion = imagen(31289595, 4205855);
		for (int y = 0; y < difusa.getHeight(); y++) {
			for (int x = 0; x < difusa.getWidth(); x++) {
				int c = difusa.getRGB(x, y);
				double ao = (oclusion.getRGB(x, y) & 0xFF) / 255.0;
				if (x >= CAPTURA_X0 && x < CAPTURA_X1 && y >= CAPTURA_Y0 && y < CAPTURA_Y1) {
					c = 0x1c211f; // pantalla apagada
					ao = 1;
				}
				int r = (int) (((c >> 16) & 0xFF) * ao), g = (int) (((c >> 8) & 0xFF) * ao), b = (int) ((c & 0xFF) * ao);
				difusa.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
			}
		}
		ImageIO.write(achicar(difusa, 2048), "png", new File(assets, "textures/modelo/gba_cuerpo.png"));
		ImageIO.write(achicar(imagen(35495450, 573517), 512), "png", new File(assets, "textures/modelo/gba_cartucho.png"));
	}

	static void malla(DataOutputStream out, int n, int offPos, int offNormal, int offUv, int nIndices, int offIndices, boolean conGrupos) throws Exception {
		int pos = bin + 80264, uv = bin + 48192, idx = bin;
		out.writeInt(n);
		int[] porGrupo = new int[6];
		double[][] centro = new double[6][2];
		for (int i = 0; i < n; i++) {
			float x = bb.getFloat(pos + offPos + i * 12), y = bb.getFloat(pos + offPos + i * 12 + 4), z = bb.getFloat(pos + offPos + i * 12 + 8);
			out.writeFloat(x);
			out.writeFloat(y);
			out.writeFloat(z);
			out.writeFloat(bb.getFloat(uv + offUv + i * 8));
			out.writeFloat(bb.getFloat(uv + offUv + i * 8 + 4));
			for (int c = 0; c < 3; c++) {
				out.writeFloat(bb.getFloat(pos + offNormal + i * 12 + c * 4));
			}
			int grupo = 0;
			if (conGrupos) {
				for (double[] boton : BOTONES) {
					double dx = x - boton[1], dz = z - boton[2];
					if (dx * dx + dz * dz < boton[3] * boton[3] && y < boton[4]) {
						grupo = (int) boton[0];
					}
				}
			}
			porGrupo[grupo]++;
			centro[grupo][0] += x;
			centro[grupo][1] += z;
			out.writeByte(grupo);
		}
		out.writeInt(nIndices);
		for (int i = 0; i < nIndices; i++) {
			out.writeInt(bb.getInt(idx + offIndices + i * 4));
		}
		for (int g = 1; g < 6 && conGrupos; g++) {
			System.out.printf("grupo %d: %d vértices, centro x=%.4f z=%.4f%n", g, porGrupo[g], centro[g][0] / porGrupo[g], centro[g][1] / porGrupo[g]);
		}
	}

	static BufferedImage imagen(int offset, int largo) throws Exception {
		byte[] png = new byte[largo];
		bb.get(bin + offset, png);
		BufferedImage leida = ImageIO.read(new ByteArrayInputStream(png));
		BufferedImage rgb = new BufferedImage(leida.getWidth(), leida.getHeight(), BufferedImage.TYPE_INT_RGB);
		rgb.getGraphics().drawImage(leida, 0, 0, null);
		return rgb;
	}

	static BufferedImage achicar(BufferedImage original, int lado) {
		BufferedImage chica = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = chica.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(original, 0, 0, lado, lado, null);
		g.dispose();
		return chica;
	}
}
