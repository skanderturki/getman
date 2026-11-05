package com.getman.core.postman;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.getman.core.json.Json;
import com.getman.core.json.JsonException;
import com.getman.core.model.Auth;
import com.getman.core.model.AuthType;
import com.getman.core.model.BodyType;
import com.getman.core.model.Environment;
import com.getman.core.model.Folder;
import com.getman.core.model.FormField;
import com.getman.core.model.HttpMethod;
import com.getman.core.model.KeyValue;
import com.getman.core.model.NodeContainer;
import com.getman.core.model.RequestCollection;
import com.getman.core.model.RequestModel;

/**
 * Imports Postman Collection v2.0 / v2.1 files and Postman environment files. Folders, requests, headers, bodies,
 * Bearer/Basic/API-key auth (including auth inherited from folders and the collection) and collection variables
 * are converted; scripts, other auth types and file bodies are listed as warnings instead of failing the import.
 * Both tools use {@code {{name}}} for variables, so placeholders carry over as written.
 */
public final class PostmanImporter {

	/** What a parsed JSON file is. */
	public enum Kind {
		POSTMAN_COLLECTION, POSTMAN_ENVIRONMENT, OTHER
	}

	public record CollectionResult(RequestCollection collection, List<String> warnings) {
	}

	public record EnvironmentResult(Environment environment, List<String> warnings) {
	}

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{[^{}]*\\}\\}");

	private PostmanImporter() {
	}

	/** Classifies an already-parsed JSON value. */
	public static Kind detect(Object json) {
		if (!(json instanceof Map<?, ?> root)) {
			return Kind.OTHER;
		}
		if (root.get("info") instanceof Map<?, ?> info
				&& (str(info.get("schema")).contains("getpostman.com") || info.containsKey("_postman_id")
						|| root.get("item") instanceof List<?>)) {
			return Kind.POSTMAN_COLLECTION;
		}
		if (root.containsKey("requests") && (root.containsKey("order") || root.containsKey("folders"))) {
			return Kind.POSTMAN_COLLECTION; // v1: rejected with a helpful message by importCollection
		}
		if (root.get("values") instanceof List<?>
				&& (root.containsKey("_postman_variable_scope") || root.containsKey("name"))) {
			return Kind.POSTMAN_ENVIRONMENT;
		}
		return Kind.OTHER;
	}

	public static CollectionResult importCollection(String json) {
		return new CollectionImport(parseObject(json)).run();
	}

	public static EnvironmentResult importEnvironment(String json) {
		Map<?, ?> root = parseObject(json);
		if (detect(root) != Kind.POSTMAN_ENVIRONMENT) {
			throw new PostmanImportException("This is not a Postman environment file.");
		}
		Environment env = new Environment(str(root.get("name")).isBlank() ? "Imported Environment" : str(root.get("name")));
		List<String> warnings = new ArrayList<>();
		int emptySecrets = 0;
		for (Object o : list(root.get("values"))) {
			if (o instanceof Map<?, ?> v && !str(v.get("key")).isBlank()) {
				boolean enabled = !(v.get("enabled") instanceof Boolean b) || b;
				String value = str(v.get("value"));
				if ("secret".equals(v.get("type")) && value.isEmpty()) {
					emptySecrets++;
				}
				env.getVariables().add(new KeyValue(str(v.get("key")), value, enabled));
			}
		}
		if (emptySecrets > 0) {
			warnings.add(emptySecrets + " secret variable(s) had no value in the file (Postman does not export "
					+ "secret values); fill them in under Manage Environments.");
		}
		return new EnvironmentResult(env, List.copyOf(warnings));
	}

	private static Map<?, ?> parseObject(String json) {
		Object parsed;
		try {
			parsed = Json.parse(json);
		} catch (JsonException e) {
			throw new PostmanImportException("The file is not valid JSON: " + e.getMessage());
		}
		if (!(parsed instanceof Map<?, ?> root)) {
			throw new PostmanImportException("The file does not contain a JSON object.");
		}
		return root;
	}

	/** One collection import; collects warnings while walking the tree. */
	private static final class CollectionImport {
		private final Map<?, ?> root;
		private final List<String> warnings = new ArrayList<>();
		private final Set<String> unsupportedAuth = new LinkedHashSet<>();
		private final Set<String> unsupportedMethods = new LinkedHashSet<>();
		private int scripts;
		private int requests;
		private int missingFilePaths;
		private boolean dynamicVariables;

		CollectionImport(Map<?, ?> root) {
			this.root = root;
		}

		CollectionResult run() {
			if (detect(root) != Kind.POSTMAN_COLLECTION) {
				throw new PostmanImportException("This is not a Postman collection file.");
			}
			if (!(root.get("info") instanceof Map<?, ?> info)) {
				throw new PostmanImportException("This is a Postman v1 collection, which is no longer supported. "
						+ "In Postman, export it again as Collection v2.1 and import that file.");
			}
			String name = str(info.get("name"));
			RequestCollection collection = new RequestCollection(name.isBlank() ? "Imported Collection" : name);
			for (Object o : list(root.get("variable"))) {
				if (o instanceof Map<?, ?> v && !str(v.get("key")).isBlank()) {
					collection.getVariables().add(new KeyValue(str(v.get("key")), str(v.get("value")),
							!(v.get("disabled") instanceof Boolean b && b)));
				}
			}
			countScripts(root);
			items(root.get("item"), collection, collection.getId(), map(root.get("auth")));
			summarize();
			return new CollectionResult(collection, List.copyOf(warnings));
		}

		private void items(Object items, NodeContainer into, String collectionId, Map<?, ?> inheritedAuth) {
			for (Object o : list(items)) {
				if (!(o instanceof Map<?, ?> item)) {
					continue;
				}
				countScripts(item);
				if (item.get("item") instanceof List<?> children) {
					Folder folder = new Folder(str(item.get("name")).isBlank() ? "Folder" : str(item.get("name")));
					Map<?, ?> auth = item.containsKey("auth") && item.get("auth") != null ? map(item.get("auth"))
							: inheritedAuth;
					items(children, folder, collectionId, auth);
					into.getItems().add(folder);
				} else {
					RequestModel r = request(item, inheritedAuth);
					r.setCollectionId(collectionId);
					into.getItems().add(r);
					requests++;
				}
			}
		}

		private RequestModel request(Map<?, ?> item, Map<?, ?> inheritedAuth) {
			RequestModel r = new RequestModel();
			r.setName(str(item.get("name")).isBlank() ? "Request" : str(item.get("name")));
			Object req = item.get("request");
			if (req instanceof String url) { // v2: a request may be just its URL
				r.setUrl(url);
				return r;
			}
			Map<?, ?> m = map(req);
			String method = str(m.get("method")).toUpperCase(Locale.ROOT);
			HttpMethod parsed = HttpMethod.parse(method);
			if (!method.isEmpty() && !parsed.name().equals(method)) {
				unsupportedMethods.add(method);
			}
			r.setMethod(parsed);
			r.setUrl(url(m.get("url")));
			headers(m.get("header"), r);
			body(map(m.get("body")), r);
			Map<?, ?> auth = m.containsKey("auth") && m.get("auth") != null ? map(m.get("auth")) : inheritedAuth;
			auth(auth, r);
			dropRedundantContentType(r);
			noteDynamicVariables(r);
			return r;
		}

		/** URL from the raw string, or rebuilt from its parts; ":name" path variables are filled in when given. */
		private String url(Object value) {
			if (value instanceof String s) {
				return s;
			}
			Map<?, ?> u = map(value);
			String url = str(u.get("raw"));
			if (url.isEmpty()) {
				url = rebuildUrl(u);
			}
			for (Object o : list(u.get("variable"))) {
				if (o instanceof Map<?, ?> v && !str(v.get("key")).isEmpty() && !str(v.get("value")).isEmpty()) {
					url = url.replaceAll("/:" + Pattern.quote(str(v.get("key"))) + "(?=[/?#]|$)",
							Matcher.quoteReplacement("/" + str(v.get("value"))));
				}
			}
			return url;
		}

		private String rebuildUrl(Map<?, ?> u) {
			StringBuilder sb = new StringBuilder();
			if (!str(u.get("protocol")).isEmpty()) {
				sb.append(str(u.get("protocol"))).append("://");
			}
			sb.append(joinParts(u.get("host"), "."));
			if (!str(u.get("port")).isEmpty()) {
				sb.append(':').append(str(u.get("port")));
			}
			String path = joinParts(u.get("path"), "/");
			if (!path.isEmpty()) {
				sb.append(path.startsWith("/") ? "" : "/").append(path);
			}
			List<String> query = new ArrayList<>();
			for (Object o : list(u.get("query"))) {
				if (o instanceof Map<?, ?> q && !(q.get("disabled") instanceof Boolean b && b)) {
					query.add(str(q.get("key")) + (q.get("value") == null ? "" : "=" + str(q.get("value"))));
				}
			}
			if (!query.isEmpty()) {
				sb.append('?').append(String.join("&", query));
			}
			return sb.toString();
		}

		private static String joinParts(Object parts, String sep) {
			if (parts instanceof List<?> l) {
				List<String> out = new ArrayList<>();
				for (Object p : l) {
					out.add(p instanceof Map<?, ?> pm ? str(pm.get("value")) : str(p));
				}
				return String.join(sep, out);
			}
			return str(parts);
		}

		private void headers(Object value, RequestModel r) {
			if (value instanceof String s) { // v2.0 allowed "Name: value\nName2: value2"
				for (String line : s.split("\\r?\\n")) {
					int colon = line.indexOf(':');
					if (colon > 0) {
						r.getHeaders().add(new KeyValue(line.substring(0, colon).trim(), line.substring(colon + 1).trim()));
					}
				}
				return;
			}
			for (Object o : list(value)) {
				if (o instanceof Map<?, ?> h && !str(h.get("key")).isBlank()) {
					r.getHeaders().add(new KeyValue(str(h.get("key")), str(h.get("value")),
							!(h.get("disabled") instanceof Boolean b && b)));
				}
			}
		}

		private void body(Map<?, ?> body, RequestModel r) {
			String mode = str(body.get("mode"));
			switch (mode) {
			case "raw" -> {
				String text = str(body.get("raw"));
				if (text.isEmpty()) {
					return;
				}
				String language = str(map(map(body.get("options")).get("raw")).get("language")).toLowerCase(Locale.ROOT);
				r.setBodyType(switch (language) {
				case "json" -> BodyType.JSON;
				case "xml" -> BodyType.XML;
				case "" -> typeFromContentType(r);
				default -> BodyType.TEXT;
				});
				r.setBody(text);
			}
			case "urlencoded" -> urlencodedBody(body.get("urlencoded"), r);
			case "formdata" -> multipartBody(body.get("formdata"), r);
			case "graphql" -> {
				Map<?, ?> g = map(body.get("graphql"));
				Map<String, Object> payload = new LinkedHashMap<>();
				payload.put("query", str(g.get("query")));
				String vars = str(g.get("variables")).trim();
				Object parsedVars = Map.of();
				if (!vars.isEmpty()) {
					try {
						parsedVars = Json.parse(vars);
					} catch (JsonException e) {
						warnings.add("GraphQL variables of \"" + r.getName() + "\" are not valid JSON and were left out.");
					}
				}
				payload.put("variables", parsedVars);
				r.setBodyType(BodyType.JSON);
				r.setBody(Json.writePretty(payload));
			}
			case "file" -> {
				String path = firstPath(map(body.get("file")).get("src"));
				r.setBodyType(BodyType.BINARY);
				r.setBinaryFile(path);
				if (path.isEmpty()) {
					missingFilePaths++;
				}
			}
			default -> {
			}
			}
		}

		private static void urlencodedBody(Object fields, RequestModel r) {
			List<String> pairs = new ArrayList<>();
			for (Object o : list(fields)) {
				if (o instanceof Map<?, ?> f && !(f.get("disabled") instanceof Boolean b && b)) {
					pairs.add(encodeKeepingVariables(str(f.get("key"))) + "=" + encodeKeepingVariables(str(f.get("value"))));
				}
			}
			if (!pairs.isEmpty()) {
				r.setBodyType(BodyType.FORM);
				r.setBody(String.join("&", pairs));
			}
		}

		/** Text and file fields; disabled fields are kept (disabled), as Postman shows them. */
		private void multipartBody(Object fields, RequestModel r) {
			for (Object o : list(fields)) {
				if (!(o instanceof Map<?, ?> f) || str(f.get("key")).isBlank()) {
					continue;
				}
				boolean enabled = !(f.get("disabled") instanceof Boolean b && b);
				if ("file".equals(f.get("type"))) {
					String path = firstPath(f.get("src"));
					if (path.isEmpty()) {
						missingFilePaths++;
					}
					r.getFormFields().add(new FormField(str(f.get("key")), path, enabled, true));
				} else {
					r.getFormFields().add(new FormField(str(f.get("key")), str(f.get("value")), enabled, false));
				}
			}
			if (!r.getFormFields().isEmpty()) {
				r.setBodyType(BodyType.MULTIPART);
				r.getHeaders().removeIf(h -> h.getKey().equalsIgnoreCase("Content-Type")
						&& h.getValue().toLowerCase(Locale.ROOT).startsWith("multipart/form-data"));
			}
		}

		/** Postman stores a file path as a string, or a list when several files were picked (first one used). */
		private static String firstPath(Object src) {
			if (src instanceof List<?> l) {
				return l.isEmpty() ? "" : str(l.get(0));
			}
			return str(src);
		}

		private static BodyType typeFromContentType(RequestModel r) {
			for (KeyValue h : r.getHeaders()) {
				if (h.isActive() && h.getKey().equalsIgnoreCase("Content-Type")) {
					String v = h.getValue().toLowerCase(Locale.ROOT);
					if (v.contains("json")) {
						return BodyType.JSON;
					}
					if (v.contains("xml")) {
						return BodyType.XML;
					}
					if (v.contains("x-www-form-urlencoded")) {
						return BodyType.FORM;
					}
				}
			}
			return BodyType.TEXT;
		}

		private void auth(Map<?, ?> auth, RequestModel r) {
			String type = str(auth.get("type"));
			Auth a = r.getAuth();
			switch (type) {
			case "", "noauth", "inherit" -> {
			}
			case "bearer" -> {
				a.setType(AuthType.BEARER);
				a.setToken(authParam(auth, "bearer", "token"));
			}
			case "basic" -> {
				a.setType(AuthType.BASIC);
				a.setUsername(authParam(auth, "basic", "username"));
				a.setPassword(authParam(auth, "basic", "password"));
			}
			case "apikey" -> {
				String key = authParam(auth, "apikey", "key");
				String value = authParam(auth, "apikey", "value");
				if (!key.isBlank()) {
					if ("query".equals(authParam(auth, "apikey", "in"))) {
						r.getParams().add(new KeyValue(key, value));
					} else {
						r.getHeaders().add(new KeyValue(key, value));
					}
				}
			}
			default -> unsupportedAuth.add(type);
			}
		}

		/** v2.1 stores auth fields as [{key, value}], v2.0 as {key: value}. */
		private static String authParam(Map<?, ?> auth, String type, String key) {
			Object params = auth.get(type);
			if (params instanceof List<?> l) {
				for (Object o : l) {
					if (o instanceof Map<?, ?> p && key.equals(p.get("key"))) {
						return str(p.get("value"));
					}
				}
				return "";
			}
			return str(map(params).get(key));
		}

		private static void dropRedundantContentType(RequestModel r) {
			if (!r.hasBody()) {
				return;
			}
			String expected = r.getBodyType().contentType();
			r.getHeaders().removeIf(h -> h.isActive() && h.getKey().equalsIgnoreCase("Content-Type")
					&& h.getValue().trim().equalsIgnoreCase(expected));
		}

		private void noteDynamicVariables(RequestModel r) {
			if (dynamicVariables) {
				return;
			}
			StringBuilder all = new StringBuilder(r.getUrl()).append(r.getBody());
			r.getHeaders().forEach(h -> all.append(h.getValue()));
			r.getParams().forEach(p -> all.append(p.getValue()));
			dynamicVariables = all.indexOf("{{$") >= 0;
		}

		private void countScripts(Map<?, ?> node) {
			for (Object o : list(node.get("event"))) {
				if (o instanceof Map<?, ?> e) {
					Object exec = map(e.get("script")).get("exec");
					boolean hasCode = exec instanceof List<?> l ? l.stream().anyMatch(x -> !str(x).isBlank())
							: !str(exec).isBlank();
					if (hasCode) {
						scripts++;
					}
				}
			}
		}

		private void summarize() {
			if (requests == 0) {
				warnings.add("The collection contains no requests.");
			}
			if (scripts > 0) {
				warnings.add(scripts + " pre-request or test script(s) were skipped; GetMan does not run scripts.");
			}
			if (!unsupportedAuth.isEmpty()) {
				warnings.add("Auth type(s) " + String.join(", ", unsupportedAuth) + " are not supported; those "
						+ "requests were imported without auth.");
			}
			if (!unsupportedMethods.isEmpty()) {
				warnings.add("Method(s) " + String.join(", ", unsupportedMethods) + " are not supported; GET was used.");
			}
			if (missingFilePaths > 0) {
				warnings.add(missingFilePaths + " file field(s) or file body(ies) have no file path in the export; "
						+ "choose the files in the Body tab.");
			}
			if (dynamicVariables) {
				warnings.add("Postman dynamic variables such as {{$guid}} or {{$timestamp}} are not supported "
						+ "and are left as written.");
			}
		}
	}

	/** URL-encodes text but leaves {{variables}} intact, so they still resolve later. */
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

	private static String str(Object o) {
		if (o == null) {
			return "";
		}
		if (o instanceof Double d && d == Math.rint(d) && !d.isInfinite()) {
			return String.valueOf(d.longValue());
		}
		return o instanceof String s ? s : String.valueOf(o);
	}

	private static List<?> list(Object o) {
		return o instanceof List<?> l ? l : List.of();
	}

	private static Map<?, ?> map(Object o) {
		return o instanceof Map<?, ?> m ? m : Map.of();
	}
}
