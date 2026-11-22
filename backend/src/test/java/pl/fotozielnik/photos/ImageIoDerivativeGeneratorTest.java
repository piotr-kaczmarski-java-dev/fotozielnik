package pl.fotozielnik.photos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.fotozielnik.TestImages;
import pl.fotozielnik.photos.DerivativeGenerator.UnreadableImageException;

class ImageIoDerivativeGeneratorTest {

    @TempDir
    Path dir;

    private final ImageIoDerivativeGenerator generator = new ImageIoDerivativeGenerator();

    @Test
    void makesThumbnailAndPreviewWithinTheirEdges() throws Exception {
        Path original = Files.write(dir.resolve("big.jpg"), TestImages.jpeg(1, 4000, 3000));

        var d = generator.generate(original, 1);

        assertThat(d.width()).isEqualTo(4000);
        assertThat(d.height()).isEqualTo(3000);
        assertThat(d.contentType()).isEqualTo("image/jpeg");
        BufferedImage preview = decode(d.preview());
        BufferedImage thumb = decode(d.thumbnail());
        assertThat(preview.getWidth()).isEqualTo(2048);
        assertThat(preview.getHeight()).isEqualTo(1536);
        assertThat(thumb.getWidth()).isEqualTo(400);
        assertThat(thumb.getHeight()).isEqualTo(300);
    }

    @Test
    void smallImagesAreNotUpscaled() throws Exception {
        Path original = Files.write(dir.resolve("small.jpg"), TestImages.jpeg(2, 300, 200));
        var d = generator.generate(original, 1);
        assertThat(decode(d.preview()).getWidth()).isEqualTo(300);
        assertThat(decode(d.thumbnail()).getWidth()).isEqualTo(300);
    }

    @Test
    void portraitFromOrientation6IsRotatedUpright() throws Exception {
        // the sensor stored 4000x3000 landscape; EXIF 6 says "rotate 90 degrees clockwise to view"
        Path original = Files.write(dir.resolve("rotated.jpg"), TestImages.jpeg(3, 4000, 3000));
        var d = generator.generate(original, 6);
        assertThat(d.width()).isEqualTo(3000);
        assertThat(d.height()).isEqualTo(4000);
        BufferedImage thumb = decode(d.thumbnail());
        assertThat(thumb.getWidth()).isEqualTo(300);
        assertThat(thumb.getHeight()).isEqualTo(400);
    }

    @Test
    void garbageIsUnreadableNotACrash() throws Exception {
        Path junk = Files.write(dir.resolve("junk.jpg"), new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3});
        assertThatThrownBy(() -> generator.generate(junk, 1)).isInstanceOf(UnreadableImageException.class);
    }

    @Test
    void orientationMovesPixelsCorrectly() {
        // 2x1 image: A B  -> orientation 6 (rotate CW) -> column A over B
        BufferedImage src = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);
        src.setRGB(0, 0, 0xAA0000);
        src.setRGB(1, 0, 0x0000BB);
        BufferedImage cw = Orientation.apply(src, 6);
        assertThat(cw.getWidth()).isEqualTo(1);
        assertThat(cw.getRGB(0, 0) & 0xFFFFFF).isEqualTo(0xAA0000);
        assertThat(cw.getRGB(0, 1) & 0xFFFFFF).isEqualTo(0x0000BB);
        BufferedImage ccw = Orientation.apply(src, 8);
        assertThat(ccw.getRGB(0, 0) & 0xFFFFFF).isEqualTo(0x0000BB);
        BufferedImage mirrored = Orientation.apply(src, 2);
        assertThat(mirrored.getRGB(0, 0) & 0xFFFFFF).isEqualTo(0x0000BB);
    }

    private static BufferedImage decode(byte[] jpeg) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(jpeg));
    }
}
