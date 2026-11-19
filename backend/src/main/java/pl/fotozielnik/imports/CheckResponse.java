package pl.fotozielnik.imports;

import java.util.List;

/** The hashes the library does not have yet: upload exactly these. */
record CheckResponse(List<String> missing) {}
