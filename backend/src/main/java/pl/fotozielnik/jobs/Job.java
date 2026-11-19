package pl.fotozielnik.jobs;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A claimed job as a {@link JobHandler} sees it. {@code attempt} starts at 1. */
public record Job(UUID id, String type, @Nullable UUID ownerId, String payload, int attempt, int maxAttempts) {

    public boolean isLastAttempt() {
        return attempt >= maxAttempts;
    }
}
