package pl.fotozielnik.imports;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.fotozielnik.ApiClient;
import pl.fotozielnik.IntegrationTest;
import pl.fotozielnik.TestImages;

/** FR-004..FR-006: upload only new files; a second import of the same folder uploads nothing. */
class ImportDedupIT extends IntegrationTest {

    @Test
    void secondImportOfTheSameFolderUploadsNothing() {
        ApiClient user = signedInAs(newUser());
        List<byte[]> folder = IntStream.range(0, 3).mapToObj(TestImages::jpeg).toList();
        List<String> hashes = folder.stream().map(TestImages::sha256).toList();

        // first import: everything is new
        String first = startImport(user, 3);
        assertThat(check(user, first, hashes)).containsExactlyElementsOf(hashes);
        for (int i = 0; i < folder.size(); i++) {
            var r = upload(user, first, folder.get(i), "Łąka 2024/IMG_" + i + ".jpg");
            assertThat(r.status()).as(r.body()).isEqualTo(201);
        }
        var done = user.post("/api/imports/" + first + "/finish").json();
        assertThat(done.get("status").asString()).isEqualTo("FINISHED");
        assertThat(done.get("filesUploaded").asInt()).isEqualTo(3);

        // second import of the same folder: nothing to upload, all counted as duplicates
        String second = startImport(user, 3);
        assertThat(check(user, second, hashes)).isEmpty();
        var progress = user.get("/api/imports/" + second).json();
        assertThat(progress.get("filesDuplicate").asInt()).isEqualTo(3);
        assertThat(progress.get("filesUploaded").asInt()).isZero();
    }

    @Test
    void interruptedImportResumesWithTheRemainingFiles() {
        ApiClient user = signedInAs(newUser());
        List<byte[]> folder =
                IntStream.range(100, 104).mapToObj(TestImages::jpeg).toList();
        List<String> hashes = folder.stream().map(TestImages::sha256).toList();

        String first = startImport(user, 4);
        check(user, first, hashes);
        upload(user, first, folder.get(0), "a.jpg");
        upload(user, first, folder.get(1), "b.jpg");
        // ... tab closed: the batch is never finished

        String resumed = startImport(user, 4);
        assertThat(check(user, resumed, hashes)).containsExactly(hashes.get(2), hashes.get(3));
    }

    @Test
    void dedupIsPerUserNotAcrossUsers() {
        byte[] photo = TestImages.jpeg(200);
        String hash = TestImages.sha256(photo);
        ApiClient alice = signedInAs(newUser());
        ApiClient bob = signedInAs(newUser());

        String a = startImport(alice, 1);
        upload(alice, a, photo, "x.jpg");

        String b = startImport(bob, 1);
        assertThat(check(bob, b, List.of(hash))).containsExactly(hash);
    }

    @Test
    void uploadingTheSameContentTwiceIsADuplicateNotAnError() {
        ApiClient user = signedInAs(newUser());
        byte[] photo = TestImages.jpeg(300);
        String batch = startImport(user, 2);
        assertThat(upload(user, batch, photo, "a.jpg").status()).isEqualTo(201);
        var again = upload(user, batch, photo, "copy/a.jpg");
        assertThat(again.status()).isEqualTo(200);
        assertThat(again.json().get("created").asBoolean()).isFalse();
    }

    @Test
    void wrongHashIsRejected() {
        ApiClient user = signedInAs(newUser());
        String batch = startImport(user, 1);
        var r = user.putBytes(
                "/api/imports/" + batch + "/files/" + "0".repeat(64),
                TestImages.jpeg(400),
                Map.of("X-File-Path", "a.jpg"));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.json().get("code").asString()).isEqualTo("import.hash-mismatch");
    }

    @Test
    void nonJpegIsRejectedAndCountedAsFailed() {
        ApiClient user = signedInAs(newUser());
        String batch = startImport(user, 1);
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        var r = upload(user, batch, png, "a.png");
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.json().get("code").asString()).isEqualTo("import.not-a-jpeg");
        assertThat(user.get("/api/imports/" + batch).json().get("filesFailed").asInt())
                .isEqualTo(1);
    }

    @Test
    void malformedPathOrHashIsABadRequest() {
        ApiClient user = signedInAs(newUser());
        String batch = startImport(user, 1);
        byte[] jpeg = TestImages.jpeg(700);
        var badPath = user.putBytes(
                "/api/imports/" + batch + "/files/" + TestImages.sha256(jpeg), jpeg, Map.of("X-File-Path", "%E0%A4%A"));
        assertThat(badPath.status()).isEqualTo(400);
        var badHash =
                user.putBytes("/api/imports/" + batch + "/files/NOT-A-HASH", jpeg, Map.of("X-File-Path", "a.jpg"));
        assertThat(badHash.status()).isEqualTo(400);
    }

    @Test
    void finishedBatchAcceptsNoMoreFiles() {
        ApiClient user = signedInAs(newUser());
        String batch = startImport(user, 1);
        user.post("/api/imports/" + batch + "/finish");
        assertThat(upload(user, batch, TestImages.jpeg(500), "a.jpg").status()).isEqualTo(409);
    }

    @Test
    void anotherUsersBatchIsNotFound() {
        ApiClient alice = signedInAs(newUser());
        ApiClient bob = signedInAs(newUser());
        String batch = startImport(alice, 1);

        assertThat(bob.get("/api/imports/" + batch).status()).isEqualTo(404);
        assertThat(bob.post("/api/imports/" + batch + "/check", Map.of("sha256s", List.of()))
                        .status())
                .isEqualTo(404);
        assertThat(upload(bob, batch, TestImages.jpeg(600), "a.jpg").status()).isEqualTo(404);
        assertThat(bob.get("/api/imports").json().get("items").size()).isZero();
    }

    static String startImport(ApiClient user, int files) {
        var r = user.post("/api/imports", Map.of("label", "Łąka 2024", "filesFound", files, "filesSkipped", 1));
        assertThat(r.status()).as(r.body()).isEqualTo(201);
        return r.json().get("id").asString();
    }

    static List<String> check(ApiClient user, String batch, List<String> hashes) {
        var r = user.post("/api/imports/" + batch + "/check", Map.of("sha256s", hashes));
        assertThat(r.status()).as(r.body()).isEqualTo(200);
        return r.json().get("missing").valueStream().map(n -> n.asString()).toList();
    }

    static ApiClient.Response upload(ApiClient user, String batch, byte[] bytes, String path) {
        return user.putBytes(
                "/api/imports/" + batch + "/files/" + TestImages.sha256(bytes),
                bytes,
                Map.of(
                        "X-File-Path",
                        URLEncoder.encode(path, StandardCharsets.UTF_8),
                        "X-File-Modified",
                        "1700000000000"));
    }
}
