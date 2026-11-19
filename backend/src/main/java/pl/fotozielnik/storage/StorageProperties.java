package pl.fotozielnik.storage;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3 connection (ADR-0007). Dev defaults point at compose.yml's Garage; the prod profile takes everything from
 * environment variables ({@code S3_ENDPOINT}, {@code S3_ACCESS_KEY_ID}, ...).
 */
@ConfigurationProperties("fotozielnik.storage")
record StorageProperties(
        URI endpoint, String region, String bucket, String accessKeyId, String secretAccessKey, boolean pathStyle) {}
