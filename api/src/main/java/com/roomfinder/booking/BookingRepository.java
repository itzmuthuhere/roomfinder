package com.roomfinder.booking;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
class BookingRepository {

	private final JdbcClient db;

	BookingRepository(JdbcClient db) {
		this.db = db;
	}

	record BookingRow(long id, long bedId, long seekerId, String kind, String status,
	                   OffsetDateTime visitSlot, OffsetDateTime holdExpiresAt, int tokenAmount,
	                   OffsetDateTime createdAt) {}

	long insertVisit(long bedId, long seekerId, OffsetDateTime visitSlot, OffsetDateTime holdExpiresAt) {
		return db.sql("""
				insert into booking (bed_id, seeker_id, kind, status, visit_slot, hold_expires_at)
				values (:bedId, :seekerId, 'VISIT', 'REQUESTED', :visitSlot, :holdExpiresAt)
				returning id
				""")
				.param("bedId", bedId)
				.param("seekerId", seekerId)
				.param("visitSlot", visitSlot)
				.param("holdExpiresAt", holdExpiresAt)
				.query(Long.class)
				.single();
	}

	Optional<BookingRow> findById(long id) {
		return db.sql(SELECT + " where id = :id")
				.param("id", id)
				.query(BookingRepository::map)
				.optional();
	}

	List<BookingRow> listForSeeker(long seekerId) {
		return db.sql(SELECT + " where seeker_id = :seekerId order by created_at desc")
				.param("seekerId", seekerId)
				.query(BookingRepository::map)
				.list();
	}

	/** Open (REQUESTED/CONFIRMED) bookings against any bed in a PG - what the owner needs to act on. */
	List<BookingRow> listOpenForPg(long pgId) {
		return db.sql("""
				select bk.id, bk.bed_id, bk.seeker_id, bk.kind, bk.status, bk.visit_slot, bk.hold_expires_at,
				       bk.token_amount, bk.created_at
				  from booking bk
				  join bed b on b.id = bk.bed_id
				  join room r on r.id = b.room_id
				 where r.pg_id = :pgId and bk.status in ('REQUESTED','CONFIRMED')
				 order by bk.visit_slot
				""")
				.param("pgId", pgId)
				.query(BookingRepository::map)
				.list();
	}

	/** Still-open bookings whose hold has passed its expiry - what the sweep job releases. */
	List<BookingRow> expiredPending(OffsetDateTime now) {
		return db.sql(SELECT + " where status in ('REQUESTED','CONFIRMED') and hold_expires_at < :now")
				.param("now", now)
				.query(BookingRepository::map)
				.list();
	}

	void updateStatus(long id, String status) {
		db.sql("update booking set status = :status where id = :id")
				.param("status", status)
				.param("id", id)
				.update();
	}

	Optional<String> phoneOf(long userId) {
		return db.sql("select phone from app_user where id = :id")
				.param("id", userId)
				.query(String.class)
				.optional();
	}

	private static final String SELECT = """
			select id, bed_id, seeker_id, kind, status, visit_slot, hold_expires_at, token_amount, created_at
			  from booking
			""";

	private static BookingRow map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
		return new BookingRow(
				rs.getLong("id"),
				rs.getLong("bed_id"),
				rs.getLong("seeker_id"),
				rs.getString("kind"),
				rs.getString("status"),
				rs.getObject("visit_slot", OffsetDateTime.class),
				rs.getObject("hold_expires_at", OffsetDateTime.class),
				rs.getInt("token_amount"),
				rs.getObject("created_at", OffsetDateTime.class));
	}
}
