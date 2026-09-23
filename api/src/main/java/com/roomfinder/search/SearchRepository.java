package com.roomfinder.search;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
class SearchRepository {

	private final JdbcClient db;

	SearchRepository(JdbcClient db) {
		this.db = db;
	}

	record ResultRow(long id, String name, String area, String address, String genderType,
	                  int minRentPerBed, int vacantBeds, Double distanceKm) {}

	/**
	 * Only LIVE PGs with at least one currently VACANT bed in a room that matches every filter.
	 * {@code minRentPerBed} and {@code vacantBeds} are both scoped to that same matching-room set,
	 * via {@code filter (where ...)} on the aggregates, so a PG never advertises a price for a room
	 * type it doesn't currently have a vacancy in.
	 *
	 * <p>A gender filter also matches UNISEX PGs: a seeker filtering for BOYS or GIRLS or
	 * WORKING_WOMEN should still see a unisex option, not just an exact match.
	 */
	List<ResultRow> search(Double lat, Double lng, Double radiusMeters, String area,
	                       Integer minRent, Integer maxRent, String sharingType, String genderType,
	                       Boolean ac, Boolean foodIncluded) {
		String geoOrder = lat != null ? "distance_km nulls last, " : "";
		return db.sql("""
				select p.id, p.name, p.area, p.address, p.gender_type,
				       min(r.rent_per_bed) filter (where b.state = 'VACANT') as min_rent_per_bed,
				       count(b.id) filter (where b.state = 'VACANT') as vacant_beds,
				       case when :lat::float8 is null then null
				            else ST_Distance(p.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) / 1000.0
				       end as distance_km
				  from pg_property p
				  join room r on r.pg_id = p.id
				  join bed b on b.room_id = r.id
				 where p.status = 'LIVE'
				   and (:area::text is null or p.area ilike '%' || :area || '%')
				   and (:genderType::text is null or p.gender_type = :genderType or p.gender_type = 'UNISEX')
				   and (:foodIncluded::boolean is null or p.food_included = :foodIncluded)
				   and (:sharingType::text is null or r.sharing_type = :sharingType)
				   and (:ac::boolean is null or r.ac = :ac)
				   and (:minRent::int is null or r.rent_per_bed >= :minRent)
				   and (:maxRent::int is null or r.rent_per_bed <= :maxRent)
				   and (:lat::float8 is null
				        or ST_DWithin(p.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusMeters))
				 group by p.id
				having count(b.id) filter (where b.state = 'VACANT') > 0
				 order by """ + geoOrder + """
				          p.area, p.name
				""")
				.param("lat", lat)
				.param("lng", lng)
				.param("radiusMeters", radiusMeters)
				.param("area", area)
				.param("genderType", genderType)
				.param("foodIncluded", foodIncluded)
				.param("sharingType", sharingType)
				.param("ac", ac)
				.param("minRent", minRent)
				.param("maxRent", maxRent)
				.query((rs, i) -> new ResultRow(
						rs.getLong("id"),
						rs.getString("name"),
						rs.getString("area"),
						rs.getString("address"),
						rs.getString("gender_type"),
						rs.getInt("min_rent_per_bed"),
						rs.getInt("vacant_beds"),
						(Double) rs.getObject("distance_km")))
				.list();
	}
}
