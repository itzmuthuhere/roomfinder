package com.roomfinder.booking;

import com.roomfinder.AbstractDbTest;
import com.roomfinder.auth.AuthedUser;
import com.roomfinder.auth.Role;
import com.roomfinder.booking.BookingDtos.BookingView;
import com.roomfinder.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingServiceTest extends AbstractDbTest {

	@Autowired JdbcClient db;
	@Autowired BookingService bookings;

	AuthedUser owner;
	AuthedUser otherOwner;
	AuthedUser seeker;
	AuthedUser otherSeeker;
	long bedId;

	@BeforeEach
	void seed() {
		db.sql("delete from booking").update();
		db.sql("delete from bed_state_log").update();
		db.sql("delete from bed").update();
		db.sql("delete from room").update();
		db.sql("delete from pg_property").update();
		db.sql("delete from app_user").update();

		long ownerId = insertUser("9000000030", "OWNER");
		long otherOwnerId = insertUser("9000000031", "OWNER");
		long seekerId = insertUser("9000000032", "SEEKER");
		long otherSeekerId = insertUser("9000000033", "SEEKER");
		owner = new AuthedUser(ownerId, "9000000030", Role.OWNER);
		otherOwner = new AuthedUser(otherOwnerId, "9000000031", Role.OWNER);
		seeker = new AuthedUser(seekerId, "9000000032", Role.SEEKER);
		otherSeeker = new AuthedUser(otherSeekerId, "9000000033", Role.SEEKER);

		long pgId = db.sql("""
				insert into pg_property (owner_id, name, gender_type, address, area, location, status)
				values (:o, 'Visit PG', 'BOYS', 'Addr', 'Velachery',
				        ST_SetSRID(ST_MakePoint(80.2200, 12.9800), 4326)::geography, 'LIVE')
				returning id""").param("o", ownerId).query(Long.class).single();
		long roomId = db.sql("""
				insert into room (pg_id, room_no, sharing_type, ac, attached_bath, rent_per_bed, deposit_amount)
				values (:p, '1', 'DOUBLE', false, false, 8000, 16000) returning id""")
				.param("p", pgId).query(Long.class).single();
		bedId = db.sql("insert into bed (room_id, bed_label) values (:r, 'A') returning id")
				.param("r", roomId).query(Long.class).single();
	}

	@Test
	void requesting_a_visit_holds_the_bed_and_creates_a_requested_booking() {
		OffsetDateTime slot = OffsetDateTime.now().plusDays(2);

		BookingView view = bookings.requestVisit(seeker, bedId, slot);

		assertThat(view.status()).isEqualTo("REQUESTED");
		assertThat(view.kind()).isEqualTo("VISIT");
		assertThat(currentBedState()).isEqualTo("HELD");
	}

	@Test
	void a_future_visit_gets_a_24_hour_hold_and_a_same_day_visit_gets_2_hours() {
		OffsetDateTime future = OffsetDateTime.now().plusDays(5);
		BookingView futureView = bookings.requestVisit(seeker, bedId, future);
		long futureHoursHeld = java.time.Duration.between(OffsetDateTime.now(), futureView.holdExpiresAt()).toHours();
		assertThat(futureHoursHeld).isBetween(23L, 24L);

		bookings.cancelBySeeker(seeker, futureView.id()); // free the bed for the next part of this test

		OffsetDateTime laterToday = OffsetDateTime.now().plusMinutes(30);
		BookingView todayView = bookings.requestVisit(seeker, bedId, laterToday);
		long todayMinutesHeld = java.time.Duration.between(OffsetDateTime.now(), todayView.holdExpiresAt()).toMinutes();
		assertThat(todayMinutesHeld).isBetween(115L, 120L);
	}

	@Test
	void a_second_seeker_cannot_also_hold_an_already_held_bed() {
		bookings.requestVisit(seeker, bedId, OffsetDateTime.now().plusDays(1));

		assertThatThrownBy(() -> bookings.requestVisit(otherSeeker, bedId, OffsetDateTime.now().plusDays(1)))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void an_owner_cannot_request_a_visit() {
		assertThatThrownBy(() -> bookings.requestVisit(owner, bedId, OffsetDateTime.now().plusDays(1)))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("seeker");
	}

	@Test
	void a_visit_slot_in_the_past_is_rejected() {
		assertThatThrownBy(() -> bookings.requestVisit(seeker, bedId, OffsetDateTime.now().minusHours(1)))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("future");
	}

	@Test
	void the_owner_can_accept_a_visit_and_the_bed_stays_held() {
		BookingView requested = bookings.requestVisit(seeker, bedId, OffsetDateTime.now().plusDays(1));

		BookingView accepted = bookings.accept(owner, requested.id());

		assertThat(accepted.status()).isEqualTo("CONFIRMED");
		assertThat(currentBedState()).isEqualTo("HELD");
	}

	@Test
	void a_different_owner_cannot_accept_or_decline() {
		BookingView requested = bookings.requestVisit(seeker, bedId, OffsetDateTime.now().plusDays(1));

		assertThatThrownBy(() -> bookings.accept(otherOwner, requested.id())).isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> bookings.decline(otherOwner, requested.id())).isInstanceOf(ApiException.class);
	}

	@Test
	void declining_releases_the_bed_and_cancels_the_booking() {
		BookingView requested = bookings.requestVisit(seeker, bedId, OffsetDateTime.now().plusDays(1));

		BookingView declined = bookings.decline(owner, requested.id());

		assertThat(declined.status()).isEqualTo("CANCELLED");
		assertThat(currentBedState()).isEqualTo("VACANT");
	}

	@Test
	void the_seeker_can_cancel_their_own_booking_and_it_releases_the_bed() {
		BookingView requested = bookings.requestVisit(seeker, bedId, OffsetDateTime.now().plusDays(1));

		bookings.cancelBySeeker(seeker, requested.id());

		assertThat(currentBedState()).isEqualTo("VACANT");
		assertThat(bookings.myBookings(seeker)).extracting(BookingView::status).containsExactly("CANCELLED");
	}

	@Test
	void a_different_seeker_cannot_cancel_someone_elses_booking() {
		BookingView requested = bookings.requestVisit(seeker, bedId, OffsetDateTime.now().plusDays(1));

		assertThatThrownBy(() -> bookings.cancelBySeeker(otherSeeker, requested.id()))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("someone else");
	}

	// --- the required hold expiry job test ---

	@Test
	void the_expiry_sweep_releases_a_bed_whose_hold_has_passed_and_marks_the_booking_expired() {
		long bookingId = seedOverdueVisitBooking();

		int released = bookings.expireOverdueHolds();

		assertThat(released).isEqualTo(1);
		assertThat(currentBedState()).isEqualTo("VACANT");
		assertThat(bookingStatus(bookingId)).isEqualTo("EXPIRED");
	}

	@Test
	void the_expiry_sweep_leaves_a_booking_alone_if_its_hold_has_not_expired_yet() {
		bookings.requestVisit(seeker, bedId, OffsetDateTime.now().plusDays(1));

		int released = bookings.expireOverdueHolds();

		assertThat(released).isEqualTo(0);
		assertThat(currentBedState()).isEqualTo("HELD");
	}

	@Test
	void the_expiry_sweep_skips_a_booking_whose_bed_already_moved_on_without_crashing() {
		long bookingId = seedOverdueVisitBooking();
		// The hold was already resolved some other way before the sweep ran (e.g. a prior sweep run
		// released it but its own status update never landed) - the bed is VACANT, not HELD, and
		// VACANT -> VACANT is not a legal transition, so releasing it again must fail gracefully.
		db.sql("update bed set state = 'VACANT' where id = :id").param("id", bedId).update();

		int released = bookings.expireOverdueHolds();

		assertThat(released).isEqualTo(0);
		assertThat(bookingStatus(bookingId)).isEqualTo("REQUESTED"); // left alone, not force-marked EXPIRED
		assertThat(currentBedState()).isEqualTo("VACANT"); // untouched
	}

	private long seedOverdueVisitBooking() {
		db.sql("update bed set state = 'HELD' where id = :id").param("id", bedId).update();
		return db.sql("""
				insert into booking (bed_id, seeker_id, kind, status, visit_slot, hold_expires_at)
				values (:bedId, :seekerId, 'VISIT', 'REQUESTED', :slot, :expired)
				returning id
				""")
				.param("bedId", bedId)
				.param("seekerId", seeker.id())
				.param("slot", OffsetDateTime.now().minusHours(3))
				.param("expired", OffsetDateTime.now().minusHours(1))
				.query(Long.class)
				.single();
	}

	private String currentBedState() {
		return db.sql("select state from bed where id = :id").param("id", bedId).query(String.class).single();
	}

	private String bookingStatus(long bookingId) {
		return db.sql("select status from booking where id = :id").param("id", bookingId).query(String.class).single();
	}

	private long insertUser(String phone, String role) {
		return db.sql("insert into app_user (phone, name, role) values (:phone, 'T', :role) returning id")
				.param("phone", phone)
				.param("role", role)
				.query(Long.class)
				.single();
	}
}
