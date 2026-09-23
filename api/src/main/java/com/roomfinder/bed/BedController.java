package com.roomfinder.bed;

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

	// TODO(auth): actorId comes from the JWT once phone OTP login exists.
	@PatchMapping("/beds/{bedId}/state")
	public Map<String, Object> changeState(@PathVariable long bedId,
	                                       @Valid @RequestBody ChangeStateRequest req,
	                                       @RequestHeader(value = "X-Owner-Id", required = false) Long actorId) {
		BedState now = service.changeState(bedId, req.toState(), req.reason(), req.availableFrom(), actorId);
		return Map.of("id", bedId, "state", now);
	}
}
