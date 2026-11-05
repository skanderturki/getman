package com.getman.core.store;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.getman.core.store.SecretStore.SecretStoreException;

/**
 * One save or load pass against a {@link SecretStore}. While saving it records which keys are still in use, so
 * {@link #finish()} can delete secrets that no longer belong to anything (a deleted request, a removed variable).
 * If the store fails, saving falls back to writing the secret inline (plain text, as before 0.8) and loading
 * yields an empty value; either way a warning is recorded instead of throwing, so the user never loses work.
 */
public final class Secrets {
	private final SecretStore store;
	private final Set<String> used = new LinkedHashSet<>();
	private final List<String> warnings = new ArrayList<>();
	private boolean writeFailed;

	public Secrets(SecretStore store) {
		this.store = store;
	}

	/**
	 * Stores {@code value} under {@code key}. Returns {@code true} when it is now in the store (the caller then
	 * leaves it out of the JSON), {@code false} when the caller must write it inline.
	 */
	boolean put(String key, String value) {
		if (writeFailed) {
			return false;
		}
		try {
			store.put(key, value);
			used.add(key);
			return true;
		} catch (SecretStoreException e) {
			writeFailed = true;
			warnings.add("Secure storage is not available (" + e.getMessage() + "); secrets were saved in the "
					+ "GetMan state file in plain text instead.");
			return false;
		}
	}

	/** The secret stored under {@code key}, or "" when missing or unreadable. */
	String get(String key) {
		try {
			String value = store.get(key);
			if (value == null) {
				warnings.add("A saved secret was not found in secure storage (" + key + "); it is now empty.");
				return "";
			}
			return value;
		} catch (SecretStoreException e) {
			warnings.add("Could not read secure storage (" + e.getMessage() + "); some tokens, passwords or secret "
					+ "variables are empty.");
			return "";
		}
	}

	/** Ends a save: removes secrets no longer referenced. Skipped after a failed write, to be safe. */
	public void finish() {
		if (writeFailed) {
			return;
		}
		try {
			store.retainOnly(used);
		} catch (SecretStoreException e) {
			warnings.add("Could not update secure storage: " + e.getMessage());
		}
	}

	/** Problems met during this pass, for logging or showing once. */
	public List<String> warnings() {
		return warnings.stream().distinct().toList();
	}
}
