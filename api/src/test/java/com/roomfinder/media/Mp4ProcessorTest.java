package com.roomfinder.media;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure byte-level tests, no Spring, no database.
 *
 * The important property: stripping metadata must not change the file's length. A faststart
 * file has moov before mdat, and every chunk offset inside moov points at an absolute position
 * in the file, so shifting bytes would silently corrupt playback.
 */
class Mp4ProcessorTest {

	@Test
	void reads_duration_and_wipes_udta_without_moving_any_bytes() {
		byte[] udta = box("udta", "GPS: 12.9391,80.2297".getBytes(StandardCharsets.UTF_8));
		byte[] moov = box("moov", concat(mvhd(600, 18000), udta));   // 18000/600 = 30s
		byte[] file = concat(box("ftyp", new byte[8]), moov, box("mdat", new byte[64]));

		Mp4Processor.Result result = Mp4Processor.process(file);

		assertThat(result.durationSeconds()).isEqualTo(30.0);
		assertThat(result.bytes()).hasSameSizeAs(file);
		assertThat(new String(result.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("12.9391");
		assertThat(new String(result.bytes(), StandardCharsets.ISO_8859_1)).contains("free");
		// everything after moov is untouched, so mdat offsets stay valid
		int tailStart = file.length - 64;
		assertThat(java.util.Arrays.copyOfRange(result.bytes(), tailStart, file.length))
				.isEqualTo(java.util.Arrays.copyOfRange(file, tailStart, file.length));
	}

	@Test
	void file_without_metadata_is_returned_unchanged() {
		byte[] file = concat(box("ftyp", new byte[8]), box("moov", mvhd(1000, 45000)), box("mdat", new byte[16]));

		Mp4Processor.Result result = Mp4Processor.process(file);

		assertThat(result.durationSeconds()).isEqualTo(45.0);
		assertThat(result.bytes()).isEqualTo(file);
	}

	@Test
	void rubbish_is_rejected_rather_than_stored() {
		assertThatThrownBy(() -> Mp4Processor.process("not a video at all".getBytes(StandardCharsets.UTF_8)))
				.isInstanceOf(Mp4ParseException.class);
	}

	// --- helpers that build a minimal ISO-BMFF file ---

	private static byte[] mvhd(long timescale, long duration) {
		byte[] payload = new byte[20];
		payload[0] = 0; // version 0
		writeUint32(payload, 12, timescale);
		writeUint32(payload, 16, duration);
		return box("mvhd", payload);
	}

	private static byte[] box(String type, byte[] payload) {
		byte[] out = new byte[8 + payload.length];
		writeUint32(out, 0, out.length);
		System.arraycopy(type.getBytes(StandardCharsets.US_ASCII), 0, out, 4, 4);
		System.arraycopy(payload, 0, out, 8, payload.length);
		return out;
	}

	private static byte[] concat(byte[]... parts) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (byte[] p : parts) out.writeBytes(p);
		return out.toByteArray();
	}

	private static void writeUint32(byte[] b, int offset, long value) {
		b[offset] = (byte) (value >>> 24);
		b[offset + 1] = (byte) (value >>> 16);
		b[offset + 2] = (byte) (value >>> 8);
		b[offset + 3] = (byte) value;
	}
}
