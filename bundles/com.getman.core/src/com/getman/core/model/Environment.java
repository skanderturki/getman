package com.getman.core.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A named set of variables, e.g. "Local" or "Production", used to fill {@code {{name}}} placeholders. */
public final class Environment {
	private String id = UUID.randomUUID().toString();
	private String name = "New Environment";
	private final List<KeyValue> variables = new ArrayList<>();
	private boolean production;
	/** Created by an AI assistant through the MCP server, which may change only such environments' variables. */
	private boolean createdByAssistant;

	public Environment() {
	}

	public Environment(String name) {
		setName(name);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name == null ? "" : name;
	}

	/**
	 * Marked as a production environment: an AI assistant connected through the MCP server must get the user's
	 * approval before sending anything other than GET, HEAD or OPTIONS in it.
	 */
	public boolean isProduction() {
		return production;
	}

	public void setProduction(boolean production) {
		this.production = production;
	}

	public boolean isCreatedByAssistant() {
		return createdByAssistant;
	}

	public void setCreatedByAssistant(boolean createdByAssistant) {
		this.createdByAssistant = createdByAssistant;
	}

	/** The variable named {@code name} (ignoring surrounding spaces), or {@code null}. */
	public KeyValue variable(String name) {
		for (KeyValue v : variables) {
			if (v.getKey().trim().equals(name.trim())) {
				return v;
			}
		}
		return null;
	}

	/** Live, mutable list of variables in display order. */
	public List<KeyValue> getVariables() {
		return variables;
	}

	/** Enabled variables with a non-blank name; later rows win when a name repeats. */
	public Map<String, String> activeValues() {
		Map<String, String> values = new LinkedHashMap<>();
		for (KeyValue v : variables) {
			if (v.isActive()) {
				values.put(v.getKey().trim(), v.getValue());
			}
		}
		return values;
	}

	/** Deep copy with the same id and assistant marker (for editing in a dialog without touching the live object). */
	public Environment copy() {
		Environment c = new Environment(name);
		c.id = id;
		c.production = production;
		c.createdByAssistant = createdByAssistant;
		variables.forEach(v -> c.variables.add(v.copy()));
		return c;
	}
}
