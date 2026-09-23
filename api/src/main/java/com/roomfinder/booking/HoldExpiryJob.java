package com.roomfinder.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Holds expire automatically - never a manual step. Runs every minute; a minute of extra hold time
 * past expiry is an acceptable trade for not needing anything more precise than a fixed-delay
 * {@code @Scheduled} sweep over the {@code booking} table (no queue, no broker - Postgres is the
 * only moving part, per CLAUDE.md).
 */
@Component
class HoldExpiryJob {

	private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);

	private final BookingService bookings;

	HoldExpiryJob(BookingService bookings) {
		this.bookings = bookings;
	}

	@Scheduled(fixedDelay = 60_000)
	void run() {
		try {
			int released = bookings.expireOverdueHolds();
			if (released > 0) {
				log.info("hold expiry sweep released {} bed(s)", released);
			}
		} catch (Exception e) {
			// A failed sweep must not crash the scheduler thread - it just tries again next minute.
			log.error("hold expiry sweep failed", e);
		}
	}
}
