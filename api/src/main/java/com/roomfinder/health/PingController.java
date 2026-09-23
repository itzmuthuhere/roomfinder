package com.roomfinder.health;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Proves the whole stack end to end: browser -> Vite proxy -> Spring Boot -> Postgres.
 * Delete once real endpoints exist.
 */
@RestController
@RequestMapping("/api")
public class PingController {

	private final JdbcTemplate jdbc;

	public PingController(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@GetMapping("/ping")
	public Map<String, Object> ping() {
		Integer beds = jdbc.queryForObject("select count(*) from bed", Integer.class);
		Integer pgs = jdbc.queryForObject("select count(*) from pg_property", Integer.class);
		return Map.of("ok", true, "beds", beds == null ? 0 : beds, "pgs", pgs == null ? 0 : pgs);
	}
}
