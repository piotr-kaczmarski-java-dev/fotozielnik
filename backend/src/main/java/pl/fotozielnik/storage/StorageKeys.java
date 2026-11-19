package pl.fotozielnik.storage;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Object key layout (ADR-0007): everything of a user under {@code u/{ownerId}/}, so deleting a user is one
 * prefix delete. Content-addressed by SHA-256, deduplicated per user, never across users. Derivative keys carry
 * no file extension: the format (JPEG today, maybe WebP with libvips later) is the object's content type.
 */
public final class StorageKeys {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private StorageKeys() {}

    public static String userPrefix(UUID ownerId) {
        return "u/" + ownerId + "/";
    }

    public static String original(UUID ownerId, String sha256) {
        return userPrefix(ownerId) + "o/" + checked(sha256);
    }

    public static String thumbnail(UUID ownerId, String sha256) {
        return userPrefix(ownerId) + "t/" + checked(sha256);
    }

    public static String preview(UUID ownerId, String sha256) {
        return userPrefix(ownerId) + "p/" + checked(sha256);
    }

    private static String checked(String sha256) {
        if (!SHA256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("not a lower-case hex SHA-256: " + sha256);
        }
        return sha256;
    }
}
