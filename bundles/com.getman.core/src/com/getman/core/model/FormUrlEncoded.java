package com.getman.core.model;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The {@code application/x-www-form-urlencoded} format, as browsers send HTML forms: {@code key=value} pairs joined
 * with {@code &}, UTF-8 percent-encoded, with spaces as {@code +}. Fields are stored decoded and encoded only here,
 * so a value can hold {@code &}, {@code =} or {@code +} as typed.
 */
public final class FormUrlEncoded {
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{[^{}]*\\}\\}");

	private FormUrlEncoded() {
	}

	/**
	 * Body text for the active fields. {@code {{variable}}} placeholders are kept as written, so a request that is
	 * not resolved yet (exported, saved, shown) still shows them; once resolved, their values are encoded too.
	 */
	public static String encode(List<KeyValue> fields) {
		return fields.stream().filter(KeyValue::isActive)
				.map(f -> encodeKeepingVariables(f.getKey().trim()) + "=" + encodeKeepingVariables(f.getValue()))
				.collect(Collectors.joining("&"));
	}

	/**
	 * Fields of a body written as text, decoded. Pairs without {@code =} become a field with an empty value, and
	 * malformed percent escapes are kept as written instead of failing.
	 */
	public static List<KeyValue> parse(String text) {
		List<KeyValue> fields = new ArrayList<>();
		if (text == null) {
			return fields;
		}
		for (String pair : text.strip().split("&")) {
			if (pair.isEmpty()) {
				continue;
			}
			int eq = pair.indexOf('=');
			String key = eq >= 0 ? pair.substring(0, eq) : pair;
			String value = eq >= 0 ? pair.substring(eq + 1) : "";
			fields.add(new KeyValue(decode(key), decode(value)));
		}
		return fields;
	}

	static String encodeKeepingVariables(String s) {
		StringBuilder out = new StringBuilder();
		Matcher m = PLACEHOLDER.matcher(s);
		int last = 0;
		while (m.find()) {
			out.append(URLEncoder.encode(s.substring(last, m.start()), StandardCharsets.UTF_8));
			out.append(m.group());
			last = m.end();
		}
		out.append(URLEncoder.encode(s.substring(last), StandardCharsets.UTF_8));
		return out.toString();
	}

	private static String decode(String s) {
		try {
			return URLDecoder.decode(s, StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			return s;
		}
	}
}
