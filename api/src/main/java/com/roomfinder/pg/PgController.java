package com.roomfinder.pg;

import com.roomfinder.auth.AuthedUser;
import com.roomfinder.auth.JwtAuthFilter;
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

	@PostMapping
	public PgView create(@RequestAttribute(name = JwtAuthFilter.REQUEST_ATTRIBUTE, required = false) AuthedUser user,
	                     @Valid @RequestBody CreatePgRequest req) {
		return service.create(user, req);
	}

	@GetMapping
	public List<PgView> mine(@RequestAttribute(name = JwtAuthFilter.REQUEST_ATTRIBUTE, required = false) AuthedUser user) {
		return service.listForOwner(user);
	}

	@GetMapping("/{pgId}")
	public PgDetail detail(@PathVariable long pgId) {
		return service.detail(pgId);
	}

	@PostMapping("/{pgId}/rooms")
	public PgDetail addRoom(@RequestAttribute(name = JwtAuthFilter.REQUEST_ATTRIBUTE, required = false) AuthedUser user,
	                        @PathVariable long pgId, @Valid @RequestBody CreateRoomRequest req) {
		return service.addRoom(user, pgId, req);
	}

	@PatchMapping("/{pgId}/status")
	public PgView setStatus(@RequestAttribute(name = JwtAuthFilter.REQUEST_ATTRIBUTE, required = false) AuthedUser user,
	                        @PathVariable long pgId, @RequestBody Map<String, String> body) {
		return service.setStatus(user, pgId, body.getOrDefault("status", "DRAFT"));
	}
}
