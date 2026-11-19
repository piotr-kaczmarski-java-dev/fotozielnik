package pl.fotozielnik.photos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import pl.fotozielnik.photos.PhotoService.ImageKind;
import pl.fotozielnik.photos.PhotoService.ImageRef;
import pl.fotozielnik.shared.security.CurrentUser;

/** The user's own photos (FR-009). Image bytes are streamed through the app, never from public URLs (ADR-0007). */
@RestController
@RequestMapping("/api/photos")
class PhotoController {

    /** Content-addressed (the ETag is the hash), so browsers may keep images long - but only privately. */
    private static final CacheControl IMAGE_CACHE =
            CacheControl.maxAge(Duration.ofDays(30)).cachePrivate().immutable();

    private final PhotoService photos;

    PhotoController(PhotoService photos) {
        this.photos = photos;
    }

    @GetMapping
    PhotoPage list(
            @RequestParam(required = false) @Nullable String cursor,
            @RequestParam(defaultValue = "60") @Min(1) @Max(200) int size,
            Authentication auth) {
        return photos.page(CurrentUser.id(auth), cursor, size);
    }

    @GetMapping("/{id}")
    PhotoDetails details(@PathVariable UUID id, Authentication auth) {
        return photos.details(CurrentUser.id(auth), id);
    }

    @GetMapping("/{id}/thumb")
    ResponseEntity<InputStreamResource> thumbnail(@PathVariable UUID id, Authentication auth, WebRequest request)
            throws IOException {
        return image(id, ImageKind.THUMBNAIL, auth, request);
    }

    @GetMapping("/{id}/preview")
    ResponseEntity<InputStreamResource> preview(@PathVariable UUID id, Authentication auth, WebRequest request)
            throws IOException {
        return image(id, ImageKind.PREVIEW, auth, request);
    }

    @GetMapping("/{id}/original")
    ResponseEntity<InputStreamResource> original(@PathVariable UUID id, Authentication auth, WebRequest request)
            throws IOException {
        return image(id, ImageKind.ORIGINAL, auth, request);
    }

    private ResponseEntity<InputStreamResource> image(UUID id, ImageKind kind, Authentication auth, WebRequest request)
            throws IOException {
        ImageRef ref = photos.imageRef(CurrentUser.id(auth), id, kind);
        String etag = '"' + ref.etag() + '"';
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(304)
                    .eTag(etag)
                    .cacheControl(IMAGE_CACHE)
                    .build();
        }
        var blob = photos.open(ref);
        var builder = ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(IMAGE_CACHE)
                .contentLength(blob.length())
                .contentType(MediaType.parseMediaType(blob.contentType()));
        if (kind == ImageKind.ORIGINAL) {
            builder.header(
                    HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.inline()
                            .filename(ref.fileName(), java.nio.charset.StandardCharsets.UTF_8)
                            .build()
                            .toString());
        }
        return builder.body(new InputStreamResource(blob.content()));
    }
}
