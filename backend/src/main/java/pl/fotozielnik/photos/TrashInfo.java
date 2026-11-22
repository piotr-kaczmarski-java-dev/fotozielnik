package pl.fotozielnik.photos;

/** For the "Kosz (n)" link and the trash page: how many photos, and how many days until they are purged. */
record TrashInfo(long count, int retentionDays) {}
