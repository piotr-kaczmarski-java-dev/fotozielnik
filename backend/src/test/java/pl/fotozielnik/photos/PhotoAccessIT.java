package pl.fotozielnik.photos;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.fotozielnik.ApiClient;
import pl.fotozielnik.IntegrationTest;
import pl.fotozielnik.TestImages;

/** FR-009: a user browses only their own photos; the grid is date-ordered and paginated. */
class PhotoAccessIT extends IntegrationTest {

    @Test
    void ownPhotoIsListedAndItsOriginalStreamed() {
        ApiClient user = signedInAs(newUser());
        byte[] jpeg = TestImages.jpeg(1000);
        String id = uploadOne(user, jpeg, "Wiosna/Zawilec gajowy.jpg", 1_700_000_000_000L);

        var page = user.get("/api/photos").json();
        assertThat(page.get("items").size()).isEqualTo(1);
        assertThat(page.get("items").get(0).get("id").asString()).isEqualTo(id);
        assertThat(page.get("nextCursor").isNull()).isTrue();

        var details = user.get("/api/photos/" + id).json();
        assertThat(details.get("originalPath").asString()).isEqualTo("Wiosna/Zawilec gajowy.jpg");
        assertThat(details.get("sha256").asString()).isEqualTo(TestImages.sha256(jpeg));

        var original = user.get("/api/photos/" + id + "/original");
        assertThat(original.status()).isEqualTo(200);
        assertThat(original.bytes()).isEqualTo(jpeg);
        assertThat(original.header("Content-Type")).contains("image/jpeg");
        assertThat(original.header("Cache-Control").orElseThrow()).contains("private");
        assertThat(original.header("ETag")).isPresent();
    }

    @Test
    void anotherUsersPhotoIsNotFoundEverywhere() {
        ApiClient alice = signedInAs(newUser());
        ApiClient bob = signedInAs(newUser());
        String id = uploadOne(alice, TestImages.jpeg(1001), "a.jpg", 1_700_000_000_000L);

        assertThat(bob.get("/api/photos/" + id).status()).isEqualTo(404);
        assertThat(bob.get("/api/photos/" + id + "/original").status()).isEqualTo(404);
        assertThat(bob.get("/api/photos/" + id + "/thumb").status()).isEqualTo(404);
        assertThat(bob.get("/api/photos").json().get("items").size()).isZero();
        assertThat(anonymous().get("/api/photos/" + id + "/original").status()).isEqualTo(401);
    }

    @Test
    void gridIsNewestFirstAndPagesDoNotOverlap() {
        ApiClient user = signedInAs(newUser());
        List<String> idsNewestFirst = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            idsNewestFirst.addFirst(
                    uploadOne(user, TestImages.jpeg(2000 + i), "p" + i + ".jpg", 1_700_000_000_000L + i * 1000L));
        }

        List<String> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            var page = user.get("/api/photos?size=2" + (cursor != null ? "&cursor=" + cursor : ""))
                    .json();
            page.get("items").valueStream().forEach(n -> seen.add(n.get("id").asString()));
            cursor = page.get("nextCursor").isNull()
                    ? null
                    : page.get("nextCursor").asString();
            pages++;
        } while (cursor != null);

        assertThat(seen).containsExactlyElementsOf(idsNewestFirst);
        assertThat(pages).isEqualTo(3);
    }

    @Test
    void badParametersAreBadRequestsNotServerErrors() {
        ApiClient user = signedInAs(newUser());
        assertThat(user.get("/api/photos?cursor=not-a-cursor").status()).isEqualTo(400);
        assertThat(user.get("/api/photos?size=0").status()).isEqualTo(400);
        assertThat(user.get("/api/photos?size=1000").status()).isEqualTo(400);
        assertThat(user.get("/api/photos/not-a-uuid").status()).isEqualTo(400);
        assertThat(user.get("/api/imports?size=0").status()).isEqualTo(400);
    }

    @Test
    void unchangedImageIsNotSentAgain() {
        ApiClient user = signedInAs(newUser());
        String id = uploadOne(user, TestImages.jpeg(1002), "a.jpg", 1_700_000_000_000L);
        String etag = user.get("/api/photos/" + id + "/original").header("ETag").orElseThrow();
        var again = user.getWithHeaders("/api/photos/" + id + "/original", Map.of("If-None-Match", etag));
        assertThat(again.status()).isEqualTo(304);
    }

    private static String uploadOne(ApiClient user, byte[] jpeg, String path, long modified) {
        String batch = user.post("/api/imports", Map.of("label", "t", "filesFound", 1, "filesSkipped", 0))
                .json()
                .get("id")
                .asString();
        var r = user.putBytes(
                "/api/imports/" + batch + "/files/" + TestImages.sha256(jpeg),
                jpeg,
                Map.of(
                        "X-File-Path", URLEncoder.encode(path, StandardCharsets.UTF_8),
                        "X-File-Modified", Long.toString(modified)));
        assertThat(r.status()).as(r.body()).isEqualTo(201);
        return r.json().get("photoId").asString();
    }
}
