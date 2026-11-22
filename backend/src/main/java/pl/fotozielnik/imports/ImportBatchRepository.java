package pl.fotozielnik.imports;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {

    Optional<ImportBatch> findByIdAndOwnerId(UUID id, UUID ownerId);

    Page<ImportBatch> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable pageable);

    // Atomic increments: parallel uploads of one batch must not lose updates or fail on @Version.

    @Modifying
    @Query("update ImportBatch b set b.filesDuplicate = b.filesDuplicate + :n, b.updatedAt = :now where b.id = :id")
    void addDuplicates(@Param("id") UUID id, @Param("n") int n, @Param("now") Instant now);

    @Modifying
    @Query("update ImportBatch b set b.filesUploaded = b.filesUploaded + 1, b.updatedAt = :now where b.id = :id")
    void addUploaded(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("update ImportBatch b set b.filesFailed = b.filesFailed + 1, b.updatedAt = :now where b.id = :id")
    void addFailed(@Param("id") UUID id, @Param("now") Instant now);
}
