package pl.fotozielnik.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.fotozielnik.IntegrationTest;

/**
 * Production serving of the frontend (SpaConfig + SecurityConfig): the shell is public, deep links get the
 * shell, the API stays protected and unknown API paths never turn into HTML. Uses the test static/index.html.
 */
class SpaIT extends IntegrationTest {

    @Test
    void shellAndDeepLinksArePublic() {
        for (String path : new String[] {"/", "/photos/0190a1b2-0000-7000-8000-000000000000", "/import"}) {
            var r = anonymous().get(path);
            assertThat(r.status()).as(path).isEqualTo(200);
            assertThat(r.body()).as(path).contains("spa-shell");
            assertThat(r.header("Cache-Control").orElse("")).as(path).contains("no-cache");
        }
    }

    @Test
    void apiStaysProtectedAndUnknownApiPathsAreNotHtml() {
        assertThat(anonymous().get("/api/photos").status()).isEqualTo(401);
        var signedIn = signedInAs(newUser());
        var unknown = signedIn.get("/api/does-not-exist");
        assertThat(unknown.status()).isEqualTo(404);
        assertThat(unknown.body()).doesNotContain("spa-shell");
    }

    @Test
    void encodedApiPathsAreStillProtected() {
        // regression: a raw-string check let "/%61pi/photos" (= /api/photos) past the security rules
        for (String path : new String[] {"/%61pi/photos", "/%61pi/me", "/api/%70hotos"}) {
            assertThat(anonymous().get(path).status()).as(path).isIn(400, 401);
        }
    }

    @Test
    void missingFilesAre404NotTheShell() {
        assertThat(anonymous().get("/assets/missing-123.js").status()).isEqualTo(404);
        assertThat(anonymous().get("/robots.txt").status()).isEqualTo(404);
    }
}
