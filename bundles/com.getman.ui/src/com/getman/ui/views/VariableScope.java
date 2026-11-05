package com.getman.ui.views;

import java.util.Map;

/**
 * The variables a request is resolved against: the active environment's name (empty for "No Environment") and
 * its enabled values.
 */
record VariableScope(String environmentName, Map<String, String> values) {
	static final VariableScope NONE = new VariableScope("", Map.of());
}
