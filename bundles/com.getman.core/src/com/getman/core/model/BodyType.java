package com.getman.core.model;

/** Kind of request body. Each type implies a default Content-Type header. */
public enum BodyType {
	NONE("None", null),
	JSON("JSON", "application/json"),
	TEXT("Text", "text/plain"),
	XML("XML", "application/xml"),
	FORM("Form URL-encoded", "application/x-www-form-urlencoded");

	private final String label;
	private final String contentType;

	BodyType(String label, String contentType) {
		this.label = label;
		this.contentType = contentType;
	}

	public String label() {
		return label;
	}

	/** Default Content-Type for this body, or {@code null} when there is no body. */
	public String contentType() {
		return contentType;
	}

	public static BodyType parse(String value) {
		if (value == null) {
			return NONE;
		}
		try {
			return valueOf(value);
		} catch (IllegalArgumentException e) {
			return NONE;
		}
	}
}
