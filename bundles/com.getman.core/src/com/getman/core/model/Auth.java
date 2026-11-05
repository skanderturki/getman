package com.getman.core.model;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * Authorization settings of a request. All fields are kept when the type changes, so switching from Basic to
 * Bearer and back does not lose what the user typed; only the fields of the current type are sent.
 */
public final class Auth {
	public static final String HEADER = "Authorization";

	private AuthType type = AuthType.NONE;
	private String token = "";
	private String username = "";
	private String password = "";

	public AuthType getType() {
		return type;
	}

	public void setType(AuthType type) {
		this.type = type == null ? AuthType.NONE : type;
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token == null ? "" : token;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username == null ? "" : username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password == null ? "" : password;
	}

	/** Whether a token or password is stored (in any type), i.e. whether exporting would reveal a secret. */
	public boolean hasSecrets() {
		return !token.isEmpty() || !password.isEmpty();
	}

	/**
	 * Value of the {@code Authorization} header for the current type, or {@code null} when nothing should be
	 * sent (no auth, or the fields are empty).
	 */
	public String headerValue() {
		switch (type) {
		case BEARER:
			String t = token.trim();
			return t.isEmpty() ? null : "Bearer " + t;
		case BASIC:
			if (username.isEmpty() && password.isEmpty()) {
				return null;
			}
			// RFC 7617: user-id ":" password, encoded as UTF-8 then Base64.
			byte[] credentials = (username + ":" + password).getBytes(StandardCharsets.UTF_8);
			return "Basic " + Base64.getEncoder().encodeToString(credentials);
		default:
			return null;
		}
	}

	public Auth copy() {
		Auth c = new Auth();
		c.type = type;
		c.token = token;
		c.username = username;
		c.password = password;
		return c;
	}

	/** Clears the token and password but keeps the type and username. */
	public void clearSecrets() {
		token = "";
		password = "";
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Auth a && type == a.type && token.equals(a.token) && username.equals(a.username)
				&& password.equals(a.password);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, token, username, password);
	}

	@Override
	public String toString() {
		return "Auth[" + type + ", user=" + username + "]"; // never print secrets
	}
}
