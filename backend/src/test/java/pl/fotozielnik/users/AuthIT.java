package pl.fotozielnik.users;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.fotozielnik.ApiClient;
import pl.fotozielnik.IntegrationTest;

/** FR-001: sign in with email + password into a server-side session, sign out; ADR-0005 details. */
class AuthIT extends IntegrationTest {

    @Test
    void signInStartsASessionAndSignOutEndsIt() {
        String email = newUser();
        ApiClient browser = signedInAs(email);

        var me = browser.get("/api/me");
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.json().get("email").asString()).isEqualTo(email);
        assertThat(me.json().get("role").asString()).isEqualTo("USER");
        assertThat(browser.cookie("SESSION")).isPresent();

        assertThat(browser.post("/api/auth/logout").status()).isEqualTo(204);
        assertThat(browser.get("/api/me").status()).isEqualTo(401);
    }

    @Test
    void emailIsCaseInsensitive() {
        String email = newUser();
        new ApiClient(port).login("  " + email.toUpperCase(java.util.Locale.ROOT) + " ", USER_PASSWORD);
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() {
        String email = newUser();
        var wrongPassword = anonymous().post("/api/auth/login", Map.of("email", email, "password", "nope-nope-nope"));
        var unknownEmail =
                anonymous().post("/api/auth/login", Map.of("email", "nobody@it.test", "password", "nope-nope-nope"));

        for (var r : java.util.List.of(wrongPassword, unknownEmail)) {
            assertThat(r.status()).isEqualTo(401);
            assertThat(r.json().get("code").asString()).isEqualTo("auth.bad-credentials");
        }
    }

    @Test
    void sessionIdChangesOnSignIn() {
        String email = newUser();
        ApiClient browser = anonymous();
        browser.get("/api/auth/csrf");
        browser.post("/api/auth/login", Map.of("email", "nobody@it.test", "password", "x")); // creates no session
        String before = browser.cookie("SESSION").orElse("none");
        browser.login(email, USER_PASSWORD);
        assertThat(browser.cookie("SESSION")).isPresent().get().isNotEqualTo(before);
    }

    @Test
    void unsafeRequestsNeedTheCsrfToken() {
        String email = newUser();
        var r = anonymous().postWithoutCsrf("/api/auth/login", Map.of("email", email, "password", USER_PASSWORD));
        assertThat(r.status()).isEqualTo(403);
    }

    @Test
    void tooManyFailuresForOneEmailAreThrottled() {
        String email = newUser();
        ApiClient browser = anonymous();
        for (int i = 0; i < LoginThrottle.MAX_FAILURES_PER_EMAIL; i++) {
            assertThat(browser.post("/api/auth/login", Map.of("email", email, "password", "wrong-" + i))
                            .status())
                    .isEqualTo(401);
        }
        var blocked = browser.post("/api/auth/login", Map.of("email", email, "password", USER_PASSWORD));
        assertThat(blocked.status()).isEqualTo(429);
        assertThat(blocked.json().get("code").asString()).isEqualTo("auth.too-many-attempts");
        assertThat(blocked.header("Retry-After")).isPresent();
    }

    @Test
    void changingThePasswordSignsOutOtherSessionsOnly() {
        String email = newUser();
        ApiClient laptop = signedInAs(email);
        ApiClient phone = signedInAs(email);
        assertThat(laptop.get("/api/me").json().get("mustChangePassword").asBoolean())
                .isTrue();

        var r = laptop.put(
                "/api/me/password", Map.of("currentPassword", USER_PASSWORD, "newPassword", "brand-new-password"));
        assertThat(r.status()).as(r.body()).isEqualTo(204);

        assertThat(laptop.get("/api/me").json().get("mustChangePassword").asBoolean())
                .isFalse();
        assertThat(phone.get("/api/me").status()).isEqualTo(401);
        new ApiClient(port).login(email, "brand-new-password");
    }

    @Test
    void changingThePasswordNeedsTheCurrentOne() {
        ApiClient browser = signedInAs(newUser());
        var r = browser.put(
                "/api/me/password", Map.of("currentPassword", "not-my-password", "newPassword", "brand-new-password"));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.json().get("code").asString()).isEqualTo("auth.wrong-current-password");
    }

    @Test
    void tooShortNewPasswordIsRejected() {
        ApiClient browser = signedInAs(newUser());
        var r = browser.put("/api/me/password", Map.of("currentPassword", USER_PASSWORD, "newPassword", "short"));
        assertThat(r.status()).isEqualTo(400);
    }
}
