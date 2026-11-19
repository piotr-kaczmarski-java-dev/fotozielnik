package pl.fotozielnik.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdsTest {

    @Test
    void isVersion7WithIetfVariant() {
        UUID id = Ids.newId();
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void encodesTheTimestampInTheTopBits() {
        long millis = 1_763_424_000_000L;
        assertThat(Ids.uuidV7(millis).getMostSignificantBits() >>> 16).isEqualTo(millis);
    }

    @Test
    void laterIdsSortAfterEarlierOnes() {
        UUID earlier = Ids.uuidV7(1_000);
        UUID later = Ids.uuidV7(2_000);
        assertThat(later.toString()).isGreaterThan(earlier.toString());
    }
}
