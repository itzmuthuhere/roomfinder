package com.roomfinder.rent;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
class RentRepository {

	private final JdbcClient db;

	RentRepository(JdbcClient db) {
		this.db = db;
	}

	record RentPaymentRow(long id, long tenancyId, long bedId, long pgId, String tenantPhone,
	                       LocalDate forMonth, int amount, LocalDate paidOn, String status) {}

	long insertPending(long tenancyId, LocalDate forMonth, int amount) {
		return db.sql("""
				insert into rent_payment (tenancy_id, for_month, amount, status)
				values (:tenancyId, :forMonth, :amount, 'PENDING')
				returning id
				""")
				.param("tenancyId", tenancyId)
				.param("forMonth", forMonth)
				.param("amount", amount)
				.query(Long.class)
				.single();
	}

	boolean existsForMonth(long tenancyId, LocalDate forMonth) {
		return db.sql("select count(*) from rent_payment where tenancy_id = :tenancyId and for_month = :forMonth")
				.param("tenancyId", tenancyId)
				.param("forMonth", forMonth)
				.query(Integer.class)
				.single() > 0;
	}

	Optional<RentPaymentRow> findById(long id) {
		return db.sql(SELECT + " where rp.id = :id")
				.param("id", id)
				.query(RentRepository::map)
				.optional();
	}

	List<RentPaymentRow> listForPg(long pgId) {
		return db.sql(SELECT + " where r.pg_id = :pgId order by rp.for_month desc, rp.status")
				.param("pgId", pgId)
				.query(RentRepository::map)
				.list();
	}

	void markPaid(long id, LocalDate paidOn, int amount, String status) {
		db.sql("update rent_payment set paid_on = :paidOn, amount = :amount, status = :status where id = :id")
				.param("paidOn", paidOn)
				.param("amount", amount)
				.param("status", status)
				.param("id", id)
				.update();
	}

	private static final String SELECT = """
			select rp.id, rp.tenancy_id, t.bed_id, r.pg_id, u.phone as tenant_phone,
			       rp.for_month, rp.amount, rp.paid_on, rp.status
			  from rent_payment rp
			  join tenancy t on t.id = rp.tenancy_id
			  join bed b on b.id = t.bed_id
			  join room r on r.id = b.room_id
			  join app_user u on u.id = t.tenant_id
			""";

	private static RentPaymentRow map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
		return new RentPaymentRow(
				rs.getLong("id"),
				rs.getLong("tenancy_id"),
				rs.getLong("bed_id"),
				rs.getLong("pg_id"),
				rs.getString("tenant_phone"),
				rs.getObject("for_month", LocalDate.class),
				rs.getInt("amount"),
				rs.getObject("paid_on", LocalDate.class),
				rs.getString("status"));
	}
}
