package com.roomfinder.media;

import com.roomfinder.auth.AuthedUser;
import com.roomfinder.common.ApiException;
import com.roomfinder.media.MediaDtos.MediaView;
import com.roomfinder.pg.PgRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MediaService {

	private static final Logger log = LoggerFactory.getLogger(MediaService.class);

	static final int MAX_PHOTOS_PER_ROOM = 10;
	static final long MAX_VIDEO_BYTES = 50L * 1024 * 1024;
	static final double MAX_VIDEO_SECONDS = 60.0;
	private static final double VIDEO_DURATION_TOLERANCE_SECONDS = 1.0;

	private final MediaRepository media;
	private final PgRepository pgs;
	private final MediaStorage storage;

	public MediaService(MediaRepository media, PgRepository pgs, MediaStorage storage) {
		this.media = media;
		this.pgs = pgs;
		this.storage = storage;
	}

	public List<MediaView> listForPg(long pgId) {
		return media.listByPg(pgId).stream().map(this::toView).toList();
	}

	@Transactional
	public MediaView uploadPhoto(AuthedUser user, long pgId, long roomId, byte[] content) {
		requireOwnsPg(user, pgId);
		requireRoomInPg(pgId, roomId);

		MediaFileType type = sniffOrReject(content, MediaKind.PHOTO);

		int existing = media.countPhotosInRoom(roomId);
		if (existing >= MAX_PHOTOS_PER_ROOM) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PHOTO_LIMIT_REACHED",
					"This room already has " + MAX_PHOTOS_PER_ROOM + " photos. Delete one before adding another.");
		}

		boolean makeCover = !media.hasCoverPhoto(pgId);
		String key = storage.store(content, type.extension);
		long id = media.insert(pgId, roomId, key, MediaKind.PHOTO, existing, makeCover);
		return toView(media.findById(id).orElseThrow());
	}

	@Transactional
	public MediaView uploadVideo(AuthedUser user, long pgId, byte[] content) {
		requireOwnsPg(user, pgId);
		sniffOrReject(content, MediaKind.VIDEO);

		if (content.length > MAX_VIDEO_BYTES) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VIDEO_TOO_LARGE",
					"Video must be under " + (MAX_VIDEO_BYTES / (1024 * 1024)) + "MB.");
		}

		Mp4Processor.Result processed;
		try {
			processed = Mp4Processor.process(content);
		} catch (Mp4ParseException e) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_VIDEO_FORMAT",
					"Could not read this video file.");
		}
		if (processed.durationSeconds() > MAX_VIDEO_SECONDS + VIDEO_DURATION_TOLERANCE_SECONDS) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VIDEO_TOO_LONG",
					"Video must be " + (int) MAX_VIDEO_SECONDS + " seconds or under.");
		}

		var existingVideo = media.findVideoForPg(pgId);
		String key = storage.store(processed.bytes(), MediaFileType.MP4.extension);

		// Delete-then-insert: the unique index allows only one VIDEO row per PG, checked per statement.
		existingVideo.ifPresent(row -> {
			media.deleteById(row.id());
			deleteFileAfterCommit(row.storageKey());
		});
		long id = media.insert(pgId, null, key, MediaKind.VIDEO, 0, false);
		return toView(media.findById(id).orElseThrow());
	}

	@Transactional
	public MediaView setCover(AuthedUser user, long pgId, long mediaId) {
		requireOwnsPg(user, pgId);
		MediaRow row = requirePhotoInPg(pgId, mediaId);
		media.setCover(pgId, row.id());
		return toView(media.findById(row.id()).orElseThrow());
	}

	@Transactional
	public void reorder(AuthedUser user, long pgId, long roomId, List<Long> orderedIds) {
		requireOwnsPg(user, pgId);
		requireRoomInPg(pgId, roomId);
		List<MediaRow> current = media.listByPg(pgId).stream()
				.filter(r -> r.kind() == MediaKind.PHOTO && r.roomId() != null && r.roomId() == roomId)
				.toList();
		Set<Long> currentIds = current.stream().map(MediaRow::id).collect(Collectors.toSet());
		if (currentIds.size() != orderedIds.size() || !currentIds.containsAll(orderedIds)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "REORDER_MISMATCH",
					"The photo list changed - reload and try again.");
		}
		for (int i = 0; i < orderedIds.size(); i++) {
			media.updateSortOrder(orderedIds.get(i), i);
		}
	}

	@Transactional
	public void delete(AuthedUser user, long mediaId) {
		MediaRow row = media.findById(mediaId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "No such photo or video"));
		requireOwnsPg(user, row.pgId());

		media.deleteById(mediaId);

		if (row.kind() == MediaKind.PHOTO && row.isCover()) {
			media.nextCoverCandidate(row.pgId()).ifPresent(nextId -> media.setCover(row.pgId(), nextId));
		}

		deleteFileAfterCommit(row.storageKey());
	}

	private MediaFileType sniffOrReject(byte[] content, MediaKind expectedKind) {
		MediaFileType type = com.roomfinder.media.ContentSniffer.sniff(content)
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_MEDIA_TYPE",
						"Only jpeg, png, webp photos and mp4 video are accepted."));
		if (type.kind != expectedKind) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_MEDIA_TYPE",
					"That file looks like a " + type.kind.name().toLowerCase() + ", not a " + expectedKind.name().toLowerCase() + ".");
		}
		return type;
	}

	private void requireOwnsPg(AuthedUser user, long pgId) {
		AuthedUser authed = AuthedUser.require(user);
		long ownerId = pgs.ownerIdOf(pgId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PG_NOT_FOUND", "No such PG"));
		authed.requireOwnerOf(ownerId);
	}

	private void requireRoomInPg(long pgId, long roomId) {
		Long ownerPgId = media.roomPgId(roomId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "No such room"));
		if (ownerPgId != pgId) {
			throw new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "No such room");
		}
	}

	private MediaRow requirePhotoInPg(long pgId, long mediaId) {
		MediaRow row = media.findById(mediaId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "No such photo"));
		if (row.pgId() != pgId || row.kind() != MediaKind.PHOTO) {
			throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "No such photo");
		}
		return row;
	}

	private MediaView toView(MediaRow row) {
		return new MediaView(row.id(), row.pgId(), row.roomId(), storage.publicUrl(row.storageKey()),
				row.kind().name(), row.sortOrder(), row.isCover());
	}

	/**
	 * A filesystem delete can't be rolled back, so it must never run inside the transaction that
	 * might still fail. We queue it for right after commit and only log if it fails - the DB row
	 * is already gone either way, so a stray file is a harmless cleanup item, not a correctness bug.
	 */
	private void deleteFileAfterCommit(String storageKey) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				try {
					storage.delete(storageKey);
				} catch (Exception e) {
					log.warn("failed to delete media file {}", storageKey, e);
				}
			}
		});
	}
}
