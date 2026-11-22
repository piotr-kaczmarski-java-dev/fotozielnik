package pl.fotozielnik.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import pl.fotozielnik.IntegrationTest;

/** ADR-0009: enqueue in the caller's transaction, SKIP LOCKED claiming, retries with backoff, lease recovery. */
class JobQueueIT extends IntegrationTest {

    @Autowired
    JobQueue queue;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    @BeforeEach
    void emptyQueue() {
        jdbc.update("delete from job");
    }

    @Test
    void enqueueNeedsATransactionAndIsRolledBackWithIt() {
        assertThatThrownBy(() -> queue.enqueue("T", null, Map.of(), 0))
                .isInstanceOf(IllegalTransactionStateException.class);

        try {
            tx.executeWithoutResult(s -> {
                queue.enqueue("T", null, Map.of(), 0);
                throw new IllegalStateException("business rule failed");
            });
        } catch (IllegalStateException expected) {
            // rolled back
        }
        assertThat(queue.claim("w", 10, Duration.ofMinutes(1))).isEmpty();
    }

    @Test
    void claimsByPriorityAndPassesThePayload() {
        tx.executeWithoutResult(s -> {
            queue.enqueue("LOW", null, Map.of("n", 1), JobQueue.PRIORITY_LOW);
            queue.enqueue("NORMAL", null, Map.of("n", 2), JobQueue.PRIORITY_NORMAL);
        });
        List<Job> first = queue.claim("w", 1, Duration.ofMinutes(1));
        assertThat(first).singleElement().satisfies(j -> {
            assertThat(j.type()).isEqualTo("NORMAL");
            assertThat(j.payload()).contains("\"n\"").contains("2");
            assertThat(j.attempt()).isEqualTo(1);
        });
        assertThat(queue.claim("w", 1, Duration.ofMinutes(1)))
                .singleElement()
                .extracting(Job::type)
                .isEqualTo("LOW");
    }

    @Test
    void concurrentWorkersNeverClaimTheSameJob() throws Exception {
        tx.executeWithoutResult(s -> {
            for (int i = 0; i < 200; i++) {
                queue.enqueue("T", null, Map.of("i", i), 0);
            }
        });
        Set<UUID> claimed = ConcurrentHashMap.newKeySet();
        List<UUID> all = Collections.synchronizedList(new ArrayList<>());
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int w = 0; w < 8; w++) {
                String worker = "w" + w;
                futures.add(pool.submit(() -> {
                    for (List<Job> jobs = queue.claim(worker, 3, Duration.ofMinutes(1));
                            !jobs.isEmpty();
                            jobs = queue.claim(worker, 3, Duration.ofMinutes(1))) {
                        jobs.forEach(j -> {
                            claimed.add(j.id());
                            all.add(j.id());
                        });
                    }
                }));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        }
        assertThat(all).hasSize(200);
        assertThat(claimed).hasSize(200);
    }

    @Test
    void failedJobIsRetriedLaterAndFailsForGoodAfterTheLastAttempt() {
        tx.executeWithoutResult(s -> queue.enqueue("T", null, Map.of(), 0));
        Job job = queue.claim("w", 1, Duration.ofMinutes(1)).getFirst();

        queue.fail(job, "boom");
        assertThat(queue.claim("w", 1, Duration.ofMinutes(1))).as("backoff").isEmpty();
        assertThat(jdbc.queryForObject("select last_error from job where id = ?", String.class, job.id()))
                .isEqualTo("boom");

        jdbc.update("update job set run_after = now(), attempts = max_attempts - 1 where id = ?", job.id());
        Job last = queue.claim("w", 1, Duration.ofMinutes(1)).getFirst();
        assertThat(last.isLastAttempt()).isTrue();
        queue.fail(last, "boom again");
        assertThat(queue.countsByStatus("T")).containsEntry("FAILED", 1L);
    }

    @Test
    void jobOfACrashedWorkerIsPickedUpAfterTheLeaseExpires() {
        tx.executeWithoutResult(s -> queue.enqueue("T", null, Map.of(), 0));
        Job taken = queue.claim("crashed", 1, Duration.ofMinutes(10)).getFirst();
        assertThat(queue.claim("w", 1, Duration.ofMinutes(1))).isEmpty();

        jdbc.update("update job set locked_until = now() - interval '1 second' where id = ?", taken.id());

        assertThat(queue.claim("w", 1, Duration.ofMinutes(1))).singleElement().satisfies(j -> {
            assertThat(j.id()).isEqualTo(taken.id());
            assertThat(j.attempt()).isEqualTo(2);
        });
    }

    @Test
    void backoffGrowsAndIsCapped() {
        assertThat(JobQueue.backoff(1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(JobQueue.backoff(2)).isEqualTo(Duration.ofSeconds(40));
        assertThat(JobQueue.backoff(50)).isEqualTo(Duration.ofHours(6));
    }
}
