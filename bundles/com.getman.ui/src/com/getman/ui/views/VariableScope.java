package com.getman.ui.views;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The variables a request is resolved against: the request's collection variables overlaid by the active
 * environment (the environment wins). Names are empty when there is no environment or no collection variables.
 */
record VariableScope(String environmentName, String collectionName, Map<String, String> values) {
	static final VariableScope NONE = new VariableScope("", "", Map.of());

	/** "environment "Local" or collection "API"", or {@code null} when nothing defines variables. */
	String describeSources() {
		List<String> sources = new ArrayList<>();
		if (!environmentName.isEmpty()) {
			sources.add("environment \"" + environmentName + "\"");
		}
		if (!collectionName.isEmpty()) {
			sources.add("collection \"" + collectionName + "\"");
		}
		return sources.isEmpty() ? null : String.join(" or ", sources);
	}
}
