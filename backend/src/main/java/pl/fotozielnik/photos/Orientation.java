package pl.fotozielnik.photos;

import java.awt.image.BufferedImage;

/** Applies an EXIF orientation (1..8) so the result is upright; 1 or unknown values return the input. */
final class Orientation {

    private Orientation() {}

    static BufferedImage apply(BufferedImage src, int orientation) {
        if (orientation < 2 || orientation > 8) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        boolean swap = orientation >= 5;
        BufferedImage dst = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        int[] in = src.getRGB(0, 0, w, h, null, 0, w);
        int dw = dst.getWidth();
        int[] out = new int[in.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int dx;
                int dy;
                switch (orientation) {
                    case 2 -> { // mirror horizontal
                        dx = w - 1 - x;
                        dy = y;
                    }
                    case 3 -> { // rotate 180
                        dx = w - 1 - x;
                        dy = h - 1 - y;
                    }
                    case 4 -> { // mirror vertical
                        dx = x;
                        dy = h - 1 - y;
                    }
                    case 5 -> { // transpose
                        dx = y;
                        dy = x;
                    }
                    case 6 -> { // rotate 90 clockwise
                        dx = h - 1 - y;
                        dy = x;
                    }
                    case 7 -> { // transverse
                        dx = h - 1 - y;
                        dy = w - 1 - x;
                    }
                    default -> { // 8: rotate 90 counter-clockwise
                        dx = y;
                        dy = w - 1 - x;
                    }
                }
                out[dy * dw + dx] = in[y * w + x];
            }
        }
        dst.setRGB(0, 0, dw, dst.getHeight(), out, 0, dw);
        return dst;
    }
}
