package pl.fotozielnik;

import org.springframework.boot.SpringApplication;

/** Runs the app against a Testcontainers Postgres instead of compose.yml: `./gradlew bootTestRun`. */
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.from(Application::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
