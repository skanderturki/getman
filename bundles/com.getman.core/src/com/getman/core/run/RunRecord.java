package com.getman.core.run;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

import com.getman.core.model.KeyValue;
import com.getman.core.run.CollectionRunner.Outcome;
import com.getman.core.run.CollectionRunner.Result;

/**
 * A finished run of a collection or folder, as kept in the History tab and written to reports. Response bodies
 * are not kept, and values of secret variables are masked in URLs and messages.
 *
 * @param folderId   the folder that was run; empty when the whole collection was run
 * @param folderName the folder's name (or path), empty for the whole collection
 * @param environment the environment's name, empty when none was selected
 */
public record RunRecord(String id, long startedAtMillis, String collectionId, String collectionName,
		String folderId, String folderName, String environment, boolean viaAssistant, List<Item> items) {

	/** Replaces values of secret variables in stored URLs and messages. */
	public static final String MASK = "[secret]";

	/** What happened to one request of the run. {@code status} is 0 when no response came back. */
	public record Item(String requestId, String method, String name, String url, String expected, Outcome outcome,
			int status, String statusLine, long durationMs, String message) {

		public Item {
			requestId = nz(requestId);
			method = nz(method);
			name = nz(name);
			url = nz(url);
			expected = nz(expected);
			outcome = outcome == null ? Outcome.ERROR : outcome;
			statusLine = nz(statusLine);
			message = nz(message);
		}

		public static Item of(Result r, UnaryOperator<String> mask) {
			boolean answered = r.response() != null && !r.response().isError();
			return new Item(r.request().getId(), r.request().getMethod().name(), r.request().getName(),
					mask.apply(nz(r.url())), r.request().getExpectedStatus(), r.outcome(),
					answered ? r.response().getStatusCode() : 0, answered ? r.response().getStatusLine() : "",
					r.response() == null ? 0 : r.response().getDurationMs(), mask.apply(nz(r.message())));
		}

		/** The name, or the URL for unnamed requests. */
		public String label() {
			return name.isBlank() ? url : name;
		}
	}

	public RunRecord {
		id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
		collectionId = nz(collectionId);
		collectionName = nz(collectionName);
		folderId = nz(folderId);
		folderName = nz(folderName);
		environment = nz(environment);
		items = items == null ? List.of() : List.copyOf(items);
	}

	/** A run without items yet, to be completed with {@link #withResults}. */
	public static RunRecord template(String collectionId, String collectionName, String folderId, String folderName,
			String environment, boolean viaAssistant) {
		return new RunRecord(null, 0, collectionId, collectionName, folderId, folderName, environment, viaAssistant,
				List.of());
	}

	/** This run's details with the given results, as a new run with its own id. */
	public RunRecord withResults(long startedAt, List<Result> results, UnaryOperator<String> mask) {
		return new RunRecord(null, startedAt, collectionId, collectionName, folderId, folderName, environment,
				viaAssistant, results.stream().map(r -> Item.of(r, mask)).toList());
	}

	/** "orders-api" or "orders-api › Customers". */
	public String title() {
		return folderName.isEmpty() ? collectionName : collectionName + " › " + folderName;
	}

	public CollectionRunner.Summary summary() {
		return CollectionRunner.Summary.count(items.stream().map(Item::outcome).toList());
	}

	/** True when a request failed its check or got no response. */
	public boolean hasProblems() {
		return items.stream().anyMatch(i -> i.outcome() == Outcome.FAILED || i.outcome() == Outcome.ERROR);
	}

	/** Total time of the requests, in milliseconds. */
	public long durationMs() {
		return items.stream().mapToLong(Item::durationMs).sum();
	}

	/**
	 * Masks the values of the secret variables among {@code variables} (values shorter than 4 characters are left
	 * alone, so ordinary text is not masked).
	 */
	public static UnaryOperator<String> masking(Collection<KeyValue> variables) {
		Set<String> values = new LinkedHashSet<>();
		for (KeyValue kv : variables) {
			if (kv.isSecret() && kv.getValue() != null && kv.getValue().trim().length() >= 4) {
				values.add(kv.getValue());
				values.add(kv.getValue().trim());
			}
		}
		List<String> sorted = values.stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
		return text -> {
			String out = text;
			for (String v : sorted) {
				out = out.replace(v, MASK);
			}
			return out;
		};
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}
}
