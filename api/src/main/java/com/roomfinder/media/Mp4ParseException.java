package com.roomfinder.media;

/** The uploaded file isn't a box-structured MP4 we can parse, or is missing boxes we rely on. */
class Mp4ParseException extends RuntimeException {
	Mp4ParseException(String message) {
		super(message);
	}
}
