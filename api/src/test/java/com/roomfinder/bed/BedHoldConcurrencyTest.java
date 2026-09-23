package com.roomfinder.bed;

import com.roomfinder.AbstractDbTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DO NOT DELETE OR WEAKEN THIS TEST.
 *
 * Two seekers must never hold the same bed. 50 threads race for one bed;
 * exactly one must win and the audit log must contain exactly one row.
 */
class BedHoldConcurrencyTest extends AbstractDbTest {

	private static final int THREADS = 50;

	@Autowired JdbcClient db;
	@Autowired BedService bedService;

	long bedId;

	@BeforeEach
	void seed() {
		db.sql("delete from bed_state_log").update();
		db.sql("delete from bed").update();
		db.sql("delete from room").update();
		db.sql("delete from pg_property").update();
		db.sql("delete from app_user").update();

		long ownerId = db.sql("insert into app_user (phone, name, role) values ('9000000002','O','OWNER') returning id")
				.query(Long.class).single();
		long pgId = db.sql("""
				insert into pg_property (owner_id, name, gender_type, address, area, location, status)
				values (:o, 'Race PG', 'BOYS', 'Addr', 'Sholinganallur',
				        ST_SetSRID(ST_MakePoint(80.2279, 12.9010), 4326)::geography, 'LIVE')
				returning id""").param("o", ownerId).query(Long.class).single();
		long roomId = db.sql("""
				insert into room (pg_id, room_no, sharing_type, ac, attached_bath, rent_per_bed, deposit_amount)
				values (:p, '1', 'SINGLE', false, false, 7000, 14000) returning id""")
				.param("p", pgId).query(Long.class).single();
		bedId = db.sql("insert into bed (room_id, bed_label) values (:r, 'A') returning id")
				.param("r", roomId).query(Long.class).single();
	}

	@Test
	void exactly_one_of_fifty_concurrent_holds_wins() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(THREADS);
		CountDownLatch startGun = new CountDownLatch(1);
		AtomicInteger winners = new AtomicInteger();
		AtomicInteger losers = new AtomicInteger();

		List<Future<?>> futures = new java.util.ArrayList<>();
		for (int i = 0; i < THREADS; i++) {
			futures.add(pool.submit(() -> {
				startGun.await();
				try {
					bedService.changeState(bedId, BedState.HELD, "race", null, null);
					winners.incrementAndGet();
				} catch (RuntimeException expected) {
					losers.incrementAndGet();
				}
				return null;
			}));
		}

		startGun.countDown();
		for (Future<?> f : futures) {
			f.get(30, TimeUnit.SECONDS);
		}
		pool.shutdown();

		assertThat(winners.get()).as("exactly one seeker may hold a bed").isEqualTo(1);
		assertThat(losers.get()).isEqualTo(THREADS - 1);

		String state = db.sql("select state from bed where id = :id").param("id", bedId)
				.query(String.class).single();
		assertThat(state).isEqualTo("HELD");

		Integer logRows = db.sql("select count(*) from bed_state_log where bed_id = :id and to_state = 'HELD'")
				.param("id", bedId).query(Integer.class).single();
		assertThat(logRows).as("one winner means one audit row").isEqualTo(1);
	}
}
