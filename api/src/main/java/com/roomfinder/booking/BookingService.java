package com.roomfinder.booking;

import com.roomfinder.auth.AuthedUser;
import com.roomfinder.auth.Role;
import com.roomfinder.bed.BedRepository;
import com.roomfinder.bed.BedService;
import com.roomfinder.booking.BookingDtos.BookingView;
import com.roomfinder.booking.BookingRepository.BookingRow;
import com.roomfinder.common.ApiException;
import com.roomfinder.notify.WhatsAppSender;
import com.roomfinder.pg.PgRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

@Service
public class BookingService {

	private static final Logger log = LoggerFactory.getLogger(BookingService.class);

	static final ZoneId IST = ZoneId.of("Asia/Kolkata");
	static final Duration SAME_DAY_HOLD = Duration.ofHours(2);
	static final Duration FUTURE_VISIT_HOLD = Duration.ofHours(24);
	private static final Set<String> OPEN_STATUSES = Set.of("REQUESTED", "CONFIRMED");

	private final BookingRepository bookings;
	private final BedService bedService;
	private final BedRepository beds;
	private final PgRepository pgs;
	private final WhatsAppSender whatsapp;

	public BookingService(BookingRepository bookings, BedService bedService, BedRepository beds,
	                      PgRepository pgs, WhatsAppSender whatsapp) {
		this.bookings = bookings;
		this.bedService = bedService;
		this.beds = beds;
		this.pgs = pgs;
		this.whatsapp = whatsapp;
	}

	/**
	 * A seeker holds a bed for a visit. The hold is the same conditional VACANT-only update every
	 * other bed transition uses, so two seekers racing for one bed is exactly as safe here as it is
	 * from the owner's bed grid.
	 */
	@Transactional
	public BookingView requestVisit(AuthedUser user, long bedId, OffsetDateTime visitSlot) {
		AuthedUser seeker = AuthedUser.require(user);
		if (seeker.role() != Role.SEEKER) {
			throw new ApiException(HttpStatus.FORBIDDEN, "SEEKER_ONLY", "Only a seeker can book a visit");
		}
		if (visitSlot == null || !visitSlot.isAfter(OffsetDateTime.now())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VISIT_SLOT_IN_PAST", "Pick a visit time in the future");
		}

		bedService.holdForVisit(bedId, seeker.id());

		OffsetDateTime now = OffsetDateTime.now();
		Duration holdFor = isSameDay(visitSlot, now) ? SAME_DAY_HOLD : FUTURE_VISIT_HOLD;
		long id = bookings.insertVisit(bedId, seeker.id(), visitSlot, now.plus(holdFor));
		BookingView view = toView(bookings.findById(id).orElseThrow());

		notifyOwner(bedId, seeker, view);
		return view;
	}

	public List<BookingView> myBookings(AuthedUser user) {
		AuthedUser seeker = AuthedUser.require(user);
		return bookings.listForSeeker(seeker.id()).stream().map(this::toView).toList();
	}

