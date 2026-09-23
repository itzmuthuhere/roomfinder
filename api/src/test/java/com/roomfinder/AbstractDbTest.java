package com.roomfinder;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Every test runs against its own throwaway Postgres + PostGIS container, never the dev database.
 *
 * The container is started once per JVM (singleton pattern) and the datasource properties are
 * set explicitly, because application.yml hardcodes a localhost URL that would otherwise win.
 */
@SpringBootTest
public abstract class AbstractDbTest {

	static final PostgreSQLContainer<?> POSTGRES;

	static {
		POSTGRES = new PostgreSQLContainer<>(
				DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))
				.withDatabaseName("roomfinder_test")
				.withUsername("test")
				.withPassword("test");
		POSTGRES.start();
	}

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.flyway.enabled", () -> true);
		registry.add("media.root", () -> System.getProperty("java.io.tmpdir") + "/roomfinder-test-media");
	}
}
