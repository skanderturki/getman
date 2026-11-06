package com.getman.mcp.ui;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/** Stops the MCP server when Eclipse shuts down. */
public final class Activator implements BundleActivator {
	@Override
	public void start(BundleContext context) {
		// the server starts from McpStartup or the preference page
	}

	@Override
	public void stop(BundleContext context) {
		McpServerManager.get().stop();
	}
}
