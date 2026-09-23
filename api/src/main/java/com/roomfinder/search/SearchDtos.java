package com.roomfinder.search;

public class SearchDtos {

	/** Every field optional; the repository turns an absent one into "don't filter on this". */
	public record SearchQuery(
			Double lat, Double lng, Double radiusKm,
			String area,
			Integer minRent, Integer maxRent,
			String sharingType,      // SINGLE / DOUBLE / TRIPLE / FOUR_PLUS
			String genderType,       // BOYS / GIRLS / UNISEX / WORKING_WOMEN
			Boolean ac,
			Boolean foodIncluded) {}

	public record SearchResultView(
			long id,
			String name,
			String area,
			String address,
			String genderType,
			String coverPhotoUrl,
			int minRentPerBed,
			int vacantBeds,
			Double distanceKm) {}
}
