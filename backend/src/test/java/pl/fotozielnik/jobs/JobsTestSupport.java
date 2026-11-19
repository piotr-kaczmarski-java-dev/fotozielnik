package pl.fotozielnik.jobs;

import org.springframework.context.ApplicationContext;

/** Lets tests outside this package run the queue synchronously. */
public final class JobsTestSupport {

    private JobsTestSupport() {}

    public static int drain(ApplicationContext context) {
        return context.getBean(JobWorkers.class).drain();
    }
}
