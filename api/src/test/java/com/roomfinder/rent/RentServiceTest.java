package com.roomfinder.rent;

import com.roomfinder.AbstractDbTest;
import com.roomfinder.auth.AuthedUser;
import com.roomfinder.auth.Role;
import com.roomfinder.common.ApiException;
import com.roomfinder.rent.RentDtos.RentPaymentView;
import com.roomfinder.tenancy.TenancyDtos.TenancyView;
import com.roomfinder.tenancy.TenancyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RentServiceTest extends AbstractDbTest {

	@Autowired JdbcClient db;
	@Autowired RentService rent;
	@Autowired TenancyService tenancies;

	AuthedUser owner;
	AuthedUser otherOwner;
	long bedId;
	long tenancyId;

	@BeforeEach
	void seed() {
		db.sql("delete from rent_payment").update();
		db.sql("delete from tenancy").update();
		db.sql("delete from bed_state_log").update();
		db.sql("delete from bed").update();
		db.sql("delete from room").update();
		db.sql("delete from pg_property").update();
		db.sql("delete from app_user").update();

		long ownerId = insertUser("9000000050", "OWNER");
		long otherOwnerId = insertUser("9000000051", "OWNER");
		owner = new AuthedUser(ownerId, "9000000050", Role.OWNER);
		otherOwner = new AuthedUser(otherOwnerId, "9000000051", Role.OWNER);

		long pgId = db.sql("""
				insert into pg_property (owner_id, name, gender_type, address, area, location, status)
				values (:o, 'Rent PG', 'BOYS', 'Addr', 'Velachery',
				        ST_SetSRID(ST_MakePoint(80.2200, 12.9800), 4326)::geography, 'LIVE')
				returning id""").param("o", ownerId).query(Long.class).single();
		long roomId = db.sql("""
				insert into room (pg_id, room_no, sharing_type, ac, attached_bath, rent_per_bed, deposit_amount)
				values (:p, '1', 'DOUBLE', false, false, 9000, 18000) returning id""")
				.param("p", pgId).query(Long.class).single();
		bedId = db.sql("insert into bed (room_id, bed_label) values (:r, 'A') returning id")
				.param("r", roomId).query(Long.class).single();

		TenancyView tenancy = tenancies.createTenancy(owner, bedId, "9111111111", "Arun", LocalDate.now(), 9000, null);
		tenancyId = tenancy.id();
	}

	@Test
	void the_due_sweep_creates_one_pending_payment_per_open_tenancy_for_the_current_month() {
		int created = rent.generateDueForCurrentMonth();

		assertThat(created).isEqualTo(1);
		List<RentPaymentView> pending = rent.pendingForPg(owner, pgIdOfBed());
		assertThat(pending).hasSize(1);
		assertThat(pending.get(0).status()).isEqualTo("PENDING");
		assertThat(pending.get(0).amount()).isEqualTo(9000);
		assertThat(pending.get(0).forMonth()).isEqualTo(LocalDate.now().withDayOfMonth(1));
	}

	@Test
	void running_the_due_sweep_twice_in_the_same_month_does_not_duplicate() {
		rent.generateDueForCurrentMonth();
		int createdSecondTime = rent.generateDueForCurrentMonth();

		assertThat(createdSecondTime).isEqualTo(0);
		assertThat(rent.pendingForPg(owner, pgIdOfBed())).hasSize(1);
	}

	@Test
	void marking_a_payment_paid_in_full_sets_status_paid() {
		rent.generateDueForCurrentMonth();
		long paymentId = rent.pendingForPg(owner, pgIdOfBed()).get(0).id();

		RentPaymentView paid = rent.markPaid(owner, paymentId, LocalDate.now(), null);

		assertThat(paid.status()).isEqualTo("PAID");
		assertThat(paid.amount()).isEqualTo(9000);
	}

	@Test
	void marking_a_payment_paid_with_a_smaller_amount_sets_status_partial() {
		rent.generateDueForCurrentMonth();
		long paymentId = rent.pendingForPg(owner, pgIdOfBed()).get(0).id();

		RentPaymentView paid = rent.markPaid(owner, paymentId, LocalDate.now(), 5000);

		assertThat(paid.status()).isEqualTo("PARTIAL");
		assertThat(paid.amount()).isEqualTo(5000);
	}

	@Test
	void a_different_owner_cannot_see_or_mark_another_owners_rent() {
		rent.generateDueForCurrentMonth();
		long paymentId = rent.pendingForPg(owner, pgIdOfBed()).get(0).id();

		assertThatThrownBy(() -> rent.markPaid(otherOwner, paymentId, LocalDate.now(), null))
				.isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> rent.pendingForPg(otherOwner, pgIdOfBed()))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void a_reminder_can_be_sent_without_throwing_even_with_no_upi_vpa_set() {
		rent.generateDueForCurrentMonth();
		long paymentId = rent.pendingForPg(owner, pgIdOfBed()).get(0).id();

		rent.sendReminder(owner, paymentId); // no assertion beyond "does not throw" - delivery is logged, not verified here
	}

	private long pgIdOfBed() {
		return db.sql("select r.pg_id from bed b join room r on r.id = b.room_id where b.id = :id")
				.param("id", bedId).query(Long.class).single();
	}

	private long insertUser(String phone, String role) {
		return db.sql("insert into app_user (phone, name, role) values (:phone, 'T', :role) returning id")
				.param("phone", phone)
				.param("role", role)
				.query(Long.class)
				.single();
	}
}
