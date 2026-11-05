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

	public static String toJson(RequestCollection collection) {
		Map<String, Object> root = new LinkedHashMap<>();
		root.put(FORMAT_KEY, FORMAT_VERSION);
		root.putAll(StateCodec.collectionToMap(collection));
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

	public static void write(RequestCollection collection, Path file) throws IOException {
		Files.writeString(file, toJson(collection), StandardCharsets.UTF_8);
	}

	public static RequestCollection read(Path file) throws IOException {
		return fromJson(Files.readString(file, StandardCharsets.UTF_8));
	}
}
