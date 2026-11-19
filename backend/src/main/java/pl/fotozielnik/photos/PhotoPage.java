package pl.fotozielnik.photos;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** A page of the date-ordered grid; pass {@code nextCursor} to get the next one (null = last page). */
record PhotoPage(List<PhotoSummary> items, @Nullable String nextCursor) {}
