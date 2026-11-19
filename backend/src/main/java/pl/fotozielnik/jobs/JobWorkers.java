package pl.fotozielnik.jobs;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A fixed pool of platform threads that claim and run jobs (ADR-0009). Each worker polls the queue; when it
 * finds work it keeps going without sleeping, so a big import drains at full speed.
 */
@Component
class JobWorkers implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(JobWorkers.class);

    private final JobQueue queue;
    private final JobProperties properties;
    private final Map<String, JobHandler> handlers;
    private final String nodeId = ManagementFactory.getRuntimeMXBean().getName();
    private final AtomicInteger threadCounter = new AtomicInteger();

    private volatile boolean running;
    private @Nullable ExecutorService pool;

    JobWorkers(JobQueue queue, JobProperties properties, List<JobHandler> handlers) {
        this.queue = queue;
        this.properties = properties;
        this.handlers = handlers.stream().collect(Collectors.toMap(JobHandler::type, Function.identity()));
    }

    @Override
    public void start() {
        if (!properties.enabled()) {
            log.info("Job workers disabled (fotozielnik.jobs.enabled=false)");
            return;
        }
        int n = properties.effectiveWorkers();
        running = true;
        ExecutorService p = Executors.newFixedThreadPool(n, r -> {
            Thread t = new Thread(r, "job-worker-" + threadCounter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        for (int i = 0; i < n; i++) {
            p.execute(this::loop);
        }
        pool = p;
        log.info("Started {} job workers for {}", n, handlers.keySet());
    }

    @Override
    public void stop() {
        running = false;
        ExecutorService p = pool;
        if (p != null) {
            p.shutdownNow(); // interrupts sleeping workers; a running job is retried after its lease expires
            try {
                p.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            pool = null;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Runs queued jobs on the calling thread until none is due; returns how many ran. For tests and tools,
     * where background threads would make outcomes timing-dependent.
     */
    int drain() {
        int ran = 0;
        while (true) {
            List<Job> jobs = queue.claim(nodeId + "/drain", 1, properties.lease());
            if (jobs.isEmpty()) {
                return ran;
            }
            jobs.forEach(this::run);
            ran += jobs.size();
        }
    }

    private void loop() {
        String workerId = nodeId + "/" + Thread.currentThread().getName();
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                List<Job> jobs = queue.claim(workerId, 1, properties.lease());
                if (jobs.isEmpty()) {
                    Thread.sleep(properties.pollInterval());
                } else {
                    jobs.forEach(this::run);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                // database briefly unavailable etc.: back off, never let the worker thread die
                log.warn("Job worker error: {}", e.toString());
                sleepQuietly();
            }
        }
    }

    private void run(Job job) {
        JobHandler handler = handlers.get(job.type());
        if (handler == null) {
            queue.fail(job, "No handler for job type " + job.type());
            return;
        }
        try {
            handler.handle(job);
            queue.complete(job.id());
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            boolean permanent = e instanceof PermanentJobFailure;
            log.warn(
                    "Job {} {} attempt {}/{} failed{}: {}",
                    job.type(),
                    job.id(),
                    job.attempt(),
                    job.maxAttempts(),
                    permanent ? " permanently" : "",
                    e.toString());
            queue.fail(job, e.toString(), permanent);
            if (permanent || job.isLastAttempt()) {
                try {
                    handler.onFailed(job, e);
                } catch (RuntimeException inner) {
                    log.error("onFailed of job {} threw", job.id(), inner);
                }
            }
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(properties.pollInterval().multipliedBy(5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    void cleanUp() {
        int deleted = queue.deleteFinishedBefore(Instant.now().minus(properties.keepFinished()));
        if (deleted > 0) {
            log.info("Deleted {} finished jobs", deleted);
        }
    }
}
