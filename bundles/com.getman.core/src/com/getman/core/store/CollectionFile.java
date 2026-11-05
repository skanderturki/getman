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
import com.getman.core.model.CollectionNode;
import com.getman.core.model.Folder;
import com.getman.core.model.NodeContainer;
import com.getman.core.model.RequestCollection;
import com.getman.core.model.RequestModel;

/** Exports a collection to a standalone JSON file and imports it back, e.g. to share it or commit it to git. */
public final class CollectionFile {
	static final String FORMAT_KEY = "getmanCollection";
	/** 2: folders ("items") and collection variables. Version-1 files (flat "requests") still import. */
	static final int FORMAT_VERSION = 2;

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
		Map<String, Object> root = new LinkedHashMap<>();
		root.put(FORMAT_KEY, FORMAT_VERSION);
		// Round-trip through the codec to get an independent copy, then strip secrets from the copy.
		RequestCollection exported = StateCodec.collectionFromMap(StateCodec.collectionToMap(collection));
		if (!includeSecrets) {
			exported.allRequests().forEach(r -> r.getAuth().clearSecrets());
		}
		root.putAll(StateCodec.collectionToMap(exported));
		root.remove("id");
		root.remove("requests"); // export files always carry the tree
		return Json.writePretty(root);
	}

	/** Parses an exported collection. Ids are always fresh, so importing a file twice gives two copies. */
	public static RequestCollection fromJson(String json) {
		Object parsed = Json.parse(json);
		if (!(parsed instanceof Map<?, ?> root) || !root.containsKey(FORMAT_KEY)) {
			throw new JsonException("Not a GetMan collection file");
		}
		RequestCollection c = StateCodec.collectionFromMap(root);
		freshIds(c);
		return c;
	}

	public static void write(RequestCollection collection, Path file, boolean includeSecrets) throws IOException {
		Files.writeString(file, toJson(collection, includeSecrets), StandardCharsets.UTF_8);
	}

	/** Whether any request in the collection stores a token or password. */
	public static boolean hasSecrets(RequestCollection collection) {
		return collection.allRequests().stream().anyMatch(r -> r.getAuth().hasSecrets());
	}

	/** New ids for the collection, its folders and requests, so an imported copy never clashes with the original. */
	public static void freshIds(RequestCollection c) {
		c.setId(UUID.randomUUID().toString());
		freshIds(c, c.getId());
	}

	private static void freshIds(NodeContainer container, String collectionId) {
		for (CollectionNode node : container.getItems()) {
			if (node instanceof Folder f) {
				f.setId(UUID.randomUUID().toString());
				freshIds(f, collectionId);
			} else if (node instanceof RequestModel r) {
				r.setId(UUID.randomUUID().toString());
				r.setCollectionId(collectionId);
			}
		}
	}

	public static RequestCollection read(Path file) throws IOException {
		return fromJson(Files.readString(file, StandardCharsets.UTF_8));
	}
}
