package com.roomfinder.pg;

import com.roomfinder.AbstractDbTest;
import com.roomfinder.auth.AuthedUser;
import com.roomfinder.auth.Role;
import com.roomfinder.common.ApiException;
import com.roomfinder.pg.PgDtos.CreatePgRequest;
import com.roomfinder.pg.PgDtos.CreateRoomRequest;
import com.roomfinder.pg.PgDtos.PgView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The backlog's "an owner can only see and edit their own PGs" requirement, proven directly. */
class PgOwnershipTest extends AbstractDbTest {

	@Autowired JdbcClient db;
	@Autowired PgService pgService;

	AuthedUser ownerA;
	AuthedUser ownerB;
	AuthedUser seeker;
	long ownerAPgId;

	@BeforeEach
	void seed() {
		db.sql("delete from bed_state_log").update();
		db.sql("delete from media").update();
		db.sql("delete from bed").update();
		db.sql("delete from room").update();
		db.sql("delete from pg_property").update();
		db.sql("delete from app_user").update();

		ownerA = new AuthedUser(insertUser("9000000010", "OWNER"), "9000000010", Role.OWNER);
		ownerB = new AuthedUser(insertUser("9000000011", "OWNER"), "9000000011", Role.OWNER);
		seeker = new AuthedUser(insertUser("9000000012", "SEEKER"), "9000000012", Role.SEEKER);

		ownerAPgId = pgService.create(ownerA, new CreatePgRequest(
				"A's PG", "BOYS", "Addr", "Velachery", 12.98, 80.22, true, "BOTH", 30)).id();
	}

	@Test
	void an_owner_sees_only_their_own_pgs_in_the_list() {
		pgService.create(ownerB, new CreatePgRequest(
				"B's PG", "GIRLS", "Addr", "Adyar", 13.00, 80.25, false, "VEG", 30));

		List<PgView> mine = pgService.listForOwner(ownerA);

		assertThat(mine).extracting(PgView::name).containsExactly("A's PG");
	}

	@Test
	void an_owner_cannot_add_a_room_to_another_owners_pg() {
		CreateRoomRequest room = new CreateRoomRequest("101", "DOUBLE", false, false, 9000, 18000, 2);

		assertThatThrownBy(() -> pgService.addRoom(ownerB, ownerAPgId, room))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("different owner");
	}

	@Test
	void an_owner_cannot_change_another_owners_pg_status() {
		assertThatThrownBy(() -> pgService.setStatus(ownerB, ownerAPgId, "LIVE"))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("different owner");
	}

	@Test
	void a_seeker_cannot_create_a_pg() {
		CreatePgRequest req = new CreatePgRequest("Seeker's PG", "BOYS", "Addr", "T Nagar", 13.04, 80.23, true, "BOTH", 30);

		assertThatThrownBy(() -> pgService.create(seeker, req))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("PG owner");
	}

	@Test
	void an_unauthenticated_caller_cannot_create_or_edit_a_pg() {
		CreatePgRequest req = new CreatePgRequest("Nobody's PG", "BOYS", "Addr", "T Nagar", 13.04, 80.23, true, "BOTH", 30);

		assertThatThrownBy(() -> pgService.create(null, req))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("Sign in required");
		assertThatThrownBy(() -> pgService.setStatus(null, ownerAPgId, "LIVE"))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("Sign in required");
	}

	@Test
	void the_rightful_owner_can_still_edit_their_own_pg() {
		PgView updated = pgService.setStatus(ownerA, ownerAPgId, "LIVE");

		assertThat(updated.status()).isEqualTo("LIVE");
	}

	private long insertUser(String phone, String role) {
		return db.sql("insert into app_user (phone, name, role) values (:phone, 'T', :role) returning id")
				.param("phone", phone)
				.param("role", role)
				.query(Long.class)
				.single();
	}
}
