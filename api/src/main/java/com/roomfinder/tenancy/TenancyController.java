package com.roomfinder.tenancy;

import com.roomfinder.auth.AuthedUser;
import com.roomfinder.auth.JwtAuthFilter;
import com.roomfinder.tenancy.TenancyDtos.CreateTenancyRequest;
import com.roomfinder.tenancy.TenancyDtos.NoticeRequest;
import com.roomfinder.tenancy.TenancyDtos.TenancyView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TenancyController {

	private static final String AUTHED_USER = JwtAuthFilter.REQUEST_ATTRIBUTE;

	private final TenancyService service;

	public TenancyController(TenancyService service) {
		this.service = service;
	}

	@PostMapping("/tenancies")
	public TenancyView create(@RequestAttribute(name = AUTHED_USER, required = false) AuthedUser user,
	                          @Valid @RequestBody CreateTenancyRequest req) {
		return service.createTenancy(user, req.bedId(), req.tenantPhone(), req.tenantName(),
				req.moveInDate(), req.rentAmount(), req.rentDueDay());
	}

	@PatchMapping("/tenancies/{tenancyId}/notice")
	public TenancyView giveNotice(@RequestAttribute(name = AUTHED_USER, required = false) AuthedUser user,
	                              @PathVariable long tenancyId, @Valid @RequestBody NoticeRequest req) {
		return service.giveNotice(user, tenancyId, req.vacateDate());
	}

	@GetMapping("/pgs/{pgId}/tenancies")
	public List<TenancyView> listForPg(@RequestAttribute(name = AUTHED_USER, required = false) AuthedUser user,
	                                   @PathVariable long pgId) {
		return service.listForPg(user, pgId);
	}
}
