package com.roomfinder.pg;

import jakarta.validation.constraints.*;

import java.util.List;

public class PgDtos {

	public record CreatePgRequest(
			@NotBlank String name,
			@NotBlank String genderType,      // BOYS / GIRLS / UNISEX / WORKING_WOMEN
			@NotBlank String address,
			@NotBlank String area,
			@NotNull Double lat,
			@NotNull Double lng,
			Boolean foodIncluded,
			String foodType,                  // VEG / NONVEG / BOTH
			Integer noticePeriodDays) {}

	public record CreateRoomRequest(
			String roomNo,
			@NotBlank String sharingType,     // SINGLE / DOUBLE / TRIPLE / FOUR_PLUS
			boolean ac,
			boolean attachedBath,
			@Positive int rentPerBed,
			@PositiveOrZero int depositAmount,
			@Min(1) @Max(20) int bedCount) {}

	public record PgView(
			long id,
			String name,
			String genderType,
			String area,
			String address,
			String status,
			boolean instantBook,
			int totalBeds,
			int vacantBeds) {}

	public record PgDetail(PgView pg,
	                       List<com.roomfinder.bed.BedDtos.BedView> beds,
	                       List<com.roomfinder.media.MediaDtos.MediaView> media) {}
}
