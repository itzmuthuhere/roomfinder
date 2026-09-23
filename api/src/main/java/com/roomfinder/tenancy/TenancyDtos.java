package com.roomfinder.tenancy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public class TenancyDtos {

	public record CreateTenancyRequest(
			@NotNull Long bedId,
			@NotBlank @Pattern(regexp = "[6-9]\\d{9}") String tenantPhone,
			String tenantName,
			@NotNull LocalDate moveInDate,
			@Positive int rentAmount,
			Integer rentDueDay) {}

	public record NoticeRequest(@NotNull LocalDate vacateDate) {}

	public record TenancyView(
			long id,
			long bedId,
			String tenantPhone,
			String tenantName,
			LocalDate moveInDate,
			int rentAmount,
			int rentDueDay,
			LocalDate noticeDate,
			LocalDate vacateDate,
			String status) {}
}
