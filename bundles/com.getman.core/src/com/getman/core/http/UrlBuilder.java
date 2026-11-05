package com.getman.core.http;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import com.getman.core.model.KeyValue;
import com.getman.core.model.RequestModel;

/** Turns the URL field plus the params table into the URI that is actually requested. */
public final class UrlBuilder {

	private UrlBuilder() {
	}

	/**
	 * Full URL text: the URL field (with "http://" added when no scheme is typed) followed by the enabled params,
	 * URL-encoded and appended after any query string already in the field.
	 */
	public static String buildUrl(RequestModel request) {
		String url = request.getUrl().trim();
		if (url.isEmpty()) {
			return "";
		}
		if (!url.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) {
			url = "http://" + url;
		}
		String query = request.getParams().stream()
				.filter(KeyValue::isActive)
				.map(p -> encode(p.getKey().trim()) + "=" + encode(p.getValue()))
				.collect(Collectors.joining("&"));
		if (query.isEmpty()) {
			return url;
		}
		int hash = url.indexOf('#');
		String fragment = hash >= 0 ? url.substring(hash) : "";
		String base = hash >= 0 ? url.substring(0, hash) : url;
		String sep = base.contains("?") ? (base.endsWith("?") || base.endsWith("&") ? "" : "&") : "?";
		return base + sep + query + fragment;
	}

	/** Parses {@link #buildUrl}; throws IllegalArgumentException with a readable message. */
	public static URI buildUri(RequestModel request) {
		String url = buildUrl(request);
		if (url.isEmpty()) {
			throw new IllegalArgumentException("Enter a URL first.");
		}
		URI uri;
		try {
			uri = URI.create(url);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Invalid URL: " + url, e);
		}
		String scheme = uri.getScheme().toLowerCase();
		if (!scheme.equals("http") && !scheme.equals("https")) {
			throw new IllegalArgumentException("Only http and https URLs are supported: " + url);
		}
		if (uri.getHost() == null) {
			throw new IllegalArgumentException("URL has no host: " + url);
		}
		return uri;
	}

	private static String encode(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
	}
}
