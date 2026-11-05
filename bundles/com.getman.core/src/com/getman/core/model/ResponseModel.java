package com.getman.core.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Result of sending a request: either a received HTTP response (status, headers, body, timing) or an error
 * that prevented one (bad URL, connection refused, timeout...).
 */
public final class ResponseModel {
	private final int statusCode;
	private final String httpVersion;
	private final List<Map.Entry<String, String>> headers;
	private final String body;
	private final long sizeBytes;
	private final long durationMs;
	private final String finalUrl;
	private final String error;

	private ResponseModel(int statusCode, String httpVersion, List<Map.Entry<String, String>> headers, String body,
			long sizeBytes, long durationMs, String finalUrl, String error) {
		this.statusCode = statusCode;
		this.httpVersion = httpVersion;
		this.headers = headers == null ? List.of() : Collections.unmodifiableList(headers);
		this.body = body == null ? "" : body;
		this.sizeBytes = sizeBytes;
		this.durationMs = durationMs;
		this.finalUrl = finalUrl;
		this.error = error;
	}

	public static ResponseModel success(int statusCode, String httpVersion, List<Map.Entry<String, String>> headers,
			String body, long sizeBytes, long durationMs, String finalUrl) {
		return new ResponseModel(statusCode, httpVersion, headers, body, sizeBytes, durationMs, finalUrl, null);
	}

	public static ResponseModel failure(String error, long durationMs) {
		return new ResponseModel(0, null, List.of(), "", 0, durationMs, null, error);
	}

	public boolean isError() {
		return error != null;
	}

	public int getStatusCode() {
		return statusCode;
	}

	/** e.g. "HTTP/1.1" or "HTTP/2", or {@code null} on error. */
	public String getHttpVersion() {
		return httpVersion;
	}

	/** Response headers in the order received; a header with several values appears once per value. */
	public List<Map.Entry<String, String>> getHeaders() {
		return headers;
	}

	/** First value of a header, case-insensitive, or {@code null}. */
	public String getHeader(String name) {
		return headers.stream().filter(e -> e.getKey().equalsIgnoreCase(name)).map(Map.Entry::getValue).findFirst()
				.orElse(null);
	}

	public String getBody() {
		return body;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public long getDurationMs() {
		return durationMs;
	}

	/** URL that produced the response, after following redirects. */
	public String getFinalUrl() {
		return finalUrl;
	}

	public String getError() {
		return error;
	}

	/** "200 OK", "404 Not Found"... or "Error" when no response was received. */
	public String getStatusLine() {
		if (isError()) {
			return "Error";
		}
		String reason = HttpStatus.reasonPhrase(statusCode);
		return reason.isEmpty() ? String.valueOf(statusCode) : statusCode + " " + reason;
	}

	/** Human-readable size: "512 B", "3.2 KB", "1.4 MB". */
	public static String formatSize(long bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		}
		if (bytes < 1024 * 1024) {
			return String.format("%.1f KB", bytes / 1024.0);
		}
		return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
	}
}
