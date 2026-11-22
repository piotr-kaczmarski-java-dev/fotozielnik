package pl.fotozielnik.photos;

/** Background processing of a stored photo (EXIF, thumbnails; later embeddings). */
public enum ProcessingState {
    PENDING,
    DONE,
    FAILED
}
