package pl.fotozielnik.photos;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.fotozielnik.jobs.Job;
import pl.fotozielnik.jobs.JobHandler;
import pl.fotozielnik.jobs.JobQueue;
import pl.fotozielnik.storage.BlobStore;
import pl.fotozielnik.storage.StorageKeys;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code PURGE_TRASH} (FR-022): removes photos from the trash for good - their three objects, then the row.
 * Nightly for photos past the retention period (everybody's), or at once for one user ("Opróżnij kosz").
 * Works in portions and re-enqueues itself, so a huge trash never holds a job lease too long. Idempotent:
 * a photo restored in the meantime no longer matches and is kept.
 */
@Component
class TrashPurger implements JobHandler {

    static final String JOB_TYPE = "PURGE_TRASH";
    static final int PORTION = 500;

    /** {@code ownerId} null = all users; photos deleted before {@code cutoff} are purged. */
    record Payload(@Nullable UUID ownerId, Instant cutoff) {}

    private static final Logger log = LoggerFactory.getLogger(TrashPurger.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final PhotoRepository photos;
    private final BlobStore blobs;
    private final JobQueue jobs;
    private final PhotoProperties properties;
    private final TransactionTemplate tx;

    TrashPurger(
            PhotoRepository photos,
            BlobStore blobs,
            JobQueue jobs,
            PhotoProperties properties,
            PlatformTransactionManager txManager) {
        this.photos = photos;
        this.blobs = blobs;
        this.jobs = jobs;
        this.properties = properties;
        this.tx = new TransactionTemplate(txManager);
    }

    @Override
    public String type() {
        return JOB_TYPE;
    }

    /** Nightly: purge everything that has been in the trash longer than the retention period. */
    @Scheduled(cron = "${fotozielnik.photos.purge-cron:0 30 3 * * *}")
    void purgeExpired() {
        enqueue(null, Instant.now().minus(properties.trashRetention()));
    }

    void enqueue(@Nullable UUID ownerId, Instant cutoff) {
        tx.executeWithoutResult(
                s -> jobs.enqueue(JOB_TYPE, ownerId, new Payload(ownerId, cutoff), JobQueue.PRIORITY_LOW));
    }

    @Override
    public void handle(Job job) {
        Payload p = JSON.readValue(job.payload(), Payload.class);
        List<Photo> portion = photos.findTrashedBefore(p.cutoff(), p.ownerId(), Limit.of(PORTION));
        for (Photo photo : portion) {
            // Row first, and only if the photo is still in the trash: a photo restored a moment ago keeps its
            // files. The worst case (crash in between) is orphaned objects, never a photo without its file.
            Integer deleted = tx.execute(s -> photos.deleteTrashed(photo.getId()));
            if (deleted != null && deleted == 1) {
                blobs.delete(photo.getStorageKey());
                blobs.delete(StorageKeys.thumbnail(photo.getOwnerId(), photo.getSha256()));
                blobs.delete(StorageKeys.preview(photo.getOwnerId(), photo.getSha256()));
            }
        }
        if (portion.size() == PORTION) {
            enqueue(p.ownerId(), p.cutoff()); // more to do: continue in a fresh job
        }
        if (!portion.isEmpty()) {
            log.info(
                    "Purged {} photos from the trash (owner {})",
                    portion.size(),
                    p.ownerId() == null ? "all" : p.ownerId());
        }
    }
}
