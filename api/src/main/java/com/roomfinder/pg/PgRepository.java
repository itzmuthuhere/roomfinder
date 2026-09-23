package com.roomfinder.pg;

import com.roomfinder.pg.PgDtos.CreatePgRequest;
import com.roomfinder.pg.PgDtos.CreateRoomRequest;
import com.roomfinder.pg.PgDtos.PgView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PgRepository {

	private final JdbcClient db;

	public PgRepository(JdbcClient db) {
		this.db = db;
	}

	public long createPg(long ownerId, CreatePgRequest r) {
		return db.sql("""
				insert into pg_property
				  (owner_id, name, gender_type, address, area, location, food_included, food_type, notice_period_days, status)
				values
				  (:ownerId, :name, :genderType, :address, :area,
				   ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
				   :foodIncluded, :foodType, :noticePeriodDays, 'DRAFT')
				returning id
				""")
				.param("ownerId", ownerId)
				.param("name", r.name())
				.param("genderType", r.genderType())
				.param("address", r.address())
				.param("area", r.area())
				.param("lat", r.lat())
				.param("lng", r.lng())
				.param("foodIncluded", r.foodIncluded())
				.param("foodType", r.foodType())
				.param("noticePeriodDays", r.noticePeriodDays())
				.query(Long.class)
				.single();
	}

	public long createRoom(long pgId, CreateRoomRequest r) {
		return db.sql("""
				insert into room (pg_id, room_no, sharing_type, ac, attached_bath, rent_per_bed, deposit_amount)
				values (:pgId, :roomNo, :sharingType, :ac, :attachedBath, :rent, :deposit)
				returning id
				""")
				.param("pgId", pgId)
				.param("roomNo", r.roomNo())
				.param("sharingType", r.sharingType())
				.param("ac", r.ac())
				.param("attachedBath", r.attachedBath())
				.param("rent", r.rentPerBed())
				.param("deposit", r.depositAmount())
				.query(Long.class)
				.single();
	}

	public long createBed(long roomId, String label) {
		return db.sql("insert into bed (room_id, bed_label, state) values (:roomId, :label, 'VACANT') returning id")
				.param("roomId", roomId)
				.param("label", label)
				.query(Long.class)
				.single();
	}

	public Optional<PgView> findById(long pgId) {
		return db.sql(SELECT_PG + " where p.id = :id group by p.id")
				.param("id", pgId)
				.query(PgRepository::mapPg)
				.optional();
	}

	public List<PgView> findByOwner(long ownerId) {
		return db.sql(SELECT_PG + " where p.owner_id = :ownerId group by p.id order by p.id desc")
				.param("ownerId", ownerId)
				.query(PgRepository::mapPg)
				.list();
	}

	public void setStatus(long pgId, String status) {
		db.sql("update pg_property set status = :status where id = :id")
				.param("status", status)
				.param("id", pgId)
				.update();
	}

	private static final String SELECT_PG = """
			select p.id, p.name, p.gender_type, p.area, p.address, p.status, p.instant_book,
			       count(b.id) as total_beds,
			       count(b.id) filter (where b.state = 'VACANT') as vacant_beds
			  from pg_property p
			  left join room r on r.pg_id = p.id
			  left join bed b on b.room_id = r.id
			""";

	private static PgView mapPg(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
		return new PgView(
				rs.getLong("id"),
				rs.getString("name"),
				rs.getString("gender_type"),
				rs.getString("area"),
				rs.getString("address"),
				rs.getString("status"),
				rs.getBoolean("instant_book"),
				rs.getInt("total_beds"),
				rs.getInt("vacant_beds"));
	}
}
