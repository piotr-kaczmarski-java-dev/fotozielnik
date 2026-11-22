package pl.fotozielnik;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.imageio.ImageIO;

/** Small, distinct JPEGs generated on the fly; the seed makes the content (and hash) unique. */
public final class TestImages {

    private TestImages() {}

    public static byte[] jpeg(int seed) {
        return jpeg(seed, 64, 48);
    }

    public static byte[] jpeg(int seed, int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(new Color(seed * 7919 & 0xFFFFFF));
        g.fillRect(0, 0, width, height);
        g.setColor(Color.WHITE);
        g.drawString(Integer.toString(seed), 2, height / 2);
        g.dispose();
        try (var out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
