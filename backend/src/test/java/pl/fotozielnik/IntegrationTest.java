package pl.fotozielnik;

import java.util.UUID;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * Base class for HTTP-level integration tests: full app on a random port, real Postgres (and S3) from
 * Testcontainers. All subclasses share one Spring context and containers, so keep tests independent of each
 * other: create your own users with {@link #newUser}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "fotozielnik.bootstrap-admin.email=" + IntegrationTest.ADMIN_EMAIL,
            "fotozielnik.bootstrap-admin.initial-password=" + IntegrationTest.ADMIN_PASSWORD,
            // no background workers: tests run jobs explicitly with Jobs.drain(), so outcomes are deterministic
            "fotozielnik.jobs.enabled=false"
        })
public abstract class IntegrationTest {

    protected static final String ADMIN_EMAIL = "admin@it.test";
    /** Test fixture only; the bootstrap admin of the throwaway test database. */
    protected static final String ADMIN_PASSWORD = "it-admin-password";

    protected static final String USER_PASSWORD = "it-user-password";

    @LocalServerPort
    protected int port;

    protected ApiClient anonymous() {
        return new ApiClient(port);
    }

    protected ApiClient admin() {
        return new ApiClient(port).login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    /** Creates a fresh USER account (via the admin API) and returns its email; password {@link #USER_PASSWORD}. */
    protected String newUser() {
        String email = "user-" + UUID.randomUUID() + "@it.test";
        var r = admin().post(
                        "/api/admin/users",
                        java.util.Map.of(
                                "email",
                                email,
                                "displayName",
                                "Test User",
                                "role",
                                "USER",
                                "temporaryPassword",
                                USER_PASSWORD));
        if (r.status() != 201) {
            throw new IllegalStateException("could not create user: " + r.status() + " " + r.body());
        }
        return email;
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationContext context;

    /** Runs all due background jobs now (workers are off in tests). */
    protected int runJobs() {
        return pl.fotozielnik.jobs.JobsTestSupport.drain(context);
    }

    protected ApiClient signedInAs(String email) {
        return new ApiClient(port).login(email, USER_PASSWORD);
    }
}
