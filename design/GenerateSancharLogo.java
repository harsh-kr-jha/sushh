import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public final class GenerateSancharLogo {

	public static void main(String[] args) throws Exception {
		int[] densities = {48, 72, 96, 144, 192};
		String[] folders = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
		for (int i = 0; i < densities.length; i++) {
			writeLogo(densities[i], folders[i], "ic_launcher.png");
			writeLogo(densities[i], folders[i], "ic_launcher_round.png");
		}
	}

	private static void writeLogo(int size, String density, String name)
			throws Exception {
		BufferedImage image =
				new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(Color.WHITE);
		graphics.fillRect(0, 0, size, size);
		float scale = size / 108f;
		int diameter = Math.round(48 * scale);
		int offset = (size - diameter) / 2;
		graphics.setColor(new Color(0x19, 0x6f, 0xde));
		graphics.fillOval(offset, offset, diameter, diameter);
		graphics.dispose();
		File output = new File("briar-android/src/main/res/mipmap-" + density,
				name);
		ImageIO.write(image, "png", output);
	}

	private GenerateSancharLogo() {
	}
}
