package com.getman.core.history;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.getman.core.json.Json;
import com.getman.core.json.JsonException;
import com.getman.core.model.RequestModel;
import com.getman.core.store.SecretStore;
import com.getman.core.store.Secrets;
import com.getman.core.store.StateCodec;

/**
 * Request history in its own JSON file (newest first), kept apart from the main state so the state file stays
 * small. A damaged history file is simply treated as empty: losing history is better than failing to start.
 */
public final class HistoryStore {
	static final int FORMAT_VERSION = 1;

	private final Path file;
	private final SecretStore secretStore;

	public HistoryStore(Path file) {
		this(file, null);
	}

	/** @param secretStore where auth secrets of recorded requests go; {@code null} keeps them inline */
	public HistoryStore(Path file, SecretStore secretStore) {
		this.file = file;
		this.secretStore = secretStore;
	}

	public Path getFile() {
		return file;
	}

	/** Entries newest first; empty when there is no file or it cannot be read. */
	public List<HistoryEntry> load() {
		if (!Files.exists(file)) {
			return new ArrayList<>();
		}
		try {
			return fromJson(Files.readString(file, StandardCharsets.UTF_8),
					secretStore == null ? null : new Secrets(secretStore));
		} catch (IOException | JsonException e) {
			return new ArrayList<>();
		}
	}

	/**
	 * Writes at most {@code max} entries (the first ones, i.e. newest), atomically; secrets of dropped entries
	 * are removed from the secret store.
	 *
	 * @return problems with the secret store; empty when all went well
	 */
	public List<String> save(List<HistoryEntry> entries, int max) throws IOException {
		Secrets secrets = secretStore == null ? null : new Secrets(secretStore);
		String json = toJson(entries.subList(0, Math.min(Math.max(max, 0), entries.size())), secrets);
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, json, StandardCharsets.UTF_8);
		try {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
		if (secrets == null) {
			return List.of();
		}
		secrets.finish();
		return secrets.warnings();
	}

	/** Adds {@code entry} at the front and drops the oldest entries beyond {@code max}. */
	public static void add(List<HistoryEntry> entries, HistoryEntry entry, int max) {
		entries.add(0, entry);
		while (entries.size() > Math.max(max, 0)) {
			entries.remove(entries.size() - 1);
		}
	}

	static String toJson(List<HistoryEntry> entries) {
		return toJson(entries, null);
	}

	static String toJson(List<HistoryEntry> entries, Secrets secrets) {
		List<Object> list = new ArrayList<>();
		for (HistoryEntry e : entries) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", e.id());
			m.put("sentAt", e.sentAtMillis());
			m.put("resolvedUrl", e.resolvedUrl());
			m.put("status", e.statusCode());
			m.put("statusLine", e.statusLine());
			m.put("error", e.error());
			m.put("durationMs", e.durationMs());
			m.put("sizeBytes", e.sizeBytes());
			if (e.viaAssistant()) {
				m.put("via", "assistant");
			}
			m.put("request", StateCodec.requestToMap(e.request(), secrets, "hist:" + e.id()));
			list.add(m);
		}
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("formatVersion", FORMAT_VERSION);
		root.put("entries", list);
		return Json.writePretty(root);
	}

	static List<HistoryEntry> fromJson(String json) {
		return fromJson(json, null);
	}

	static List<HistoryEntry> fromJson(String json, Secrets secrets) {
		List<HistoryEntry> out = new ArrayList<>();
		if (!(Json.parse(json) instanceof Map<?, ?> root) || !(root.get("entries") instanceof List<?> list)) {
			return out;
		}
		for (Object o : list) {
			if (o instanceof Map<?, ?> m && m.get("request") instanceof Map<?, ?> rm) {
				RequestModel request = StateCodec.requestFromMap(rm, secrets);
				out.add(new HistoryEntry(str(m.get("id")), num(m.get("sentAt")), request, str(m.get("resolvedUrl")),
						(int) num(m.get("status")), str(m.get("statusLine")), str(m.get("error")),
						num(m.get("durationMs")), num(m.get("sizeBytes")), "assistant".equals(m.get("via"))));
			}
		}
		return out;
	}

	private static String str(Object o) {
		return o instanceof String s ? s : "";
	}

	private static long num(Object o) {
		return o instanceof Number n ? n.longValue() : 0;
	}
}
