package pl.fotozielnik.photos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import pl.fotozielnik.shared.Ids;
import pl.fotozielnik.storage.StorageKeys;

/** One stored original (FR-004). The original bytes are never modified; EXIF fields come from processing. */
@Entity
@Table(name = "photo")
class Photo {

    @Id
    private UUID id;

    @Column
    private UUID ownerId;

    @Column
    private @Nullable UUID importBatchId;

    @Column
    private String sha256;

    @Column
    private long sizeBytes;

    @Column
    private String storageKey;

    @Column
    private String originalPath;

    @Column
    private @Nullable Instant fileModifiedAt;

    @Column
    private @Nullable LocalDateTime takenAtLocal;

    @Column
    private @Nullable Instant takenAt;

    @Column
    private @Nullable String cameraMake;

    @Column
    private @Nullable String cameraModel;

    @Column
    private @Nullable String cameraSerial;

    @Column
    private @Nullable String lens;

    @Column
    private @Nullable Integer width;

    @Column
    private @Nullable Integer height;

    @Column
    private @Nullable Short orientation;

    @Column
    private Instant sortTime;

    @Enumerated(EnumType.STRING)
    @Column
    private ProcessingState processingState;

    @Column
    private @Nullable String processingError;

    @Column
    private boolean hasDerivatives;

    @Column
    private @Nullable Instant deletedAt;

    @Column
    private Instant createdAt;

    @Column
    private Instant updatedAt;

    @Version
    private Long version;

    protected Photo() {}

    Photo(
            UUID ownerId,
            @Nullable UUID importBatchId,
            String sha256,
            long sizeBytes,
            String originalPath,
            @Nullable Instant fileModifiedAt) {
        this.id = Ids.newId();
        this.ownerId = ownerId;
        this.importBatchId = importBatchId;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
        this.storageKey = StorageKeys.original(ownerId, sha256);
        this.originalPath = originalPath;
        this.fileModifiedAt = fileModifiedAt;
        this.processingState = ProcessingState.PENDING;
        // Postgres stores microseconds; truncate so the keyset cursor matches the stored value exactly.
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.updatedAt = createdAt;
        this.sortTime = fileModifiedAt != null ? fileModifiedAt.truncatedTo(ChronoUnit.MICROS) : createdAt;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    /**
     * Stores what processing found and marks the photo DONE. Grid order follows the camera's wall-clock time
     * (what the user saw), so photos with and without a recorded offset interleave naturally.
     */
    void applyProcessing(ExifData exif, int displayWidth, int displayHeight) {
        this.takenAtLocal = exif.takenAtLocal();
        this.takenAt = exif.takenAt();
        this.cameraMake = exif.cameraMake();
        this.cameraModel = exif.cameraModel();
        this.cameraSerial = exif.cameraSerial();
        this.lens = exif.lens();
        this.orientation = (short) exif.orientation();
        this.width = displayWidth;
        this.height = displayHeight;
        this.hasDerivatives = true;
        this.processingState = ProcessingState.DONE;
        this.processingError = null;
        if (takenAtLocal != null) {
            this.sortTime = takenAtLocal.toInstant(java.time.ZoneOffset.UTC);
        } else if (takenAt != null) {
            this.sortTime = takenAt.truncatedTo(ChronoUnit.MICROS);
        }
    }

    void markFailed(String error) {
        this.processingState = ProcessingState.FAILED;
        this.processingError = error.length() > 1000 ? error.substring(0, 1000) : error;
    }

    UUID getId() {
        return id;
    }

    UUID getOwnerId() {
        return ownerId;
    }

    @Nullable
    UUID getImportBatchId() {
        return importBatchId;
    }

    String getSha256() {
        return sha256;
    }

    long getSizeBytes() {
        return sizeBytes;
    }

    String getStorageKey() {
        return storageKey;
    }

    String getOriginalPath() {
        return originalPath;
    }

    @Nullable
    Instant getFileModifiedAt() {
        return fileModifiedAt;
    }

    @Nullable
    LocalDateTime getTakenAtLocal() {
        return takenAtLocal;
    }

    @Nullable
    Instant getTakenAt() {
        return takenAt;
    }

    @Nullable
    String getCameraMake() {
        return cameraMake;
    }

    @Nullable
    String getCameraModel() {
        return cameraModel;
    }

    @Nullable
    String getCameraSerial() {
        return cameraSerial;
    }

    @Nullable
    String getLens() {
        return lens;
    }

    @Nullable
    Integer getWidth() {
        return width;
    }

    @Nullable
    Integer getHeight() {
        return height;
    }

    @Nullable
    Short getOrientation() {
        return orientation;
    }

    Instant getSortTime() {
        return sortTime;
    }

    ProcessingState getProcessingState() {
        return processingState;
    }

    @Nullable
    String getProcessingError() {
        return processingError;
    }

    boolean hasDerivatives() {
        return hasDerivatives;
    }

    @Nullable
    Instant getDeletedAt() {
        return deletedAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
