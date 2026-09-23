package com.roomfinder.media;

/** Internal read model. {@code storageKey} is the value in the {@code media.url} column - a storage key, not a servable URL; {@link MediaService} asks {@link MediaStorage} to turn it into one. */
record MediaRow(long id, long pgId, Long roomId, String storageKey, MediaKind kind, int sortOrder, boolean isCover) {}