	/** The owner's pending-action list: every open visit request against any bed in one PG. */
	public List<BookingDtos.OwnerBookingView> pendingForPg(AuthedUser user, long pgId) {
		AuthedUser authed = AuthedUser.require(user);
		long ownerId = pgs.ownerIdOf(pgId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PG_NOT_FOUND", "No such PG"));
		authed.requireOwnerOf(ownerId);

		return bookings.listOpenForPg(pgId).stream()
				.map(row -> new BookingDtos.OwnerBookingView(
						row.id(), row.bedId(), bookings.phoneOf(row.seekerId()).orElse("unknown"),
						row.status(), row.visitSlot(), row.holdExpiresAt()))
				.toList();
	}

	@Transactional
	public BookingView accept(AuthedUser user, long bookingId) {
		BookingRow row = requireOwnsBooking(user, bookingId);
		requireStatus(row, "REQUESTED");
		bookings.updateStatus(bookingId, "CONFIRMED");
		BookingView view = toView(bookings.findById(bookingId).orElseThrow());
		notifySeeker(row.seekerId(), view, "confirmed");
		return view;
	}

	@Transactional
	public BookingView decline(AuthedUser user, long bookingId) {
		BookingRow row = requireOwnsBooking(user, bookingId);
		requireOpen(row);
		bedService.releaseHold(row.bedId(), user.id(), "owner declined visit");
		bookings.updateStatus(bookingId, "CANCELLED");
		BookingView view = toView(bookings.findById(bookingId).orElseThrow());
		notifySeeker(row.seekerId(), view, "declined");
		return view;
	}

	@Transactional
	public void cancelBySeeker(AuthedUser user, long bookingId) {
		AuthedUser seeker = AuthedUser.require(user);
		BookingRow row = bookings.findById(bookingId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "No such booking"));
		if (row.seekerId() != seeker.id()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "NOT_YOUR_BOOKING", "This booking belongs to someone else");
		}
		requireOpen(row);
		bedService.releaseHold(row.bedId(), seeker.id(), "seeker cancelled visit");
		bookings.updateStatus(bookingId, "CANCELLED");
	}

	/**
	 * Runs on a schedule (see {@link HoldExpiryJob}), never as a manual step. A booking whose hold
	 * expired without the owner or seeker resolving it releases the bed back to VACANT. If the bed
	 * already moved on some other way between the query and this call (e.g. the owner used the bed
	 * grid directly), releasing it is no longer a valid transition - that is not this job's problem,
	 * so that one row is skipped and the sweep continues.
	 */
	@Transactional
	public int expireOverdueHolds() {
		List<BookingRow> overdue = bookings.expiredPending(OffsetDateTime.now());
		int released = 0;
		for (BookingRow row : overdue) {
			try {
				bedService.releaseHold(row.bedId(), null, "hold expired");
				bookings.updateStatus(row.id(), "EXPIRED");
				released++;
			} catch (ApiException e) {
				log.warn("skipped expiring booking {} (bed {}): {}", row.id(), row.bedId(), e.getMessage());
			}
		}
		return released;
	}

	private BookingRow requireOwnsBooking(AuthedUser user, long bookingId) {
		AuthedUser authed = AuthedUser.require(user);
		BookingRow row = bookings.findById(bookingId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "No such booking"));
		long pgId = beds.pgIdOf(row.bedId())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "No such booking"));
		long ownerId = pgs.ownerIdOf(pgId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "No such booking"));
		authed.requireOwnerOf(ownerId);
		return row;
	}

	private void requireStatus(BookingRow row, String expected) {
		if (!row.status().equals(expected)) {
			throw new ApiException(HttpStatus.CONFLICT, "BOOKING_NOT_" + expected,
					"This booking is " + row.status().toLowerCase() + ", not " + expected.toLowerCase());
		}
	}

	private void requireOpen(BookingRow row) {
		if (!OPEN_STATUSES.contains(row.status())) {
			throw new ApiException(HttpStatus.CONFLICT, "BOOKING_NOT_OPEN",
					"This booking is already " + row.status().toLowerCase());
		}
	}

	private static boolean isSameDay(OffsetDateTime visitSlot, OffsetDateTime now) {
		return visitSlot.atZoneSameInstant(IST).toLocalDate().equals(now.atZoneSameInstant(IST).toLocalDate());
	}

	/**
	 * Best-effort: a WhatsApp send failure must never undo an otherwise-successful booking, so it is
	 * caught and logged here rather than left to propagate and roll back the transaction - unlike
	 * OTP delivery, where the message *is* the point of the request, this is a side notification.
	 */
	private void notifyOwner(long bedId, AuthedUser seeker, BookingView booking) {
		try {
			beds.pgIdOf(bedId).flatMap(pgs::ownerIdOf).flatMap(bookings::phoneOf).ifPresent(ownerPhone ->
					whatsapp.send(ownerPhone, "New visit request for bed #" + booking.bedId()
							+ " at " + booking.visitSlot() + " from " + seeker.phone() + ". Reply in the app to confirm."));
		} catch (Exception e) {
			log.warn("failed to notify owner of booking {}", booking.id(), e);
		}
	}

	private void notifySeeker(long seekerId, BookingView booking, String decision) {
		try {
			bookings.phoneOf(seekerId).ifPresent(seekerPhone ->
					whatsapp.send(seekerPhone, "Your visit request for bed #" + booking.bedId() + " was " + decision + "."));
		} catch (Exception e) {
			log.warn("failed to notify seeker of booking {}", booking.id(), e);
		}
	}

	private BookingView toView(BookingRow row) {
		return new BookingView(row.id(), row.bedId(), row.kind(), row.status(), row.visitSlot(),
				row.holdExpiresAt(), row.tokenAmount(), row.createdAt());
	}
}
