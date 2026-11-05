package com.getman.core.variables;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.getman.core.model.Auth;
import com.getman.core.model.FormField;
import com.getman.core.model.KeyValue;
import com.getman.core.model.RequestModel;

/**
 * Replaces {@code {{name}}} placeholders with variable values. Names may contain letters, digits, {@code _ - .}
 * and {@code $}, with optional spaces inside the braces. Values may themselves contain placeholders, resolved up
 * to {@value #MAX_DEPTH} levels deep; a variable that refers back to itself stops expanding there. Unknown names
 * are left exactly as written and reported, so text that merely looks like a placeholder is never lost.
 */
public final class VariableResolver {
	static final int MAX_DEPTH = 10;
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.$-]+)\\s*\\}\\}");

	private final Map<String, String> values;
	private final Set<String> unresolved = new LinkedHashSet<>();

	public VariableResolver(Map<String, String> values) {
		this.values = values == null ? Map.of() : values;
	}

	/** Names that were used but not defined, in order of first use, across all calls so far. */
	public Set<String> getUnresolved() {
		return Collections.unmodifiableSet(unresolved);
	}

	public String resolve(String text) {
		return resolve(text, 0, Set.of());
	}

	private String resolve(String text, int depth, Set<String> expanding) {
		if (text == null || text.indexOf("{{") < 0) {
			return text;
		}
		Matcher m = PLACEHOLDER.matcher(text);
		StringBuilder out = new StringBuilder();
		while (m.find()) {
			String name = m.group(1);
			String replacement;
			if (!values.containsKey(name)) {
				unresolved.add(name);
				replacement = m.group();
			} else if (depth >= MAX_DEPTH || expanding.contains(name)) {
				replacement = m.group(); // cycle or too deep: stop expanding here
			} else {
				Set<String> inner = new LinkedHashSet<>(expanding);
				inner.add(name);
				replacement = resolve(values.get(name), depth + 1, inner);
			}
			m.appendReplacement(out, Matcher.quoteReplacement(replacement));
		}
		m.appendTail(out);
		return out.toString();
	}

	/**
	 * Copy of {@code request} with placeholders filled in the URL, params, headers, body and auth. The original
	 * keeps its placeholders; id, name and collection link are copied unchanged.
	 */
	public RequestModel resolve(RequestModel request) {
		RequestModel r = request.copy();
		r.setUrl(resolve(r.getUrl()));
		resolveRows(r.getParams());
		resolveRows(r.getHeaders());
		r.setBody(resolve(r.getBody()));
		for (FormField f : r.getFormFields()) {
			f.setKey(resolve(f.getKey()));
			f.setValue(resolve(f.getValue()));
		}
		r.setBinaryFile(resolve(r.getBinaryFile()));
		Auth auth = r.getAuth();
		auth.setToken(resolve(auth.getToken()));
		auth.setUsername(resolve(auth.getUsername()));
		auth.setPassword(resolve(auth.getPassword()));
		return r;
	}

	/**
	 * Names used by the parts of {@code request} that will actually be sent (active rows, the body if any, the
	 * current auth type's fields) but not defined in {@code values}.
	 */
	public static Set<String> unresolvedIn(RequestModel request, Map<String, String> values) {
		VariableResolver resolver = new VariableResolver(values);
		resolver.resolve(request.getUrl());
		for (KeyValue kv : request.getParams()) {
			if (kv.isActive()) {
				resolver.resolve(kv.getKey());
				resolver.resolve(kv.getValue());
			}
		}
		for (KeyValue kv : request.getHeaders()) {
			if (kv.isActive()) {
				resolver.resolve(kv.getKey());
				resolver.resolve(kv.getValue());
			}
		}
		if (request.hasBody()) {
			switch (request.getBodyType()) {
			case MULTIPART -> request.getFormFields().stream().filter(FormField::isActive).forEach(f -> {
				resolver.resolve(f.getKey());
				resolver.resolve(f.getValue());
			});
			case BINARY -> resolver.resolve(request.getBinaryFile());
			default -> resolver.resolve(request.getBody());
			}
		}
		Auth auth = request.getAuth();
		switch (auth.getType()) {
		case BEARER -> resolver.resolve(auth.getToken());
		case BASIC -> {
			resolver.resolve(auth.getUsername());
			resolver.resolve(auth.getPassword());
		}
		default -> {
		}
		}
		return resolver.getUnresolved();
	}

	private void resolveRows(java.util.List<KeyValue> rows) {
		for (KeyValue kv : rows) {
			kv.setKey(resolve(kv.getKey()));
			kv.setValue(resolve(kv.getValue()));
		}
	}
}
