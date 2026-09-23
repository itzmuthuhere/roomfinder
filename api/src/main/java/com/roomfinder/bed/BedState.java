package com.roomfinder.bed;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The single source of truth for what a bed can do next.
 * Any transition not listed here is rejected. See docs/schema/bed-state-machine.md.
 */
public enum BedState {
	VACANT, HELD, BOOKED, OCCUPIED, ON_NOTICE, BLOCKED;

	private static final Map<BedState, Set<BedState>> ALLOWED = Map.of(
			VACANT, EnumSet.of(HELD, BLOCKED, OCCUPIED),
			HELD, EnumSet.of(VACANT, BOOKED),
			BOOKED, EnumSet.of(OCCUPIED, VACANT),
			OCCUPIED, EnumSet.of(ON_NOTICE, VACANT),
			ON_NOTICE, EnumSet.of(VACANT, OCCUPIED),
			BLOCKED, EnumSet.of(VACANT)
	);

	public boolean canMoveTo(BedState next) {
		return ALLOWED.getOrDefault(this, EnumSet.noneOf(BedState.class)).contains(next);
	}
}
