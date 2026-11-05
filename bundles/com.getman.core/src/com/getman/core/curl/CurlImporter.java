package com.getman.core.curl;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.getman.core.model.Auth;
import com.getman.core.model.AuthType;
import com.getman.core.model.BodyType;
import com.getman.core.model.HttpMethod;
import com.getman.core.model.KeyValue;
import com.getman.core.model.RequestModel;

/**
 * Turns a pasted cURL command into a {@link RequestModel}. Supports the options people actually paste: URL,
 * method, headers, data in all its forms, {@code --json}, {@code -G}, {@code -I}, basic and bearer auth, cookies,
 * user agent and referer. Transport-only options (timeouts, proxies, TLS, output) are ignored; anything that
 * cannot be represented is reported in {@link Result#warnings()} rather than failing the import.
 */
public final class CurlImporter {

	/** The imported request plus notes about what was ignored or changed. */
	public record Result(RequestModel request, List<String> warnings) {
	}

	private static final Pattern CURL_START = Pattern.compile("(?is)^\\s*(?:\\$\\s+)?(?:\\S*[/\\\\])?curl(?:\\.exe)?\\s.*");
	private static final Pattern URL_LIKE = Pattern.compile(
			"(?i)^(https?://|www\\.|localhost|\\[|\\d{1,3}(\\.\\d{1,3}){3}|[a-z0-9-]+(\\.[a-z0-9-]+)+([:/?#]|$)).*");

	/** Options that take a value but do not affect the request GetMan sends. */
	private static final Set<String> IGNORED_WITH_ARG = Set.of("-o", "--output", "-m", "--max-time", "--connect-timeout",
			"-w", "--write-out", "-x", "--proxy", "-U", "--proxy-user", "--cacert", "--capath", "-E", "--cert", "--key",
			"--cert-type", "--key-type", "-c", "--cookie-jar", "--resolve", "--connect-to", "--retry", "--retry-delay",
			"--retry-max-time", "--limit-rate", "--max-redirs", "--interface", "--dns-servers", "-Y", "--speed-limit",
			"-y", "--speed-time", "--max-filesize", "--trace", "--trace-ascii", "--stderr", "--expect100-timeout",
			"--keepalive-time", "--local-port", "--happy-eyeballs-timeout-ms", "--ciphers", "--pinnedpubkey",
			"--unix-socket", "--abstract-unix-socket", "--proto", "--proto-redir", "--proxy-header");

	/** Flags without a value that do not affect the request GetMan sends. */
	private static final Set<String> IGNORED_FLAGS = Set.of("-s", "--silent", "-S", "--show-error", "-v", "--verbose",
			"-L", "--location", "--location-trusted", "-k", "--insecure", "-i", "--include", "--compressed", "-f",
			"--fail", "--fail-with-body", "-#", "--progress-bar", "-N", "--no-buffer", "-g", "--globoff", "--http1.0",
			"--http1.1", "--http2", "--http2-prior-knowledge", "--http3", "-0", "-1", "-2", "-3", "-4", "-6", "-q",
			"-n", "--netrc", "-j", "--junk-session-cookies", "-l", "--list-only", "-O", "--remote-name", "-J",
			"--remote-header-name", "-Z", "--parallel", "--raw", "--tr-encoding", "--no-keepalive", "--tcp-nodelay",
			"--path-as-is", "--basic", "--ssl", "--ssl-reqd", "--tlsv1", "--tlsv1.0", "--tlsv1.1", "--tlsv1.2",
			"--tlsv1.3", "--no-progress-meter", "--no-sessionid", "--styled-output", "--create-dirs", "--disable",
			"--disable-eprt", "--disable-epsv", "--no-alpn", "--no-npn", "--ipv4", "--ipv6");

	/** Short options that take a value; used to split clusters like {@code -sXPOST}. */
	private static final String SHORT_WITH_ARG = "XHdubAeFoTmwxUEcYyrK";

	private CurlImporter() {
	}

	/** Cheap check for "this pasted text is a cURL command". */
	public static boolean looksLikeCurl(String text) {
		return text != null && CURL_START.matcher(text).matches();
	}

