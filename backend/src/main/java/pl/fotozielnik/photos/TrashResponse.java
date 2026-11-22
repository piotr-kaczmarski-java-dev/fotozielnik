package pl.fotozielnik.photos;

/** {@code changed}: photos affected by this request; {@code inTrash}: what the trash holds now. */
record TrashResponse(int changed, long inTrash) {}
