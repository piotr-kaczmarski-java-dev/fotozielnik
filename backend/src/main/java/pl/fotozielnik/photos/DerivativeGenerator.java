package pl.fotozielnik.photos;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Makes the grid thumbnail and the large preview of an original (FR-008, ADR-0013). Derivatives are upright
 * (EXIF orientation applied) and carry no metadata.
 */
interface DerivativeGenerator {

    int THUMBNAIL_EDGE = 400;
    int PREVIEW_EDGE = 2048;

    Derivatives generate(Path original, int exifOrientation) throws IOException, UnreadableImageException;

    /** {@code width}/{@code height}: the original's size as displayed (after orientation). */
    @SuppressWarnings("ArrayRecordComponent") // transient transfer object, never compared
    record Derivatives(byte[] thumbnail, byte[] preview, String contentType, int width, int height) {}

    /** The file is not a decodable image: retrying will not help. */
    class UnreadableImageException extends Exception {
        UnreadableImageException(String message, Throwable cause) {
            super(message, cause);
        }

        UnreadableImageException(String message) {
            super(message);
        }
    }
}
