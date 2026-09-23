package com.roomfinder.bed;

import com.roomfinder.AbstractDbTest;
import com.roomfinder.auth.AuthedUser;
import com.roomfinder.auth.Role;
import com.roomfinder.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BedStateMachineTest extends AbstractDbTest {

	@Autowired JdbcClient db;
	@Autowired BedService bedService;

	long bedId;
	AuthedUser owner;

	@BeforeEach
	void seed() {
		db.sql("delete from bed_state_log").update();
		db.sql("delete from bed").update();
		db.sql("delete from room").update();
		db.sql("delete from pg_property").update();
		db.sql("delete from app_user").update();

		long ownerId = db.sql("insert into app_user (phone, name, role) values ('9000000001','O','OWNER') returning id")
				.query(Long.class).single();
		owner = new AuthedUser(ownerId, "9000000001", Role.OWNER);
		long pgId = db.sql("""
				insert into pg_property (owner_id, name, gender_type, address, area, location, status)
				values (:o, 'Test PG', 'BOYS', 'Addr', 'Thoraipakkam',
				        ST_SetSRID(ST_MakePoint(80.2297, 12.9391), 4326)::geography, 'LIVE')
				returning id""").param("o", ownerId).query(Long.class).single();
		long roomId = db.sql("""
				insert into room (pg_id, room_no, sharing_type, ac, attached_bath, rent_per_bed, deposit_amount)
				values (:p, '101', 'DOUBLE', true, false, 9500, 19000) returning id""")
				.param("p", pgId).query(Long.class).single();
		bedId = db.sql("insert into bed (room_id, bed_label) values (:r, 'A') returning id")
				.param("r", roomId).query(Long.class).single();
	}

	@Test
	void allows_a_legal_transition_and_writes_an_audit_row() {
		bedService.changeState(owner, bedId, BedState.HELD, "seeker booked a visit", null);

		assertThat(currentState()).isEqualTo("HELD");
		assertThat(bedService.history(bedId)).hasSize(1);
		assertThat(bedService.history(bedId).get(0).toState()).isEqualTo("HELD");
	}

	@Test
	void rejects_an_illegal_transition() {
		assertThatThrownBy(() -> bedService.changeState(owner, bedId, BedState.ON_NOTICE, null, LocalDate.now()))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("cannot go from VACANT to ON_NOTICE");

		assertThat(currentState()).isEqualTo("VACANT");
	}

	@Test
	void on_notice_requires_a_date_and_keeps_it() {
		bedService.changeState(owner, bedId, BedState.OCCUPIED, "tenant moved in", null);

		assertThatThrownBy(() -> bedService.changeState(owner, bedId, BedState.ON_NOTICE, null, null))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("Set the date");

		LocalDate free = LocalDate.now().plusDays(30);
		bedService.changeState(owner, bedId, BedState.ON_NOTICE, "notice given", free);

		LocalDate stored = db.sql("select available_from from bed where id = :id")
				.param("id", bedId).query(LocalDate.class).single();
		assertThat(stored).isEqualTo(free);
	}

	@Test
	void asking_for_a_state_the_bed_is_already_in_is_rejected() {
		bedService.changeState(owner, bedId, BedState.HELD, "first seeker", null);

		assertThatThrownBy(() -> bedService.changeState(owner, bedId, BedState.HELD, "second seeker", null))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("already HELD");
	}

	private String currentState() {
		return db.sql("select state from bed where id = :id").param("id", bedId).query(String.class).single();
	}
}
