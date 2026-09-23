package com.roomfinder.auth;

import com.roomfinder.AbstractDbTest;
import com.roomfinder.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtpServiceTest extends AbstractDbTest {

	@Autowired JdbcClient db;

	static final String PHONE = "9123456780";

	OtpService otp;
	RecordingOtpSender sender;

	/**
	 * Built by hand rather than autowired, the same way {@code Mp4ProcessorTest} avoids Spring for
	 * pure logic - this only needs a real {@link JdbcClient} for {@link AuthRepository}, and a
	 * fake {@link OtpSender} to capture the code that would have gone out as an SMS.
	 */
	@BeforeEach
	void seed() {
		db.sql("delete from otp_request").update();
		db.sql("delete from app_user").update();

		sender = new RecordingOtpSender();
		otp = new OtpService(new AuthRepository(db), sender, new JwtService("test-secret-at-least-32-bytes-long"));
	}

	static class RecordingOtpSender implements OtpSender {
		String lastCode;

		@Override
		public void send(String phone, String code) {
			this.lastCode = code;
		}
	}

	@Test
	void a_new_number_signs_up_with_the_role_it_requested() {
		otp.requestOtp(PHONE, Role.OWNER);
		OtpService.AuthResult result = otp.verifyOtp(PHONE, sender.lastCode);

		assertThat(result.user().phone()).isEqualTo(PHONE);
		assertThat(result.user().role()).isEqualTo(Role.OWNER);
		assertThat(result.token()).isNotBlank();
	}

	@Test
	void an_existing_users_role_cannot_be_changed_by_requesting_otp_with_a_different_role() {
		otp.requestOtp(PHONE, Role.OWNER);
		otp.verifyOtp(PHONE, sender.lastCode);

		// Same phone logs in again, this time claiming to be a SEEKER.
		otp.requestOtp(PHONE, Role.SEEKER);
		OtpService.AuthResult result = otp.verifyOtp(PHONE, sender.lastCode);

		assertThat(result.user().role()).as("role is fixed at signup, not by a later login request").isEqualTo(Role.OWNER);
	}

	@Test
	void a_new_number_without_a_role_is_rejected() {
		assertThatThrownBy(() -> otp.requestOtp(PHONE, null))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("owner or a seeker");
	}

	@Test
	void the_wrong_code_is_rejected_and_the_right_one_still_works_after() {
		otp.requestOtp(PHONE, Role.OWNER);

		assertThatThrownBy(() -> otp.verifyOtp(PHONE, "000000"))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("wrong or has expired");

		OtpService.AuthResult result = otp.verifyOtp(PHONE, sender.lastCode);
		assertThat(result.user().phone()).isEqualTo(PHONE);
	}

	@Test
	void a_code_cannot_be_used_twice() {
		otp.requestOtp(PHONE, Role.OWNER);
		String code = sender.lastCode;
		otp.verifyOtp(PHONE, code);

		assertThatThrownBy(() -> otp.verifyOtp(PHONE, code))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("No pending code");
	}

	@Test
	void five_wrong_attempts_locks_the_code_even_if_the_sixth_guess_is_right() {
		otp.requestOtp(PHONE, Role.OWNER);
		String code = sender.lastCode;

		for (int i = 0; i < 5; i++) {
			assertThatThrownBy(() -> otp.verifyOtp(PHONE, "000000")).isInstanceOf(ApiException.class);
		}

		assertThatThrownBy(() -> otp.verifyOtp(PHONE, code))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("Too many wrong attempts");
	}

	@Test
	void a_sixth_otp_request_in_the_window_is_rate_limited() {
		for (int i = 0; i < 5; i++) {
			otp.requestOtp(PHONE, Role.OWNER);
		}

		assertThatThrownBy(() -> otp.requestOtp(PHONE, Role.OWNER))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("Too many codes requested");
	}

	@Test
	void an_invalid_phone_number_is_rejected_before_anything_is_sent() {
		assertThatThrownBy(() -> otp.requestOtp("12345", Role.OWNER))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("10-digit");
		assertThat(sender.lastCode).isNull();
	}
}
