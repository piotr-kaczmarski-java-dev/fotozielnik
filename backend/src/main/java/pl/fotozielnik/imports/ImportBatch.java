package pl.fotozielnik.imports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;
import pl.fotozielnik.shared.Ids;

/**
 * One import session (UC-01). The file counters are updated with atomic SQL increments
 * ({@link ImportBatchRepository}), because the browser uploads several files in parallel.
 */
@Entity
@Table(name = "import_batch")
@DynamicUpdate // never write the counters from a loaded entity: they are incremented concurrently in SQL
class ImportBatch {

    @Id
    private UUID id;

    @Column
    private UUID ownerId;

    @Column
    private String label;

    @Enumerated(EnumType.STRING)
    @Column
    private ImportStatus status;

    @Column
    private int filesFound;

    @Column
    private int filesSkipped;

    @Column
    private int filesDuplicate;

    @Column
    private int filesUploaded;

    @Column
    private int filesFailed;

    @Column
    private Instant createdAt;

    @Column
    private Instant updatedAt;

    @Version
    private Long version;

    protected ImportBatch() {}

    ImportBatch(UUID ownerId, String label, int filesFound, int filesSkipped) {
        this.id = Ids.newId();
        this.ownerId = ownerId;
        this.label = label.strip();
        this.status = ImportStatus.UPLOADING;
        this.filesFound = filesFound;
        this.filesSkipped = filesSkipped;
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.updatedAt = createdAt;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    UUID getId() {
        return id;
    }

    UUID getOwnerId() {
        return ownerId;
    }

    String getLabel() {
        return label;
    }

    ImportStatus getStatus() {
        return status;
    }

    void finish() {
        status = ImportStatus.FINISHED;
    }

    int getFilesFound() {
        return filesFound;
    }

    int getFilesSkipped() {
        return filesSkipped;
    }

    int getFilesDuplicate() {
        return filesDuplicate;
    }

    int getFilesUploaded() {
        return filesUploaded;
    }

    int getFilesFailed() {
        return filesFailed;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
