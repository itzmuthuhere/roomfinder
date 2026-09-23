package com.roomfinder.bed;

import com.roomfinder.bed.BedDtos.BedView;
import com.roomfinder.bed.BedDtos.StateLogView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class BedRepository {

	private final JdbcClient db;

	public BedRepository(JdbcClient db) {
		this.db = db;
	}

	public List<BedView> findByPg(long pgId) {
		return db.sql("""
				select b.id, b.room_id, r.room_no, r.sharing_type, r.ac, r.rent_per_bed, r.deposit_amount,
				       b.bed_label, b.state, b.available_from, b.state_changed_at
				  from bed b
				  join room r on r.id = b.room_id
				 where r.pg_id = :pgId
				 order by r.room_no nulls last, b.bed_label
				""")
				.param("pgId", pgId)
				.query((rs, i) -> new BedView(
						rs.getLong("id"),
						rs.getLong("room_id"),
						rs.getString("room_no"),
						rs.getString("sharing_type"),
						rs.getBoolean("ac"),
						rs.getInt("rent_per_bed"),
						rs.getInt("deposit_amount"),
						rs.getString("bed_label"),
						BedState.valueOf(rs.getString("state")),
						rs.getObject("available_from", LocalDate.class),
						rs.getObject("state_changed_at", java.time.OffsetDateTime.class)))
				.list();
	}

	public Optional<BedState> currentState(long bedId) {
		return db.sql("select state from bed where id = :id")
				.param("id", bedId)
				.query((rs, i) -> BedState.valueOf(rs.getString("state")))
				.optional();
	}

	/**
	 * Conditional update: succeeds only if the bed is still in {@code expected}.
	 * Returns true when exactly one row moved. This is what makes two holds on one bed impossible.
	 */
	public boolean compareAndSetState(long bedId, BedState expected, BedState next, LocalDate availableFrom) {
		int rows = db.sql("""
				update bed
				   set state = :next,
				       available_from = :availableFrom,
				       state_changed_at = now()
				 where id = :id
				   and state = :expected
				""")
				.param("id", bedId)
				.param("expected", expected.name())
				.param("next", next.name())
				.param("availableFrom", availableFrom)
				.update();
		return rows == 1;
	}

	public void log(long bedId, BedState from, BedState to, Long actorId, String reason) {
		db.sql("""
				insert into bed_state_log (bed_id, from_state, to_state, changed_by, reason)
				values (:bedId, :from, :to, :actor, :reason)
				""")
				.param("bedId", bedId)
				.param("from", from == null ? null : from.name())
				.param("to", to.name())
				.param("actor", actorId)
				.param("reason", reason)
				.update();
	}

	public List<StateLogView> history(long bedId) {
		return db.sql("""
				select id, from_state, to_state, reason, created_at
				  from bed_state_log
				 where bed_id = :bedId
				 order by created_at desc, id desc
				""")
				.param("bedId", bedId)
				.query((rs, i) -> new StateLogView(
						rs.getLong("id"),
						rs.getString("from_state"),
						rs.getString("to_state"),
						rs.getString("reason"),
						rs.getObject("created_at", java.time.OffsetDateTime.class)))
				.list();
	}
}
