package com.getman.core.model;

import java.util.Objects;

/** One row of a params or headers table. Disabled rows are kept but not sent. */
public final class KeyValue {
	private String key;
	private String value;
	private boolean enabled;

	public KeyValue() {
		this("", "", true);
	}

	public KeyValue(String key, String value) {
		this(key, value, true);
	}

	public KeyValue(String key, String value, boolean enabled) {
		this.key = key == null ? "" : key;
		this.value = value == null ? "" : value;
		this.enabled = enabled;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key == null ? "" : key;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value == null ? "" : value;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	/** Enabled and has a non-blank key, so it should be sent. */
	public boolean isActive() {
		return enabled && !key.isBlank();
	}

	public KeyValue copy() {
		return new KeyValue(key, value, enabled);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof KeyValue other && enabled == other.enabled && key.equals(other.key)
				&& value.equals(other.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(key, value, enabled);
	}

	@Override
	public String toString() {
		return (enabled ? "" : "#") + key + "=" + value;
	}
}
