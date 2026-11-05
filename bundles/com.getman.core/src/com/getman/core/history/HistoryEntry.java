package com.getman.core.history;

import java.util.UUID;

import com.getman.core.model.RequestModel;
import com.getman.core.model.ResponseModel;

/**
 * One sent request: the request as written (with its {{variables}}, so it can be re-sent in any environment),
 * the URL that was actually called, and a summary of the response. Response bodies are not kept.
 */
public record HistoryEntry(String id, long sentAtMillis, RequestModel request, String resolvedUrl, int statusCode,
		String statusLine, String error, long durationMs, long sizeBytes) {

	public HistoryEntry {
		id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
		resolvedUrl = resolvedUrl == null ? "" : resolvedUrl;
		statusLine = statusLine == null ? "" : statusLine;
		error = error == null ? "" : error;
	}

	/** Records {@code request} (copied) and the outcome. */
	public static HistoryEntry of(RequestModel request, String resolvedUrl, ResponseModel response, long sentAtMillis) {
		RequestModel snapshot = request.copy();
		snapshot.setCollectionId(""); // history entries are not linked to collections
		if (response.isError()) {
			return new HistoryEntry(null, sentAtMillis, snapshot, resolvedUrl, 0, "", response.getError(),
					response.getDurationMs(), 0);
		}
		return new HistoryEntry(null, sentAtMillis, snapshot, resolvedUrl, response.getStatusCode(),
				response.getStatusLine(), "", response.getDurationMs(), response.getSizeBytes());
	}

	public boolean isError() {
		return !error.isEmpty();
	}
}
