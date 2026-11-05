package com.getman.core.model;

public enum HttpMethod {
	GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS;

	/** Whether requests with this method normally carry a body. */
	public boolean allowsBody() {
		return this == POST || this == PUT || this == PATCH || this == DELETE;
	}

	public static HttpMethod parse(String value) {
		if (value == null) {
			return GET;
		}
		try {
			return valueOf(value.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			return GET;
		}
	}
}
