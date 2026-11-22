package pl.fotozielnik.imports;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.fotozielnik.shared.BadRequestException;
import pl.fotozielnik.shared.security.CurrentUser;

/** Folder import (UC-01). All batches are the signed-in user's own; another user's batch id is a 404. */
@RestController
@RequestMapping("/api/imports")
class ImportController {

    private final ImportService imports;

    ImportController(ImportService imports) {
        this.imports = imports;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ImportBatchResponse start(@Valid @RequestBody StartImportRequest body, Authentication auth) {
        return imports.start(CurrentUser.id(auth), body);
    }

    @PostMapping("/{id}/check")
    CheckResponse check(@PathVariable UUID id, @Valid @RequestBody CheckRequest body, Authentication auth) {
        return imports.check(CurrentUser.id(auth), id, body);
    }

    /**
     * Uploads one file as the raw request body. {@code X-File-Path}: path inside the chosen folder, URL-encoded
     * UTF-8; {@code X-File-Modified}: the file's last-modified time in epoch milliseconds. The server verifies
     * that the body's SHA-256 equals {@code sha256}.
     */
    @PutMapping(
            value = "/{id}/files/{sha256}",
            consumes = {"application/octet-stream", "image/jpeg"})
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content =
                    @Content(
                            mediaType = "application/octet-stream",
                            schema = @Schema(type = "string", format = "binary")))
    ResponseEntity<UploadResponse> upload(
            @PathVariable UUID id,
            @PathVariable @Pattern(regexp = "[0-9a-f]{64}") String sha256,
            @RequestHeader("X-File-Path") String encodedPath,
            @RequestHeader(value = "X-File-Modified", required = false) @Nullable Long modifiedEpochMillis,
            HttpServletRequest request,
            Authentication auth)
            throws IOException {
        String path = decodePath(encodedPath);
        Instant modified = modifiedEpochMillis != null ? Instant.ofEpochMilli(modifiedEpochMillis) : null;
        UploadResponse result =
                imports.receive(CurrentUser.id(auth), id, sha256, path, modified, request.getInputStream());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result);
    }

    private static String decodePath(String encoded) {
        String path;
        try {
            path = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) { // malformed %-escape
            throw new BadRequestException("import.bad-path", "Invalid file path.");
        }
        if (path.isBlank() || path.length() > 1000) {
            throw new BadRequestException("import.bad-path", "Invalid file path.");
        }
        return path;
    }

    /** The browser has sent everything it is going to send; processing continues in the background. */
    @PostMapping("/{id}/finish")
    ImportBatchResponse finish(@PathVariable UUID id, Authentication auth) {
        return imports.finish(CurrentUser.id(auth), id);
    }

    @GetMapping("/{id}")
    ImportBatchResponse get(@PathVariable UUID id, Authentication auth) {
        return imports.get(CurrentUser.id(auth), id);
    }

    @GetMapping
    ImportBatchPage list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication auth) {
        return imports.list(CurrentUser.id(auth), page, size);
    }
}
