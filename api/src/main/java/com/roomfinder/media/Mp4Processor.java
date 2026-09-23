package com.roomfinder.media;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Minimal ISO-BMFF (MP4) box parser used for exactly two things: reading the recorded
 * duration out of {@code moov/mvhd}, and removing the {@code moov/udta} box, which is
 * where phones write GPS location and other capture metadata.
 *
 * <p>This is not a general-purpose MP4 library. It understands top-level boxes and one
 * level of children under {@code moov}, enough to find {@code mvhd} and {@code udta}.
 * Anything it can't make sense of is treated as untrusted input and rejected rather than
 * stored as-is - we'd rather refuse an unusual file than silently keep its metadata.
 */
final class Mp4Processor {

	record Result(byte[] bytes, double durationSeconds) {}

	private Mp4Processor() {}

	static Result process(byte[] input) {
		List<Box> topBoxes = parseBoxes(input, 0, input.length);

		Box moov = topBoxes.stream()
				.filter(b -> b.type.equals("moov"))
				.findFirst()
				.orElseThrow(() -> new Mp4ParseException("no moov box"));

		List<Box> moovChildren = parseBoxes(input, moov.payloadStart, moov.end);

		Box mvhd = moovChildren.stream()
				.filter(b -> b.type.equals("mvhd"))
				.findFirst()
				.orElseThrow(() -> new Mp4ParseException("no mvhd box"));
		double duration = readDuration(input, mvhd);

		// Location and capture metadata live in moov/udta and moov/meta.
		byte[] output = input;
		for (Box child : moovChildren) {
			if (child.type.equals("udta") || child.type.equals("meta")) {
				output = neutralise(output, child);
			}
		}

		return new Result(output, duration);
	}

	private static double readDuration(byte[] bytes, Box mvhd) {
		int p = mvhd.payloadStart;
		int version = bytes[p] & 0xFF;
		long timescale;
		long duration;
		if (version == 1) {
			timescale = readUint32(bytes, p + 20);
			duration = readUint64(bytes, p + 24);
		} else {
			timescale = readUint32(bytes, p + 12);
			duration = readUint32(bytes, p + 16);
		}
		if (timescale == 0) throw new Mp4ParseException("mvhd timescale is zero");
		return duration / (double) timescale;
	}

	/**
	 * Wipes a metadata box without changing the file's byte layout: the box keeps its size,
	 * its type becomes "free" (a skip box every player ignores) and its payload is zeroed.
	 *
	 * <p>Removing the bytes instead would shift everything after {@code moov}. In a faststart
	 * file (moov before mdat, which is what most phones now produce) that invalidates every
	 * absolute chunk offset in stco/co64, and the video silently fails to play. Keeping the
	 * length identical avoids touching the offset tables at all.
	 */
	private static byte[] neutralise(byte[] bytes, Box child) {
		byte[] out = bytes.clone();
		out[child.headerStart + 4] = 'f';
		out[child.headerStart + 5] = 'r';
		out[child.headerStart + 6] = 'e';
		out[child.headerStart + 7] = 'e';
		java.util.Arrays.fill(out, child.payloadStart, child.end, (byte) 0);
		return out;
	}

	private enum SizeFieldKind { NORMAL, EXTENDED, IMPLICIT_TO_EOF }

	private record Box(String type, int headerStart, int headerSize, int payloadStart, long totalSize,
	                    int end, SizeFieldKind sizeFieldKind) {}

	private static List<Box> parseBoxes(byte[] bytes, int start, int end) {
		List<Box> boxes = new ArrayList<>();
		int cursor = start;
		while (cursor < end) {
			if (end - cursor < 8) throw new Mp4ParseException("truncated box header");
			long size32 = readUint32(bytes, cursor);
			String type = new String(bytes, cursor + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);

			int headerSize;
			long totalSize;
			SizeFieldKind kind;
			if (size32 == 1) {
				if (end - cursor < 16) throw new Mp4ParseException("truncated extended box header");
				headerSize = 16;
				totalSize = readUint64(bytes, cursor + 8);
				kind = SizeFieldKind.EXTENDED;
			} else if (size32 == 0) {
				headerSize = 8;
				totalSize = end - cursor;
				kind = SizeFieldKind.IMPLICIT_TO_EOF;
			} else {
				headerSize = 8;
				totalSize = size32;
				kind = SizeFieldKind.NORMAL;
			}

			if (totalSize < headerSize || cursor + totalSize > end) {
				throw new Mp4ParseException("box '" + type + "' size out of range");
			}

			int payloadStart = cursor + headerSize;
			int boxEnd = (int) (cursor + totalSize);
			boxes.add(new Box(type, cursor, headerSize, payloadStart, totalSize, boxEnd, kind));
			cursor = boxEnd;
		}
		return boxes;
	}

	private static long readUint32(byte[] b, int offset) {
		return ((long) (b[offset] & 0xFF) << 24)
				| ((b[offset + 1] & 0xFF) << 16)
				| ((b[offset + 2] & 0xFF) << 8)
				| (b[offset + 3] & 0xFF);
	}

	private static long readUint64(byte[] b, int offset) {
		return (readUint32(b, offset) << 32) | (readUint32(b, offset + 4) & 0xFFFFFFFFL);
	}

	private static void writeUint32(byte[] b, int offset, long value) {
		b[offset] = (byte) (value >>> 24);
		b[offset + 1] = (byte) (value >>> 16);
		b[offset + 2] = (byte) (value >>> 8);
		b[offset + 3] = (byte) value;
	}

}
