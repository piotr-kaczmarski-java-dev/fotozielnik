package pl.fotozielnik.photos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import pl.fotozielnik.ApiClient;
import pl.fotozielnik.ExifJpeg;
import pl.fotozielnik.IntegrationTest;
import pl.fotozielnik.TestImages;

/** FR-007, FR-008: uploaded photos are processed in the background - EXIF, location, thumbnail, preview. */
class DerivativesIT extends IntegrationTest {

    @Test
    void uploadedPhotoGetsExifThumbnailAndPreview() throws Exception {
        ApiClient user = signedInAs(newUser());
        byte[] jpeg = ExifJpeg.of(TestImages.jpeg(7000, 3000, 2000))
                .make("NIKON CORPORATION")
                .model("NIKON D7500")
                .orientation(6)
                .dateTimeOriginal("2024:05:12 10:31:07")
                .offsetTimeOriginal("+02:00")
                .gps(49.2992, 19.9496) // Zakopane
                .build();
        String batch = startBatch(user);
        String id = upload(user, batch, jpeg).get("photoId").asString();

        var before = user.get("/api/photos/" + id).json();
        assertThat(before.get("processingState").asString()).isEqualTo("PENDING");
        assertThat(user.get("/api/photos/" + id + "/thumb").status()).isEqualTo(404);
        assertThat(user.get("/api/imports/" + batch)
                        .json()
                        .get("processingPending")
                        .asInt())
                .isEqualTo(1);

        assertThat(runJobs()).isGreaterThanOrEqualTo(1);

        var details = user.get("/api/photos/" + id).json();
        assertThat(details.get("processingState").asString()).isEqualTo("DONE");
        assertThat(details.get("thumbnailReady").asBoolean()).isTrue();
        assertThat(details.get("cameraMake").asString()).isEqualTo("NIKON CORPORATION");
        assertThat(details.get("cameraModel").asString()).isEqualTo("NIKON D7500");
        assertThat(details.get("takenAtLocal").asString()).startsWith("2024-05-12T10:31:07");
        assertThat(details.get("takenAt").asString()).isEqualTo("2024-05-12T08:31:07Z");
        assertThat(details.get("width").asInt())
                .as("portrait after orientation")
                .isEqualTo(2000);
        assertThat(details.get("height").asInt()).isEqualTo(3000);
        var location = details.get("location");
        assertThat(location.get("latitude").asDouble()).isCloseTo(49.2992, within(1e-4));
        assertThat(location.get("longitude").asDouble()).isCloseTo(19.9496, within(1e-4));
        assertThat(location.get("source").asString()).isEqualTo("EXIF");

        var thumb = user.get("/api/photos/" + id + "/thumb");
        assertThat(thumb.status()).isEqualTo(200);
        assertThat(thumb.header("Content-Type")).contains("image/jpeg");
        var thumbImage = ImageIO.read(new ByteArrayInputStream(thumb.bytes()));
        assertThat(thumbImage.getHeight()).isEqualTo(400);
        assertThat(thumbImage.getWidth()).isLessThan(400);

        var preview = ImageIO.read(new ByteArrayInputStream(
                user.get("/api/photos/" + id + "/preview").bytes()));
        assertThat(preview.getHeight()).isEqualTo(2048);

        var progress = user.get("/api/imports/" + batch).json();
        assertThat(progress.get("processingDone").asInt()).isEqualTo(1);
        assertThat(progress.get("processingPending").asInt()).isZero();

        // the grid now orders by the camera time and has dimensions for layout
        var cell = user.get("/api/photos").json().get("items").get(0);
        assertThat(cell.get("sortTime").asString()).isEqualTo("2024-05-12T10:31:07Z");
        assertThat(cell.get("thumbnailReady").asBoolean()).isTrue();
    }

    @Test
    void corruptJpegIsMarkedFailedWithoutEndlessRetries() {
        ApiClient user = signedInAs(newUser());
        byte[] corrupt = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 4, 1, 2, 3, 4, 5};
        String batch = startBatch(user);
        String id = upload(user, batch, corrupt).get("photoId").asString();

        runJobs();

        var details = user.get("/api/photos/" + id).json();
        assertThat(details.get("processingState").asString()).isEqualTo("FAILED");
        assertThat(details.get("processingError").asString()).isNotBlank();
        assertThat(user.get("/api/imports/" + batch)
                        .json()
                        .get("processingFailed")
                        .asInt())
                .isEqualTo(1);
        assertThat(user.get("/api/photos/" + id + "/original").status())
                .as("the original is kept anyway")
                .isEqualTo(200);
    }

    private static String startBatch(ApiClient user) {
        return user.post("/api/imports", Map.of("label", "Tatry", "filesFound", 1, "filesSkipped", 0))
                .json()
                .get("id")
                .asString();
    }

    private static tools.jackson.databind.JsonNode upload(ApiClient user, String batch, byte[] jpeg) {
        var r = user.putBytes(
                "/api/imports/" + batch + "/files/" + TestImages.sha256(jpeg),
                jpeg,
                Map.of("X-File-Path", URLEncoder.encode("Tatry/DSC_0001.JPG", StandardCharsets.UTF_8)));
        assertThat(r.status()).as(r.body()).isEqualTo(201);
        return r.json();
    }
}
