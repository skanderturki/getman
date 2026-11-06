package com.getman.mcp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.getman.core.model.Auth;
import com.getman.core.model.Environment;
import com.getman.core.model.KeyValue;
import com.getman.core.model.RequestCollection;
import com.getman.core.model.RequestModel;

/**
 * Keeps secrets out of what the assistant sees. Two rules:
 * <ul>
 * <li>known secret values (values of variables marked secret, the auth token and password and the auth and
 * sensitive headers of the request being sent) are replaced with {@value #MASK} wherever they appear, including
 * in response bodies that echo them;</li>
 * <li>auth fields and sensitive headers of saved requests are masked unless they only hold
 * {@code {{variables}}} (like {@code Bearer {{token}}}), so literal tokens typed into a request stay hidden.</li>
 * </ul>
 * Values shorter than {@value #MIN_LENGTH} characters are not searched for, to avoid masking ordinary text.
 */
final class Redactor {
	static final String MASK = "[secret]";
	static final int MIN_LENGTH = 4;
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*[A-Za-z0-9_.$-]+\\s*\\}\\}");
	private static final Pattern SCHEME = Pattern.compile("(?i)^\\s*(bearer|basic|token)\\b");
	private static final Set<String> SENSITIVE_HEADERS = Set.of("authorization", "proxy-authorization", "cookie",
			"x-api-key", "api-key", "x-auth-token");

	private final List<String> secrets;

	Redactor(Iterable<String> values) {
		Set<String> unique = new LinkedHashSet<>();
		for (String v : values) {
			if (v != null && v.trim().length() >= MIN_LENGTH) {
				unique.add(v);
				unique.add(v.trim());
			}
		}
		List<String> sorted = new ArrayList<>(unique);
		sorted.sort(Comparator.comparingInt(String::length).reversed()); // longest first: no partial leftovers
		this.secrets = sorted;
	}

	/**
	 * Secrets of the whole workspace plus, when {@code sent} is not null, the resolved auth and sensitive header
	 * values of the request being sent.
	 */
	static Redactor of(List<Environment> environments, List<RequestCollection> collections, RequestModel sent) {
		List<String> values = new ArrayList<>();
		for (Environment e : environments) {
			secretValues(e.getVariables(), values);
		}
		for (RequestCollection c : collections) {
			secretValues(c.getVariables(), values);
		}
		if (sent != null) {
			Auth auth = sent.getAuth();
			values.add(auth.getToken());
			values.add(auth.getPassword());
			String header = auth.headerValue();
			if (header != null) {
				values.add(header);
				values.add(header.substring(header.indexOf(' ') + 1)); // the credentials alone
			}
			for (KeyValue h : sent.getHeaders()) {
				if (h.isActive() && isSensitiveHeader(h.getKey())) {
					values.add(h.getValue());
					values.add(SCHEME.matcher(h.getValue()).replaceFirst(""));
				}
			}
		}
		return new Redactor(values);
	}

	private static void secretValues(List<KeyValue> rows, List<String> out) {
		for (KeyValue kv : rows) {
			if (kv.isSecret()) {
				out.add(kv.getValue());
			}
		}
	}

	/** {@code text} with every known secret replaced by {@value #MASK}. */
	String redact(String text) {
		if (text == null || text.isEmpty()) {
			return text;
		}
		String out = text;
		for (String secret : secrets) {
			out = out.replace(secret, MASK);
		}
		return out;
	}

	/** Header names whose values are credentials. */
	static boolean isSensitiveHeader(String name) {
		return name != null && SENSITIVE_HEADERS.contains(name.trim().toLowerCase(Locale.ROOT));
	}

	/**
	 * A credential field as written: kept when empty or when it only holds {@code {{variables}}} (optionally after
	 * a scheme such as {@code Bearer}), otherwise {@value #MASK}.
	 */
	static String maskLiteral(String value) {
		if (value == null || value.isBlank()) {
			return value;
		}
		String rest = SCHEME.matcher(PLACEHOLDER.matcher(value).replaceAll("")).replaceFirst("");
		return rest.isBlank() ? value : MASK;
	}
}
