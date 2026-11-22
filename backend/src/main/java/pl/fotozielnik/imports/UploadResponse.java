package pl.fotozielnik.imports;

import java.util.UUID;

/** {@code created} false = this content was already in the library (e.g. a parallel duplicate). */
record UploadResponse(UUID photoId, boolean created) {}
