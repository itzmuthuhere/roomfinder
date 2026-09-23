package com.roomfinder.media;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class MediaDtos {

	public record MediaView(
			long id,
			long pgId,
			Long roomId,
			String url,
			String kind,
			int sortOrder,
			boolean isCover) {}

	public record ReorderRequest(@NotEmpty List<Long> orderedIds) {}
}
