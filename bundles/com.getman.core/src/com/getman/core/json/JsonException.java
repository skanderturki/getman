package com.getman.core.json;

/** Invalid JSON input, or a value that cannot be written as JSON. */
public class JsonException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public JsonException(String message) {
		super(message);
	}
}
