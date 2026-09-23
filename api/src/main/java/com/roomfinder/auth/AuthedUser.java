package com.roomfinder.auth;

import com.roomfinder.common.ApiException;
import org.springframework.http.HttpStatus;

/** The identity carried by a validated JWT, attached to the request by {@link JwtAuthFilter}. */
public record AuthedUser(long id, String phone, Role role) {

	/** Throws 401 if no token was presented (or it was invalid/expired). Never null on return. */
	public static AuthedUser require(AuthedUser user) {
		if (user == null) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Sign in required");
		}
		return user;
	}

	/** Throws 403 unless this user is an owner logged in as {@code ownerId}. Call {@link #require} first for a null user. */
	public void requireOwnerOf(long ownerId) {
		if (role != Role.OWNER) {
			throw new ApiException(HttpStatus.FORBIDDEN, "OWNER_ONLY", "Only a PG owner can do this");
		}
		if (id != ownerId) {
			throw new ApiException(HttpStatus.FORBIDDEN, "NOT_YOUR_PG", "This PG belongs to a different owner");
		}
	}
}
