package com.roomfinder.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default OTP delivery: prints the code to the server log instead of sending an SMS. This is what
 * every environment uses until {@code OTP_PROVIDER=msg91} is set, so local dev and CI never need a
 * real SMS account.
 *
 * <p>There is deliberately no startup guard against this sender being active in a deployed
 * environment yet — nothing is deployed (see BACKLOG.md item 6, "Do not deploy before item 1 (auth)
 * exists"). Before any real deploy: set {@code OTP_PROVIDER=msg91} and a real
 * {@code MSG91_AUTH_KEY}, or no one will ever receive a real code.
 */
@Component
@ConditionalOnProperty(name = "otp.provider", havingValue = "log", matchIfMissing = true)
class LoggingOtpSender implements OtpSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingOtpSender.class);

	@Override
	public void send(String phone, String code) {
		log.info("OTP for {}: {}  (logged, not sent — set OTP_PROVIDER=msg91 to send real SMS)", phone, code);
	}
}
