package com.roomfinder.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Attaches the caller's identity to the request if a valid bearer token is present, then always
 * continues the chain. This filter never rejects a request by itself — some endpoints are public
 * (PG detail, search, bed grid). Controllers that need a signed-in user read the
 * {@code authedUser} request attribute (via {@code @RequestAttribute}) and call
 * {@link AuthedUser#require} or {@link AuthedUser#requireOwnerOf}.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

	public static final String REQUEST_ATTRIBUTE = "authedUser";
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwt;

	public JwtAuthFilter(JwtService jwt) {
		this.jwt = jwt;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith(BEARER_PREFIX)) {
			jwt.parse(header.substring(BEARER_PREFIX.length()))
					.ifPresent(user -> request.setAttribute(REQUEST_ATTRIBUTE, user));
		}
		chain.doFilter(request, response);
	}
}
