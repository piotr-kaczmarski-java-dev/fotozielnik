package pl.fotozielnik.users;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pl.fotozielnik.jobs.Job;
import pl.fotozielnik.jobs.JobHandler;
import pl.fotozielnik.storage.BlobStore;
import pl.fotozielnik.storage.StorageKeys;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code DELETE_USER_OBJECTS} (NFR-006): removes every stored object of a deleted user. A job rather than a
 * direct call, so a storage outage during the deletion is retried instead of leaving orphaned photos.
 * Idempotent: deleting an empty prefix does nothing.
 */
@Component
class UserObjectsCleanup implements JobHandler {

    static final String JOB_TYPE = "DELETE_USER_OBJECTS";

    record Payload(UUID userId) {}

    private static final Logger log = LoggerFactory.getLogger(UserObjectsCleanup.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final BlobStore blobs;

    UserObjectsCleanup(BlobStore blobs) {
        this.blobs = blobs;
    }

    @Override
    public String type() {
        return JOB_TYPE;
    }

    @Override
    public void handle(Job job) {
        UUID userId = JSON.readValue(job.payload(), Payload.class).userId();
        blobs.deleteByPrefix(StorageKeys.userPrefix(userId));
        log.info("Deleted the stored objects of deleted user {}", userId);
    }
}
