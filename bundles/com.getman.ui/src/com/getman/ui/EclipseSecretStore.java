package com.getman.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.equinox.security.storage.StorageException;

import com.getman.core.store.SecretStore;

/**
 * {@link SecretStore} backed by Eclipse secure storage (encrypted; on Windows with the OS data-protection API, on
 * macOS with the keychain). Secure storage is per user, so each workspace gets its own node, named after a hash of
 * its GetMan state folder, and each file (state, history) its own namespace below it. Values are cached so saving
 * unchanged secrets does not re-encrypt and flush them every time.
 */
public final class EclipseSecretStore implements SecretStore {
	private static final String ROOT_NODE = "com.getman.ui";

	private final ISecurePreferences node;
	private final Map<String, String> cache = new HashMap<>();
	private boolean dirty;

	private EclipseSecretStore(ISecurePreferences node) {
		this.node = node;
	}

	/**
	 * Store for one workspace and namespace. When Eclipse secure storage is not available at all, the returned
	 * store fails every call, which makes GetMan fall back to plain-text secrets with a warning.
	 */
	public static SecretStore forWorkspace(Path stateLocation, String namespace) {
		ISecurePreferences root;
		try {
			root = SecurePreferencesFactory.getDefault();
		} catch (RuntimeException e) {
			root = null;
		}
		if (root == null) {
			return Unavailable.INSTANCE;
		}
		return new EclipseSecretStore(root.node(ROOT_NODE).node(workspaceId(stateLocation)).node(namespace));
	}

	static String workspaceId(Path stateLocation) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(stateLocation.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest, 0, 8);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e); // SHA-256 is always available
		}
	}

	@Override
	public String get(String key) throws SecretStoreException {
		String cached = cache.get(key);
		if (cached != null) {
			return cached;
		}
		try {
			String value = node.get(key, null);
			if (value != null) {
				cache.put(key, value);
			}
			return value;
		} catch (StorageException e) {
			throw new SecretStoreException(e.getMessage(), e);
		}
	}

	@Override
	public void put(String key, String value) throws SecretStoreException {
		if (value.equals(cache.get(key))) {
			return;
		}
		try {
			node.put(key, value, true);
			cache.put(key, value);
			dirty = true;
		} catch (StorageException e) {
			throw new SecretStoreException(e.getMessage(), e);
		}
	}

	@Override
	public void retainOnly(Set<String> keep) throws SecretStoreException {
		for (String key : node.keys()) {
			if (!keep.contains(key)) {
				node.remove(key);
				cache.remove(key);
				dirty = true;
			}
		}
		if (dirty) {
			try {
				node.flush();
				dirty = false;
			} catch (IOException e) {
				throw new SecretStoreException(e.getMessage(), e);
			}
		}
	}

	/** Used when secure storage cannot be opened: every call fails, so secrets stay in the JSON file. */
	private enum Unavailable implements SecretStore {
		INSTANCE;

		@Override
		public String get(String key) throws SecretStoreException {
			throw new SecretStoreException("Eclipse secure storage is not available", null);
		}

		@Override
		public void put(String key, String value) throws SecretStoreException {
			throw new SecretStoreException("Eclipse secure storage is not available", null);
		}

		@Override
		public void retainOnly(Set<String> keep) throws SecretStoreException {
			throw new SecretStoreException("Eclipse secure storage is not available", null);
		}
	}
}
