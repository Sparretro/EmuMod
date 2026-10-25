import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

// Genera las texturas 16x16 del bloque televisor. Uso: java GenTexturas.java <carpeta destino>
public class GenTexturas {
	static final int CARCASA = 0x4a4d52, CARCASA_CLARA = 0x5c6066, CARCASA_OSCURA = 0x34363a, CONTORNO = 0x1e1f22;

	public static void main(String[] args) throws Exception {
		File dir = new File(args[0]);
		dir.mkdirs();
		ImageIO.write(frente(), "png", new File(dir, "televisor_front.png"));
		ImageIO.write(costado(), "png", new File(dir, "televisor_side.png"));
		ImageIO.write(arriba(), "png", new File(dir, "televisor_top.png"));
	}

	// Carcasa con un ruido sutil para que no quede plana, como las texturas vanilla
	static BufferedImage carcasa(long seed) {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Random r = new Random(seed);
		for (int y = 0; y < 16; y++)
			for (int x = 0; x < 16; x++) {
				int c = switch (r.nextInt(6)) { case 0 -> CARCASA_CLARA; case 1 -> CARCASA_OSCURA; default -> CARCASA; };
				if (x == 0 || y == 0 || x == 15 || y == 15) c = CONTORNO;
				set(img, x, y, c);
			}
		return img;
	}

	static BufferedImage frente() {
		BufferedImage img = carcasa(1);
		// Marco de la pantalla
		for (int y = 1; y <= 12; y++)
			for (int x = 1; x <= 14; x++)
				if (y == 1 || y == 12 || x == 1 || x == 14) set(img, x, y, CONTORNO);
		// Pantalla apagada: verde azulado oscuro con degradé vertical
		for (int y = 2; y <= 11; y++)
			for (int x = 2; x <= 13; x++)
				set(img, x, y, mezcla(0x22363a, 0x10191b, (y - 2) / 9.0));
		// Reflejo diagonal
		for (int i = 0; i < 4; i++) set(img, 3 + i, 6 - i, 0x3d5a5f);
		for (int i = 0; i < 2; i++) set(img, 3 + i, 8 - i, 0x2f4a4e);
		// Panel inferior: perillas y LED rojo
		set(img, 3, 14, CARCASA_OSCURA);
		set(img, 5, 14, CARCASA_OSCURA);
		set(img, 12, 14, 0xd23b2f);
		set(img, 13, 14, 0x7a1f19);
		return img;
	}

	static BufferedImage costado() {
		BufferedImage img = carcasa(2);
		// Rejillas de ventilación horizontales
		for (int y = 4; y <= 11; y += 2)
			for (int x = 3; x <= 12; x++)
				set(img, x, y, CONTORNO);
		return img;
	}

	static BufferedImage arriba() {
		BufferedImage img = carcasa(3);
		// Ranuras verticales cortas
		for (int x = 3; x <= 12; x += 3)
			for (int y = 4; y <= 11; y++)
				set(img, x, y, CONTORNO);
		return img;
	}

	static void set(BufferedImage img, int x, int y, int rgb) {
		img.setRGB(x, y, 0xff000000 | rgb);
	}

	static int mezcla(int a, int b, double t) {
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return (r << 16) | (g << 8) | bl;
	}
}
