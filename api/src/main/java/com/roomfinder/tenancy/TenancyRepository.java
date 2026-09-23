package com.roomfinder.tenancy;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
class TenancyRepository {

	private final JdbcClient db;

	TenancyRepository(JdbcClient db) {
		this.db = db;
	}

	record TenancyRow(long id, long bedId, long tenantId, String tenantPhone, String tenantName,
	                   LocalDate moveInDate, int rentAmount, int rentDueDay,
	                   LocalDate noticeDate, LocalDate vacateDate, String status) {}

	/**
	 * Finds the tenant by phone or registers them as a SEEKER on the spot - most real tenants have
	 * never used the app before move-in. An existing account's role is left untouched, same
	 * principle as {@code auth.AuthRepository.upsertUser}: this call must never be a role escalation
	 * path, even though here it's the owner typing the phone number rather than the tenant themselves.
	 */
	long findOrCreateTenant(String phone, String name) {
		return db.sql("""
				insert into app_user (phone, name, role) values (:phone, :name, 'SEEKER')
				on conflict (phone) do update set name = coalesce(excluded.name, app_user.name)
				returning id
				""")
				.param("phone", phone)
				.param("name", name)
				.query(Long.class)
				.single();
	}

	long insert(long bedId, long tenantId, LocalDate moveInDate, int rentAmount, int rentDueDay) {
		return db.sql("""
				insert into tenancy (bed_id, tenant_id, move_in_date, rent_amount, rent_due_day, status)
				values (:bedId, :tenantId, :moveInDate, :rentAmount, :rentDueDay, 'ACTIVE')
				returning id
				""")
				.param("bedId", bedId)
				.param("tenantId", tenantId)
				.param("moveInDate", moveInDate)
				.param("rentAmount", rentAmount)
				.param("rentDueDay", rentDueDay)
				.query(Long.class)
				.single();
	}

	Optional<TenancyRow> findById(long id) {
		return db.sql(SELECT + " where t.id = :id")
				.param("id", id)
				.query(TenancyRepository::map)
				.optional();
	}

	List<TenancyRow> listForPg(long pgId) {
		return db.sql(SELECT + " where r.pg_id = :pgId order by t.status, t.move_in_date desc")
				.param("pgId", pgId)
				.query(TenancyRepository::map)
				.list();
	}

	void markNotice(long id, LocalDate noticeDate, LocalDate vacateDate) {
		db.sql("""
				update tenancy set status = 'ON_NOTICE', notice_date = :noticeDate, vacate_date = :vacateDate
				 where id = :id
				""")
				.param("noticeDate", noticeDate)
				.param("vacateDate", vacateDate)
				.param("id", id)
				.update();
	}

	void end(long id) {
		db.sql("update tenancy set status = 'ENDED' where id = :id")
				.param("id", id)
				.update();
	}

	/** Tenancies whose notice period has run out - what the daily vacate sweep releases. */
	List<TenancyRow> dueToVacate(LocalDate today) {
		return db.sql(SELECT + " where t.status = 'ON_NOTICE' and t.vacate_date <= :today")
				.param("today", today)
				.query(TenancyRepository::map)
				.list();
	}

	/** Every ACTIVE or ON_NOTICE tenancy - rent is still due right up to the day someone actually vacates. */
	List<TenancyRow> allOpen() {
		return db.sql(SELECT + " where t.status in ('ACTIVE','ON_NOTICE')")
				.query(TenancyRepository::map)
				.list();
	}

	private static final String SELECT = """
			select t.id, t.bed_id, t.tenant_id, u.phone as tenant_phone, u.name as tenant_name,
			       t.move_in_date, t.rent_amount, t.rent_due_day, t.notice_date, t.vacate_date, t.status
			  from tenancy t
			  join bed b on b.id = t.bed_id
			  join room r on r.id = b.room_id
			  join app_user u on u.id = t.tenant_id
			""";

	private static TenancyRow map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
		return new TenancyRow(
				rs.getLong("id"),
				rs.getLong("bed_id"),
				rs.getLong("tenant_id"),
				rs.getString("tenant_phone"),
				rs.getString("tenant_name"),
				rs.getObject("move_in_date", LocalDate.class),
				rs.getInt("rent_amount"),
				rs.getInt("rent_due_day"),
				rs.getObject("notice_date", LocalDate.class),
				rs.getObject("vacate_date", LocalDate.class),
				rs.getString("status"));
	}
}
