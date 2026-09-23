package com.roomfinder.pg;

import com.roomfinder.pg.PgDtos.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pgs")
public class PgController {

	private final PgService service;

	public PgController(PgService service) {
		this.service = service;
	}

	// TODO(auth): ownerId comes from the JWT once phone OTP login exists.
	@PostMapping
	public PgView create(@RequestHeader("X-Owner-Id") long ownerId, @Valid @RequestBody CreatePgRequest req) {
		return service.create(ownerId, req);
	}

	@GetMapping
	public List<PgView> mine(@RequestHeader("X-Owner-Id") long ownerId) {
		return service.listForOwner(ownerId);
	}

	@GetMapping("/{pgId}")
	public PgDetail detail(@PathVariable long pgId) {
		return service.detail(pgId);
	}

	@PostMapping("/{pgId}/rooms")
	public PgDetail addRoom(@PathVariable long pgId, @Valid @RequestBody CreateRoomRequest req) {
		return service.addRoom(pgId, req);
	}

	@PatchMapping("/{pgId}/status")
	public PgView setStatus(@PathVariable long pgId, @RequestBody Map<String, String> body) {
		return service.setStatus(pgId, body.getOrDefault("status", "DRAFT"));
	}
}
