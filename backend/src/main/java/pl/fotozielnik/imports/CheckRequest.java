package pl.fotozielnik.imports;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Up to 1000 SHA-256 hashes (lower-case hex) per call; send each hash once per batch. */
record CheckRequest(@NotNull @Size(max = 1000) List<@NotNull @Pattern(regexp = "[0-9a-f]{64}") String> sha256s) {}
