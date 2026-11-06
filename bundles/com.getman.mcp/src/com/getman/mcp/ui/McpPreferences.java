package com.getman.mcp.ui;

import java.security.SecureRandom;
import java.util.Base64;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.service.prefs.BackingStoreException;

/** MCP server settings, kept in the workspace's instance preferences. Off by default. */
public final class McpPreferences {
	static final String NODE = "com.getman.mcp";
	static final String ENABLED = "enabled";
	static final String PORT = "port";
	static final String TOKEN = "token";
	public static final int DEFAULT_PORT = 47123;
	static final String SERVER_NAME = "getman";

	private McpPreferences() {
	}

	private static IEclipsePreferences node() {
		return InstanceScope.INSTANCE.getNode(NODE);
	}

	public static boolean enabled() {
		return node().getBoolean(ENABLED, false);
	}

	public static int port() {
		int port = node().getInt(PORT, DEFAULT_PORT);
		return port > 0 && port < 65536 ? port : DEFAULT_PORT;
	}

	/** The access token, created (and saved) the first time it is needed. */
	public static synchronized String token() {
		String token = node().get(TOKEN, "");
		if (token.isEmpty()) {
			token = newToken();
			node().put(TOKEN, token);
			flush();
		}
		return token;
	}

	public static void save(boolean enabled, int port, String token) {
		IEclipsePreferences node = node();
		node.putBoolean(ENABLED, enabled);
		node.putInt(PORT, port);
		node.put(TOKEN, token);
		flush();
	}

	/** 256 random bits, URL-safe Base64. */
	static String newToken() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public static String url(int port) {
		return "http://127.0.0.1:" + port + "/mcp";
	}

	/** Adds the server to Claude Code for every project of the current user. */
	static String claudeCodeCommand(int port, String token) {
		return "claude mcp add --transport http --scope user " + SERVER_NAME + " " + url(port)
				+ " --header \"Authorization: Bearer " + token + "\"";
	}

	private static void flush() {
		try {
			node().flush();
		} catch (BackingStoreException e) {
			// kept in memory for this session; Eclipse saves preferences again on exit
		}
	}
}
