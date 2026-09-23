package com.roomfinder.booking;

import com.roomfinder.auth.AuthedUser;
import com.roomfinder.auth.JwtAuthFilter;
import com.roomfinder.booking.BookingDtos.BookingView;
import com.roomfinder.booking.BookingDtos.RequestVisitRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

	private static final String AUTHED_USER = JwtAuthFilter.REQUEST_ATTRIBUTE;

	private final BookingService service;

	public BookingController(BookingService service) {
		this.service = service;
	}

	@PostMapping("/visit")
	public BookingView requestVisit(@RequestAttribute(name = AUTHED_USER, required = false) AuthedUser user,
	                                @Valid @RequestBody RequestVisitRequest req) {
		return service.requestVisit(user, req.bedId(), req.visitSlot());
	}

	@GetMapping("/mine")
	public List<BookingView> mine(@RequestAttribute(name = AUTHED_USER, required = false) AuthedUser user) {
		return service.myBookings(user);
	}

	@GetMapping("/pg/{pgId}")
	public List<BookingDtos.OwnerBookingView> pendingForPg(
			@RequestAttribute(name = AUTHED_USER, required = false) AuthedUser user, @PathVariable long pgId) {
		return service.pendingForPg(user, pgId);
	}

	@PatchMapping("/{bookingId}/accept")
	public BookingView accept(@RequestAttribute(name = AUTHED_USER, required = false) AuthedUser user,
	                          @PathVariable long bookingId) {
		return service.accept(user, bookingId);
	}

	@PatchMapping("/{bookingId}/decline")
	public BookingView decline(@RequestAttribute(name = AUTHED_USER, required = false) AuthedUser user,
	                           @PathVariable long bookingId) {
		return service.decline(user, bookingId);
	}

	@DeleteMapping("/{bookingId}")
	public Map<String, Object> cancel(@RequestAttribute(name = AUTHED_USER, required = false) AuthedUser user,
	                                  @PathVariable long bookingId) {
		service.cancelBySeeker(user, bookingId);
		return Map.of("cancelled", bookingId);
	}
}
