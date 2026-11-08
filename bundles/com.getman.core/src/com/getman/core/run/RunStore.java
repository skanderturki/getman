package com.getman.core.run;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.getman.core.json.Json;
import com.getman.core.json.JsonException;
import com.getman.core.run.CollectionRunner.Outcome;

/**
 * Finished collection runs in their own JSON file, newest first. Like the request history, a damaged file is
 * treated as empty. Runs hold no secrets (values of secret variables are masked when the run is recorded).
 */
public final class RunStore {
	static final int FORMAT_VERSION = 1;
	/** Runs kept per collection. */
	public static final int MAX_PER_COLLECTION = 20;
	/** Runs kept in all. */
	public static final int MAX_TOTAL = 100;

	private final Path file;

	public RunStore(Path file) {
		this.file = file;
	}

	public Path getFile() {
		return file;
	}

	/** Runs newest first; empty when there is no file or it cannot be read. */
	public List<RunRecord> load() {
		if (!Files.exists(file)) {
			return new ArrayList<>();
		}
		try {
			return fromJson(Files.readString(file, StandardCharsets.UTF_8));
		} catch (IOException | JsonException e) {
			return new ArrayList<>();
		}
	}

	/** Writes {@code runs} atomically. */
	public void save(List<RunRecord> runs) throws IOException {
		String json = toJson(runs);
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, json, StandardCharsets.UTF_8);
		try {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/**
	 * Adds {@code run} at the front, then drops the oldest runs beyond {@link #MAX_PER_COLLECTION} for its
	 * collection and beyond {@link #MAX_TOTAL} in all.
	 */
	public static void add(List<RunRecord> runs, RunRecord run) {
		runs.add(0, run);
		Map<String, Integer> perCollection = new HashMap<>();
		int kept = 0;
		for (var it = runs.iterator(); it.hasNext();) {
			RunRecord r = it.next();
			int n = perCollection.merge(r.collectionId(), 1, Integer::sum);
			if (n > MAX_PER_COLLECTION || kept >= MAX_TOTAL) {
				it.remove();
			} else {
				kept++;
			}
		}
	}

	static String toJson(List<RunRecord> runs) {
		List<Object> list = new ArrayList<>();
		for (RunRecord r : runs) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", r.id());
			m.put("startedAt", r.startedAtMillis());
			m.put("collectionId", r.collectionId());
			m.put("collection", r.collectionName());
			if (!r.folderId().isEmpty()) {
				m.put("folderId", r.folderId());
				m.put("folder", r.folderName());
			}
			m.put("environment", r.environment());
			if (r.viaAssistant()) {
				m.put("via", "assistant");
			}
			List<Object> items = new ArrayList<>();
			for (RunRecord.Item i : r.items()) {
				Map<String, Object> im = new LinkedHashMap<>();
				im.put("requestId", i.requestId());
				im.put("method", i.method());
				im.put("name", i.name());
				im.put("url", i.url());
				im.put("expected", i.expected());
				im.put("outcome", i.outcome().name().toLowerCase(Locale.ROOT));
				im.put("status", i.status());
				im.put("statusLine", i.statusLine());
				im.put("durationMs", i.durationMs());
				im.put("message", i.message());
				items.add(im);
			}
			m.put("items", items);
			list.add(m);
		}
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("formatVersion", FORMAT_VERSION);
		root.put("runs", list);
		return Json.writePretty(root);
	}

	static List<RunRecord> fromJson(String json) {
		List<RunRecord> out = new ArrayList<>();
		if (!(Json.parse(json) instanceof Map<?, ?> root) || !(root.get("runs") instanceof List<?> list)) {
			return out;
		}
		for (Object o : list) {
			if (!(o instanceof Map<?, ?> m)) {
				continue;
			}
			List<RunRecord.Item> items = new ArrayList<>();
			if (m.get("items") instanceof List<?> il) {
				for (Object io : il) {
					if (io instanceof Map<?, ?> im) {
						items.add(new RunRecord.Item(str(im.get("requestId")), str(im.get("method")),
								str(im.get("name")), str(im.get("url")), str(im.get("expected")),
								outcome(im.get("outcome")), (int) num(im.get("status")), str(im.get("statusLine")),
								num(im.get("durationMs")), str(im.get("message"))));
					}
				}
			}
			out.add(new RunRecord(str(m.get("id")), num(m.get("startedAt")), str(m.get("collectionId")),
					str(m.get("collection")), str(m.get("folderId")), str(m.get("folder")),
					str(m.get("environment")), "assistant".equals(m.get("via")), items));
		}
		return out;
	}

	private static Outcome outcome(Object o) {
		try {
			return Outcome.valueOf(str(o).toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return Outcome.ERROR;
		}
	}

	private static String str(Object o) {
		return o instanceof String s ? s : "";
	}

	private static long num(Object o) {
		return o instanceof Number n ? n.longValue() : 0;
	}
}
