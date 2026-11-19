package pl.fotozielnik.photos;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import java.io.IOException;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads EXIF with metadata-extractor (FR-007). Lenient by design: a missing or malformed field becomes null,
 * and unreadable metadata becomes {@link ExifData#EMPTY} - the photo itself is still worth keeping.
 */
@Component
class ExifReader {

    private static final Logger log = LoggerFactory.getLogger(ExifReader.class);
    private static final DateTimeFormatter EXIF_TIME = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

    ExifData read(Path file) {
        Metadata metadata;
        try {
            metadata = ImageMetadataReader.readMetadata(file.toFile());
        } catch (ImageProcessingException | IOException e) {
            log.debug("No readable metadata in {}: {}", file.getFileName(), e.toString());
            return ExifData.EMPTY;
        }
        Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        Directory sub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        GpsDirectory gps = metadata.getFirstDirectoryOfType(GpsDirectory.class);

        LocalDateTime local = sub == null ? null : parseTime(sub.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL));
        if (local == null && ifd0 != null) {
            local = parseTime(ifd0.getString(ExifIFD0Directory.TAG_DATETIME)); // fallback: file change time
        }
        ZoneOffset offset = sub == null ? null : parseOffset(sub.getString(ExifSubIFDDirectory.TAG_TIME_ZONE_ORIGINAL));

        Double lat = null;
        Double lon = null;
        Instant gpsTime = null;
        if (gps != null) {
            GeoLocation location = gps.getGeoLocation();
            if (location != null && !location.isZero() && isValid(location)) {
                lat = location.getLatitude();
                lon = location.getLongitude();
            }
            Date gpsDate = gps.getGpsDate();
            gpsTime = gpsDate == null ? null : gpsDate.toInstant();
        }

        Integer orientation = ifd0 == null ? null : ifd0.getInteger(ExifIFD0Directory.TAG_ORIENTATION);
        return new ExifData(
                local,
                offset,
                gpsTime,
                lat,
                lon,
                text(ifd0, ExifIFD0Directory.TAG_MAKE, 100),
                text(ifd0, ExifIFD0Directory.TAG_MODEL, 100),
                text(sub, ExifSubIFDDirectory.TAG_BODY_SERIAL_NUMBER, 100),
                text(sub, ExifSubIFDDirectory.TAG_LENS_MODEL, 200),
                orientation != null && orientation >= 1 && orientation <= 8 ? orientation : 1);
    }

    static @Nullable LocalDateTime parseTime(@Nullable String value) {
        if (value == null || value.isBlank() || value.startsWith("0000")) {
            return null;
        }
        try {
            // some cameras pad with spaces/NULs or append sub-seconds; the first 19 characters are the time
            String trimmed = value.strip();
            return LocalDateTime.parse(trimmed.length() > 19 ? trimmed.substring(0, 19) : trimmed, EXIF_TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static @Nullable ZoneOffset parseOffset(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ZoneOffset.of(value.strip());
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static boolean isValid(GeoLocation l) {
        return Math.abs(l.getLatitude()) <= 90 && Math.abs(l.getLongitude()) <= 180;
    }

    private static @Nullable String text(@Nullable Directory dir, int tag, int maxLength) {
        if (dir == null) {
            return null;
        }
        String value = dir.getString(tag);
        if (value == null) {
            return null;
        }
        value = value.replace("\u0000", "").strip();
        if (value.isEmpty()) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
