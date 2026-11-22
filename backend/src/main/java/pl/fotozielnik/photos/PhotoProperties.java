package pl.fotozielnik.photos;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code trashRetention}: how long a deleted photo stays restorable before it is removed for good (FR-022). */
@ConfigurationProperties("fotozielnik.photos")
record PhotoProperties(@DefaultValue("30d") Duration trashRetention) {}
