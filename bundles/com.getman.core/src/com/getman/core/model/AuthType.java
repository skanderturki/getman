package com.getman.core.model;

/** How a request authenticates itself. */
public enum AuthType {
	NONE("No Auth"),
	BEARER("Bearer Token"),
	BASIC("Basic Auth");

	private final String label;

	AuthType(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	/** Lenient parse for saved files: unknown or missing values mean {@link #NONE}. */
	public static AuthType parse(String name) {
		if (name != null) {
			for (AuthType t : values()) {
				if (t.name().equalsIgnoreCase(name.trim())) {
					return t;
				}
			}
		}
		return NONE;
	}
}
