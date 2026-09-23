package com.roomfinder.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and validates the app's JWTs. HS256, one long-lived token per login (no refresh token
 * yet — re-running OTP login is the refresh mechanism for now, acceptable for a v1 mobile-first app).
 */
@Component
public class JwtService {

	private static final Duration TOKEN_TTL = Duration.ofDays(30);

	private final SecretKey key;

	public JwtService(@Value("${auth.jwt-secret}") String secret) {
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException(
					"auth.jwt-secret (AUTH_JWT_SECRET) must be set and at least 32 bytes for HS256");
		}
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}

	public String issue(long userId, String phone, Role role) {
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(Long.toString(userId))
				.claim("phone", phone)
				.claim("role", role.name())
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(TOKEN_TTL)))
				.signWith(key)
				.compact();
	}

	/** Empty if the token is missing, malformed, expired, or signed with a different secret. */
	public Optional<AuthedUser> parse(String token) {
		try {
			Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
			long userId = Long.parseLong(claims.getSubject());
			String phone = claims.get("phone", String.class);
			Role role = Role.valueOf(claims.get("role", String.class));
			return Optional.of(new AuthedUser(userId, phone, role));
		} catch (JwtException | IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
