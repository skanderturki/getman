package com.getman.core.curl;

import com.getman.core.http.UrlBuilder;
import com.getman.core.model.Auth;
import com.getman.core.model.AuthType;
import com.getman.core.model.BodyType;
import com.getman.core.model.FormField;
import com.getman.core.model.HttpMethod;
import com.getman.core.model.KeyValue;
import com.getman.core.model.RequestModel;

/** Renders a request as a POSIX-shell cURL command (bash, zsh, Git Bash, WSL). */
public final class CurlExporter {

	private CurlExporter() {
	}

	public static String toCurl(RequestModel request) {
		StringBuilder sb = new StringBuilder("curl");
		HttpMethod method = request.getMethod();
		if (method == HttpMethod.HEAD) {
			sb.append(" -I");
		} else if (method != HttpMethod.GET || request.hasBody()) {
			sb.append(" -X ").append(method.name());
		}
		sb.append(' ').append(quote(UrlBuilder.buildUrl(request)));
		for (KeyValue h : request.getHeaders()) {
			if (h.isActive()) {
				sb.append(" \\\n  -H ").append(quote(h.getKey().trim() + ": " + h.getValue()));
			}
		}
		if (request.authorizationFromAuth() != null) {
			Auth auth = request.getAuth();
			if (auth.getType() == AuthType.BASIC) {
				sb.append(" \\\n  -u ").append(quote(auth.getUsername() + ":" + auth.getPassword()));
			} else {
				sb.append(" \\\n  -H ").append(quote(Auth.HEADER + ": " + auth.headerValue()));
			}
		}
		BodyType type = request.getBodyType();
		if (request.hasBody() && type == BodyType.MULTIPART) {
			// curl writes the multipart Content-Type (with boundary) itself.
			for (FormField f : request.getFormFields()) {
				if (f.isActive()) {
					sb.append(f.isFile() ? " \\\n  -F " + quote(f.getKey().trim() + "=@" + f.getValue())
							: " \\\n  --form-string " + quote(f.getKey().trim() + "=" + f.getValue()));
				}
			}
			return sb.toString();
		}
		String contentType = type.contentType();
		if (request.hasBody() && contentType != null && !request.hasActiveHeader("Content-Type")) {
			sb.append(" \\\n  -H ").append(quote("Content-Type: " + contentType));
		}
		if (request.hasBody() && type == BodyType.BINARY) {
			sb.append(" \\\n  --data-binary ").append(quote("@" + request.getBinaryFile()));
		} else if (request.hasBody()) {
			sb.append(" \\\n  --data-raw ").append(quote(request.getBody()));
		}
		return sb.toString();
	}

	/** Single-quotes for POSIX shells; an embedded ' becomes '\''. */
	static String quote(String s) {
		return "'" + s.replace("'", "'\\''") + "'";
	}
}
