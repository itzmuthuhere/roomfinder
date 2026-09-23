package com.roomfinder.notify;

/**
 * Where a WhatsApp message actually goes. Same shape as {@code auth.OtpSender}: one interface,
 * swappable implementations, so a real WhatsApp Cloud API sender can be added later without
 * touching any caller.
 */
public interface WhatsAppSender {
	void send(String phone, String message);
}
