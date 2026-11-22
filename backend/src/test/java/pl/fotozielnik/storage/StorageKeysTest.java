package pl.fotozielnik.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class StorageKeysTest {

    private static final UUID OWNER = UUID.fromString("0190a1b2-0000-7000-8000-000000000001");
    private static final String SHA = "0123456789abcdef".repeat(4);

    @Test
    void layoutFollowsAdr0007() {
        assertThat(StorageKeys.original(OWNER, SHA)).isEqualTo("u/" + OWNER + "/o/" + SHA);
        assertThat(StorageKeys.thumbnail(OWNER, SHA)).isEqualTo("u/" + OWNER + "/t/" + SHA);
        assertThat(StorageKeys.preview(OWNER, SHA)).isEqualTo("u/" + OWNER + "/p/" + SHA);
    }

    @Test
    void rejectsAnythingButALowerCaseHash() {
        assertThatThrownBy(() -> StorageKeys.original(OWNER, "../../etc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageKeys.original(OWNER, SHA.toUpperCase(java.util.Locale.ROOT)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
