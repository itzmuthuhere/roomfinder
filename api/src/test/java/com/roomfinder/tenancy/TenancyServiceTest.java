package com.roomfinder.tenancy;

import com.roomfinder.AbstractDbTest;
import com.roomfinder.auth.AuthedUser;
import com.roomfinder.auth.Role;
import com.roomfinder.common.ApiException;
import com.roomfinder.tenancy.TenancyDtos.TenancyView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenancyServiceTest extends AbstractDbTest {

	@Autowired JdbcClient db;
	@Autowired TenancyService tenancies;

	AuthedUser owner;
	AuthedUser otherOwner;
	long bedId;

	@BeforeEach
	void seed() {
		db.sql("delete from rent_payment").update();
		db.sql("delete from tenancy").update();
		db.sql("delete from bed_state_log").update();
		db.sql("delete from bed").update();
		db.sql("delete from room").update();
		db.sql("delete from pg_property").update();
		db.sql("delete from app_user").update();

		long ownerId = insertUser("9000000040", "OWNER");
		long otherOwnerId = insertUser("9000000041", "OWNER");
		owner = new AuthedUser(ownerId, "9000000040", Role.OWNER);
		otherOwner = new AuthedUser(otherOwnerId, "9000000041", Role.OWNER);

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
	}

	@Test
	void moving_in_a_tenant_occupies_the_bed_and_sets_the_current_tenant() {
		TenancyView view = tenancies.createTenancy(owner, bedId, "9111111111", "Arun",
				LocalDate.now(), 9000, null);

		assertThat(view.status()).isEqualTo("ACTIVE");
		assertThat(view.rentDueDay()).isEqualTo(5); // default
		assertThat(currentBedState()).isEqualTo("OCCUPIED");
		assertThat(currentTenantId()).isNotNull();
	}

	@Test
	void a_custom_rent_due_day_is_kept() {
		TenancyView view = tenancies.createTenancy(owner, bedId, "9111111111", "Arun",
				LocalDate.now(), 9000, 10);

		assertThat(view.rentDueDay()).isEqualTo(10);
	}

	@Test
	void moving_in_a_tenant_onto_an_already_occupied_bed_is_rejected() {
		tenancies.createTenancy(owner, bedId, "9111111111", "Arun", LocalDate.now(), 9000, null);

		assertThatThrownBy(() -> tenancies.createTenancy(owner, bedId, "9222222222", "Bala", LocalDate.now(), 9000, null))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void a_different_owner_cannot_move_in_a_tenant() {
		assertThatThrownBy(() -> tenancies.createTenancy(otherOwner, bedId, "9111111111", "Arun", LocalDate.now(), 9000, null))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void giving_notice_sets_on_notice_with_available_from_in_the_same_call() {
		TenancyView tenancy = tenancies.createTenancy(owner, bedId, "9111111111", "Arun", LocalDate.now(), 9000, null);
		LocalDate vacateDate = LocalDate.now().plusDays(30);

		TenancyView noticed = tenancies.giveNotice(owner, tenancy.id(), vacateDate);

		assertThat(noticed.status()).isEqualTo("ON_NOTICE");
		assertThat(noticed.vacateDate()).isEqualTo(vacateDate);
		assertThat(currentBedState()).isEqualTo("ON_NOTICE");
		assertThat(bedAvailableFrom()).isEqualTo(vacateDate);
	}

	@Test
	void a_different_owner_cannot_give_notice() {
		TenancyView tenancy = tenancies.createTenancy(owner, bedId, "9111111111", "Arun", LocalDate.now(), 9000, null);

		assertThatThrownBy(() -> tenancies.giveNotice(otherOwner, tenancy.id(), LocalDate.now().plusDays(30)))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void notice_cannot_be_given_twice() {
		TenancyView tenancy = tenancies.createTenancy(owner, bedId, "9111111111", "Arun", LocalDate.now(), 9000, null);
		tenancies.giveNotice(owner, tenancy.id(), LocalDate.now().plusDays(30));

		assertThatThrownBy(() -> tenancies.giveNotice(owner, tenancy.id(), LocalDate.now().plusDays(45)))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("not active");
	}

	// --- the required notice -> ON_NOTICE -> VACANT transition test ---

	@Test
	void the_vacate_sweep_frees_a_bed_whose_notice_period_has_ended() {
		TenancyView tenancy = tenancies.createTenancy(owner, bedId, "9111111111", "Arun", LocalDate.now(), 9000, null);
		tenancies.giveNotice(owner, tenancy.id(), LocalDate.now().minusDays(1)); // vacate date already past

		int vacated = tenancies.processDueVacates();

		assertThat(vacated).isEqualTo(1);
		assertThat(currentBedState()).isEqualTo("VACANT");
		assertThat(currentTenantId()).isNull();
		assertThat(tenancyStatus(tenancy.id())).isEqualTo("ENDED");
	}

	@Test
	void the_vacate_sweep_leaves_a_tenancy_alone_before_its_vacate_date() {
		TenancyView tenancy = tenancies.createTenancy(owner, bedId, "9111111111", "Arun", LocalDate.now(), 9000, null);
		tenancies.giveNotice(owner, tenancy.id(), LocalDate.now().plusDays(10));

		int vacated = tenancies.processDueVacates();

		assertThat(vacated).isEqualTo(0);
		assertThat(currentBedState()).isEqualTo("ON_NOTICE");
		assertThat(tenancyStatus(tenancy.id())).isEqualTo("ON_NOTICE");
	}

	@Test
	void the_vacate_sweep_never_touches_a_tenancy_that_was_never_given_notice() {
		tenancies.createTenancy(owner, bedId, "9111111111", "Arun", LocalDate.now(), 9000, null);

		int vacated = tenancies.processDueVacates();

		assertThat(vacated).isEqualTo(0);
		assertThat(currentBedState()).isEqualTo("OCCUPIED");
	}

	private String currentBedState() {
		return db.sql("select state from bed where id = :id").param("id", bedId).query(String.class).single();
	}

	private LocalDate bedAvailableFrom() {
		return db.sql("select available_from from bed where id = :id").param("id", bedId).query(LocalDate.class).single();
	}

	private Long currentTenantId() {
		return db.sql("select current_tenant_id from bed where id = :id").param("id", bedId).query(Long.class).list().get(0);
	}

	private String tenancyStatus(long tenancyId) {
		return db.sql("select status from tenancy where id = :id").param("id", tenancyId).query(String.class).single();
	}

	private long insertUser(String phone, String role) {
		return db.sql("insert into app_user (phone, name, role) values (:phone, 'T', :role) returning id")
				.param("phone", phone)
				.param("role", role)
				.query(Long.class)
				.single();
	}
}
