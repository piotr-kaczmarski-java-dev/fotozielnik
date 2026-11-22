package pl.fotozielnik.photos;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One grid cell. {@code thumbnailReady} false = show a placeholder; processing is still running. */
record PhotoSummary(
        UUID id,
        Instant sortTime,
        @Nullable LocalDateTime takenAtLocal,
        @Nullable Integer width,
        @Nullable Integer height,
        ProcessingState processingState,
        boolean thumbnailReady,
        @Nullable Instant deletedAt) {

    static PhotoSummary of(Photo p) {
        return new PhotoSummary(
                p.getId(),
                p.getSortTime(),
                p.getTakenAtLocal(),
                p.getWidth(),
                p.getHeight(),
                p.getProcessingState(),
                p.hasDerivatives(),
                p.getDeletedAt());
    }
}
