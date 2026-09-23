package com.roomfinder.media;

import java.util.Optional;

/**
 * Identifies a file by its leading bytes, never by the client-supplied filename or
 * Content-Type header. Those are attacker-controlled; the magic bytes are not.
 */
final class ContentSniffer {

	private ContentSniffer() {}

	static Optional<MediaFileType> sniff(byte[] bytes) {
		if (isJpeg(bytes)) return Optional.of(MediaFileType.JPEG);
		if (isPng(bytes)) return Optional.of(MediaFileType.PNG);
		if (isWebp(bytes)) return Optional.of(MediaFileType.WEBP);
		if (isMp4(bytes)) return Optional.of(MediaFileType.MP4);
		return Optional.empty();
	}

	private static boolean isJpeg(byte[] b) {
		return b.length >= 3
				&& (b[0] & 0xFF) == 0xFF
				&& (b[1] & 0xFF) == 0xD8
				&& (b[2] & 0xFF) == 0xFF;
	}

	private static boolean isPng(byte[] b) {
		int[] sig = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
		if (b.length < sig.length) return false;
		for (int i = 0; i < sig.length; i++) {
			if ((b[i] & 0xFF) != sig[i]) return false;
		}
		return true;
	}

	private static boolean isWebp(byte[] b) {
		return b.length >= 12
				&& b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
				&& b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
	}

	private static boolean isMp4(byte[] b) {
		return b.length >= 8
				&& b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p';
	}
}
