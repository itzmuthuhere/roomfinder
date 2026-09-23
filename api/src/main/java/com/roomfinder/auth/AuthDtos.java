package com.roomfinder.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

class AuthDtos {

	record RequestOtpRequest(
			@NotBlank @Pattern(regexp = "[6-9]\\d{9}", message = "must be a 10-digit Indian mobile number") String phone,
			Role role) {}

	record VerifyOtpRequest(
			@NotBlank @Pattern(regexp = "[6-9]\\d{9}") String phone,
			@NotBlank @Pattern(regexp = "\\d{6}", message = "must be a 6-digit code") String code) {}

	record UserView(long id, String phone, String role) {
		static UserView of(AuthedUser u) {
			return new UserView(u.id(), u.phone(), u.role().name());
		}
	}

	record AuthResponse(String token, UserView user) {}
}
