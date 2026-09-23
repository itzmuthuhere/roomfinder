package com.roomfinder.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit test, no Spring context, no database. */
class JwtServiceTest {

	private static final String SECRET_A = "a".repeat(32);
	private static final String SECRET_B = "b".repeat(32);

	@Test
	void issues_a_token_that_parses_back_to_the_same_identity() {
		JwtService jwt = new JwtService(SECRET_A);

		String token = jwt.issue(42L, "9876543210", Role.OWNER);
		AuthedUser user = jwt.parse(token).orElseThrow();

		assertThat(user.id()).isEqualTo(42L);
		assertThat(user.phone()).isEqualTo("9876543210");
		assertThat(user.role()).isEqualTo(Role.OWNER);
	}

	@Test
	void a_token_signed_with_a_different_secret_does_not_parse() {
		JwtService issuer = new JwtService(SECRET_A);
		JwtService verifier = new JwtService(SECRET_B);

		String token = issuer.issue(1L, "9876543210", Role.SEEKER);

		assertThat(verifier.parse(token)).isEmpty();
	}

	@Test
	void a_tampered_token_does_not_parse() {
		JwtService jwt = new JwtService(SECRET_A);
		String token = jwt.issue(1L, "9876543210", Role.OWNER);

		String tampered = token.substring(0, token.length() - 4) + "abcd";

		assertThat(jwt.parse(tampered)).isEmpty();
	}

	@Test
	void garbage_input_does_not_parse() {
		JwtService jwt = new JwtService(SECRET_A);

		assertThat(jwt.parse("not.a.jwt")).isEmpty();
		assertThat(jwt.parse("")).isEmpty();
	}

	@Test
	void refuses_to_start_with_too_short_a_secret() {
		assertThatThrownBy(() -> new JwtService("too-short"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("32 bytes");
	}
}
