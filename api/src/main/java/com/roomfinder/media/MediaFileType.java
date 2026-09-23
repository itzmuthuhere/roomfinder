package com.roomfinder.media;

/** The set of file types we accept, keyed to what {@link ContentSniffer} can detect from raw bytes. */
public enum MediaFileType {
	JPEG("jpg", MediaKind.PHOTO),
	PNG("png", MediaKind.PHOTO),
	WEBP("webp", MediaKind.PHOTO),
	MP4("mp4", MediaKind.VIDEO);

	public final String extension;
	public final MediaKind kind;

	MediaFileType(String extension, MediaKind kind) {
		this.extension = extension;
		this.kind = kind;
	}
}
