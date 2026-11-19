package pl.fotozielnik.imports;

import java.util.List;

record ImportBatchPage(List<ImportBatchResponse> items, int page, int size, long totalItems) {}