	public static Result parse(String command) {
		ShellWords.Result split = ShellWords.split(stripPrompt(command));
		List<String> words = split.words();
		if (words.isEmpty() || !isCurl(words.get(0))) {
			throw new CurlParseException("This does not look like a cURL command (it should start with \"curl\").");
		}
		return new Parser(words.subList(1, words.size()), split.truncated()).run();
	}

	private static String stripPrompt(String command) {
		String t = command.strip();
		return t.startsWith("$ ") ? t.substring(2) : t;
	}

	private static boolean isCurl(String word) {
		String w = word.toLowerCase(Locale.ROOT).replace('\\', '/');
		String name = w.substring(w.lastIndexOf('/') + 1);
		return name.equals("curl") || name.equals("curl.exe");
	}

	/** One parse run; collects state while walking the options. */
	private static final class Parser {
		private final List<String> args;
		private final List<String> warnings = new ArrayList<>();
		private final List<String> positionals = new ArrayList<>();
		private final List<String> dataParts = new ArrayList<>();
		private final RequestModel request = new RequestModel();
		private String method;
		private boolean head;
		private boolean get;
		private boolean json;
		private boolean formIgnored;

		Parser(List<String> args, boolean truncated) {
			this.args = args;
			if (truncated) {
				warnings.add("Everything after an unquoted |, ;, & or > was ignored. If the URL contains &, put it "
						+ "in quotes.");
			}
		}

		Result run() {
			Iterator<String> it = args.iterator();
			boolean endOfOptions = false;
			while (it.hasNext()) {
				String arg = it.next();
				if (endOfOptions || !arg.startsWith("-") || arg.equals("-")) {
					positionals.add(arg);
				} else if (arg.equals("--")) {
					endOfOptions = true;
				} else if (arg.startsWith("--")) {
					option(arg, it);
				} else {
					shortCluster(arg, it);
				}
			}
			finish();
			return new Result(request, List.copyOf(warnings));
		}

		/** {@code -sSL}, {@code -XPOST}, {@code -H'Accept: x'}. */
		private void shortCluster(String arg, Iterator<String> it) {
			for (int i = 1; i < arg.length(); i++) {
				String opt = "-" + arg.charAt(i);
				if (SHORT_WITH_ARG.indexOf(arg.charAt(i)) >= 0) {
					String rest = arg.substring(i + 1);
					if (!rest.isEmpty()) {
						option(opt, List.of(rest).iterator());
					} else {
						option(opt, it);
					}
					return;
				}
				option(opt, it);
			}
		}

		private String value(String opt, Iterator<String> it) {
			if (!it.hasNext()) {
				throw new CurlParseException("Option " + opt + " needs a value.");
			}
			return it.next();
		}

		private void option(String opt, Iterator<String> it) {
			switch (opt) {
			case "-X", "--request" -> method = value(opt, it).toUpperCase(Locale.ROOT);
			case "-H", "--header" -> header(value(opt, it));
			case "-d", "--data", "--data-ascii", "--data-binary" -> data(value(opt, it), true);
			case "--data-raw" -> data(value(opt, it), false);
			case "--data-urlencode" -> dataUrlencode(value(opt, it));
			case "--json" -> {
				json = true;
				data(value(opt, it), true);
			}
			case "-u", "--user" -> user(value(opt, it));
			case "--oauth2-bearer" -> {
				request.getAuth().setType(AuthType.BEARER);
				request.getAuth().setToken(value(opt, it));
			}
			case "-A", "--user-agent" -> request.getHeaders().add(new KeyValue("User-Agent", value(opt, it)));
			case "-e", "--referer" -> request.getHeaders().add(new KeyValue("Referer", value(opt, it)));
			case "-b", "--cookie" -> cookie(value(opt, it));
			case "--url" -> positionals.add(0, value(opt, it));
			case "-G", "--get" -> get = true;
			case "-I", "--head" -> head = true;
			case "-F", "--form", "--form-string" -> {
				value(opt, it);
				if (!formIgnored) {
					warnings.add("Multipart form fields (-F) are not supported yet and were skipped.");
					formIgnored = true;
				}
			}
			case "-T", "--upload-file" -> warnings.add("Uploaded file \"" + value(opt, it) + "\" (-T) was not imported.");
			case "-K", "--config" -> warnings.add("Config file \"" + value(opt, it) + "\" (-K) was not read.");
			case "-r", "--range" -> request.getHeaders().add(new KeyValue("Range", "bytes=" + value(opt, it)));
			case "--digest", "--ntlm", "--negotiate", "--anyauth" ->
				warnings.add(opt + " authentication is not supported; the username and password were imported as Basic auth.");
			default -> {
				if (IGNORED_WITH_ARG.contains(opt)) {
					value(opt, it);
				} else if (!IGNORED_FLAGS.contains(opt)) {
					warnings.add("Unknown option " + opt + " was ignored.");
				}
			}
			}
		}

