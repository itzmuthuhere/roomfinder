package com.roomfinder.tenancy;

import com.roomfinder.auth.AuthedUser;
import com.roomfinder.bed.BedRepository;
import com.roomfinder.bed.BedService;
import com.roomfinder.bed.BedState;
import com.roomfinder.common.ApiException;
import com.roomfinder.pg.PgRepository;
import com.roomfinder.tenancy.TenancyDtos.TenancyView;
import com.roomfinder.tenancy.TenancyRepository.TenancyRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class TenancyService {

	private static final Logger log = LoggerFactory.getLogger(TenancyService.class);
	private static final int DEFAULT_RENT_DUE_DAY = 5;

	private final TenancyRepository tenancies;
	private final BedService bedService;
	private final BedRepository beds;
	private final PgRepository pgs;

	public TenancyService(TenancyRepository tenancies, BedService bedService, BedRepository beds, PgRepository pgs) {
		this.tenancies = tenancies;
		this.bedService = bedService;
		this.beds = beds;
		this.pgs = pgs;
	}

	/**
	 * A move-in: the bed becomes OCCUPIED (through the same owner-gated {@code BedService.changeState}
	 * the bed grid uses - VACANT or BOOKED can both move to OCCUPIED) and a tenancy row starts tracking
	 * rent from here. The tenant is found by phone or registered as a SEEKER if this is their first
	 * time in the system - most real tenants never touched the app before moving in.
	 */
	@Transactional
	public TenancyView createTenancy(AuthedUser owner, long bedId, String tenantPhone, String tenantName,
	                                 LocalDate moveInDate, int rentAmount, Integer rentDueDay) {
		bedService.changeState(owner, bedId, BedState.OCCUPIED, "tenant moved in", null);

		long tenantId = tenancies.findOrCreateTenant(tenantPhone, tenantName);
		int dueDay = rentDueDay != null ? rentDueDay : DEFAULT_RENT_DUE_DAY;
		long id = tenancies.insert(bedId, tenantId, moveInDate, rentAmount, dueDay);
		beds.setCurrentTenant(bedId, tenantId);

		return toView(tenancies.findById(id).orElseThrow());
	}

	/**
	 * Gives notice: the bed moves to ON_NOTICE with {@code availableFrom} set to the vacate date in
	 * the very same call - this is what makes it bookable for that date automatically, never a
	 * second owner action, per CLAUDE.md's domain invariants.
	 */
	@Transactional
	public TenancyView giveNotice(AuthedUser owner, long tenancyId, LocalDate vacateDate) {
		TenancyRow row = requireOwnsTenancy(owner, tenancyId);
		if (!row.status().equals("ACTIVE")) {
			throw new ApiException(HttpStatus.CONFLICT, "TENANCY_NOT_ACTIVE",
					"This tenancy is " + row.status().toLowerCase() + ", not active");
		}

		bedService.changeState(owner, row.bedId(), BedState.ON_NOTICE, "tenant gave notice", vacateDate);
		tenancies.markNotice(tenancyId, LocalDate.now(), vacateDate);

		return toView(tenancies.findById(tenancyId).orElseThrow());
	}

	/** Every currently open (ACTIVE or ON_NOTICE) tenancy across all PGs - rent is due for both. */
	public List<TenancyView> listAllOpenTenancies() {
		return tenancies.allOpen().stream().map(this::toView).toList();
	}

	public List<TenancyView> listForPg(AuthedUser user, long pgId) {
		AuthedUser authed = AuthedUser.require(user);
		long ownerId = pgs.ownerIdOf(pgId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PG_NOT_FOUND", "No such PG"));
		authed.requireOwnerOf(ownerId);
		return tenancies.listForPg(pgId).stream().map(this::toView).toList();
	}

	/**
	 * Runs on a schedule (see {@link VacateJob}), never as a manual step: a notice period that has
	 * run its course ends the tenancy and frees the bed. Skips gracefully, same as
	 * {@code booking.BookingService.expireOverdueHolds}, if the bed already moved on some other way.
	 */
	@Transactional
	public int processDueVacates() {
		List<TenancyRow> due = tenancies.dueToVacate(LocalDate.now());
		int vacated = 0;
		for (TenancyRow row : due) {
			try {
				bedService.endNoticePeriod(row.bedId(), null, "notice period ended");
				beds.setCurrentTenant(row.bedId(), null);
				tenancies.end(row.id());
				vacated++;
			} catch (ApiException e) {
				log.warn("skipped vacating tenancy {} (bed {}): {}", row.id(), row.bedId(), e.getMessage());
			}
		}
		return vacated;
	}

	private TenancyRow requireOwnsTenancy(AuthedUser user, long tenancyId) {
		AuthedUser authed = AuthedUser.require(user);
		TenancyRow row = tenancies.findById(tenancyId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TENANCY_NOT_FOUND", "No such tenancy"));
		long pgId = beds.pgIdOf(row.bedId())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TENANCY_NOT_FOUND", "No such tenancy"));
		long ownerId = pgs.ownerIdOf(pgId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TENANCY_NOT_FOUND", "No such tenancy"));
		authed.requireOwnerOf(ownerId);
		return row;
	}

	private TenancyView toView(TenancyRow row) {
		return new TenancyView(row.id(), row.bedId(), row.tenantPhone(), row.tenantName(), row.moveInDate(),
				row.rentAmount(), row.rentDueDay(), row.noticeDate(), row.vacateDate(), row.status());
	}
}
