package pl.fotozielnik.users;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.fotozielnik.ApiClient;
import pl.fotozielnik.IntegrationTest;

/** FR-002: admins create users, reset passwords and deactivate accounts; nobody else can. */
class AdminUsersIT extends IntegrationTest {

    @Test
    void nonAdminGets403AndAnonymousGets401() {
        ApiClient user = signedInAs(newUser());
        assertThat(user.get("/api/admin/users").status()).isEqualTo(403);
        assertThat(user.post("/api/admin/users", newUserBody("x@it.test")).status())
                .isEqualTo(403);
        assertThat(anonymous().get("/api/admin/users").status()).isEqualTo(401);
    }

    @Test
    void adminCreatesAUserWhoMustChangeTheTemporaryPassword() {
        ApiClient admin = admin();
        String email = "created-" + System.nanoTime() + "@it.test";
        var created = admin.post("/api/admin/users", newUserBody(email));
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        assertThat(created.json().get("mustChangePassword").asBoolean()).isTrue();
        assertThat(created.json().has("passwordHash")).isFalse();

        var me = new ApiClient(port)
                .login(email, "temporary-pass")
                .get("/api/me")
                .json();
        assertThat(me.get("mustChangePassword").asBoolean()).isTrue();

        var list = admin.get("/api/admin/users?size=200").json();
        assertThat(list.get("items").valueStream().map(u -> u.get("email").asString()))
                .contains(email);
    }

    @Test
    void duplicateEmailIsAConflict() {
        String email = newUser();
        var r = admin().post("/api/admin/users", newUserBody(email.toUpperCase(java.util.Locale.ROOT)));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.json().get("code").asString()).isEqualTo("user.email-taken");
    }

    @Test
    void invalidRequestIsRejected() {
        var r = admin().post("/api/admin/users", Map.of("email", "not-an-email", "displayName", "", "role", "USER"));
        assertThat(r.status()).isEqualTo(400);
    }

    @Test
    void passwordResetSignsTheUserOutAndSetsATemporaryPassword() {
        String email = newUser();
        ApiClient user = signedInAs(email);
        String id = user.get("/api/me").json().get("id").asString();

        var r = admin().post("/api/admin/users/" + id + "/password-reset", Map.of("temporaryPassword", "reset-pass-1"));
        assertThat(r.status()).isEqualTo(200);

        assertThat(user.get("/api/me").status()).isEqualTo(401);
        var me = new ApiClient(port).login(email, "reset-pass-1").get("/api/me").json();
        assertThat(me.get("mustChangePassword").asBoolean()).isTrue();
    }

    @Test
    void deactivatedUserIsSignedOutAndCannotSignInUntilActivatedAgain() {
        String email = newUser();
        ApiClient user = signedInAs(email);
        String id = user.get("/api/me").json().get("id").asString();
        ApiClient admin = admin();

        assertThat(admin.post("/api/admin/users/" + id + "/deactivate")
                        .json()
                        .get("active")
                        .asBoolean())
                .isFalse();
        assertThat(user.get("/api/me").status()).isEqualTo(401);
        var denied = anonymous().post("/api/auth/login", Map.of("email", email, "password", USER_PASSWORD));
        assertThat(denied.status()).isEqualTo(401);

        admin.post("/api/admin/users/" + id + "/activate");
        signedInAs(email);
    }

    @Test
    void adminCannotDeactivateThemselves() {
        ApiClient admin = admin();
        String id = admin.get("/api/me").json().get("id").asString();
        var r = admin.post("/api/admin/users/" + id + "/deactivate");
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.json().get("code").asString()).isEqualTo("user.cannot-deactivate-self");
    }

    @Test
    void outOfRangePagingIsABadRequest() {
        assertThat(admin().get("/api/admin/users?size=0").status()).isEqualTo(400);
        assertThat(admin().get("/api/admin/users?page=-1").status()).isEqualTo(400);
    }

    @Test
    void unknownUserIs404() {
        var r = admin().post("/api/admin/users/0190a1b2-0000-7000-8000-000000000000/activate");
        assertThat(r.status()).isEqualTo(404);
    }

    private static Map<String, String> newUserBody(String email) {
        return Map.of(
                "email", email, "displayName", "Jan Kowalski", "role", "USER", "temporaryPassword", "temporary-pass");
    }
}
