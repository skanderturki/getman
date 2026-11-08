package com.getman.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The status codes a request is expected to answer with, written as codes or classes separated by commas or
 * spaces: {@code 200}, {@code 201, 204}, {@code 2xx}. Empty means "no check".
 */
public final class ExpectedStatus {
	private static final Pattern PART = Pattern.compile("[1-5](\\d\\d|xx)");

	private ExpectedStatus() {
	}

	/** Canonical form ("201, 2xx"), "" for blank input, or {@code null} when the text is not valid. */
	public static String normalize(String text) {
		if (text == null || text.isBlank()) {
			return "";
		}
		List<String> parts = new ArrayList<>();
		for (String part : text.trim().toLowerCase(Locale.ROOT).split("[,;\\s]+")) {
			if (part.isEmpty()) {
				continue;
			}
			if (!PART.matcher(part).matches()) {
				return null;
			}
			if (!parts.contains(part)) {
				parts.add(part);
			}
		}
		return String.join(", ", parts);
	}

	public static boolean isValid(String text) {
		return normalize(text) != null;
	}

	/** Why {@code text} is not valid, or {@code null} when it is. */
	public static String problem(String text) {
		return isValid(text) ? null
				: "Expected status must be status codes or classes separated by commas, e.g. 200, 201, 204 or 2xx.";
	}

	/** Whether {@code status} is one of the expected codes or classes; false when nothing (or nothing valid) is expected. */
	public static boolean matches(String expected, int status) {
		String normalized = normalize(expected);
		if (normalized == null || normalized.isEmpty()) {
			return false;
		}
		String code = Integer.toString(status);
		for (String part : normalized.split(", ")) {
			if (part.equals(code) || (part.endsWith("xx") && code.length() == 3 && code.charAt(0) == part.charAt(0))) {
				return true;
			}
		}
		return false;
	}
}
