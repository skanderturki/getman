package com.getman.core.postman;

/** A file could not be imported as a Postman collection or environment; the message is meant for the user. */
public class PostmanImportException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public PostmanImportException(String message) {
		super(message);
	}
}
