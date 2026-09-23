package com.roomfinder.search;

import com.roomfinder.search.SearchDtos.SearchQuery;
import com.roomfinder.search.SearchDtos.SearchResultView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SearchController {

	private final SearchService service;

	public SearchController(SearchService service) {
		this.service = service;
	}

	@GetMapping("/search")
	public List<SearchResultView> search(
			@RequestParam(required = false) Double lat,
			@RequestParam(required = false) Double lng,
			@RequestParam(required = false) Double radiusKm,
			@RequestParam(required = false) String area,
			@RequestParam(required = false) Integer minRent,
			@RequestParam(required = false) Integer maxRent,
			@RequestParam(required = false) String sharingType,
			@RequestParam(required = false) String genderType,
			@RequestParam(required = false) Boolean ac,
			@RequestParam(required = false) Boolean foodIncluded) {
		return service.search(new SearchQuery(
				lat, lng, radiusKm, area, minRent, maxRent, sharingType, genderType, ac, foodIncluded));
	}
}
