package pl.fotozielnik.photos;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Everything known about one photo (photo page, FR-009). */
record PhotoDetails(
        UUID id,
        String originalPath,
        long sizeBytes,
        String sha256,
        @Nullable Instant fileModifiedAt,
        @Nullable LocalDateTime takenAtLocal,
        @Nullable Instant takenAt,
        @Nullable String cameraMake,
        @Nullable String cameraModel,
        @Nullable String lens,
        @Nullable Integer width,
        @Nullable Integer height,
        @Nullable PhotoLocation location,
        ProcessingState processingState,
        @Nullable String processingError,
        boolean thumbnailReady,
        @Nullable UUID importBatchId,
        Instant createdAt) {

    /** WGS84 degrees. {@code source}: EXIF (from the camera), INFERRED (from nearby photos), MANUAL. */
    record PhotoLocation(double latitude, double longitude, String source) {}

    static PhotoDetails of(Photo p, @Nullable PhotoLocation location) {
        return new PhotoDetails(
                p.getId(),
                p.getOriginalPath(),
                p.getSizeBytes(),
                p.getSha256(),
                p.getFileModifiedAt(),
                p.getTakenAtLocal(),
                p.getTakenAt(),
                p.getCameraMake(),
                p.getCameraModel(),
                p.getLens(),
                p.getWidth(),
                p.getHeight(),
                location,
                p.getProcessingState(),
                p.getProcessingError(),
                p.hasDerivatives(),
                p.getImportBatchId(),
                p.getCreatedAt());
    }
}
