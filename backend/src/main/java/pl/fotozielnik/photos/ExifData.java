package pl.fotozielnik.photos;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.jspecify.annotations.Nullable;

/**
 * What we take from a photo's metadata (FR-007). Every field is optional: phones, scanners and edited files
 * all lack something. {@code orientation} is the EXIF value 1..8 (1 = upright, the default).
 */
record ExifData(
        @Nullable LocalDateTime takenAtLocal,
        @Nullable ZoneOffset offset,
        @Nullable Instant gpsTime,
        @Nullable Double latitude,
        @Nullable Double longitude,
        @Nullable String cameraMake,
        @Nullable String cameraModel,
        @Nullable String cameraSerial,
        @Nullable String lens,
        int orientation) {

    static final ExifData EMPTY = new ExifData(null, null, null, null, null, null, null, null, null, 1);

    /**
     * The absolute capture time: local time + offset when the camera recorded the offset, else the GPS clock
     * (always UTC). Null when neither exists - then only the camera's wall-clock time is known.
     */
    @Nullable
    Instant takenAt() {
        if (takenAtLocal != null && offset != null) {
            return takenAtLocal.toInstant(offset);
        }
        return gpsTime;
    }

    boolean hasLocation() {
        return latitude != null && longitude != null;
    }
}
