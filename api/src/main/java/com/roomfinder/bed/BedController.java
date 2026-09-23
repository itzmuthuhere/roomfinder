package com.roomfinder.bed;

import com.roomfinder.auth.AuthedUser;
import com.roomfinder.auth.JwtAuthFilter;
import com.roomfinder.bed.BedDtos.ChangeStateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class BedController {

	private final BedService service;

	public BedController(BedService service) {
		this.service = service;
	}

	@GetMapping("/pgs/{pgId}/beds")
	public List<BedDtos.BedView> grid(@PathVariable long pgId) {
		return service.gridFor(pgId);
	}

	@GetMapping("/beds/{bedId}/history")
	public List<BedDtos.StateLogView> history(@PathVariable long bedId) {
		return service.history(bedId);
	}

	@PatchMapping("/beds/{bedId}/state")
	public Map<String, Object> changeState(
			@RequestAttribute(name = JwtAuthFilter.REQUEST_ATTRIBUTE, required = false) AuthedUser user,
			@PathVariable long bedId,
			@Valid @RequestBody ChangeStateRequest req) {
		BedState now = service.changeState(user, bedId, req.toState(), req.reason(), req.availableFrom());
		return Map.of("id", bedId, "state", now);
	}
}
