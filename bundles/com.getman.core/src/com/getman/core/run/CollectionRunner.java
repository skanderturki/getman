package com.getman.core.run;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import com.getman.core.http.HttpService;
import com.getman.core.http.UrlBuilder;
import com.getman.core.model.ExpectedStatus;
import com.getman.core.model.RequestModel;
import com.getman.core.model.ResponseModel;
import com.getman.core.variables.VariableResolver;

/**
 * Sends a list of saved requests one after the other and checks each status against the request's expected
 * status. One runner runs once; {@link #cancel()} may be called from any thread.
 */
public final class CollectionRunner {

	public enum Outcome {
		/** The status matched the expected status. */
		PASSED,
		/** The status did not match the expected status. */
		FAILED,
		/** A response came back, but the request has no expected status. */
		NOT_CHECKED,
		/** No response: unknown variables, a connection problem, a timeout. */
		ERROR,
		/** Not sent because the run was stopped. */
		STOPPED
	}

	/** What happened to one request. {@code response} is null when nothing was sent. */
	public record Result(RequestModel request, String url, ResponseModel response, Outcome outcome, String message) {
	}

	/** Counts per outcome. */
	public record Summary(int passed, int failed, int notChecked, int errors, int stopped) {
		public static Summary of(List<Result> results) {
			int[] n = new int[Outcome.values().length];
			results.forEach(r -> n[r.outcome().ordinal()]++);
			return new Summary(n[0], n[1], n[2], n[3], n[4]);
		}

		/** "3 passed, 1 failed, 2 not checked" (zero counts left out). */
		@Override
		public String toString() {
			List<String> parts = new ArrayList<>();
			add(parts, passed, "passed");
			add(parts, failed, "failed");
			add(parts, notChecked, "not checked");
			add(parts, errors, errors == 1 ? "error" : "errors");
			add(parts, stopped, "not sent");
			return parts.isEmpty() ? "nothing to run" : String.join(", ", parts);
		}

		private static void add(List<String> parts, int count, String label) {
			if (count > 0) {
				parts.add(count + " " + label);
			}
		}
	}

	private final HttpService http;
	private volatile boolean cancelled;
	private volatile CompletableFuture<ResponseModel> inFlight;

	public CollectionRunner(HttpService http) {
		this.http = http;
	}

	/** Stops the run: the request in flight is aborted and the rest are reported as {@link Outcome#STOPPED}. */
	public void cancel() {
		cancelled = true;
		CompletableFuture<ResponseModel> f = inFlight;
		if (f != null) {
			f.cancel(true);
		}
	}

	public boolean isCancelled() {
		return cancelled;
	}

	/**
	 * Sends {@code requests} in order on the calling thread, filling {@code {{variables}}} from {@code values}.
	 * Requests that use unknown variables are not sent. {@code onResult} is called after each request.
	 */
	public List<Result> run(List<RequestModel> requests, Map<String, String> values, Consumer<Result> onResult) {
		List<Result> results = new ArrayList<>();
		for (RequestModel request : requests) {
			Result result = cancelled ? new Result(request, "", null, Outcome.STOPPED, "The run was stopped.")
					: runOne(request, values);
			results.add(result);
			if (onResult != null) {
				onResult.accept(result);
			}
		}
		return results;
	}

	private Result runOne(RequestModel request, Map<String, String> values) {
		Set<String> unknown = VariableResolver.unresolvedIn(request, values);
		if (!unknown.isEmpty()) {
			return new Result(request, request.getUrl(), null, Outcome.ERROR,
					"Unknown variables: " + String.join(", ", unknown));
		}
		RequestModel resolved = new VariableResolver(values).resolve(request);
		String url = UrlBuilder.buildUrl(resolved);
		CompletableFuture<ResponseModel> future = http.send(resolved);
		inFlight = future;
		if (cancelled) {
			future.cancel(true);
		}
		ResponseModel response;
		try {
			response = future.get();
		} catch (CancellationException e) {
			return new Result(request, url, null, Outcome.STOPPED, "The run was stopped.");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			cancel();
			return new Result(request, url, null, Outcome.STOPPED, "The run was stopped.");
		} catch (ExecutionException e) {
			return new Result(request, url, null, Outcome.ERROR, String.valueOf(e.getCause()));
		} finally {
			inFlight = null;
		}
		if (response.isError()) {
			return new Result(request, url, response, Outcome.ERROR, response.getError());
		}
		String expected = request.getExpectedStatus();
		if (expected.isEmpty()) {
			return new Result(request, url, response, Outcome.NOT_CHECKED, "");
		}
		if (!ExpectedStatus.isValid(expected)) {
			return new Result(request, url, response, Outcome.FAILED, ExpectedStatus.problem(expected));
		}
		boolean ok = ExpectedStatus.matches(expected, response.getStatusCode());
		return new Result(request, url, response, ok ? Outcome.PASSED : Outcome.FAILED,
				ok ? "" : "Expected " + expected + ", got " + response.getStatusCode() + ".");
	}
}
