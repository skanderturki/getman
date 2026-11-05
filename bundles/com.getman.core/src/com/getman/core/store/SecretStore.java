package com.getman.core.store;

import java.util.Set;

/**
 * Somewhere safer than the JSON state files to keep secrets (auth tokens, passwords, secret variables). The
 * Eclipse UI plugs in Eclipse secure storage; tests use an in-memory map. Keys are short ASCII strings made of
 * letters, digits, {@code -} and {@code :}.
 */
public interface SecretStore {

	/** The stored value, or {@code null} when there is none. */
	String get(String key) throws SecretStoreException;

	void put(String key, String value) throws SecretStoreException;

	/** Deletes every secret whose key is not in {@code keep}, and persists the store. Called after each save. */
	void retainOnly(Set<String> keep) throws SecretStoreException;

	/** The store is unavailable or refused an operation (e.g. the user cancelled a master-password prompt). */
	class SecretStoreException extends Exception {
		private static final long serialVersionUID = 1L;

		public SecretStoreException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