		private void header(String raw) {
			int colon = raw.indexOf(':');
			if (colon <= 0) {
				if (raw.endsWith(";") && raw.length() > 1) { // curl: "Name;" sends an empty header
					request.getHeaders().add(new KeyValue(raw.substring(0, raw.length() - 1).trim(), ""));
				} else {
					warnings.add("Header \"" + raw + "\" has no colon and was skipped.");
				}
				return;
			}
			String name = raw.substring(0, colon).trim();
			String value = raw.substring(colon + 1).trim();
			if (value.isEmpty()) {
				return; // curl: "Name:" removes a default header; nothing to send
			}
			request.getHeaders().add(new KeyValue(name, value));
		}

		private void data(String value, boolean fileAllowed) {
			if (fileAllowed && value.startsWith("@")) {
				warnings.add("The body was read from file \"" + value.substring(1) + "\" by cURL and could not be "
						+ "imported; paste the file's content into the Body tab.");
				return;
			}
			dataParts.add(value);
		}

		/** curl's rules: "content", "=content", "name=content", "@file", "name@file". */
		private void dataUrlencode(String value) {
			int eq = value.indexOf('=');
			int at = value.indexOf('@');
			if (at >= 0 && (eq < 0 || at < eq)) {
				warnings.add("--data-urlencode from file \"" + value.substring(at + 1) + "\" could not be imported.");
				return;
			}
			if (eq < 0) {
				dataParts.add(encode(value));
			} else if (eq == 0) {
				dataParts.add(encode(value.substring(1)));
			} else {
				dataParts.add(value.substring(0, eq) + "=" + encode(value.substring(eq + 1)));
			}
		}

		private void user(String value) {
			Auth auth = request.getAuth();
			auth.setType(AuthType.BASIC);
			int colon = value.indexOf(':');
			if (colon < 0) {
				auth.setUsername(value);
				warnings.add("No password was given with -u; cURL would ask for it. Enter it in the Auth tab.");
			} else {
				auth.setUsername(value.substring(0, colon));
				auth.setPassword(value.substring(colon + 1));
			}
		}

		private void cookie(String value) {
			if (!value.contains("=")) {
				warnings.add("Cookie file \"" + value + "\" (-b) was not read.");
				return;
			}
			request.getHeaders().add(new KeyValue("Cookie", value));
		}

		private void finish() {
			request.setUrl(pickUrl());

			String data = String.join("&", dataParts);
			boolean hasData = !dataParts.isEmpty();
			if (get && hasData) {
				String url = request.getUrl();
				int hash = url.indexOf('#');
				String fragment = hash >= 0 ? url.substring(hash) : "";
				String base = hash >= 0 ? url.substring(0, hash) : url;
				request.setUrl(base + (base.contains("?") ? "&" : "?") + data + fragment);
				hasData = false;
			}

			HttpMethod m;
			if (method != null) {
				m = HttpMethod.parse(method);
				if (!m.name().equals(method)) {
					warnings.add("Method " + method + " is not supported; GET was used instead.");
				}
			} else if (head) {
				m = HttpMethod.HEAD;
			} else if (hasData) {
				m = HttpMethod.POST;
			} else {
				m = HttpMethod.GET;
			}
			request.setMethod(m);

			if (json) {
				addHeaderIfMissing("Content-Type", "application/json");
				addHeaderIfMissing("Accept", "application/json");
			}
			if (hasData) {
				applyBody(data);
			}
			convertAuthorizationHeader();
			request.setName(nameFor(m, request.getUrl()));
		}

