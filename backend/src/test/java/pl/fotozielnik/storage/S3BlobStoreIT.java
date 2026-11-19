package pl.fotozielnik.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import pl.fotozielnik.IntegrationTest;

/** ADR-0007: the BlobStore works against a real Garage (Testcontainers, same image as compose.yml). */
class S3BlobStoreIT extends IntegrationTest {

    private static final String SHA = "a".repeat(64);

    @Autowired
    BlobStore store;

    @Test
    void putGetExistsDelete(@TempDir Path dir) throws Exception {
        UUID owner = UUID.randomUUID();
        String key = StorageKeys.original(owner, SHA);
        Path file = Files.writeString(dir.resolve("f.jpg"), "fake jpeg bytes");

        assertThat(store.exists(key)).isFalse();
        assertThat(store.get(key)).isEmpty();

        store.put(key, file, "image/jpeg");

        assertThat(store.exists(key)).isTrue();
        try (var blob = store.get(key).orElseThrow()) {
            assertThat(blob.length()).isEqualTo(15);
            assertThat(blob.contentType()).isEqualTo("image/jpeg");
            assertThat(new String(blob.content().readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("fake jpeg bytes");
        }

        store.delete(key);
        assertThat(store.exists(key)).isFalse();
    }

    @Test
    void deleteByPrefixRemovesOnlyThatUsersObjects() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        store.put(StorageKeys.original(alice, SHA), new byte[] {1}, "image/jpeg");
        store.put(StorageKeys.thumbnail(alice, SHA), new byte[] {2}, "image/webp");
        store.put(StorageKeys.original(bob, SHA), new byte[] {3}, "image/jpeg");

        store.deleteByPrefix(StorageKeys.userPrefix(alice));

        assertThat(store.exists(StorageKeys.original(alice, SHA))).isFalse();
        assertThat(store.exists(StorageKeys.thumbnail(alice, SHA))).isFalse();
        assertThat(store.exists(StorageKeys.original(bob, SHA))).isTrue();
    }
}
