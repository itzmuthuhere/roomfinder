package com.roomfinder.tenancy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A tenancy on notice becomes bookable, then actually vacant, on its own - never a manual owner
 * step (CLAUDE.md's domain invariants). Runs once a day; a day's slack past the vacate date is an
 * acceptable trade for not needing anything more precise than a plain sweep over the tenancy table.
 */
@Component
class VacateJob {

	private static final Logger log = LoggerFactory.getLogger(VacateJob.class);

	private final TenancyService tenancies;

	VacateJob(TenancyService tenancies) {
		this.tenancies = tenancies;
	}

	@Scheduled(fixedDelay = 24 * 60 * 60 * 1000L, initialDelay = 60 * 1000L)
	void run() {
		try {
			int vacated = tenancies.processDueVacates();
			if (vacated > 0) {
				log.info("vacate sweep freed {} bed(s)", vacated);
			}
		} catch (Exception e) {
			log.error("vacate sweep failed", e);
		}
	}
}
