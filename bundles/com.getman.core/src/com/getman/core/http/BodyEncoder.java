package com.getman.core.http;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import com.getman.core.model.BodyType;
import com.getman.core.model.FormField;
import com.getman.core.model.RequestModel;

/**
 * Turns a request's body into a {@link BodyPublisher}. Text bodies are UTF-8; multipart bodies follow RFC 7578
 * with a random boundary; files (multipart file fields and binary bodies) are streamed from disk, not loaded into
 * memory. Problems such as a missing file throw {@link IllegalArgumentException} with a readable message.
 */
final class BodyEncoder {
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final String CRLF = "\r\n";

	/**
	 * @param contentType the Content-Type the body requires (multipart, with its boundary), or {@code null} to use
	 *                    the usual rule: the body type's default unless the user set a Content-Type header
	 */
	record Encoded(BodyPublisher publisher, String contentType) {
	}

	private BodyEncoder() {
	}

	static Encoded encode(RequestModel r) {
		return encode(r, newBoundary());
	}

	static Encoded encode(RequestModel r, String boundary) {
		if (!r.hasBody()) {
			return new Encoded(BodyPublishers.noBody(), null);
		}
		if (r.getBodyType() == BodyType.MULTIPART) {
			return new Encoded(multipart(r, boundary), "multipart/form-data; boundary=" + boundary);
		}
		if (r.getBodyType() == BodyType.BINARY) {
			return new Encoded(ofFile(existingFile(r.getBinaryFile(), "The binary body file")), null);
		}
		return new Encoded(BodyPublishers.ofString(r.getBody(), StandardCharsets.UTF_8), null);
	}

	static String newBoundary() {
		byte[] bytes = new byte[12];
		RANDOM.nextBytes(bytes);
		return "----GetManBoundary" + HexFormat.of().formatHex(bytes);
	}

	private static BodyPublisher multipart(RequestModel r, String boundary) {
		List<BodyPublisher> parts = new ArrayList<>();
		for (FormField f : r.getFormFields()) {
			if (!f.isActive()) {
				continue;
			}
			StringBuilder head = new StringBuilder("--").append(boundary).append(CRLF)
					.append("Content-Disposition: form-data; name=\"").append(quoted(f.getKey().trim())).append('"');
			if (f.isFile()) {
				Path file = existingFile(f.getValue(), "The file of form field \"" + f.getKey().trim() + "\"");
				head.append("; filename=\"").append(quoted(file.getFileName().toString())).append('"').append(CRLF)
						.append("Content-Type: ").append(contentTypeOf(file)).append(CRLF).append(CRLF);
				parts.add(BodyPublishers.ofString(head.toString(), StandardCharsets.UTF_8));
				parts.add(ofFile(file));
			} else {
				head.append(CRLF).append(CRLF).append(f.getValue());
				parts.add(BodyPublishers.ofString(head.toString(), StandardCharsets.UTF_8));
			}
			parts.add(BodyPublishers.ofString(CRLF, StandardCharsets.UTF_8));
		}
		parts.add(BodyPublishers.ofString("--" + boundary + "--" + CRLF, StandardCharsets.UTF_8));
		return HttpRequest.BodyPublishers.concat(parts.toArray(BodyPublisher[]::new));
	}

	/** RFC 7578 §4.2: escape quotes; line breaks are not allowed in the header. */
	static String quoted(String s) {
		return s.replace("\"", "%22").replace("\r", "%0D").replace("\n", "%0A");
	}

	private static String contentTypeOf(Path file) {
		try {
			String type = Files.probeContentType(file);
			if (type != null) {
				return type;
			}
		} catch (IOException e) {
			// fall through
		}
		return "application/octet-stream";
	}

	private static Path existingFile(String path, String what) {
		Path p;
		try {
			p = Path.of(path.trim());
		} catch (InvalidPathException e) {
			throw new IllegalArgumentException(what + " has an invalid path: " + path, e);
		}
		if (!Files.isRegularFile(p)) {
			throw new IllegalArgumentException(what + " was not found: " + path);
		}
		if (!Files.isReadable(p)) {
			throw new IllegalArgumentException(what + " cannot be read: " + path);
		}
		return p;
	}

	private static BodyPublisher ofFile(Path p) {
		try {
			return BodyPublishers.ofFile(p);
		} catch (FileNotFoundException e) {
			throw new IllegalArgumentException("File not found: " + p, e);
		}
	}
}
