package com.roomfinder.rent;

import com.roomfinder.auth.AuthedUser;
import com.roomfinder.common.ApiException;
import com.roomfinder.notify.WhatsAppSender;
import com.roomfinder.pg.PgRepository;
import com.roomfinder.rent.RentDtos.RentPaymentView;
import com.roomfinder.rent.RentRepository.RentPaymentRow;
import com.roomfinder.tenancy.TenancyDtos.TenancyView;
import com.roomfinder.tenancy.TenancyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class RentService {

	private static final Logger log = LoggerFactory.getLogger(RentService.class);
	private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMMM yyyy");

	private final RentRepository payments;
	private final TenancyService tenancyService;
	private final PgRepository pgs;
	private final WhatsAppSender whatsapp;

	public RentService(RentRepository payments, TenancyService tenancyService, PgRepository pgs, WhatsAppSender whatsapp) {
		this.payments = payments;
		this.tenancyService = tenancyService;
		this.pgs = pgs;
		this.whatsapp = whatsapp;
	}

	/**
	 * Runs on a schedule (see {@link RentDueJob}), never as a manual step: every open tenancy gets a
	 * PENDING row for the current month if it doesn't already have one. Safe to run more than once a
	 * month - {@code existsForMonth} makes this idempotent.
	 */
	@Transactional
	public int generateDueForCurrentMonth() {
		LocalDate forMonth = LocalDate.now().withDayOfMonth(1);
		int created = 0;
		for (TenancyView t : tenancyService.listAllOpenTenancies()) {
			if (!payments.existsForMonth(t.id(), forMonth)) {
				payments.insertPending(t.id(), forMonth, t.rentAmount());
				created++;
			}
		}
		return created;
	}

	public List<RentPaymentView> pendingForPg(AuthedUser user, long pgId) {
		AuthedUser authed = AuthedUser.require(user);
		long ownerId = pgs.ownerIdOf(pgId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PG_NOT_FOUND", "No such PG"));
		authed.requireOwnerOf(ownerId);
		return payments.listForPg(pgId).stream().map(this::toView).toList();
	}

	@Transactional
	public RentPaymentView markPaid(AuthedUser user, long paymentId, LocalDate paidOn, Integer amountOverride) {
		RentPaymentRow row = requireOwnsPayment(user, paymentId);
		LocalDate when = paidOn != null ? paidOn : LocalDate.now();
		int amount = amountOverride != null ? amountOverride : row.amount();
		String status = amount >= row.amount() ? "PAID" : "PARTIAL";
		payments.markPaid(paymentId, when, amount, status);
		return toView(payments.findById(paymentId).orElseThrow());
	}

	/** Best-effort, same principle as {@code booking.BookingService}'s reminders: never let a failed send undo anything. */
	public void sendReminder(AuthedUser user, long paymentId) {
		RentPaymentRow row = requireOwnsPayment(user, paymentId);
		try {
			String monthLabel = row.forMonth().format(MONTH_LABEL);
			String upiVpa = pgs.upiVpaOf(row.pgId()).orElse(null);
			String message = "Rent reminder: ₹" + row.amount() + " for " + monthLabel
					+ (upiVpa != null ? ". Pay here: " + upiLink(upiVpa, row.amount(), monthLabel) : ". Please pay the owner directly.");
			whatsapp.send(row.tenantPhone(), message);
		} catch (Exception e) {
			log.warn("failed to send rent reminder for payment {}", paymentId, e);
		}
	}

	private static String upiLink(String vpa, int amount, String note) {
		String encodedNote = urlEncode("Rent " + note);
		return "upi://pay?pa=" + urlEncode(vpa) + "&am=" + amount + "&cu=INR&tn=" + encodedNote;
	}

	private static String urlEncode(String s) {
		try {
			return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException("UTF-8 is a required JDK charset", e);
		}
	}

	private RentPaymentRow requireOwnsPayment(AuthedUser user, long paymentId) {
		AuthedUser authed = AuthedUser.require(user);
		RentPaymentRow row = payments.findById(paymentId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "No such payment"));
		long ownerId = pgs.ownerIdOf(row.pgId())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "No such payment"));
		authed.requireOwnerOf(ownerId);
		return row;
	}

	private RentPaymentView toView(RentPaymentRow row) {
		return new RentPaymentView(row.id(), row.tenancyId(), row.bedId(), row.tenantPhone(),
				row.forMonth(), row.amount(), row.paidOn(), row.status());
	}
}
