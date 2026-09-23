package com.roomfinder;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

class ApiApplicationTests extends AbstractDbTest {

	@Autowired JdbcClient db;

	@Test
	void context_loads_and_migrations_applied() {
		Integer tables = db.sql("""
				select count(*) from information_schema.tables
				 where table_schema = 'public' and table_type = 'BASE TABLE'
				""").query(Integer.class).single();
		assertThat(tables).isGreaterThanOrEqualTo(11);
	}
}