		private String pickUrl() {
			if (positionals.isEmpty()) {
				throw new CurlParseException("No URL found in the cURL command.");
			}
			String url = positionals.stream().filter(p -> URL_LIKE.matcher(p).matches()).findFirst()
					.orElse(positionals.get(0));
			if (positionals.size() > 1) {
				warnings.add("cURL was given " + positionals.size() + " URLs or extra arguments; only " + url
						+ " was imported.");
			}
			return url;
		}

		/** Body type from the Content-Type header, like cURL would send it (plain -d means form-encoded). */
		private void applyBody(String data) {
			KeyValue ct = findHeader("Content-Type");
			String type = ct == null ? "" : ct.getValue().toLowerCase(Locale.ROOT);
			BodyType bodyType;
			if (type.isEmpty()) {
				bodyType = BodyType.FORM;
			} else if (type.contains("json")) {
				bodyType = BodyType.JSON;
			} else if (type.contains("xml")) {
				bodyType = BodyType.XML;
			} else if (type.contains("x-www-form-urlencoded")) {
				bodyType = BodyType.FORM;
			} else {
				bodyType = BodyType.TEXT;
			}
			request.setBodyType(bodyType);
			request.setBody(data);
			// The body type sends its own Content-Type; keep the header only when it says something more.
			if (ct != null && ct.getValue().trim().equalsIgnoreCase(bodyType.contentType())) {
				request.getHeaders().remove(ct);
			}
		}

		/** "Authorization: Bearer x" and decodable "Authorization: Basic x" headers become Auth settings. */
		private void convertAuthorizationHeader() {
			KeyValue h = findHeader(Auth.HEADER);
			if (h == null || request.getAuth().getType() != AuthType.NONE) {
				return;
			}
			String v = h.getValue().trim();
			Auth auth = request.getAuth();
			if (v.regionMatches(true, 0, "Bearer ", 0, 7) && !v.substring(7).isBlank()) {
				auth.setType(AuthType.BEARER);
				auth.setToken(v.substring(7).trim());
				request.getHeaders().remove(h);
			} else if (v.regionMatches(true, 0, "Basic ", 0, 6)) {
				try {
					String decoded = new String(Base64.getDecoder().decode(v.substring(6).trim()), StandardCharsets.UTF_8);
					int colon = decoded.indexOf(':');
					if (colon >= 0) {
						auth.setType(AuthType.BASIC);
						auth.setUsername(decoded.substring(0, colon));
						auth.setPassword(decoded.substring(colon + 1));
						request.getHeaders().remove(h);
					}
				} catch (IllegalArgumentException e) {
					// not valid Base64: keep it as a plain header
				}
			}
		}

		private KeyValue findHeader(String name) {
			KeyValue found = null;
			for (KeyValue h : request.getHeaders()) {
				if (h.getKey().equalsIgnoreCase(name)) {
					found = h; // last one wins, as in cURL
				}
			}
			return found;
		}

		private void addHeaderIfMissing(String name, String value) {
			if (findHeader(name) == null) {
				request.getHeaders().add(new KeyValue(name, value));
			}
		}
	}

	/** Tab name such as "POST /users" from the method and the URL path. */
	static String nameFor(HttpMethod method, String url) {
		String rest = url.replaceFirst("(?i)^[a-z][a-z0-9+.-]*://", "");
		int slash = rest.indexOf('/');
		String path = slash < 0 ? "/" : rest.substring(slash);
		path = path.replaceFirst("[?#].*$", "");
		if (path.isEmpty()) {
			path = "/";
		}
		if (path.length() > 40) {
			path = "…" + path.substring(path.length() - 39);
		}
		return method.name() + " " + path;
	}

	private static String encode(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}
}
