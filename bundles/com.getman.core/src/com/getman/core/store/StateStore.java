package com.getman.core.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.getman.core.json.JsonException;

/**
 * Reads and writes {@link WorkspaceState} as a JSON file. Writes go to a temp file that is then moved over the
 * real one, so a crash mid-write never leaves a half-written state file.
 */
public final class StateStore {
	private final Path file;
	private final SecretStore secretStore;

	public StateStore(Path file) {
		this(file, null);
	}

	/** @param secretStore where secrets go instead of the JSON file; {@code null} keeps them inline */
	public StateStore(Path file, SecretStore secretStore) {
		this.file = file;
		this.secretStore = secretStore;
	}

	public Path getFile() {
		return file;
	}

	/**
	 * Loads the saved state. Returns an empty state when there is no file yet. If the file is unreadable or
	 * corrupt it is renamed to {@code <name>.corrupt-<timestamp>} (so nothing is silently lost) and an empty state
	 * is returned; the problem is reported through {@link LoadResult#warning()}.
	 */
	public LoadResult load() {
		if (!Files.exists(file)) {
			return new LoadResult(new WorkspaceState(), null);
		}
		try {
			String json = Files.readString(file, StandardCharsets.UTF_8);
			Secrets secrets = secretStore == null ? null : new Secrets(secretStore);
			WorkspaceState state = StateCodec.fromJson(json, secrets);
			String warning = secrets == null || secrets.warnings().isEmpty() ? null
					: String.join("\n", secrets.warnings());
			return new LoadResult(state, warning);
		} catch (IOException | JsonException e) {
			String backup = quarantine();
			return new LoadResult(new WorkspaceState(),
					"Could not read saved requests (" + e.getMessage() + ")."
							+ (backup != null ? " The old file was kept as " + backup + "." : ""));
		}
	}

	/**
	 * Saves atomically. Secrets go to the secret store (if any) and secrets no longer referenced are removed
	 * from it afterwards, so the JSON on disk never points at a deleted secret.
	 *
	 * @return problems with the secret store (the save itself still succeeded); empty when all went well
	 */
	public List<String> save(WorkspaceState state) throws IOException {
		Secrets secrets = secretStore == null ? null : new Secrets(secretStore);
		writeAtomically(file, StateCodec.toJson(state, secrets));
		if (secrets == null) {
			return List.of();
		}
		secrets.finish();
		return secrets.warnings();
	}

	/** Writes to a temp file next to {@code target}, then moves it over the target. */
	static void writeAtomically(Path target, String content) throws IOException {
		Files.createDirectories(target.getParent());
		Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
		Files.writeString(tmp, content, StandardCharsets.UTF_8);
		try {
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private String quarantine() {
		String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		Path backup = file.resolveSibling(file.getFileName() + ".corrupt-" + stamp);
		try {
			Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
			return backup.toString();
		} catch (IOException e) {
			return null;
		}
	}

	/** Loaded state plus an optional warning to show the user. */
	public record LoadResult(WorkspaceState state, String warning) {
	}
}
