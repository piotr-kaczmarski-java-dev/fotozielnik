package pl.fotozielnik.users;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.fotozielnik.IntegrationTest;

/** FR-003: the first admin comes from ADMIN_EMAIL / ADMIN_INITIAL_PASSWORD (set by {@link IntegrationTest}). */
class BootstrapAdminIT extends IntegrationTest {

    @Autowired
    UserService users;

    @Test
    void adminFromEnvironmentCanSignInAndMustChangePassword() {
        var me = admin().get("/api/me").json();
        assertThat(me.get("email").asString()).isEqualTo(ADMIN_EMAIL);
        assertThat(me.get("role").asString()).isEqualTo("ADMIN");
        assertThat(me.get("mustChangePassword").asBoolean()).isTrue();
    }

    @Test
    void bootstrapIsSkippedOnceAnAdminExists() {
        assertThat(users.bootstrapAdmin("someone-else@it.test", "another-password"))
                .isFalse();
        anonymous()
                .post(
                        "/api/auth/login",
                        java.util.Map.of("email", "someone-else@it.test", "password", "another-password"))
                .status();
        assertThat(anonymous()
                        .post(
                                "/api/auth/login",
                                java.util.Map.of("email", "someone-else@it.test", "password", "another-password"))
                        .status())
                .isEqualTo(401);
    }
}
