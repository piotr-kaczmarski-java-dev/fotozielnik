package pl.fotozielnik;

import java.nio.file.Path;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real Postgres and S3 for integration tests, built from the same {@code docker/} Dockerfiles as compose.yml
 * (ADR-0007, ADR-0008). The images keep their tags, so Docker's layer cache makes later builds instant.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static final String POSTGRES_IMAGE = "fotozielnik/postgres:18";
    static final String GARAGE_IMAGE = "fotozielnik/garage:2.2";

    // Throwaway credentials of a throwaway container.
    private static final String S3_ACCESS_KEY_ID = "GK0000000000000000000000aa";
    private static final String S3_SECRET = "00000000000000000000000000000000000000000000000000000000000000bb";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        String image = new ImageFromDockerfile(POSTGRES_IMAGE, false)
                .withFileFromPath(".", Path.of("../docker/postgres"))
                .get();
        return new PostgreSQLContainer(DockerImageName.parse(image).asCompatibleSubstituteFor("postgres"));
    }

    @Bean
    GenericContainer<?> garageContainer() {
        String image = new ImageFromDockerfile(GARAGE_IMAGE, false)
                .withFileFromPath(".", Path.of("../docker/garage"))
                .get();
        return new GenericContainer<>(DockerImageName.parse(image))
                .withEnv("GARAGE_RPC_SECRET", "00000000000000000000000000000000000000000000000000000000000000cc")
                .withEnv("GARAGE_ACCESS_KEY_ID", S3_ACCESS_KEY_ID)
                .withEnv("GARAGE_SECRET_ACCESS_KEY", S3_SECRET)
                .withEnv("GARAGE_BUCKET", "photos")
                .withExposedPorts(3900)
                .waitingFor(Wait.forHealthcheck());
    }

    @Bean
    DynamicPropertyRegistrar garageProperties(GenericContainer<?> garageContainer) {
        return registry -> {
            registry.add(
                    "fotozielnik.storage.endpoint",
                    () -> "http://" + garageContainer.getHost() + ":" + garageContainer.getMappedPort(3900));
            registry.add("fotozielnik.storage.access-key-id", () -> S3_ACCESS_KEY_ID);
            registry.add("fotozielnik.storage.secret-access-key", () -> S3_SECRET);
            registry.add("fotozielnik.storage.bucket", () -> "photos");
        };
    }
}
