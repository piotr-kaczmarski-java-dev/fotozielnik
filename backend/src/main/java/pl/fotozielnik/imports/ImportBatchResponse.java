package pl.fotozielnik.imports;

import java.time.Instant;
import java.util.UUID;

/**
 * Progress of an import (FR-008): upload counters plus how many of its new photos are processed.
 * {@code processingPending + processingDone + processingFailed == filesUploaded}.
 */
record ImportBatchResponse(
        UUID id,
        String label,
        ImportStatus status,
        int filesFound,
        int filesSkipped,
        int filesDuplicate,
        int filesUploaded,
        int filesFailed,
        long processingPending,
        long processingDone,
        long processingFailed,
        Instant createdAt,
        Instant updatedAt) {}
