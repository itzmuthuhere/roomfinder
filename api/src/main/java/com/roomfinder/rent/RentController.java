package com.roomfinder.rent;

import com.roomfinder.auth.AuthedUser;
import com.roomfinder.auth.JwtAuthFilter;
import com.roomfinder.rent.RentDtos.MarkPaidRequest;
import com.roomfinder.rent.RentDtos.RentPaymentView;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RentController {

	private static final String AUTHED_USER = JwtAuthFilter.REQUEST_ATTRIBUTE;

	private final RentService service;

	public RentController(RentService service) {
		this.service = service;
	}

	@GetMapping("/pgs/{pgId}/rent")
	public List<RentPaymentView> pendingForPg(@RequestAttribute(name = AUTHED_USER, required = false) AuthedUser user,
	                                          @PathVariable long pgId) {
		return service.pendingForPg(user, pgId);
	}

	@PatchMapping("/rent/{paymentId}/paid")
	public RentPaymentView markPaid(@RequestAttribute(name = AUTHED_USER, required = false) AuthedUser user,
	                                @PathVariable long paymentId, @RequestBody(required = false) MarkPaidRequest req) {
		MarkPaidRequest body = req != null ? req : new MarkPaidRequest(null, null);
		return service.markPaid(user, paymentId, body.paidOn(), body.amount());
	}

	@PostMapping("/rent/{paymentId}/remind")
	public Map<String, Object> remind(@RequestAttribute(name = AUTHED_USER, required = false) AuthedUser user,
	                                  @PathVariable long paymentId) {
		service.sendReminder(user, paymentId);
		return Map.of("sent", true);
	}
}
