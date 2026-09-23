package com.roomfinder.bed;

import com.roomfinder.auth.AuthedUser;
import com.roomfinder.common.ApiException;
import com.roomfinder.pg.PgRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class BedService {

	private final BedRepository beds;
	private final PgRepository pgs;

	public BedService(BedRepository beds, PgRepository pgs) {
		this.beds = beds;
		this.pgs = pgs;
	}

	public List<BedDtos.BedView> gridFor(long pgId) {
		return beds.findByPg(pgId);
	}

	public List<BedDtos.StateLogView> history(long bedId) {
		return beds.history(bedId);
	}

	/**
	 * Moves a bed to a new state. Rejects transitions the state machine does not allow,
	 * and loses gracefully if someone else changed the bed first.
	 */
	@Transactional
	public BedState changeState(AuthedUser user, long bedId, BedState next, String reason, LocalDate availableFrom) {
		AuthedUser authed = AuthedUser.require(user);
		long pgId = beds.pgIdOf(bedId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BED_NOT_FOUND", "No such bed"));
		long ownerId = pgs.ownerIdOf(pgId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BED_NOT_FOUND", "No such bed"));
		authed.requireOwnerOf(ownerId);

		BedState current = beds.currentState(bedId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BED_NOT_FOUND", "No such bed"));

		// No same-state shortcut on purpose: if a bed is already HELD, a second seeker
		// asking to hold it must be told no, not quietly told yes. See BedHoldConcurrencyTest.
		if (!current.canMoveTo(next)) {
			throw new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION",
					current == next
							? "This bed is already " + current
							: "A bed cannot go from " + current + " to " + next);
		}
		if (next == BedState.ON_NOTICE && availableFrom == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "AVAILABLE_FROM_REQUIRED",
					"Set the date the bed becomes free");
		}

		LocalDate from = next == BedState.ON_NOTICE ? availableFrom : null;
		boolean moved = beds.compareAndSetState(bedId, current, next, from);
		if (!moved) {
			throw new ApiException(HttpStatus.CONFLICT, "BED_STATE_CHANGED",
					"Someone else just changed this bed. Refresh and try again.");
		}
		beds.log(bedId, current, next, authed.id(), reason);
		return next;
	}
}
