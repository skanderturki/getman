package com.getman.mcp;

/** A tool call that cannot be done; the message is returned to the assistant as the tool's error result. */
public class ToolException extends Exception {
	private static final long serialVersionUID = 1L;

	public ToolException(String message) {
		super(message);
	}
}
