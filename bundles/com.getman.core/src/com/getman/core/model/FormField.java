package com.getman.core.model;

import java.util.Objects;

/** One part of a multipart/form-data body: a text value, or a file given by its path. */
public final class FormField {
	private String key;
	private String value;
	private boolean enabled;
	private boolean file;

	public FormField() {
		this("", "", true, false);
	}

	public FormField(String key, String value, boolean enabled, boolean file) {
		setKey(key);
		setValue(value);
		this.enabled = enabled;
		this.file = file;
	}

	public static FormField text(String key, String value) {
		return new FormField(key, value, true, false);
	}

	public static FormField file(String key, String path) {
		return new FormField(key, path, true, true);
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key == null ? "" : key;
	}

	/** The text, or for a file field the file's path. */
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

	public boolean isFile() {
		return file;
	}

	public void setFile(boolean file) {
		this.file = file;
	}

	/** Enabled with a non-blank name (and, for files, a path): this part will be sent. */
	public boolean isActive() {
		return enabled && !key.isBlank() && (!file || !value.isBlank());
	}

	public FormField copy() {
		return new FormField(key, value, enabled, file);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof FormField f && key.equals(f.key) && value.equals(f.value) && enabled == f.enabled
				&& file == f.file;
	}

	@Override
	public int hashCode() {
		return Objects.hash(key, value, enabled, file);
	}

	@Override
	public String toString() {
		return (file ? key + "=@" : key + "=") + value;
	}
}
