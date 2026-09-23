package com.roomfinder.booking;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public class BookingDtos {

	public record RequestVisitRequest(@NotNull Long bedId, @NotNull OffsetDateTime visitSlot) {}

	public record BookingView(
			long id,
			long bedId,
			String kind,
			String status,
			OffsetDateTime visitSlot,
			OffsetDateTime holdExpiresAt,
			int tokenAmount,
			OffsetDateTime createdAt) {}

	/** The owner-facing view of a pending request - includes the seeker's phone, unlike {@link BookingView}. */
	public record OwnerBookingView(
			long id,
			long bedId,
			String seekerPhone,
			String status,
			OffsetDateTime visitSlot,
			OffsetDateTime holdExpiresAt) {}
}
