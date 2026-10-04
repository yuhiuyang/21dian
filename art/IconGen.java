import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Generates Android launcher icon resources from the master app icon PNG.
 * Uses only JDK (javax.imageio), run as a single-file source program:
 *
 *   java IconGen.java inspect <source.png>
 *   java IconGen.java crop <src> <x> <y> <w> <h> <out.png>
 *   java IconGen.java gen <source.png> <resDir>
 *
 * Source art notes: the PNG is an opaque red rounded-square tile; outside the
 * corner arcs the pixels are solid black, and the bottom-left card overlaps
 * that corner region. So NO pixel synthesis is done here:
 *   - adaptive foreground keeps the art as-is; every launcher mask rounds the
 *     corners by more than the art's own radius (~20%), which cuts the black away.
 *   - legacy icons get an alpha-cut rounded corner (radius slightly inside the
 *     art's arc) so no black shows on pre-adaptive launchers.
 *
 * Outputs per density (mdpi..xxxhdpi):
 *   ic_launcher.png            48dp  legacy icon (rounded transparent corners)
 *   ic_launcher_round.png      48dp  circular legacy icon
 *   ic_launcher_foreground.png 108dp adaptive icon foreground (full-bleed)
 * Prints the average edge color for the adaptive background color resource.
 */
public class IconGen {

    static final String[] DENS = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
    static final int[] LEGACY_PX = {48, 72, 96, 144, 192};
    static final int[] ADAPT_PX = {108, 162, 216, 324, 432};

    /** brightness (max RGB channel) below which a pixel counts as the black corner area */
    static final int DARK = 45;

    /** how many full-res pixels to bite inside the detected arc when alpha-cutting legacy icons */
    static final int CORNER_BITE = 8;

    public static void main(String[] args) throws Exception {
        if (args.length >= 2 && args[0].equals("inspect")) {
            inspect(new File(args[1]));
        } else if (args.length >= 3 && args[0].equals("gen")) {
            generate(new File(args[1]), new File(args[2]));
        } else if (args.length >= 7 && args[0].equals("crop")) {
            BufferedImage img = ImageIO.read(new File(args[1]));
            BufferedImage out = img.getSubimage(
                    Integer.parseInt(args[2]), Integer.parseInt(args[3]),
                    Integer.parseInt(args[4]), Integer.parseInt(args[5]));
            ImageIO.write(out, "png", new File(args[6]));
            System.out.println("cropped -> " + args[6]);
        } else if (args.length >= 8 && args[0].equals("zoom")) {
            BufferedImage img = ImageIO.read(new File(args[1]));
            BufferedImage sub = img.getSubimage(
                    Integer.parseInt(args[2]), Integer.parseInt(args[3]),
                    Integer.parseInt(args[4]), Integer.parseInt(args[5]));
            int z = Integer.parseInt(args[6]);
            BufferedImage out = new BufferedImage(sub.getWidth() * z, sub.getHeight() * z, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(sub, 0, 0, out.getWidth(), out.getHeight(), null);
            g.dispose();
            ImageIO.write(out, "png", new File(args[7]));
            System.out.println("zoomed -> " + args[7]);
        } else {
            System.out.println("usage: inspect <src> | gen <src> <resDir> | crop <src> <x> <y> <w> <h> <out> | zoom <src> <x> <y> <w> <h> <factor> <out>");
        }
    }

    // ---- inspect ----

    static void inspect(File src) throws Exception {
        BufferedImage img = ImageIO.read(src);
        int w = img.getWidth(), h = img.getHeight();
        System.out.println("size: " + w + "x" + h);
        int[][] pts = {
                {0, 0}, {w - 1, 0}, {0, h - 1}, {w - 1, h - 1},
                {w / 2, 3}, {w / 2, h - 4}, {3, h / 2}, {w - 4, h / 2}, {w / 2, h / 2},
        };
        for (int[] p : pts) {
            int argb = img.getRGB(p[0], p[1]);
            System.out.printf("(%4d,%4d) a=%3d rgb=%02X%02X%02X%n", p[0], p[1],
                    argb >>> 24, (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
        }
        int d = diagonalDarkRun(img);
        System.out.println("diagonal black run: " + d + " px -> corner radius ~ " + radiusFromRun(d));
    }

    /** scan top-left diagonal until a pixel brighter than DARK appears */
    static int diagonalDarkRun(BufferedImage img) {
        int n = Math.min(img.getWidth(), img.getHeight());
        for (int d = 0; d < n; d++) {
            if (brightness(img.getRGB(d, d)) >= DARK) return d;
        }
        return 0;
    }

    /** on a 45-degree diagonal, the arc boundary sits at r * (1 - 1/sqrt(2)) from the corner */
    static int radiusFromRun(int d) {
        return (int) Math.round(d / (1.0 - 1.0 / Math.sqrt(2.0)));
    }

    static int brightness(int argb) {
        return Math.max((argb >> 16) & 0xFF, Math.max((argb >> 8) & 0xFF, argb & 0xFF));
    }

    // ---- generate ----

    static void generate(File srcFile, File resDir) throws Exception {
        BufferedImage src = ImageIO.read(srcFile);
        int w = src.getWidth();
        int rFull = radiusFromRun(diagonalDarkRun(src));
        System.out.println("corner radius: " + rFull + " px of " + w + " (alpha-cut at r-" + CORNER_BITE + ")");

        for (int i = 0; i < DENS.length; i++) {
            File dir = new File(resDir, "mipmap-" + DENS[i]);
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("mkdir " + dir);

            BufferedImage scaled = scale(src, LEGACY_PX[i]);
            int rs = Math.max(2, Math.round((rFull - CORNER_BITE) * (float) LEGACY_PX[i] / w));
            ImageIO.write(roundAlpha(scaled, rs), "png", new File(dir, "ic_launcher.png"));
            ImageIO.write(circle(scale(src, LEGACY_PX[i])), "png", new File(dir, "ic_launcher_round.png"));
            ImageIO.write(scale(src, ADAPT_PX[i]), "png", new File(dir, "ic_launcher_foreground.png"));
        }
        System.out.println("bgColor=#" + edgeColorHex(src));
        System.out.println("done: " + DENS.length + " densities x 3 files");
    }

    static BufferedImage scale(BufferedImage src, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        return out;
    }

    static BufferedImage circle(BufferedImage src) {
        int size = src.getWidth();
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setClip(new Ellipse2D.Float(0, 0, size, size));
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    /** make everything outside the rounded rect transparent (with a 1.5px feather) */
    static BufferedImage roundAlpha(BufferedImage src, int r) {
        int w = src.getWidth(), h = src.getHeight();
        r = Math.max(2, Math.min(r, Math.min(w, h) / 3));
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int cx = Math.min(x, w - 1 - x); // distance to the nearest vertical edge
                int cy = Math.min(y, h - 1 - y);
                if (cx >= r || cy >= r) continue; // not in a corner region
                double dist = Math.hypot(r - cx, r - cy);
                if (dist <= r - 1.5) continue;
                int argb = src.getRGB(x, y);
                double cov = Math.max(0.0, Math.min(1.0, (r - dist) / 1.5));
                int a = (int) Math.round(((argb >>> 24) & 0xFF) * cov);
                src.setRGB(x, y, (a << 24) | (argb & 0x00FFFFFF));
            }
        }
        return src;
    }

    /** Average of the four edge midpoints -> adaptive background color */
    static String edgeColorHex(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[][] pts = {{w / 2, 3}, {w / 2, h - 4}, {3, h / 2}, {w - 4, h / 2}};
        long r = 0, g = 0, b = 0;
        int n = 0;
        for (int[] p : pts) {
            int argb = img.getRGB(p[0], p[1]);
            if ((argb >>> 24) > 128) {
                r += (argb >> 16) & 0xFF;
                g += (argb >> 8) & 0xFF;
                b += argb & 0xFF;
                n++;
            }
        }
        if (n == 0) return "B00010"; // fallback: deep red
        return String.format("%02X%02X%02X", r / n, g / n, b / n);
    }
}
