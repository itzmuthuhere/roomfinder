package com.roomfinder.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The only {@link WhatsAppSender} implementation that exists right now: prints the message instead
 * of sending it. There is no WhatsApp Cloud API integration yet - it needs a Meta Business account,
 * a verified phone number and template message approval, none of which can be done from code. Until
 * that manual setup happens and a real implementation is added, booking confirmations are visible
 * only in the server log, exactly like {@code auth.LoggingOtpSender} for OTP codes.
 */
@Component
public class LoggingWhatsAppSender implements WhatsAppSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingWhatsAppSender.class);

	@Override
	public void send(String phone, String message) {
		log.info("WhatsApp to {} (logged, not sent): {}", phone, message);
	}
}
