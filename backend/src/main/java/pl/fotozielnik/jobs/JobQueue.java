package pl.fotozielnik.jobs;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.fotozielnik.shared.Ids;
import tools.jackson.databind.json.JsonMapper;

/**
 * The job table (ADR-0009). {@link #enqueue} joins the caller's transaction, so a job exists exactly when the
 * data it refers to was committed. Plain JDBC: SKIP LOCKED and RETURNING are clearer in SQL than in JPA.
 */
@Service
public class JobQueue {

    /** Interactive work (one photo just uploaded) before bulk work (re-processing everything). */
    public static final int PRIORITY_NORMAL = 0;

    public static final int PRIORITY_LOW = -10;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcTemplate jdbc;

    JobQueue(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Adds a job; {@code payload} is serialized to JSON (a record or a map). */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueue(String type, @Nullable UUID ownerId, Object payload, int priority) {
        UUID id = Ids.newId();
        jdbc.update("""
                insert into job (id, owner_id, type, payload, status, priority, run_after, created_at, updated_at)
                values (?, ?, ?, ?::jsonb, 'QUEUED', ?, now(), now(), now())""", id, ownerId, type, JSON.writeValueAsString(payload), priority);
        return id;
    }

    /** Claims up to {@code limit} runnable jobs (queued and due, or running with an expired lease). */
    @Transactional
    List<Job> claim(String workerId, int limit, Duration lease) {
        return jdbc.query(
                """
                update job set status = 'RUNNING', attempts = attempts + 1, locked_by = ?,
                               locked_until = now() + make_interval(secs => ?), updated_at = now()
                where id in (
                    select id from job
                    where (status = 'QUEUED' and run_after <= now())
                       or (status = 'RUNNING' and locked_until < now())
                    order by priority desc, run_after
                    limit ?
                    for update skip locked)
                returning id, type, owner_id, payload::text as payload, attempts, max_attempts""",
                (rs, i) -> new Job(
                        rs.getObject("id", UUID.class),
                        rs.getString("type"),
                        rs.getObject("owner_id", UUID.class),
                        rs.getString("payload"),
                        rs.getInt("attempts"),
                        rs.getInt("max_attempts")),
                workerId,
                lease.toSeconds(),
                limit);
    }

    @Transactional
    void fail(Job job, String error) {
        fail(job, error, false);
    }

    @Transactional
    void complete(UUID jobId) {
        jdbc.update(
                "update job set status = 'DONE', locked_until = null, finished_at = now(), updated_at = now() where id = ?",
                jobId);
    }

    /**
     * Schedules a retry with exponential backoff, or marks the job FAILED after its last attempt (or at once
     * when {@code permanent}).
     */
    @Transactional
    void fail(Job job, String error, boolean permanent) {
        String message = error.length() > 2000 ? error.substring(0, 2000) : error;
        if (permanent || job.isLastAttempt()) {
            jdbc.update("""
                    update job set status = 'FAILED', last_error = ?, locked_until = null,
                                   finished_at = now(), updated_at = now() where id = ?""", message, job.id());
        } else {
            jdbc.update("""
                    update job set status = 'QUEUED', last_error = ?, locked_until = null, run_after = ?,
                                   updated_at = now() where id = ?""", message, Timestamp.from(Instant.now().plus(backoff(job.attempt()))), job.id());
        }
    }

    /** 10 s, 40 s, 160 s, ~11 min, ~43 min ... capped at 6 h. */
    static Duration backoff(int attempt) {
        long seconds = 10L << Math.min(2 * (attempt - 1), 20);
        return Duration.ofSeconds(Math.min(seconds, Duration.ofHours(6).toSeconds()));
    }

    @Transactional
    int deleteFinishedBefore(Instant cutoff) {
        return jdbc.update(
                "delete from job where status in ('DONE', 'FAILED') and finished_at < ?", Timestamp.from(cutoff));
    }

    /** Job counts by status, e.g. for health/diagnostics and tests. */
    @Transactional(readOnly = true)
    public Map<String, Long> countsByStatus(String type) {
        Map<String, Long> counts = new java.util.TreeMap<>();
        jdbc.query(
                "select status, count(*) from job where type = ? group by status",
                rs -> {
                    counts.put(rs.getString(1), rs.getLong(2));
                },
                type);
        return counts;
    }
}
