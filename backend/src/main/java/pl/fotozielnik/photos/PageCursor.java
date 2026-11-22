package pl.fotozielnik.photos;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import pl.fotozielnik.shared.BadRequestException;

/** Opaque keyset cursor for the photo grid: position (sortTime, id) of the last item of the previous page. */
record PageCursor(Instant sortTime, UUID id) {

    String encode() {
        String raw = sortTime.getEpochSecond() + "_" + sortTime.getNano() + "_" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static PageCursor decode(String cursor) {
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("_", 3);
            Instant time = Instant.ofEpochSecond(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
            return new PageCursor(time, UUID.fromString(parts[2]));
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            throw new BadRequestException("page.bad-cursor", "Invalid page cursor.");
        }
    }
}
