package com.roomfinder.bed;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public class BedDtos {

	public record BedView(
			long id,
			long roomId,
			String roomNo,
			String sharingType,
			boolean ac,
			int rentPerBed,
			int depositAmount,
			String bedLabel,
			BedState state,
			LocalDate availableFrom,
			OffsetDateTime stateChangedAt) {}

	public record ChangeStateRequest(
			@NotNull BedState toState,
			String reason,
			LocalDate availableFrom) {}

	public record StateLogView(
			long id,
			String fromState,
			String toState,
			String reason,
			OffsetDateTime createdAt) {}
}
