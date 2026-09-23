package com.roomfinder.auth;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
class AuthRepository {

	private final JdbcClient db;

	AuthRepository(JdbcClient db) {
		this.db = db;
	}

	record OtpRow(long id, String codeHash, Role role, int attempts) {}

	long insertOtpRequest(String phone, String codeHash, Role role, OffsetDateTime expiresAt) {
		return db.sql("""
				insert into otp_request (phone, code_hash, role, expires_at)
				values (:phone, :hash, :role, :expiresAt)
				returning id
				""")
				.param("phone", phone)
				.param("hash", codeHash)
				.param("role", role.name())
				.param("expiresAt", expiresAt)
				.query(Long.class)
				.single();
	}

	/** The newest not-yet-consumed, not-yet-expired code for this phone, if any. */
	Optional<OtpRow> latestActiveOtp(String phone) {
		return db.sql("""
				select id, code_hash, role, attempts
				  from otp_request
				 where phone = :phone and consumed_at is null and expires_at > now()
				 order by created_at desc
				 limit 1
				""")
				.param("phone", phone)
				.query((rs, i) -> new OtpRow(
						rs.getLong("id"), rs.getString("code_hash"), Role.valueOf(rs.getString("role")), rs.getInt("attempts")))
				.optional();
	}

	void incrementAttempts(long otpRequestId) {
		db.sql("update otp_request set attempts = attempts + 1 where id = :id")
				.param("id", otpRequestId)
				.update();
	}

	void markConsumed(long otpRequestId) {
		db.sql("update otp_request set consumed_at = now() where id = :id")
				.param("id", otpRequestId)
				.update();
	}

	int countRecentRequests(String phone, OffsetDateTime since) {
		return db.sql("select count(*) from otp_request where phone = :phone and created_at > :since")
				.param("phone", phone)
				.param("since", since)
				.query(Integer.class)
				.single();
	}

	Optional<AuthedUser> findUserByPhone(String phone) {
		return db.sql("select id, phone, role from app_user where phone = :phone")
				.param("phone", phone)
				.query(AuthRepository::mapUser)
				.optional();
	}

	/**
	 * Creates the user on first login; on a repeat login, returns the existing row untouched.
	 * The role passed in is only ever used for the insert branch — an existing user's role is
	 * never changed here, which is what stops a login request from being used to escalate role.
	 */
	AuthedUser upsertUser(String phone, Role role) {
		return db.sql("""
				insert into app_user (phone, role) values (:phone, :role)
				on conflict (phone) do update set phone = excluded.phone
				returning id, phone, role
				""")
				.param("phone", phone)
				.param("role", role.name())
				.query(AuthRepository::mapUser)
				.single();
	}

	private static AuthedUser mapUser(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
		return new AuthedUser(rs.getLong("id"), rs.getString("phone"), Role.valueOf(rs.getString("role")));
	}
}
