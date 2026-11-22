package pl.fotozielnik.storage;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

/** {@link BlobStore} on the S3 API (Garage in dev/prod, any S3 provider later). */
@Component
class S3BlobStore implements BlobStore {

    private final S3Client s3;
    private final String bucket;

    S3BlobStore(S3Client s3, StorageProperties properties) {
        this.s3 = s3;
        this.bucket = properties.bucket();
    }

    @Override
    public void put(String key, Path file, String contentType) {
        s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType), RequestBody.fromFile(file));
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType), RequestBody.fromBytes(bytes));
    }

    @Override
    public Optional<Blob> get(String key) {
        try {
            ResponseInputStream<GetObjectResponse> in =
                    s3.getObject(b -> b.bucket(bucket).key(key));
            GetObjectResponse meta = in.response();
            String type = meta.contentType() != null ? meta.contentType() : "application/octet-stream";
            return Optional.of(new Blob(in, meta.contentLength(), type));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            s3.headObject(b -> b.bucket(bucket).key(key));
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(b -> b.bucket(bucket).key(key));
    }

    @Override
    public void deleteByPrefix(String prefix) {
        if (prefix.isEmpty()) {
            throw new IllegalArgumentException("refusing to delete the whole bucket");
        }
        var pages = s3.listObjectsV2Paginator(
                ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build());
        for (var page : pages) {
            List<ObjectIdentifier> ids = page.contents().stream()
                    .map(S3Object::key)
                    .map(k -> ObjectIdentifier.builder().key(k).build())
                    .toList();
            if (!ids.isEmpty()) { // a page holds at most 1000 keys = the DeleteObjects limit
                s3.deleteObjects(b -> b.bucket(bucket)
                        .delete(Delete.builder().objects(ids).quiet(true).build()));
            }
        }
    }
}
