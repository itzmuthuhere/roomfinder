package com.roomfinder.auth;

/** Mirrors the {@code role} check constraint on {@code app_user}. ADMIN has no login path yet. */
public enum Role {
	OWNER, SEEKER, ADMIN
}
