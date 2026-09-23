package com.roomfinder.search;

import com.roomfinder.media.MediaService;
import com.roomfinder.search.SearchDtos.SearchQuery;
import com.roomfinder.search.SearchDtos.SearchResultView;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SearchService {

	static final double DEFAULT_RADIUS_KM = 5.0;

	private final SearchRepository repo;
	private final MediaService mediaService;

	public SearchService(SearchRepository repo, MediaService mediaService) {
		this.repo = repo;
		this.mediaService = mediaService;
	}

	public List<SearchResultView> search(SearchQuery q) {
		Double radiusKm = q.lat() != null ? (q.radiusKm() != null ? q.radiusKm() : DEFAULT_RADIUS_KM) : null;
		Double radiusMeters = radiusKm != null ? radiusKm * 1000 : null;

		List<SearchRepository.ResultRow> rows = repo.search(
				q.lat(), q.lng(), radiusMeters, blankToNull(q.area()),
				q.minRent(), q.maxRent(), blankToNull(q.sharingType()), blankToNull(q.genderType()),
				q.ac(), q.foodIncluded());

		Map<Long, String> coverPhotos = mediaService.coverPhotoUrls(rows.stream().map(SearchRepository.ResultRow::id).toList());

		return rows.stream()
				.map(r -> new SearchResultView(
						r.id(), r.name(), r.area(), r.address(), r.genderType(),
						coverPhotos.get(r.id()), r.minRentPerBed(), r.vacantBeds(), r.distanceKm()))
				.toList();
	}

	private static String blankToNull(String s) {
		return (s == null || s.isBlank()) ? null : s;
	}
}
