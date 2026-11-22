package pl.fotozielnik.shared;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * Primary keys for user data: UUIDv7 (RFC 9562), generated in the app. Time-ordered, so B-tree inserts stay
 * local and ids sort roughly by creation time.
 */
public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {}

    public static UUID newId() {
        return uuidV7(Clock.systemUTC().millis());
    }

    static UUID uuidV7(long epochMillis) {
        long randA = RANDOM.nextInt(1 << 12);
        long randB = RANDOM.nextLong();
        long msb = (epochMillis << 16) | (0x7L << 12) | randA;
        long lsb = (randB & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L; // IETF variant 10
        return new UUID(msb, lsb);
    }
}
