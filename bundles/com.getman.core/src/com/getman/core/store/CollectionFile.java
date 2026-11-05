package com.getman.core.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.getman.core.json.Json;
import com.getman.core.json.JsonException;
import com.getman.core.model.RequestCollection;
import com.getman.core.model.RequestModel;

/** Exports a collection to a standalone JSON file and imports it back, e.g. to share it or commit it to git. */
public final class CollectionFile {
	static final String FORMAT_KEY = "getmanCollection";
	static final int FORMAT_VERSION = 1;

	private CollectionFile() {
	}

	/** Exports without tokens and passwords. */
	public static String toJson(RequestCollection collection) {
		return toJson(collection, false);
	}

	/**
	 * @param includeSecrets whether auth tokens and passwords are written; when {@code false} the auth type and
	 *                       username are kept so the recipient only has to fill in the secret
	 */
	public static String toJson(RequestCollection collection, boolean includeSecrets) {
		RequestCollection exported = collection;
		if (!includeSecrets) {
			exported = new RequestCollection(collection.getName());
			exported.setId(collection.getId());
			for (RequestModel r : collection.getRequests()) {
				RequestModel c = r.copy();
				c.getAuth().clearSecrets();
				exported.getRequests().add(c);
			}
		}
		Map<String, Object> root = new LinkedHashMap<>();
		root.put(FORMAT_KEY, FORMAT_VERSION);
		root.putAll(StateCodec.collectionToMap(exported));
		root.remove("id");
		return Json.writePretty(root);
	}

	/** Parses an exported collection. Ids are always fresh, so importing a file twice gives two copies. */
	public static RequestCollection fromJson(String json) {
		Object parsed = Json.parse(json);
		if (!(parsed instanceof Map<?, ?> root) || !root.containsKey(FORMAT_KEY)) {
			throw new JsonException("Not a GetMan collection file");
		}
		RequestCollection c = StateCodec.collectionFromMap(root);
		c.setId(UUID.randomUUID().toString());
		for (RequestModel r : c.getRequests()) {
			r.setId(UUID.randomUUID().toString());
			r.setCollectionId(c.getId());
		}
		return c;
	}

	public static void write(RequestCollection collection, Path file, boolean includeSecrets) throws IOException {
		Files.writeString(file, toJson(collection, includeSecrets), StandardCharsets.UTF_8);
	}

	/** Whether any request in the collection stores a token or password. */
	public static boolean hasSecrets(RequestCollection collection) {
		return collection.getRequests().stream().anyMatch(r -> r.getAuth().hasSecrets());
	}

	public static RequestCollection read(Path file) throws IOException {
		return fromJson(Files.readString(file, StandardCharsets.UTF_8));
	}
}
