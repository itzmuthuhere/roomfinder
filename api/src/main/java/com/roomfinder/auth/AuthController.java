package com.roomfinder.auth;

import com.roomfinder.auth.AuthDtos.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final OtpService otp;

	public AuthController(OtpService otp) {
		this.otp = otp;
	}

	@PostMapping("/otp/request")
	public Map<String, Object> requestOtp(@Valid @RequestBody RequestOtpRequest req) {
		otp.requestOtp(req.phone(), req.role());
		return Map.of("sent", true, "expiresInSeconds", OtpService.OTP_TTL.toSeconds());
	}

	@PostMapping("/otp/verify")
	public AuthResponse verifyOtp(@Valid @RequestBody VerifyOtpRequest req) {
		OtpService.AuthResult result = otp.verifyOtp(req.phone(), req.code());
		return new AuthResponse(result.token(), UserView.of(result.user()));
	}

	@GetMapping("/me")
	public UserView me(@RequestAttribute(name = JwtAuthFilter.REQUEST_ATTRIBUTE, required = false) AuthedUser user) {
		return UserView.of(AuthedUser.require(user));
	}
}
