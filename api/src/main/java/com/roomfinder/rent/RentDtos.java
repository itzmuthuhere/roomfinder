package com.roomfinder.rent;

import java.time.LocalDate;

public class RentDtos {

	public record MarkPaidRequest(LocalDate paidOn, Integer amount) {}

	public record RentPaymentView(
			long id,
			long tenancyId,
			long bedId,
			String tenantPhone,
			LocalDate forMonth,
			int amount,
			LocalDate paidOn,
			String status) {}
}
