import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Reproduce OptiFine J3's Java2D atlas resize before testing the real GLSL. */
class PrepareOptifineCarrier {
    public static void main(String[] args) throws Exception {
        BufferedImage source = ImageIO.read(new File(args[0]));
        for (int size : new int[] {16, 32, 64, 128}) {
            var resized = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            var graphics = resized.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                graphics.drawImage(source, 0, 0, size, size, null);
            } finally { graphics.dispose(); }
            System.out.printf("%d: %08X -> %08X%n", size, source.getRGB(0, 0), resized.getRGB(0, 0));
            ImageIO.write(resized, "png", new File(args[1], "carrier-resized-" + size + ".png"));
        }
    }
}
