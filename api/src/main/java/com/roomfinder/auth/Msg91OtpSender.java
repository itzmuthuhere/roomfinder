package com.roomfinder.auth;

import com.roomfinder.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Sends the OTP as a real SMS through MSG91's v5 "Send OTP" API, using our own generated code
 * (not MSG91's) so expiry and hashing stay under {@link OtpService}'s control.
 *
 * <p><b>MANUAL EFFORT, not verified by this code change:</b> this class has never been run
 * against a real MSG91 account — there is no key to test with in this environment. Before it can
 * send a single real message you must, by hand:
 * <ol>
 *   <li>Create an MSG91 account and complete TRAI/DLT sender-id + template registration, which
 *       Indian transactional SMS legally requires — this alone can take days and cannot be
 *       automated by any code.</li>
 *   <li>Register an OTP template containing an {@code ##OTP##} placeholder and note its
 *       {@code template_id}.</li>
 *   <li>Set {@code OTP_PROVIDER=msg91}, {@code MSG91_AUTH_KEY}, {@code MSG91_TEMPLATE_ID} in the
 *       environment.</li>
 *   <li>Send yourself a real code and confirm the SMS arrives, the phone number formatting MSG91
 *       expects is correct, and the response-shape assumptions below match what your account
 *       actually returns (MSG91 has more than one OTP API generation in the wild; confirm this is
 *       the one your account is provisioned for).</li>
 * </ol>
 * Until all of that is done, leave {@code OTP_PROVIDER} unset — {@link LoggingOtpSender} is the
 * active default and login still works end to end, just by reading the code from the server log
 * instead of a text message.
 */
@Component
@ConditionalOnProperty(name = "otp.provider", havingValue = "msg91")
class Msg91OtpSender implements OtpSender {

	private static final Logger log = LoggerFactory.getLogger(Msg91OtpSender.class);

	private final RestClient http = RestClient.create("https://control.msg91.com");
	private final String authKey;
	private final String templateId;

	Msg91OtpSender(@Value("${otp.msg91.auth-key:}") String authKey,
	               @Value("${otp.msg91.template-id:}") String templateId) {
		if (authKey.isBlank() || templateId.isBlank()) {
			throw new IllegalStateException(
					"OTP_PROVIDER=msg91 requires MSG91_AUTH_KEY and MSG91_TEMPLATE_ID to be set");
		}
		this.authKey = authKey;
		this.templateId = templateId;
	}

	@Override
	public void send(String phone, String code) {
		try {
			http.get()
					.uri(uriBuilder -> uriBuilder
							.path("/api/v5/otp")
							.queryParam("template_id", templateId)
							.queryParam("mobile", "91" + phone)
							.queryParam("authkey", authKey)
							.queryParam("otp", code)
							.build())
					.retrieve()
					.toBodilessEntity();
		} catch (Exception e) {
			// An SMS provider outage must not be indistinguishable from "no such account" to the
			// caller, and must not leak the auth key in a stack trace reaching the client.
			log.error("MSG91 OTP send failed for a request to {}", phone, e);
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
					"OTP_SEND_FAILED", "Could not send the verification code. Try again shortly.");
		}
	}
}
