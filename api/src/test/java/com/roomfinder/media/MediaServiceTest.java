package com.roomfinder.media;

import com.roomfinder.AbstractDbTest;
import com.roomfinder.common.ApiException;
import com.roomfinder.media.MediaDtos.MediaView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaServiceTest extends AbstractDbTest {

	@Autowired JdbcClient db;
	@Autowired MediaService media;

	long pgId;
	long roomId;

	@BeforeEach
	void seed() {
		db.sql("delete from media").update();
		db.sql("delete from bed_state_log").update();
		db.sql("delete from bed").update();
		db.sql("delete from room").update();
		db.sql("delete from pg_property").update();
		db.sql("delete from app_user").update();

		long ownerId = db.sql("insert into app_user (phone, name, role) values ('9000000003','O','OWNER') returning id")
				.query(Long.class).single();
		pgId = db.sql("""
				insert into pg_property (owner_id, name, gender_type, address, area, location, status)
				values (:o, 'Media PG', 'BOYS', 'Addr', 'Velachery',
				        ST_SetSRID(ST_MakePoint(80.2200, 12.9800), 4326)::geography, 'LIVE')
				returning id""").param("o", ownerId).query(Long.class).single();
		roomId = db.sql("""
				insert into room (pg_id, room_no, sharing_type, ac, attached_bath, rent_per_bed, deposit_amount)
				values (:p, '1', 'DOUBLE', false, false, 8000, 16000) returning id""")
				.param("p", pgId).query(Long.class).single();
	}

	@Test
	void first_photo_becomes_the_cover_automatically() {
		MediaView first = media.uploadPhoto(pgId, roomId, jpeg());
		MediaView second = media.uploadPhoto(pgId, roomId, jpeg());

		assertThat(first.isCover()).isTrue();
		assertThat(second.isCover()).isFalse();
		assertThat(first.url()).startsWith("/media/").endsWith(".jpg");
	}

	@Test
	void deleting_the_cover_promotes_the_next_photo() {
		MediaView first = media.uploadPhoto(pgId, roomId, jpeg());
		MediaView second = media.uploadPhoto(pgId, roomId, jpeg());

		media.delete(first.id());

		List<MediaView> left = media.listForPg(pgId);
		assertThat(left).hasSize(1);
		assertThat(left.get(0).id()).isEqualTo(second.id());
		assertThat(left.get(0).isCover()).as("a listing must never be left without a cover").isTrue();
	}

	@Test
	void eleventh_photo_in_a_room_is_rejected() {
		for (int i = 0; i < 10; i++) {
			media.uploadPhoto(pgId, roomId, jpeg());
		}

		assertThatThrownBy(() -> media.uploadPhoto(pgId, roomId, jpeg()))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("already has 10 photos");
	}

	@Test
	void a_file_that_is_not_an_accepted_type_is_rejected() {
		byte[] notAnImage = "<html>hello</html>".getBytes(StandardCharsets.UTF_8);

		assertThatThrownBy(() -> media.uploadPhoto(pgId, roomId, notAnImage))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("jpeg, png, webp");
	}

	@Test
	void a_video_uploaded_where_a_photo_is_expected_is_rejected() {
		assertThatThrownBy(() -> media.uploadPhoto(pgId, roomId, mp4(30)))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("looks like a video");
	}

	@Test
	void reorder_rewrites_sort_order() {
		MediaView a = media.uploadPhoto(pgId, roomId, jpeg());
		MediaView b = media.uploadPhoto(pgId, roomId, jpeg());
		MediaView c = media.uploadPhoto(pgId, roomId, jpeg());

		media.reorder(pgId, roomId, List.of(c.id(), a.id(), b.id()));

		List<MediaView> ordered = media.listForPg(pgId).stream()
				.sorted(java.util.Comparator.comparingInt(MediaView::sortOrder))
				.toList();
		assertThat(ordered).extracting(MediaView::id).containsExactly(c.id(), a.id(), b.id());
	}

	@Test
	void reorder_with_a_stale_list_is_rejected() {
		MediaView a = media.uploadPhoto(pgId, roomId, jpeg());
		media.uploadPhoto(pgId, roomId, jpeg());

		assertThatThrownBy(() -> media.reorder(pgId, roomId, List.of(a.id())))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("list changed");
	}

	@Test
	void uploading_a_second_video_replaces_the_first() {
		MediaView first = media.uploadVideo(pgId, mp4(30));
		MediaView second = media.uploadVideo(pgId, mp4(20));

		List<MediaView> videos = media.listForPg(pgId).stream().filter(m -> m.kind().equals("VIDEO")).toList();
		assertThat(videos).hasSize(1);
		assertThat(videos.get(0).id()).isEqualTo(second.id());
		assertThat(videos.get(0).id()).isNotEqualTo(first.id());
	}

	@Test
	void a_video_longer_than_a_minute_is_rejected() {
		assertThatThrownBy(() -> media.uploadVideo(pgId, mp4(95)))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("60 seconds or under");
	}

	// --- fixtures ---

	/** Smallest bytes that sniff as JPEG. */
	private static byte[] jpeg() {
		byte[] b = new byte[64];
		b[0] = (byte) 0xFF;
		b[1] = (byte) 0xD8;
		b[2] = (byte) 0xFF;
		return b;
	}

	private static byte[] mp4(int seconds) {
		byte[] mvhdPayload = new byte[20];
		writeUint32(mvhdPayload, 12, 1000);
		writeUint32(mvhdPayload, 16, seconds * 1000L);
		byte[] moov = box("moov", box("mvhd", mvhdPayload));
		byte[] ftyp = box("ftyp", new byte[8]);
		byte[] mdat = box("mdat", new byte[32]);
		byte[] out = new byte[ftyp.length + moov.length + mdat.length];
		System.arraycopy(ftyp, 0, out, 0, ftyp.length);
		System.arraycopy(moov, 0, out, ftyp.length, moov.length);
		System.arraycopy(mdat, 0, out, ftyp.length + moov.length, mdat.length);
		return out;
	}

	private static byte[] box(String type, byte[] payload) {
		byte[] out = new byte[8 + payload.length];
		writeUint32(out, 0, out.length);
		System.arraycopy(type.getBytes(StandardCharsets.US_ASCII), 0, out, 4, 4);
		System.arraycopy(payload, 0, out, 8, payload.length);
		return out;
	}

	private static void writeUint32(byte[] b, int offset, long value) {
		b[offset] = (byte) (value >>> 24);
		b[offset + 1] = (byte) (value >>> 16);
		b[offset + 2] = (byte) (value >>> 8);
		b[offset + 3] = (byte) value;
	}
}
