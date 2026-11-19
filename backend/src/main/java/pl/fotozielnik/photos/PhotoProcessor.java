package pl.fotozielnik.photos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.fotozielnik.jobs.Job;
import pl.fotozielnik.jobs.JobHandler;
import pl.fotozielnik.jobs.PermanentJobFailure;
import pl.fotozielnik.photos.DerivativeGenerator.Derivatives;
import pl.fotozielnik.photos.DerivativeGenerator.UnreadableImageException;
import pl.fotozielnik.storage.BlobStore;
import pl.fotozielnik.storage.StorageKeys;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code PROCESS_PHOTO} (FR-007, FR-008): reads EXIF, stores thumbnail + preview, marks the photo DONE.
 * Idempotent - running it twice overwrites the same derivatives with the same content. No transaction is held
 * while bytes move; only the final update is transactional.
 */
@Component
class PhotoProcessor implements JobHandler {

    static final String JOB_TYPE = "PROCESS_PHOTO";

    record Payload(UUID photoId) {}

    private static final Logger log = LoggerFactory.getLogger(PhotoProcessor.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final PhotoRepository photos;
    private final BlobStore blobs;
    private final ExifReader exifReader;
    private final DerivativeGenerator derivatives;
    private final TransactionTemplate tx;

    PhotoProcessor(
            PhotoRepository photos,
            BlobStore blobs,
            ExifReader exifReader,
            DerivativeGenerator derivatives,
            PlatformTransactionManager txManager) {
        this.photos = photos;
        this.blobs = blobs;
        this.exifReader = exifReader;
        this.derivatives = derivatives;
        this.tx = new TransactionTemplate(txManager);
    }

    @Override
    public String type() {
        return JOB_TYPE;
    }

    @Override
    public void handle(Job job) throws IOException, PermanentJobFailure {
        UUID photoId = JSON.readValue(job.payload(), Payload.class).photoId();
        var photo = photos.findById(photoId);
        if (photo.isEmpty()) {
            log.debug("Photo {} is gone; nothing to process", photoId);
            return;
        }
        UUID owner = photo.get().getOwnerId();
        String sha = photo.get().getSha256();

        Path tmp = Files.createTempFile("process-", ".jpg");
        try {
            try (var blob = blobs.get(photo.get().getStorageKey())
                    .orElseThrow(() -> new IOException("original missing in storage"))) {
                Files.copy(blob.content(), tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            ExifData exif = exifReader.read(tmp);
            Derivatives d;
            try {
                d = derivatives.generate(tmp, exif.orientation());
            } catch (UnreadableImageException e) {
                throw new PermanentJobFailure(e.getMessage() != null ? e.getMessage() : "unreadable image", e);
            }
            blobs.put(StorageKeys.thumbnail(owner, sha), d.thumbnail(), d.contentType());
            blobs.put(StorageKeys.preview(owner, sha), d.preview(), d.contentType());

            tx.executeWithoutResult(status -> photos.findById(photoId).ifPresent(p -> {
                p.applyProcessing(exif, d.width(), d.height());
                photos.saveAndFlush(p);
                Double lat = exif.latitude();
                Double lon = exif.longitude();
                if (lat != null && lon != null) {
                    photos.setLocation(photoId, lat, lon, "EXIF");
                }
            }));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** After the last attempt the photo shows as failed (with the reason) instead of "processing" forever. */
    @Override
    public void onFailed(Job job, Exception lastError) {
        UUID photoId = JSON.readValue(job.payload(), Payload.class).photoId();
        String reason = lastError instanceof PermanentJobFailure && lastError.getMessage() != null
                ? lastError.getMessage()
                : "Processing failed: " + lastError;
        tx.executeWithoutResult(status -> photos.findById(photoId).ifPresent(p -> p.markFailed(reason)));
    }
}
