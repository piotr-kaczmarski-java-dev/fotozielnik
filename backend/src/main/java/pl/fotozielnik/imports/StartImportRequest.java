package pl.fotozielnik.imports;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code filesFound}: JPEGs the browser will check/upload; {@code filesSkipped}: other files it ignored. */
record StartImportRequest(
        @NotBlank @Size(max = 200) String label,
        @Min(0) @Max(10_000_000) int filesFound,
        @Min(0) @Max(10_000_000) int filesSkipped) {}
