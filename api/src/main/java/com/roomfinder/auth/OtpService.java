package com.roomfinder.auth;

import com.roomfinder.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;

@Service
public class OtpService {

	static final Duration OTP_TTL = Duration.ofMinutes(5);
	static final int MAX_ATTEMPTS = 5;
	static final int MAX_REQUESTS_PER_WINDOW = 5;
	static final Duration RATE_LIMIT_WINDOW = Duration.ofHours(1);

	private final AuthRepository repo;
	private final OtpSender sender;
	private final JwtService jwt;
	private final SecureRandom random = new SecureRandom();

	public OtpService(AuthRepository repo, OtpSender sender, JwtService jwt) {
		this.repo = repo;
		this.sender = sender;
		this.jwt = jwt;
	}

	public record AuthResult(String token, AuthedUser user) {}

	/**
	 * Sends a code to {@code phone}. {@code roleForNewUser} is only used the first time a phone
	 * number ever logs in — an already-registered phone always keeps its existing role, so this
	 * call can never be used to promote a seeker account to an owner account or vice versa.
	 */
	@Transactional
	public void requestOtp(String phone, Role roleForNewUser) {
		validatePhone(phone);

		int recent = repo.countRecentRequests(phone, OffsetDateTime.now().minus(RATE_LIMIT_WINDOW));
		if (recent >= MAX_REQUESTS_PER_WINDOW) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_RATE_LIMITED",
					"Too many codes requested for this number. Try again in a while.");
		}

		Role role = repo.findUserByPhone(phone).map(AuthedUser::role).orElse(roleForNewUser);
		if (role == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_REQUIRED",
					"This is a new number - say whether you're signing up as an owner or a seeker");
		}

		String code = generateCode();
		repo.insertOtpRequest(phone, hash(phone, code), role, OffsetDateTime.now().plus(OTP_TTL));
		sender.send(phone, code);
	}

	@Transactional
	public AuthResult verifyOtp(String phone, String code) {
		validatePhone(phone);

		AuthRepository.OtpRow row = repo.latestActiveOtp(phone)
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "OTP_NOT_FOUND",
						"No pending code for this number - request a new one"));

		if (row.attempts() >= MAX_ATTEMPTS) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_LOCKED",
					"Too many wrong attempts. Request a new code.");
		}

		if (!row.codeHash().equals(hash(phone, code))) {
			repo.incrementAttempts(row.id());
			throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_INVALID", "That code is wrong or has expired");
		}

		repo.markConsumed(row.id());
		AuthedUser user = repo.upsertUser(phone, row.role());
		String token = jwt.issue(user.id(), user.phone(), user.role());
		return new AuthResult(token, user);
	}

	private String generateCode() {
		return String.format("%06d", random.nextInt(1_000_000));
	}

	/**
	 * SHA-256(phone : code). OTPs are 5-minute-lived, rate-limited to 5 requests/hour, and locked
	 * after 5 wrong attempts, which is enough protection for a 6-digit code without also needing a
	 * per-row salt. Binding the phone into the hash stops a code minted for one number being
	 * replayed against a different one.
	 */
	private static String hash(String phone, String code) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] out = digest.digest((phone + ':' + code).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(out);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is a required JDK algorithm", e);
		}
	}

	private static void validatePhone(String phone) {
		if (phone == null || !phone.matches("[6-9]\\d{9}")) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PHONE", "Enter a 10-digit Indian mobile number");
		}
	}
}
