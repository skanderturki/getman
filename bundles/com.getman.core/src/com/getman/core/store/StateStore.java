package com.getman.core.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.getman.core.json.JsonException;

/**
 * Reads and writes {@link WorkspaceState} as a JSON file. Writes go to a temp file that is then moved over the
 * real one, so a crash mid-write never leaves a half-written state file.
 */
public final class StateStore {
	private final Path file;

	public StateStore(Path file) {
		this.file = file;
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
			return new LoadResult(StateCodec.fromJson(json), null);
		} catch (IOException | JsonException e) {
			String backup = quarantine();
			return new LoadResult(new WorkspaceState(),
					"Could not read saved requests (" + e.getMessage() + ")."
							+ (backup != null ? " The old file was kept as " + backup + "." : ""));
		}
	}

	public void save(WorkspaceState state) throws IOException {
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, StateCodec.toJson(state), StandardCharsets.UTF_8);
		try {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
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
