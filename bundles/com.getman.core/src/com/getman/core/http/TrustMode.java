package com.getman.core.http;

/** Which TLS server certificates are accepted. */
public enum TrustMode {
	/** Only certificates trusted by the JDK, with host-name checks (the browser behaviour). */
	STRICT("Only trusted certificates"),
	/**
	 * Like {@link #STRICT}, except that any certificate is accepted for local hosts (localhost, *.localhost,
	 * 127.x.x.x, ::1), so self-signed development certificates just work.
	 */
	LOCALHOST("Also accept self-signed certificates on localhost"),
	/** Any certificate for any host. Insecure: for testing internal servers only. */
	ALL("Accept any certificate (insecure)");

	private final String label;

	TrustMode(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	public static TrustMode parse(String name) {
		if (name != null) {
			for (TrustMode m : values()) {
				if (m.name().equalsIgnoreCase(name.trim())) {
					return m;
				}
			}
		}
		return LOCALHOST;
	}
}
