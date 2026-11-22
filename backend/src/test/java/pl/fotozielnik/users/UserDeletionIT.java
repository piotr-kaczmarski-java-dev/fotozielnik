package pl.fotozielnik.users;

import static org.assertj.core.api.Assertions.assertThat;

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

/** NFR-006: deleting a user deletes their photos, stored objects and rows - and nobody else's. */
class UserDeletionIT extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    BlobStore blobs;

    @Test
    void deletingAUserRemovesTheirWholeLibraryAndNothingElse() {
        String doomedEmail = newUser();
        ApiClient doomed = signedInAs(doomedEmail);
        UUID doomedId = UUID.fromString(doomed.get("/api/me").json().get("id").asString());
        String doomedSha = upload(doomed, TestImages.jpeg(9001));

        ApiClient other = signedInAs(newUser());
        UUID otherId = UUID.fromString(other.get("/api/me").json().get("id").asString());
        String otherSha = upload(other, TestImages.jpeg(9002));
        runJobs(); // thumbnails + previews exist for both

        assertThat(blobs.exists(StorageKeys.thumbnail(doomedId, doomedSha))).isTrue();

        var r = admin().delete("/api/admin/users/" + doomedId);
        assertThat(r.status()).as(r.body()).isEqualTo(204);

        // signed out and gone
        assertThat(doomed.get("/api/me").status()).isEqualTo(401);
        assertThat(anonymous()
                        .post("/api/auth/login", Map.of("email", doomedEmail, "password", USER_PASSWORD))
                        .status())
                .isEqualTo(401);
        for (String table : new String[] {"app_user where id", "photo where owner_id", "import_batch where owner_id"}) {
            assertThat(jdbc.queryForObject("select count(*) from " + table + " = ?", Long.class, doomedId))
                    .as(table)
                    .isZero();
        }

        // objects are removed by the background job
        runJobs();
        assertThat(blobs.exists(StorageKeys.original(doomedId, doomedSha))).isFalse();
        assertThat(blobs.exists(StorageKeys.thumbnail(doomedId, doomedSha))).isFalse();
        assertThat(blobs.exists(StorageKeys.preview(doomedId, doomedSha))).isFalse();

        // the other user is untouched
        assertThat(blobs.exists(StorageKeys.original(otherId, otherSha))).isTrue();
        assertThat(blobs.exists(StorageKeys.thumbnail(otherId, otherSha))).isTrue();
        assertThat(other.get("/api/photos").json().get("items").size()).isEqualTo(1);
    }

    @Test
    void onlyAdminsDeleteAndNeverThemselves() {
        ApiClient admin = admin();
        String adminId = admin.get("/api/me").json().get("id").asString();
        var self = admin.delete("/api/admin/users/" + adminId);
        assertThat(self.status()).isEqualTo(400);
        assertThat(self.json().get("code").asString()).isEqualTo("user.cannot-delete-self");

        ApiClient user = signedInAs(newUser());
        String victim = signedInAs(newUser()).get("/api/me").json().get("id").asString();
        assertThat(user.delete("/api/admin/users/" + victim).status()).isEqualTo(403);
        assertThat(admin.delete("/api/admin/users/" + UUID.randomUUID()).status())
                .isEqualTo(404);
    }

    private static String upload(ApiClient user, byte[] jpeg) {
        String batch = user.post("/api/imports", Map.of("label", "x", "filesFound", 1, "filesSkipped", 0))
                .json()
                .get("id")
                .asString();
        String sha = TestImages.sha256(jpeg);
        var r = user.putBytes("/api/imports/" + batch + "/files/" + sha, jpeg, Map.of("X-File-Path", "a.jpg"));
        assertThat(r.status()).as(r.body()).isEqualTo(201);
        return sha;
    }
}
