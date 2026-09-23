package com.roomfinder.pg;

import com.roomfinder.auth.AuthedUser;
import com.roomfinder.auth.Role;
import com.roomfinder.bed.BedService;
import com.roomfinder.media.MediaService;
import com.roomfinder.common.ApiException;
import com.roomfinder.pg.PgDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PgService {

	private final PgRepository pgs;
	private final BedService bedService;
	private final MediaService mediaService;

	public PgService(PgRepository pgs, BedService bedService, MediaService mediaService) {
		this.pgs = pgs;
		this.bedService = bedService;
		this.mediaService = mediaService;
	}

	@Transactional
	public PgView create(AuthedUser user, CreatePgRequest req) {
		AuthedUser authed = AuthedUser.require(user);
		if (authed.role() != Role.OWNER) {
			throw new ApiException(HttpStatus.FORBIDDEN, "OWNER_ONLY", "Only a PG owner can do this");
		}
		long id = pgs.createPg(authed.id(), req);
		return pgs.findById(id).orElseThrow();
	}

	/** Creates the room and its beds in one go: a room of 3 sharing gets beds A, B, C. */
	@Transactional
	public PgDetail addRoom(AuthedUser user, long pgId, CreateRoomRequest req) {
		requireOwnsPg(user, pgId);
		long roomId = pgs.createRoom(pgId, req);
		for (int i = 0; i < req.bedCount(); i++) {
			pgs.createBed(roomId, String.valueOf((char) ('A' + i)));
		}
		return detail(pgId);
	}

	public List<PgView> listForOwner(AuthedUser user) {
		AuthedUser authed = AuthedUser.require(user);
		return pgs.findByOwner(authed.id());
	}

	public PgDetail detail(long pgId) {
		PgView pg = pgs.findById(pgId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PG_NOT_FOUND", "No such PG"));
		return new PgDetail(pg, bedService.gridFor(pgId), mediaService.listForPg(pgId));
	}

	@Transactional
	public PgView setStatus(AuthedUser user, long pgId, String status) {
		requireOwnsPg(user, pgId);
		if (!List.of("DRAFT", "LIVE", "PAUSED", "REMOVED").contains(status)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Unknown status " + status);
		}
		pgs.setStatus(pgId, status);
		return pgs.findById(pgId).orElseThrow();
	}

	@Transactional
	public PgView setUpiVpa(AuthedUser user, long pgId, String upiVpa) {
		requireOwnsPg(user, pgId);
		pgs.setUpiVpa(pgId, (upiVpa == null || upiVpa.isBlank()) ? null : upiVpa.trim());
		return pgs.findById(pgId).orElseThrow();
	}

	/** Shared by every write that touches an existing PG: 404 if it doesn't exist, 403 if it isn't the caller's. */
	private void requireOwnsPg(AuthedUser user, long pgId) {
		AuthedUser authed = AuthedUser.require(user);
		long ownerId = pgs.ownerIdOf(pgId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PG_NOT_FOUND", "No such PG"));
		authed.requireOwnerOf(ownerId);
	}
}
