package com.getman.core.http;

import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.getman.core.model.Auth;
import com.getman.core.model.HttpMethod;
import com.getman.core.model.KeyValue;
import com.getman.core.model.RequestModel;
import com.getman.core.model.ResponseModel;

/** Sends {@link RequestModel}s with the JDK HTTP client. Thread-safe. */
public final class HttpService {
	private static final Pattern CHARSET = Pattern.compile("(?i)charset=\"?([^\";\\s]+)");

	/** Settings and the client built from them, swapped together so a request never mixes the two. */
	private record Configured(HttpSettings settings, HttpClient client) {
	}

	private volatile Configured configured;

	public HttpService() {
		this(HttpSettings.DEFAULT);
	}

	public HttpService(HttpSettings settings) {
		setSettings(settings);
	}

	public HttpSettings getSettings() {
		return configured.settings();
	}

	/** Applies new settings to requests sent from now on; requests in flight finish with the old ones. */
	public void setSettings(HttpSettings settings) {
		HttpClient.Builder builder = HttpClient.newBuilder()
				.connectTimeout(settings.connectTimeout())
				.followRedirects(settings.followRedirects() ? HttpClient.Redirect.NORMAL : HttpClient.Redirect.NEVER);
		javax.net.ssl.SSLContext ssl = TlsTrust.sslContext(settings.trustMode());
		if (ssl != null) {
			builder.sslContext(ssl);
		}
		configured = new Configured(settings, builder.build());
	}

	/**
	 * Sends the request asynchronously. The future always completes normally with a {@link ResponseModel}
	 * (possibly an error one) unless it is cancelled; cancelling it aborts the exchange.
	 */
	public CompletableFuture<ResponseModel> send(RequestModel request) {
		long start = System.nanoTime();
		Configured c = configured;
		HttpRequest httpRequest;
		try {
			httpRequest = toHttpRequest(request, c.settings());
		} catch (IllegalArgumentException e) {
			return CompletableFuture.completedFuture(ResponseModel.failure(e.getMessage(), 0));
		}
		CompletableFuture<HttpResponse<byte[]>> exchange = c.client().sendAsync(httpRequest,
				HttpResponse.BodyHandlers.ofByteArray());
		CompletableFuture<ResponseModel> result = exchange
				.thenApply(response -> toResponseModel(response, elapsedMs(start)))
				.exceptionally(t -> ResponseModel.failure(describe(t), elapsedMs(start)));
		// Cancelling the returned future must abort the underlying exchange too.
		result.whenComplete((r, t) -> {
			if (t instanceof CancellationException) {
				exchange.cancel(true);
			}
		});
		return result;
	}

	HttpRequest toHttpRequest(RequestModel request) {
		return toHttpRequest(request, configured.settings());
	}

	private HttpRequest toHttpRequest(RequestModel request, HttpSettings settings) {
		URI uri = UrlBuilder.buildUri(request);
		HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(settings.requestTimeout());

		for (KeyValue header : request.getHeaders()) {
			if (header.isActive()) {
				try {
					builder.header(header.getKey().trim(), header.getValue());
				} catch (IllegalArgumentException e) {
					throw new IllegalArgumentException(
							"Header '" + header.getKey().trim() + "' cannot be set: " + e.getMessage(), e);
				}
			}
		}
		String contentType = request.getBodyType().contentType();
		if (request.hasBody() && contentType != null && !request.hasActiveHeader("Content-Type")) {
			builder.header("Content-Type", contentType);
		}
		String authorization = request.authorizationFromAuth();
		if (authorization != null) {
			try {
				builder.header(Auth.HEADER, authorization);
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("The auth token contains characters that are not allowed in a header.", e);
			}
		}

		HttpRequest.BodyPublisher body = request.hasBody()
				? HttpRequest.BodyPublishers.ofString(request.getBody(), StandardCharsets.UTF_8)
				: HttpRequest.BodyPublishers.noBody();
		HttpMethod method = request.getMethod();
		builder.method(method.name(), body);
		return builder.build();
	}

	private static ResponseModel toResponseModel(HttpResponse<byte[]> response, long durationMs) {
		List<Map.Entry<String, String>> headers = new ArrayList<>();
		response.headers().map().forEach((name, values) -> {
			if (!name.startsWith(":")) { // skip HTTP/2 pseudo-headers
				values.forEach(v -> headers.add(new AbstractMap.SimpleImmutableEntry<>(name, v)));
			}
		});
		headers.sort(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER));
		byte[] bytes = response.body() == null ? new byte[0] : response.body();
		Charset charset = charsetOf(response.headers().firstValue("content-type").orElse(null));
		String version = response.version() == HttpClient.Version.HTTP_2 ? "HTTP/2" : "HTTP/1.1";
		return ResponseModel.success(response.statusCode(), version, headers, new String(bytes, charset),
				bytes.length, durationMs, response.uri().toString());
	}

	static Charset charsetOf(String contentType) {
		if (contentType != null) {
			Matcher m = CHARSET.matcher(contentType);
			if (m.find()) {
				try {
					return Charset.forName(m.group(1));
				} catch (IllegalArgumentException e) {
					// unknown charset: fall through to UTF-8
				}
			}
		}
		return StandardCharsets.UTF_8;
	}

	private static String describe(Throwable t) {
		Throwable cause = t;
		while (cause instanceof CompletionException && cause.getCause() != null) {
			cause = cause.getCause();
		}
		if (cause instanceof HttpConnectTimeoutException) {
			return "Connection timed out.";
		}
		if (cause instanceof HttpTimeoutException) {
			return "Request timed out.";
		}
		if (cause instanceof ConnectException) {
			return "Could not connect to the server (connection refused or unreachable).";
		}
		if (cause instanceof UnknownHostException) {
			return "Unknown host: " + cause.getMessage();
		}
		if (cause instanceof javax.net.ssl.SSLException) {
			String m = String.valueOf(cause.getMessage());
			if (m.contains("PKIX") || m.contains("certificate") || m.contains("No subject alternative")) {
				return "TLS certificate not trusted: " + m + System.lineSeparator()
						+ "For a self-signed development server, change \"Certificates\" in Window › Preferences › GetMan.";
			}
			return "TLS/SSL error: " + m;
		}
		String message = cause.getMessage();
		return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
	}

	private static long elapsedMs(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000;
	}
}
