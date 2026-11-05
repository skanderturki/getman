package com.getman.core.curl;

/** A cURL command could not be turned into a request; the message is meant for the user. */
public class CurlParseException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public CurlParseException(String message) {
		super(message);
	}
}
