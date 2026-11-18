package pl.fotozielnik;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Full context + real Postgres (Docker): Flyway migrations apply and Hibernate validates the schema. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ApplicationIT {

    @Autowired
    RestTestClient client;

    @Test
    void healthIsUp() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void apiIsDenyByDefault() {
        var status = client.get()
                .uri("/api/anything")
                .exchange()
                .returnResult(String.class)
                .getStatus();
        assertThat(status).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
