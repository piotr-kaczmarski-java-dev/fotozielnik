package pl.fotozielnik.imports;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.fotozielnik.photos.PhotoService;
import pl.fotozielnik.photos.ProcessingState;
import pl.fotozielnik.shared.BadRequestException;
import pl.fotozielnik.shared.ConflictException;
import pl.fotozielnik.shared.NotFoundException;
import pl.fotozielnik.storage.BlobStore;
import pl.fotozielnik.storage.StorageKeys;

/**
 * Import of a folder (UC-01, FR-004..FR-006): the browser hashes files, asks which are new ({@link #check}) and
 * uploads only those ({@link #receive}). Resuming = starting a new batch with the same folder; files already
 * stored come back as duplicates and are not uploaded again.
 *
 * <p>Transactions are explicit ({@link TransactionTemplate}): an upload must not hold a database transaction
 * open while bytes travel to object storage.
 */
@Service
class ImportService {

    private final ImportBatchRepository batches;
    private final PhotoService photos;
    private final BlobStore blobs;
    private final ImportProperties properties;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    ImportService(
            ImportBatchRepository batches,
            PhotoService photos,
            BlobStore blobs,
            ImportProperties properties,
            PlatformTransactionManager txManager) {
        this.batches = batches;
        this.photos = photos;
        this.blobs = blobs;
        this.properties = properties;
        this.tx = new TransactionTemplate(txManager);
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
    }

    ImportBatchResponse start(UUID ownerId, StartImportRequest request) {
        return execute(tx, () -> {
            ImportBatch batch = batches.save(
                    new ImportBatch(ownerId, request.label(), request.filesFound(), request.filesSkipped()));
            return toResponse(batch, Map.of());
        });
    }

    /** Returns the hashes not yet in the library; the others are counted as duplicates of this batch. */
    CheckResponse check(UUID ownerId, UUID batchId, CheckRequest request) {
        return execute(tx, () -> {
            requireUploading(find(ownerId, batchId));
            List<String> requested = request.sha256s().stream().distinct().toList();
            var existing = new java.util.HashSet<>(photos.existingHashes(ownerId, requested));
            if (!existing.isEmpty()) {
                batches.addDuplicates(batchId, existing.size(), Instant.now());
            }
            return new CheckResponse(
                    requested.stream().filter(h -> !existing.contains(h)).toList());
        });
    }

    UploadResponse receive(
            UUID ownerId,
            UUID batchId,
            String claimedSha256,
            String originalPath,
            @Nullable Instant fileModifiedAt,
            InputStream body)
            throws IOException {
        run(readTx, () -> requireUploading(find(ownerId, batchId)));

        try (ReceivedFile file =
                ReceivedFile.receive(body, properties.maxFileSize().toBytes())) {
            if (!file.sha256().equals(claimedSha256)) {
                // usually a transfer problem: not counted as failed, the browser may retry
                throw new BadRequestException("import.hash-mismatch", "The file content does not match its hash.");
            }
            if (!file.looksLikeJpeg()) {
                recordFailure(batchId);
                throw new BadRequestException("import.not-a-jpeg", "Only JPEG photos can be imported.");
            }
            String key = StorageKeys.original(ownerId, file.sha256());
            if (!blobs.exists(key)) {
                blobs.put(key, file.path(), "image/jpeg");
            }
            PhotoService.Registered registered;
            try {
                registered =
                        photos.register(ownerId, batchId, file.sha256(), file.size(), originalPath, fileModifiedAt);
            } catch (DataIntegrityViolationException e) {
                // the same content was registered concurrently (e.g. two copies in one folder): a duplicate
                run(tx, () -> batches.addDuplicates(batchId, 1, Instant.now()));
                return new UploadResponse(
                        photos.register(ownerId, batchId, file.sha256(), file.size(), originalPath, fileModifiedAt)
                                .photoId(),
                        false);
            }
            run(tx, () -> {
                if (registered.created()) {
                    batches.addUploaded(batchId, Instant.now());
                } else {
                    batches.addDuplicates(batchId, 1, Instant.now());
                }
            });
            return new UploadResponse(registered.photoId(), registered.created());
        } catch (BadRequestException e) {
            if ("import.file-too-large".equals(e.code())) {
                recordFailure(batchId);
            }
            throw e;
        }
    }

    ImportBatchResponse finish(UUID ownerId, UUID batchId) {
        return execute(tx, () -> {
            ImportBatch batch = find(ownerId, batchId);
            batch.finish();
            return toResponse(batch, photos.processingCounts(ownerId, batchId));
        });
    }

    ImportBatchResponse get(UUID ownerId, UUID batchId) {
        return execute(readTx, () -> toResponse(find(ownerId, batchId), photos.processingCounts(ownerId, batchId)));
    }

    ImportBatchPage list(UUID ownerId, int page, int size) {
        return execute(readTx, () -> {
            Page<ImportBatch> result = batches.findByOwnerIdOrderByCreatedAtDesc(ownerId, PageRequest.of(page, size));
            // at most `size` batches per page, each a small grouped count query
            var items = result.getContent().stream()
                    .map(b -> toResponse(b, photos.processingCounts(ownerId, b.getId())))
                    .toList();
            return new ImportBatchPage(items, page, size, result.getTotalElements());
        });
    }

    private void recordFailure(UUID batchId) {
        run(tx, () -> batches.addFailed(batchId, Instant.now()));
    }

    private ImportBatch find(UUID ownerId, UUID batchId) {
        return batches.findByIdAndOwnerId(batchId, ownerId)
                .orElseThrow(() -> new NotFoundException("Import not found."));
    }

    private static ImportBatch requireUploading(ImportBatch batch) {
        if (batch.getStatus() != ImportStatus.UPLOADING) {
            throw new ConflictException("import.finished", "This import is already finished; start a new one.");
        }
        return batch;
    }

    private static ImportBatchResponse toResponse(ImportBatch b, Map<ProcessingState, Long> processing) {
        return new ImportBatchResponse(
                b.getId(),
                b.getLabel(),
                b.getStatus(),
                b.getFilesFound(),
                b.getFilesSkipped(),
                b.getFilesDuplicate(),
                b.getFilesUploaded(),
                b.getFilesFailed(),
                processing.getOrDefault(ProcessingState.PENDING, 0L),
                processing.getOrDefault(ProcessingState.DONE, 0L),
                processing.getOrDefault(ProcessingState.FAILED, 0L),
                b.getCreatedAt(),
                b.getUpdatedAt());
    }

    private static void run(TransactionTemplate template, Runnable work) {
        template.executeWithoutResult(status -> work.run());
    }

    /** TransactionTemplate.execute is @Nullable; our callbacks never return null where a value is used. */
    private static <T> T execute(TransactionTemplate template, java.util.function.Supplier<T> work) {
        return java.util.Objects.requireNonNull(template.execute(status -> work.get()));
    }
}
