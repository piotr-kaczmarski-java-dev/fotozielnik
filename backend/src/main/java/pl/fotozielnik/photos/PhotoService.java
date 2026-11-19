package pl.fotozielnik.photos;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fotozielnik.jobs.JobQueue;
import pl.fotozielnik.shared.NotFoundException;
import pl.fotozielnik.storage.BlobStore;
import pl.fotozielnik.storage.BlobStore.Blob;
import pl.fotozielnik.storage.StorageKeys;

/**
 * Photo records of a user's library. Other features (imports) register photos through the public methods;
 * everything is scoped by owner, and another user's photo is "not found" (never "forbidden").
 */
@Service
public class PhotoService {

    private final PhotoRepository photos;
    private final BlobStore blobs;
    private final JobQueue jobs;

    PhotoService(PhotoRepository photos, BlobStore blobs, JobQueue jobs) {
        this.photos = photos;
        this.blobs = blobs;
        this.jobs = jobs;
    }

    /** Result of {@link #register}: the photo id and whether it was new (false = duplicate of an existing one). */
    public record Registered(UUID photoId, boolean created) {}

    /**
     * Records an original that is already in object storage under {@link StorageKeys#original}. Idempotent per
     * (owner, sha256): registering the same content again returns the existing photo.
     */
    @Transactional
    public Registered register(
            UUID ownerId,
            @Nullable UUID importBatchId,
            String sha256,
            long sizeBytes,
            String originalPath,
            @Nullable Instant fileModifiedAt) {
        var existing = photos.findExistingHashes(ownerId, List.of(sha256));
        if (!existing.isEmpty()) {
            return new Registered(findIdByHash(ownerId, sha256), false);
        }
        Photo photo = photos.save(new Photo(ownerId, importBatchId, sha256, sizeBytes, originalPath, fileModifiedAt));
        photos.flush(); // surface a concurrent duplicate (unique owner+sha256) here, before the job is queued
        jobs.enqueue(
                PhotoProcessor.JOB_TYPE, ownerId, new PhotoProcessor.Payload(photo.getId()), JobQueue.PRIORITY_NORMAL);
        return new Registered(photo.getId(), true);
    }

    /** Which of these hashes the user's library already holds (dedup, FR-005). */
    @Transactional(readOnly = true)
    public List<String> existingHashes(UUID ownerId, List<String> sha256s) {
        return sha256s.isEmpty() ? List.of() : photos.findExistingHashes(ownerId, sha256s);
    }

    /** Processing progress of the photos of one import batch (FR-008). */
    @Transactional(readOnly = true)
    public Map<ProcessingState, Long> processingCounts(UUID ownerId, UUID importBatchId) {
        Map<ProcessingState, Long> counts = new EnumMap<>(ProcessingState.class);
        for (ProcessingState s : ProcessingState.values()) {
            counts.put(s, 0L);
        }
        photos.countByState(ownerId, importBatchId).forEach(c -> counts.put(c.getState(), c.getCount()));
        return counts;
    }

    /** {@link #processingCounts} for several batches in one query (import history page). */
    @Transactional(readOnly = true)
    public Map<UUID, Map<ProcessingState, Long>> processingCounts(UUID ownerId, java.util.Collection<UUID> batchIds) {
        Map<UUID, Map<ProcessingState, Long>> result = new java.util.HashMap<>();
        if (batchIds.isEmpty()) {
            return result;
        }
        for (var c : photos.countByStateForBatches(ownerId, batchIds)) {
            result.computeIfAbsent(c.getBatchId(), id -> new EnumMap<>(ProcessingState.class))
                    .put(c.getState(), c.getCount());
        }
        return result;
    }

    @Transactional(readOnly = true)
    PhotoPage page(UUID ownerId, @Nullable String cursor, int size) {
        Limit limit = Limit.of(size + 1); // one extra row tells us whether there is a next page
        List<Photo> rows;
        if (cursor == null) {
            rows = photos.findFirstPage(ownerId, limit);
        } else {
            PageCursor c = PageCursor.decode(cursor);
            rows = photos.findPageAfter(ownerId, c.sortTime(), c.id(), limit);
        }
        boolean hasMore = rows.size() > size;
        List<Photo> page = hasMore ? rows.subList(0, size) : rows;
        String next = null;
        if (hasMore) {
            Photo last = page.getLast();
            next = new PageCursor(last.getSortTime(), last.getId()).encode();
        }
        return new PhotoPage(page.stream().map(PhotoSummary::of).toList(), next);
    }

    @Transactional(readOnly = true)
    PhotoDetails details(UUID ownerId, UUID photoId) {
        Photo photo = find(ownerId, photoId);
        var location = photos.findLocation(photoId, ownerId)
                .map(l -> new PhotoDetails.PhotoLocation(l.getLatitude(), l.getLongitude(), l.getSource()))
                .orElse(null);
        return PhotoDetails.of(photo, location);
    }

    enum ImageKind {
        ORIGINAL,
        THUMBNAIL,
        PREVIEW
    }

    /** What the image endpoints need before touching storage: the object key and an ETag. */
    record ImageRef(String key, String etag, String fileName) {}

    @Transactional(readOnly = true)
    ImageRef imageRef(UUID ownerId, UUID photoId, ImageKind kind) {
        Photo p = find(ownerId, photoId);
        if (kind != ImageKind.ORIGINAL && !p.hasDerivatives()) {
            throw new NotFoundException("This photo has not been processed yet.");
        }
        String key = switch (kind) {
            case ORIGINAL -> p.getStorageKey();
            case THUMBNAIL -> StorageKeys.thumbnail(ownerId, p.getSha256());
            case PREVIEW -> StorageKeys.preview(ownerId, p.getSha256());
        };
        String name = p.getOriginalPath().substring(p.getOriginalPath().lastIndexOf('/') + 1);
        return new ImageRef(key, p.getSha256() + "-" + kind.name().toLowerCase(java.util.Locale.ROOT), name);
    }

    /** Opens image bytes; outside a transaction on purpose (streaming can take a while). */
    Blob open(ImageRef ref) {
        return blobs.get(ref.key()).orElseThrow(() -> new NotFoundException("Image file is missing."));
    }

    private Photo find(UUID ownerId, UUID photoId) {
        return photos.findByIdAndOwnerId(photoId, ownerId).orElseThrow(() -> new NotFoundException("Photo not found."));
    }

    private UUID findIdByHash(UUID ownerId, String sha256) {
        return photos.findByOwnerIdAndSha256(ownerId, sha256)
                .map(Photo::getId)
                .orElseThrow(() -> new IllegalStateException("hash vanished"));
    }
}
