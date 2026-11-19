package pl.fotozielnik.jobs;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Worker pool (ADR-0009). {@code workers} 0 = number of cores - 1 (at least 1): processing is CPU-heavy and the
 * web requests need a core too.
 */
@ConfigurationProperties("fotozielnik.jobs")
record JobProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("0") int workers,
        @DefaultValue("1s") Duration pollInterval,
        @DefaultValue("10m") Duration lease,
        @DefaultValue("7d") Duration keepFinished) {

    int effectiveWorkers() {
        return workers > 0 ? workers : Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    }
}
