package io.github.petebankhead.icc;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.plugins.tiff.TIFFDirectory;
import javax.imageio.plugins.tiff.TIFFField;
import javax.imageio.stream.ImageInputStream;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Basic app to explore the application of ICC Profiles.
 * @author Pete Bankhead
 */
public class App {

    private static final Logger logger = Logger.getLogger(App.class.getName());

    /**
     * Image with ICC profile applied
     */
    private static final String NAME_APPLY = "CMU-Apply.tif";

    /**
     * Image with ICC profile embedded
     */
    private static final String NAME_EMBED = "CMU-Embed.tif";

    /**
     * Image with the ICC profile ignored
     */
    private static final String NAME_IGNORE = "CMU-Ignore.tif";

    public static void main(String[] args) {

        var dir = new File("../images");

        // Read the three images
        // Optionally convert to a different RGB type
//        int targetType = BufferedImage.TYPE_INT_RGB;
        int targetType = -1;
        List<String> names = new ArrayList<>(Arrays.asList(NAME_APPLY, NAME_EMBED, NAME_IGNORE));
        Map<String, BufferedImage> map = names.stream()
                    .collect(Collectors.toMap(n -> n, n -> readImage(new File(dir, n), targetType)));

        // Read the embedded ICC profile
        ICC_Profile iccSource = readICCProfile(new File(dir, NAME_EMBED));

        // Create a color convert op to apply the transform
        ICC_Profile iccTarget = ICC_Profile.getInstance(ColorSpace.CS_sRGB);
        ColorConvertOp op = new ColorConvertOp(
                new ICC_Profile[] {iccSource, iccTarget},
                null
        );

        // Try applying the transform to the buffered image
        BufferedImage imgTransformed = op.filter(map.get(NAME_IGNORE), null);
        names.add("TRANSFORMED");
        map.put("TRANSFORMED", imgTransformed);

        // Try applying the transform to the raster only
        BufferedImage imgTransformedRaster =
                new BufferedImage(
                        new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), false, false,
                                ColorModel.OPAQUE, DataBuffer.TYPE_BYTE),
                        op.filter(map.get(NAME_IGNORE).getRaster(), null),
                        false,
                        null
                );
        names.add("TRANSFORMED-RASTER");
        map.put("TRANSFORMED-RASTER", imgTransformedRaster);

        // Compare the images
        for (int i = 0; i < names.size(); i++) {
            for (int j = i+1; j < names.size(); j++) {
                String name1 = names.get(i);
                String name2 = names.get(j);
                System.out.println("-----");
                compareImages(name1, map.get(name1), name2, map.get(name2));
            }
            System.out.println("-----");
        }

    }


    static void compareImages(String name1, BufferedImage img1, String name2, BufferedImage img2) {
        int[] rgb1 = img1.getRGB(0, 0, img1.getWidth(), img1.getHeight(), null, 0, img1.getWidth());
        int[] rgb2 = img2.getRGB(0, 0, img2.getWidth(), img2.getHeight(), null, 0, img2.getWidth());
        assert rgb1.length == rgb2.length;
        if (Arrays.equals(rgb1, rgb2)) {
            System.out.println(String.format("%s and %s are identical", name1, name2));
        } else {
            System.out.println(String.format("%s and %s are different", name1, name2));
            computeRGBDifferences(rgb1, rgb2);
        }
    }

    static double[] computeRGBDifferences(int[] rgb1, int[] rgb2) {
        double[] differences = new double[rgb1.length * 3];
        int nDifferent = 0;
        for (int i = 0; i < rgb1.length; i++) {
            int v1 = rgb1[i];
            int v2 = rgb2[i];
            if (v1 == v2)
                continue;
            differences[i*3] = red(v1) - red(v2);
            differences[i*3+1] = green(v1) - green(v2);
            differences[i*3+2] = blue(v1) - blue(v2);
            nDifferent++;
        }
        System.out.println(String.format("   Different pixels: %.1f %%", (nDifferent*100.0/rgb1.length)));
        DoubleSummaryStatistics summary = Arrays.stream(differences).summaryStatistics();
        DoubleSummaryStatistics absSummary = Arrays.stream(differences).map(d -> Math.abs(d)).summaryStatistics();
        System.out.println(String.format("   Mean: %.1f, Min: %.1f, Max: %.1f, MAD: %.2f",
                summary.getAverage(), summary.getMin(), summary.getMax(), absSummary.getAverage()));
        return null;
    }


    static double red(int rgb) {
        return (rgb >> 16) & 0xff;
    }

    static double green(int rgb) {
        return (rgb >> 8) & 0xff;
    }

    static double blue(int rgb) {
        return (rgb & 0xff);
    }

    static BufferedImage readImage(File file, int targetType) {
        try {
            BufferedImage img = ImageIO.read(file);
            if (targetType >= 0)
                return ensureType(img, targetType);
            return img;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    static BufferedImage ensureType(BufferedImage img, int type) {
        if (img.getType() == type)
            return img;
        var img2 = new BufferedImage(img.getWidth(), img.getHeight(), type);
        var g2d = img2.createGraphics();
        g2d.drawImage(img, 0, 0, null);
        g2d.dispose();
        return img2;
    }

    static ICC_Profile readICCProfile(File input) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(input)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (readers == null) {
                logger.log(Level.WARNING, "No readers found to extract ICC profile from {0}", input);
                return null;
            }
            while (readers.hasNext()) {
                ImageReader reader = readers.next();
                stream.reset();
                reader.setInput(stream);
                TIFFDirectory tiffDir = TIFFDirectory.createFromMetadata(reader.getImageMetadata(0));
                TIFFField tiffField = tiffDir.getTIFFField(34675);
                byte[] bytes = tiffField.getAsBytes();
                return ICC_Profile.getInstance(bytes);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Unable to read ICC profile: {0}", input);
        }
        return null;
    }

}
