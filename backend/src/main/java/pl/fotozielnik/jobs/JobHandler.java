package pl.fotozielnik.jobs;

/**
 * Runs one type of background job. Implementations are Spring beans and must be <b>idempotent</b>: a job can
 * run again after a crash (lease expiry) or a retry. Throwing schedules a retry with backoff; after the last
 * attempt the job is FAILED and {@link #onFailed} is called.
 */
public interface JobHandler {

    String type();

    void handle(Job job) throws Exception;

    /** Called once when the job has failed for good, e.g. to mark the affected record as failed. */
    default void onFailed(Job job, Exception lastError) {}
}
