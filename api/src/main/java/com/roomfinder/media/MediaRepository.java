package com.roomfinder.media;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class MediaRepository {

	private final JdbcClient db;

	MediaRepository(JdbcClient db) {
		this.db = db;
	}

	long insert(long pgId, Long roomId, String storageKey, MediaKind kind, int sortOrder, boolean isCover) {
		return db.sql("""
				insert into media (pg_id, room_id, url, kind, sort_order, is_cover)
				values (:pgId, :roomId, :key, :kind, :sortOrder, :isCover)
				returning id
				""")
				.param("pgId", pgId)
				.param("roomId", roomId)
				.param("key", storageKey)
				.param("kind", kind.name())
				.param("sortOrder", sortOrder)
				.param("isCover", isCover)
				.query(Long.class)
				.single();
	}

	Optional<MediaRow> findById(long id) {
		return db.sql(SELECT + " where id = :id")
				.param("id", id)
				.query(MediaRepository::map)
				.optional();
	}

	List<MediaRow> listByPg(long pgId) {
		return db.sql(SELECT + " where pg_id = :pgId order by kind, room_id nulls last, sort_order, id")
				.param("pgId", pgId)
				.query(MediaRepository::map)
				.list();
	}

	Optional<Long> roomPgId(long roomId) {
		return db.sql("select pg_id from room where id = :roomId")
				.param("roomId", roomId)
				.query(Long.class)
				.optional();
	}

	int countPhotosInRoom(long roomId) {
		return db.sql("select count(*) from media where room_id = :roomId and kind = 'PHOTO'")
				.param("roomId", roomId)
				.query(Integer.class)
				.single();
	}

	Optional<MediaRow> findVideoForPg(long pgId) {
		return db.sql(SELECT + " where pg_id = :pgId and kind = 'VIDEO'")
				.param("pgId", pgId)
				.query(MediaRepository::map)
				.optional();
	}

	/** One row per PG that has a cover photo, for a batch of PGs (e.g. search results). */
	List<MediaRow> listCoverPhotos(List<Long> pgIds) {
		if (pgIds.isEmpty()) {
			return List.of();
		}
		return db.sql(SELECT + " where pg_id in (:pgIds) and kind = 'PHOTO' and is_cover")
				.param("pgIds", pgIds)
				.query(MediaRepository::map)
				.list();
	}

	boolean hasCoverPhoto(long pgId) {
		return db.sql("select count(*) from media where pg_id = :pgId and kind = 'PHOTO' and is_cover")
				.param("pgId", pgId)
				.query(Integer.class)
				.single() > 0;
	}

	/** id of the photo that should become cover next, by display order. Empty if the PG has no photos left. */
	Optional<Long> nextCoverCandidate(long pgId) {
		return db.sql("""
				select id from media
				 where pg_id = :pgId and kind = 'PHOTO'
				 order by sort_order, id
				 limit 1
				""")
				.param("pgId", pgId)
				.query(Long.class)
				.optional();
	}

	void setCover(long pgId, long mediaId) {
		db.sql("update media set is_cover = false where pg_id = :pgId and kind = 'PHOTO' and is_cover")
				.param("pgId", pgId)
				.update();
		db.sql("update media set is_cover = true where id = :id")
				.param("id", mediaId)
				.update();
	}

	void updateSortOrder(long id, int sortOrder) {
		db.sql("update media set sort_order = :sortOrder where id = :id")
				.param("sortOrder", sortOrder)
				.param("id", id)
				.update();
	}

	void deleteById(long id) {
		db.sql("delete from media where id = :id").param("id", id).update();
	}

	private static final String SELECT =
			"select id, pg_id, room_id, url, kind, sort_order, is_cover from media";

	private static MediaRow map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
		long roomId = rs.getLong("room_id");
		Long roomIdOrNull = rs.wasNull() ? null : roomId;
		return new MediaRow(
				rs.getLong("id"),
				rs.getLong("pg_id"),
				roomIdOrNull,
				rs.getString("url"),
				MediaKind.valueOf(rs.getString("kind")),
				rs.getInt("sort_order"),
				rs.getBoolean("is_cover"));
	}
}
