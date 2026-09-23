package com.roomfinder.search;

import com.roomfinder.AbstractDbTest;
import com.roomfinder.search.SearchDtos.SearchQuery;
import com.roomfinder.search.SearchDtos.SearchResultView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchServiceTest extends AbstractDbTest {

	@Autowired JdbcClient db;
	@Autowired SearchService search;

	// A search origin in Chennai; PGs are seeded at known offsets from it.
	static final double ORIGIN_LAT = 13.0000;
	static final double ORIGIN_LNG = 80.2000;

	long ownerId;

	@BeforeEach
	void seed() {
		db.sql("delete from bed_state_log").update();
		db.sql("delete from media").update();
		db.sql("delete from bed").update();
		db.sql("delete from room").update();
		db.sql("delete from pg_property").update();
		db.sql("delete from app_user").update();

		ownerId = db.sql("insert into app_user (phone, name, role) values ('9000000020','O','OWNER') returning id")
				.query(Long.class).single();
	}

	@Test
	void only_live_pgs_with_a_vacant_bed_are_returned() {
		long livePgId = insertPg("Live with vacancy", "LIVE", "SomeArea", "BOYS", ORIGIN_LAT, ORIGIN_LNG);
		insertRoom(livePgId, "DOUBLE", 9000, "VACANT");

		long draftPgId = insertPg("Draft", "DRAFT", "SomeArea", "BOYS", ORIGIN_LAT, ORIGIN_LNG);
		insertRoom(draftPgId, "DOUBLE", 9000, "VACANT");

		long fullPgId = insertPg("Live but full", "LIVE", "SomeArea", "BOYS", ORIGIN_LAT, ORIGIN_LNG);
		insertRoom(fullPgId, "DOUBLE", 9000, "OCCUPIED");

		List<SearchResultView> results = search.search(emptyQuery());

		assertThat(results).extracting(SearchResultView::name).containsExactly("Live with vacancy");
	}

	@Test
	void a_pg_outside_the_radius_is_excluded_and_a_nearby_one_is_ordered_by_distance() {
		// ~0.5 km from the origin
		long nearId = insertPg("Nearby", "LIVE", "SomeArea", "BOYS", ORIGIN_LAT + 0.005, ORIGIN_LNG);
		insertRoom(nearId, "DOUBLE", 9000, "VACANT");

		// ~55 km from the origin - well outside a 5 km search
		long farId = insertPg("Far away", "LIVE", "SomeArea", "BOYS", ORIGIN_LAT + 0.5, ORIGIN_LNG);
		insertRoom(farId, "DOUBLE", 9000, "VACANT");

		List<SearchResultView> results = search.search(new SearchQuery(
				ORIGIN_LAT, ORIGIN_LNG, 5.0, null, null, null, null, null, null, null));

		assertThat(results).extracting(SearchResultView::id).containsExactly(nearId);
		assertThat(results.get(0).distanceKm()).isNotNull().isLessThan(1.0);
	}

	@Test
	void without_coordinates_distance_is_null_and_nothing_is_geographically_excluded() {
		long farId = insertPg("Far away", "LIVE", "SomeArea", "BOYS", ORIGIN_LAT + 0.5, ORIGIN_LNG);
		insertRoom(farId, "DOUBLE", 9000, "VACANT");

		List<SearchResultView> results = search.search(emptyQuery());

		assertThat(results).hasSize(1);
		assertThat(results.get(0).distanceKm()).isNull();
	}

	@Test
	void budget_filter_only_counts_rooms_in_range() {
		long cheapId = insertPg("Cheap room", "LIVE", "SomeArea", "BOYS", ORIGIN_LAT, ORIGIN_LNG);
		insertRoom(cheapId, "SINGLE", 6000, "VACANT");

		long priceyId = insertPg("Pricey room", "LIVE", "SomeArea", "BOYS", ORIGIN_LAT, ORIGIN_LNG);
		insertRoom(priceyId, "SINGLE", 15000, "VACANT");

		List<SearchResultView> results = search.search(new SearchQuery(
				null, null, null, null, 5000, 8000, null, null, null, null));

		assertThat(results).extracting(SearchResultView::name).containsExactly("Cheap room");
	}

	@Test
	void min_rent_shown_is_scoped_to_rooms_that_still_have_a_vacancy() {
		long pgId = insertPg("Mixed PG", "LIVE", "SomeArea", "BOYS", ORIGIN_LAT, ORIGIN_LNG);
		insertRoom(pgId, "SINGLE", 20000, "OCCUPIED"); // pricier room, no vacancy - must not set the price
		insertRoom(pgId, "DOUBLE", 8000, "VACANT");    // cheaper room, has vacancy - this is the real "from" price

		List<SearchResultView> results = search.search(emptyQuery());

		assertThat(results).hasSize(1);
		assertThat(results.get(0).minRentPerBed()).isEqualTo(8000);
	}

	@Test
	void sharing_type_filter_excludes_pgs_without_a_matching_vacant_room() {
		long singlesId = insertPg("Only singles", "LIVE", "SomeArea", "BOYS", ORIGIN_LAT, ORIGIN_LNG);
		insertRoom(singlesId, "SINGLE", 9000, "VACANT");

		long doublesId = insertPg("Only doubles", "LIVE", "SomeArea", "BOYS", ORIGIN_LAT, ORIGIN_LNG);
		insertRoom(doublesId, "DOUBLE", 9000, "VACANT");

		List<SearchResultView> results = search.search(new SearchQuery(
				null, null, null, null, null, null, "DOUBLE", null, null, null));

		assertThat(results).extracting(SearchResultView::name).containsExactly("Only doubles");
	}

	@Test
	void gender_filter_also_matches_unisex_pgs() {
		long boysId = insertPg("Boys only", "LIVE", "SomeArea", "BOYS", ORIGIN_LAT, ORIGIN_LNG);
		insertRoom(boysId, "DOUBLE", 9000, "VACANT");

		long girlsId = insertPg("Girls only", "LIVE", "SomeArea", "GIRLS", ORIGIN_LAT, ORIGIN_LNG);
		insertRoom(girlsId, "DOUBLE", 9000, "VACANT");

		long unisexId = insertPg("Unisex", "LIVE", "SomeArea", "UNISEX", ORIGIN_LAT, ORIGIN_LNG);
		insertRoom(unisexId, "DOUBLE", 9000, "VACANT");

		List<SearchResultView> results = search.search(new SearchQuery(
				null, null, null, null, null, null, null, "BOYS", null, null));

		assertThat(results).extracting(SearchResultView::name).containsExactlyInAnyOrder("Boys only", "Unisex");
	}

	@Test
	void an_area_filter_matches_case_insensitively_on_part_of_the_name() {
		long velacheryId = insertPg("Velachery PG", "LIVE", "Velachery", "BOYS", ORIGIN_LAT, ORIGIN_LNG);
		insertRoom(velacheryId, "DOUBLE", 9000, "VACANT");

		long adyarId = insertPg("Adyar PG", "LIVE", "Adyar", "BOYS", ORIGIN_LAT, ORIGIN_LNG);
		insertRoom(adyarId, "DOUBLE", 9000, "VACANT");

		List<SearchResultView> results = search.search(new SearchQuery(
				null, null, null, "velach", null, null, null, null, null, null));

		assertThat(results).extracting(SearchResultView::name).containsExactly("Velachery PG");
	}

	private static SearchQuery emptyQuery() {
		return new SearchQuery(null, null, null, null, null, null, null, null, null, null);
	}

	// --- fixtures ---

	private long insertPg(String name, String status, String area, String genderType, double lat, double lng) {
		return db.sql("""
				insert into pg_property (owner_id, name, gender_type, address, area, location, status, food_included)
				values (:o, :name, :gender, 'Addr', :area, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :status, true)
				returning id
				""")
				.param("o", ownerId).param("name", name).param("gender", genderType)
				.param("area", area).param("lat", lat).param("lng", lng).param("status", status)
				.query(Long.class).single();
	}

	private void insertRoom(long pgId, String sharingType, int rentPerBed, String bedState) {
		long roomId = db.sql("""
				insert into room (pg_id, room_no, sharing_type, ac, attached_bath, rent_per_bed, deposit_amount)
				values (:p, '1', :sharing, false, false, :rent, :rent * 2) returning id
				""")
				.param("p", pgId).param("sharing", sharingType).param("rent", rentPerBed)
				.query(Long.class).single();
		db.sql("insert into bed (room_id, bed_label, state) values (:r, 'A', :state)")
				.param("r", roomId).param("state", bedState).update();
	}
}
