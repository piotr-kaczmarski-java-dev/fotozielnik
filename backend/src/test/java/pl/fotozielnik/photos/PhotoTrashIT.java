package pl.fotozielnik.photos;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.fotozielnik.ApiClient;
import pl.fotozielnik.IntegrationTest;
import pl.fotozielnik.TestImages;
import pl.fotozielnik.storage.BlobStore;
import pl.fotozielnik.storage.StorageKeys;

/** FR-022: photos go to the trash, can be restored, and are purged (objects + rows) after the retention period. */
class PhotoTrashIT extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    BlobStore blobs;

    @Autowired
    TrashPurger purger;

    @Test
    void trashedPhotosLeaveTheGridAndComeBackWhenRestored() {
        ApiClient user = signedInAs(newUser());
        List<String> ids = upload(user, 3, 31000);

        var r = user.post("/api/photos/trash", Map.of("ids", List.of(ids.get(0), ids.get(1))));
        assertThat(r.status()).as(r.body()).isEqualTo(200);
        assertThat(r.json().get("changed").asInt()).isEqualTo(2);
        assertThat(r.json().get("inTrash").asInt()).isEqualTo(2);

        assertThat(gridIds(user)).containsExactly(ids.get(2));
        assertThat(user.get("/api/photos/trash/info").json().get("count").asInt())
                .isEqualTo(2);
        assertThat(user.get("/api/photos/trash/info")
                        .json()
                        .get("retentionDays")
                        .asInt())
                .isEqualTo(30);
        var trash = user.get("/api/photos/trash").json().get("items");
        assertThat(trash.size()).isEqualTo(2);
        assertThat(trash.get(0).get("deletedAt").isNull()).isFalse();

        // a trashed photo can still be opened (to look at it before restoring)
        var details = user.get("/api/photos/" + ids.get(0)).json();
        assertThat(details.get("deletedAt").isNull()).isFalse();
        assertThat(user.get("/api/photos/" + ids.get(0) + "/original").status()).isEqualTo(200);

        var restored = user.post("/api/photos/restore", Map.of("ids", List.of(ids.get(0))));
        assertThat(restored.json().get("changed").asInt()).isEqualTo(1);
        assertThat(gridIds(user)).containsExactlyInAnyOrder(ids.get(0), ids.get(2));
        assertThat(user.get("/api/photos/" + ids.get(0)).json().get("deletedAt").isNull())
                .isTrue();

        // and once more: a restored photo can go to the trash again
        var again = user.post("/api/photos/trash", Map.of("ids", List.of(ids.get(0))));
        assertThat(again.json().get("changed").asInt()).isEqualTo(1);
        assertThat(gridIds(user)).containsExactly(ids.get(2));
    }

    @Test
    void nobodyCanTrashOrRestoreSomebodyElsesPhotos() {
        ApiClient alice = signedInAs(newUser());
        ApiClient bob = signedInAs(newUser());
        List<String> ids = upload(alice, 1, 32000);

        assertThat(bob.post("/api/photos/trash", Map.of("ids", ids))
                        .json()
                        .get("changed")
                        .asInt())
                .isZero();
        assertThat(gridIds(alice)).containsExactlyElementsOf(ids);

        alice.post("/api/photos/trash", Map.of("ids", ids));
        assertThat(bob.post("/api/photos/restore", Map.of("ids", ids))
                        .json()
                        .get("changed")
                        .asInt())
                .isZero();
        assertThat(bob.get("/api/photos/trash").json().get("items").size()).isZero();
        assertThat(gridIds(alice)).isEmpty();
    }

    @Test
    void expiredPhotosArePurgedWithTheirFilesFreshOnesStay() {
        ApiClient user = signedInAs(newUser());
        UUID owner = UUID.fromString(user.get("/api/me").json().get("id").asString());
        List<String> ids = upload(user, 2, 33000);
        runJobs(); // thumbnails + previews
        user.post("/api/photos/trash", Map.of("ids", ids));
        String oldSha = sha(ids.get(0));
        jdbc.update(
                "update photo set deleted_at = now() - interval '31 days' where id = ?", UUID.fromString(ids.get(0)));

        purger.purgeExpired();
        runJobs();

        assertThat(jdbc.queryForObject(
                        "select count(*) from photo where id = ?", Long.class, UUID.fromString(ids.get(0))))
                .isZero();
        assertThat(blobs.exists(StorageKeys.original(owner, oldSha))).isFalse();
        assertThat(blobs.exists(StorageKeys.thumbnail(owner, oldSha))).isFalse();
        assertThat(blobs.exists(StorageKeys.preview(owner, oldSha))).isFalse();
        // the photo trashed just now stays restorable
        assertThat(user.get("/api/photos/trash").json().get("items").size()).isEqualTo(1);
        assertThat(blobs.exists(StorageKeys.original(owner, sha(ids.get(1))))).isTrue();
    }

    @Test
    void emptyingTheTrashPurgesItNowButKeepsTheLibrary() {
        ApiClient user = signedInAs(newUser());
        List<String> ids = upload(user, 3, 34000);
        user.post("/api/photos/trash", Map.of("ids", List.of(ids.get(0), ids.get(1))));

        var r = user.post("/api/photos/trash/empty");
        assertThat(r.status()).isEqualTo(202);
        assertThat(r.json().get("changed").asInt()).isEqualTo(2);
        // gone from the trash at once, even before the background purge ran - and no longer restorable
        assertThat(user.get("/api/photos/trash/info").json().get("count").asInt())
                .isZero();
        assertThat(user.get("/api/photos/trash").json().get("items").size()).isZero();
        assertThat(user.post("/api/photos/restore", Map.of("ids", List.of(ids.get(0))))
                        .json()
                        .get("changed")
                        .asInt())
                .isZero();
        runJobs();
        assertThat(jdbc.queryForObject(
                        "select count(*) from photo where id = ?", Long.class, UUID.fromString(ids.get(0))))
                .isZero();

        assertThat(user.get("/api/photos/trash/info").json().get("count").asInt())
                .isZero();
        assertThat(gridIds(user)).containsExactly(ids.get(2));
    }

    @Test
    void importingATrashedPhotoAgainRestoresIt() {
        ApiClient user = signedInAs(newUser());
        byte[] jpeg = TestImages.jpeg(35000);
        String id = upload(user, List.of(jpeg)).getFirst();
        user.post("/api/photos/trash", Map.of("ids", List.of(id)));
        assertThat(gridIds(user)).isEmpty();

        String batch = startBatch(user);
        var check = user.post("/api/imports/" + batch + "/check", Map.of("sha256s", List.of(TestImages.sha256(jpeg))));
        assertThat(check.json().get("missing").size())
                .as("already in the library")
                .isZero();
        assertThat(gridIds(user)).containsExactly(id);
    }

    @Test
    void invalidRequestsAreRejected() {
        ApiClient user = signedInAs(newUser());
        assertThat(user.post("/api/photos/trash", Map.of("ids", List.of())).status())
                .isEqualTo(400);
        assertThat(user.post("/api/photos/trash", Map.of("ids", List.of("not-a-uuid")))
                        .status())
                .isEqualTo(400);
        assertThat(anonymous().get("/api/photos/trash").status()).isEqualTo(401);
    }

    private String sha(String photoId) {
        return jdbc.queryForObject("select sha256 from photo where id = ?", String.class, UUID.fromString(photoId));
    }

    private static List<String> gridIds(ApiClient user) {
        List<String> ids = new ArrayList<>();
        user.get("/api/photos")
                .json()
                .get("items")
                .valueStream()
                .forEach(n -> ids.add(n.get("id").asString()));
        return ids;
    }

    private static String startBatch(ApiClient user) {
        return user.post("/api/imports", Map.of("label", "t", "filesFound", 1, "filesSkipped", 0))
                .json()
                .get("id")
                .asString();
    }

    private static List<String> upload(ApiClient user, int n, int seed) {
        List<byte[]> files = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            files.add(TestImages.jpeg(seed + i));
        }
        return upload(user, files);
    }

    private static List<String> upload(ApiClient user, List<byte[]> files) {
        String batch = startBatch(user);
        List<String> ids = new ArrayList<>();
        long modified = Instant.parse("2024-05-01T10:00:00Z").toEpochMilli();
        for (byte[] jpeg : files) {
            var r = user.putBytes(
                    "/api/imports/" + batch + "/files/" + TestImages.sha256(jpeg),
                    jpeg,
                    Map.of(
                            "X-File-Path", URLEncoder.encode("a.jpg", StandardCharsets.UTF_8),
                            "X-File-Modified", Long.toString(modified++)));
            assertThat(r.status()).as(r.body()).isEqualTo(201);
            ids.add(r.json().get("photoId").asString());
        }
        return ids;
    }
}
