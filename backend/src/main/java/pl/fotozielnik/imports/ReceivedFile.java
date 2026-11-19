package pl.fotozielnik.imports;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import pl.fotozielnik.shared.BadRequestException;

/**
 * An uploaded file spooled to a temp file while its SHA-256 is computed, so the claimed hash can be verified
 * before anything is stored. Delete it with {@link #close()}.
 */
final class ReceivedFile implements AutoCloseable {

    private final Path path;
    private final String sha256;
    private final long size;
    private final boolean looksLikeJpeg;

    private ReceivedFile(Path path, String sha256, long size, boolean looksLikeJpeg) {
        this.path = path;
        this.sha256 = sha256;
        this.size = size;
        this.looksLikeJpeg = looksLikeJpeg;
    }

    static ReceivedFile receive(InputStream body, long maxBytes) throws IOException {
        Path tmp = Files.createTempFile("upload-", ".part");
        try {
            MessageDigest digest = sha256Digest();
            byte[] head = new byte[3];
            long size = 0;
            try (OutputStream out = new DigestOutputStream(Files.newOutputStream(tmp), digest)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = body.read(buf)) != -1) {
                    if (size < head.length) {
                        System.arraycopy(buf, 0, head, (int) size, (int) Math.min(n, head.length - size));
                    }
                    size += n;
                    if (size > maxBytes) {
                        throw new BadRequestException("import.file-too-large", "The file is too large.");
                    }
                    out.write(buf, 0, n);
                }
            }
            // JPEG files start with the SOI marker FF D8 followed by another marker (FF ..)
            boolean jpeg =
                    size >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
            return new ReceivedFile(tmp, HexFormat.of().formatHex(digest.digest()), size, jpeg);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }

    Path path() {
        return path;
    }

    String sha256() {
        return sha256;
    }

    long size() {
        return size;
    }

    boolean looksLikeJpeg() {
        return looksLikeJpeg;
    }

    @Override
    public void close() throws IOException {
        Files.deleteIfExists(path);
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
