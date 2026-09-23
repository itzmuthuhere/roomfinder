package com.roomfinder.rent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Creates this month's PENDING rent row for every open tenancy - never a manual owner step. */
@Component
class RentDueJob {

	private static final Logger log = LoggerFactory.getLogger(RentDueJob.class);

	private final RentService rent;

	RentDueJob(RentService rent) {
		this.rent = rent;
	}

	@Scheduled(fixedDelay = 24 * 60 * 60 * 1000L, initialDelay = 60 * 1000L)
	void run() {
		try {
			int created = rent.generateDueForCurrentMonth();
			if (created > 0) {
				log.info("rent due sweep created {} pending payment(s)", created);
			}
		} catch (Exception e) {
			log.error("rent due sweep failed", e);
		}
	}
}
