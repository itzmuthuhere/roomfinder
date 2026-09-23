package com.roomfinder.dev;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Temporary. Creates an owner without authentication so the owner screens can be used
 * before phone OTP login exists. Delete this class when auth ships.
 */
@RestController
@RequestMapping("/api/dev")
public class DevController {

	private final JdbcClient db;

	public DevController(JdbcClient db) {
		this.db = db;
	}

	@PostMapping("/owners")
	public Map<String, Object> createOwner(@RequestBody Map<String, String> body) {
		String phone = body.getOrDefault("phone", "9000000000");
		String name = body.getOrDefault("name", "Test Owner");
		Long id = db.sql("""
				insert into app_user (phone, name, role) values (:phone, :name, 'OWNER')
				on conflict (phone) do update set name = excluded.name
				returning id
				""")
				.param("phone", phone).param("name", name)
				.query(Long.class).single();
		return Map.of("id", id, "phone", phone, "name", name);
	}

	@GetMapping("/owners")
	public List<Map<String, Object>> owners() {
		return db.sql("select id, phone, name from app_user where role = 'OWNER' order by id")
				.query((rs, i) -> Map.<String, Object>of(
						"id", rs.getLong("id"), "phone", rs.getString("phone"), "name", rs.getString("name")))
				.list();
	}
}
