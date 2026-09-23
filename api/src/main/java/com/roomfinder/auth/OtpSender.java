package com.roomfinder.auth;

/** Where an OTP code actually goes. Swap the implementation without touching {@link OtpService}. */
interface OtpSender {
	void send(String phone, String code);
}
