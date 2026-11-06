package com.getman.mcp.ui;

import org.eclipse.ui.IStartup;

/** Starts the MCP server when Eclipse starts, if it is enabled. */
public final class McpStartup implements IStartup {
	@Override
	public void earlyStartup() {
		if (McpPreferences.enabled()) {
			McpServerManager.get().applyPreferences();
		}
	}
}
