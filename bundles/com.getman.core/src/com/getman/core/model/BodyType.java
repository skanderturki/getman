package com.getman.core.model;

/** Kind of request body. Each type implies a default Content-Type header. */
public enum BodyType {
	NONE("None", null),
	JSON("JSON", "application/json"),
	TEXT("Text", "text/plain"),
	XML("XML", "application/xml"),
	FORM("Form URL-encoded", "application/x-www-form-urlencoded"),
	/** Text fields and files, from {@link RequestModel#getFormFields()}; the boundary is added when sending. */
	MULTIPART("Form Data (multipart)", "multipart/form-data"),
	/** The content of {@link RequestModel#getBinaryFile()}, sent as-is. */
	BINARY("Binary File", "application/octet-stream");

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

	/** Whether the body is the text in {@link RequestModel#getBody()}. */
	public boolean isText() {
		return this == JSON || this == TEXT || this == XML || this == FORM;
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
