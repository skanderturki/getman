package com.getman.ui.preferences;

import java.time.Duration;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;

import com.getman.core.http.HttpSettings;
import com.getman.core.http.TrustMode;

/** GetMan preference keys, defaults and typed accessors (stored per workspace, in the instance scope). */
public final class GetmanPreferences {
	public static final String QUALIFIER = "com.getman.ui";

	public static final String CONNECT_TIMEOUT_SECONDS = "connectTimeoutSeconds";
	public static final String REQUEST_TIMEOUT_SECONDS = "requestTimeoutSeconds";
	public static final String FOLLOW_REDIRECTS = "followRedirects";
	public static final String TRUST_MODE = "trustMode";
	public static final String HISTORY_SIZE = "historySize";

	static final int DEFAULT_CONNECT_TIMEOUT = 10;
	static final int DEFAULT_REQUEST_TIMEOUT = 60;
	static final int DEFAULT_HISTORY_SIZE = 100;

	private GetmanPreferences() {
	}

	static void initializeDefaults() {
		IEclipsePreferences d = DefaultScope.INSTANCE.getNode(QUALIFIER);
		d.putInt(CONNECT_TIMEOUT_SECONDS, DEFAULT_CONNECT_TIMEOUT);
		d.putInt(REQUEST_TIMEOUT_SECONDS, DEFAULT_REQUEST_TIMEOUT);
		d.putBoolean(FOLLOW_REDIRECTS, true);
		d.put(TRUST_MODE, TrustMode.LOCALHOST.name());
		d.putInt(HISTORY_SIZE, DEFAULT_HISTORY_SIZE);
	}

	/** Node to listen on for changes made on the preference page. */
	public static IEclipsePreferences instanceNode() {
		return InstanceScope.INSTANCE.getNode(QUALIFIER);
	}

	public static HttpSettings httpSettings() {
		int connect = Math.max(1, getInt(CONNECT_TIMEOUT_SECONDS, DEFAULT_CONNECT_TIMEOUT));
		int request = Math.max(1, getInt(REQUEST_TIMEOUT_SECONDS, DEFAULT_REQUEST_TIMEOUT));
		boolean redirects = Platform.getPreferencesService().getBoolean(QUALIFIER, FOLLOW_REDIRECTS, true, null);
		TrustMode trust = TrustMode.parse(
				Platform.getPreferencesService().getString(QUALIFIER, TRUST_MODE, TrustMode.LOCALHOST.name(), null));
		return new HttpSettings(Duration.ofSeconds(connect), Duration.ofSeconds(request), redirects, trust);
	}

	public static int historySize() {
		return Math.max(0, getInt(HISTORY_SIZE, DEFAULT_HISTORY_SIZE));
	}

	private static int getInt(String key, int fallback) {
		return Platform.getPreferencesService().getInt(QUALIFIER, key, fallback, null);
	}
}
