package pl.fotozielnik.photos;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Component;

/**
 * Pure-Java derivatives with ImageIO (+ the TwelveMonkeys JPEG plugin for CMYK and slightly broken files).
 * The ADR-0013 fallback, used until libvips is in place. The trick that keeps it fast and small: the JPEG
 * decoder <i>subsamples while decoding</i>, so a 40 MP original is decoded at roughly preview size instead of
 * full size (about 1/16 of the memory and time).
 */
@Component
class ImageIoDerivativeGenerator implements DerivativeGenerator {

    private static final float THUMBNAIL_QUALITY = 0.80f;
    private static final float PREVIEW_QUALITY = 0.85f;

    @Override
    public Derivatives generate(Path original, int exifOrientation) throws IOException, UnreadableImageException {
        BufferedImage decoded;
        int width;
        int height;
        try (ImageInputStream in = ImageIO.createImageInputStream(original.toFile())) {
            if (in == null) {
                throw new UnreadableImageException("Cannot open the file.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new UnreadableImageException("Not an image format we can read.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                width = reader.getWidth(0);
                height = reader.getHeight(0);
                // largest integer factor that still leaves the long edge >= the preview size
                int factor = Math.max(1, Math.max(width, height) / PREVIEW_EDGE);
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(factor, factor, 0, 0);
                decoded = reader.read(0, param);
            } catch (IOException | RuntimeException e) {
                throw new UnreadableImageException("The image data is damaged.", e);
            } finally {
                reader.dispose();
            }
        }
        BufferedImage upright = Orientation.apply(toRgb(decoded), exifOrientation);
        BufferedImage preview = fit(upright, PREVIEW_EDGE);
        BufferedImage thumbnail = fit(preview, THUMBNAIL_EDGE);
        boolean swapped = exifOrientation >= 5;
        return new Derivatives(
                encodeJpeg(thumbnail, THUMBNAIL_QUALITY),
                encodeJpeg(preview, PREVIEW_QUALITY),
                "image/jpeg",
                swapped ? height : width,
                swapped ? width : height);
    }

    private static BufferedImage toRgb(BufferedImage img) {
        if (img.getType() == BufferedImage.TYPE_INT_RGB) {
            return img;
        }
        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return rgb;
    }

    /** Scales so the long edge is at most {@code maxEdge}, halving step by step for quality. */
    static BufferedImage fit(BufferedImage img, int maxEdge) {
        int longEdge = Math.max(img.getWidth(), img.getHeight());
        if (longEdge <= maxEdge) {
            return img;
        }
        double scale = (double) maxEdge / longEdge;
        int targetW = Math.max(1, (int) Math.round(img.getWidth() * scale));
        int targetH = Math.max(1, (int) Math.round(img.getHeight() * scale));
        BufferedImage current = img;
        while (current.getWidth() / 2 >= targetW && current.getHeight() / 2 >= targetH) {
            current = resize(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        return resize(current, targetW, targetH);
    }

    private static BufferedImage resize(BufferedImage img, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static byte[] encodeJpeg(BufferedImage img, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        if (param.canWriteProgressive()) {
            param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT); // shows a blurry version early on slow links
        }
        var bytes = new ByteArrayOutputStream();
        try (var out = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
