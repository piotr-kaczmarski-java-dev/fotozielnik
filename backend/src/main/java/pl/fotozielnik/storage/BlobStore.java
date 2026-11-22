package pl.fotozielnik.storage;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Object storage for photo bytes (ADR-0007). Keys come from {@link StorageKeys}; implementations must be
 * thread-safe. Only basic S3 semantics are assumed, so any S3-compatible server works.
 */
public interface BlobStore {

    /** Stores a file under {@code key}, replacing any existing object. */
    void put(String key, Path file, String contentType);

    /** Stores bytes under {@code key}, replacing any existing object. */
    void put(String key, byte[] bytes, String contentType);

    /** Opens an object for reading; the caller must close the returned blob. */
    Optional<Blob> get(String key);

    boolean exists(String key);

    void delete(String key);

    /** Deletes every object whose key starts with {@code prefix} (e.g. all of a user's objects). */
    void deleteByPrefix(String prefix);

    /** An open object: its content stream plus metadata. */
    record Blob(InputStream content, long length, String contentType) implements AutoCloseable {
        @Override
        public void close() throws java.io.IOException {
            content.close();
        }
    }
}
