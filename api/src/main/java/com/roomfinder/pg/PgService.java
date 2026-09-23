package com.roomfinder.pg;

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
	public PgView create(long ownerId, CreatePgRequest req) {
		long id = pgs.createPg(ownerId, req);
		return pgs.findById(id).orElseThrow();
	}

	/** Creates the room and its beds in one go: a room of 3 sharing gets beds A, B, C. */
	@Transactional
	public PgDetail addRoom(long pgId, CreateRoomRequest req) {
		pgs.findById(pgId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PG_NOT_FOUND", "No such PG"));
		long roomId = pgs.createRoom(pgId, req);
		for (int i = 0; i < req.bedCount(); i++) {
			pgs.createBed(roomId, String.valueOf((char) ('A' + i)));
		}
		return detail(pgId);
	}

	public List<PgView> listForOwner(long ownerId) {
		return pgs.findByOwner(ownerId);
	}

	public PgDetail detail(long pgId) {
		PgView pg = pgs.findById(pgId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PG_NOT_FOUND", "No such PG"));
		return new PgDetail(pg, bedService.gridFor(pgId), mediaService.listForPg(pgId));
	}

	@Transactional
	public PgView setStatus(long pgId, String status) {
		if (!List.of("DRAFT", "LIVE", "PAUSED", "REMOVED").contains(status)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Unknown status " + status);
		}
		pgs.setStatus(pgId, status);
		return pgs.findById(pgId).orElseThrow();
	}
}
