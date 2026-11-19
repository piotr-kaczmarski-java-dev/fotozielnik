package pl.fotozielnik.photos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.fotozielnik.ExifJpeg;
import pl.fotozielnik.TestImages;

/** FR-007: capture time (+ offset), GPS, camera make/model/serial, lens, orientation. */
class ExifReaderTest {

    @TempDir
    Path dir;

    private final ExifReader reader = new ExifReader();

    @Test
    void readsEverythingACameraWrites() throws Exception {
        byte[] jpeg = ExifJpeg.of(TestImages.jpeg(1))
                .make("Canon")
                .model("Canon EOS 80D")
                .orientation(6)
                .dateTimeOriginal("2024:05:12 10:31:07")
                .offsetTimeOriginal("+02:00")
                .bodySerial("123456789")
                .lensModel("EF-S18-135mm f/3.5-5.6 IS USM")
                .gps(50.0614, 19.9372) // Kraków
                .build();

        ExifData exif = reader.read(write(jpeg));

        assertThat(exif.takenAtLocal()).isEqualTo(LocalDateTime.of(2024, 5, 12, 10, 31, 7));
        assertThat(exif.offset()).isEqualTo(ZoneOffset.ofHours(2));
        assertThat(exif.takenAt()).isEqualTo(Instant.parse("2024-05-12T08:31:07Z"));
        assertThat(exif.cameraMake()).isEqualTo("Canon");
        assertThat(exif.cameraModel()).isEqualTo("Canon EOS 80D");
        assertThat(exif.cameraSerial()).isEqualTo("123456789");
        assertThat(exif.lens()).isEqualTo("EF-S18-135mm f/3.5-5.6 IS USM");
        assertThat(exif.orientation()).isEqualTo(6);
        assertThat(exif.latitude()).isCloseTo(50.0614, within(1e-4));
        assertThat(exif.longitude()).isCloseTo(19.9372, within(1e-4));
    }

    @Test
    void withoutOffsetTheGpsClockGivesTheAbsoluteTime() throws Exception {
        byte[] jpeg = ExifJpeg.of(TestImages.jpeg(2))
                .dateTimeOriginal("2023:07:01 14:00:00")
                .gps(-33.9, -70.6)
                .gpsTime("2023:07:01", 12, 0, 5)
                .build();

        ExifData exif = reader.read(write(jpeg));

        assertThat(exif.offset()).isNull();
        assertThat(exif.takenAt()).isEqualTo(Instant.parse("2023-07-01T12:00:05Z"));
        assertThat(exif.latitude()).isNegative();
        assertThat(exif.longitude()).isNegative();
    }

    @Test
    void onlyWallClockTimeWhenNothingElseIsKnown() throws Exception {
        ExifData exif = reader.read(write(ExifJpeg.of(TestImages.jpeg(3))
                .dateTimeOriginal("2022:04:30 09:15:00")
                .build()));
        assertThat(exif.takenAtLocal()).isEqualTo(LocalDateTime.of(2022, 4, 30, 9, 15));
        assertThat(exif.takenAt()).isNull();
        assertThat(exif.hasLocation()).isFalse();
        assertThat(exif.orientation()).isEqualTo(1);
    }

    @Test
    void jpegWithoutExifGivesEmptyData() throws Exception {
        ExifData exif = reader.read(write(TestImages.jpeg(4)));
        assertThat(exif.takenAtLocal()).isNull();
        assertThat(exif.cameraMake()).isNull();
        assertThat(exif.orientation()).isEqualTo(1);
    }

    @Test
    void garbageIsNotAnError() throws Exception {
        assertThat(reader.read(write(new byte[] {1, 2, 3}))).isEqualTo(ExifData.EMPTY);
    }

    @Test
    void zeroDatesAndBadOffsetsAreIgnored() {
        assertThat(ExifReader.parseTime("0000:00:00 00:00:00")).isNull();
        assertThat(ExifReader.parseTime("2024:05:12 10:31:07.123")).isEqualTo(LocalDateTime.of(2024, 5, 12, 10, 31, 7));
        assertThat(ExifReader.parseTime("yesterday")).isNull();
        assertThat(ExifReader.parseOffset("+25:00")).isNull();
        assertThat(ExifReader.parseOffset("-05:00")).isEqualTo(ZoneOffset.ofHours(-5));
    }

    private Path write(byte[] bytes) throws Exception {
        return Files.write(Files.createTempFile(dir, "p", ".jpg"), bytes);
    }
}
