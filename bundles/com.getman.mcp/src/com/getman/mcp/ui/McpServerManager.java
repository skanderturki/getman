package com.getman.mcp.ui;

import java.io.IOException;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import com.getman.mcp.GetmanMcpServer;

/** Starts and stops the one MCP server of this Eclipse according to the preferences. */
public final class McpServerManager {
	private static final McpServerManager INSTANCE = new McpServerManager();

	private GetmanMcpServer server;
	private String problem;

	private McpServerManager() {
	}

	public static McpServerManager get() {
		return INSTANCE;
	}

	/** (Re)starts the server with the saved settings, or stops it when it is disabled. */
	public synchronized void applyPreferences() {
		stop();
		problem = null;
		if (!McpPreferences.enabled()) {
			return;
		}
		int port = McpPreferences.port();
		Bundle bundle = FrameworkUtil.getBundle(getClass());
		ILog log = Platform.getLog(bundle);
		try {
			server = new GetmanMcpServer(port, McpPreferences.token(), bundle.getVersion().toString(),
					new EclipseToolHost());
			log.log(Status.info("GetMan MCP server listening on " + McpPreferences.url(server.port())));
		} catch (IOException | RuntimeException e) {
			problem = "Could not start on port " + port + " (" + e.getMessage() + "). Another program, or another "
					+ "Eclipse with GetMan, may be using it: choose another port.";
			log.log(Status.warning("GetMan MCP server: " + problem, e));
		}
	}

	public synchronized void stop() {
		if (server != null) {
			server.stop();
			server = null;
		}
	}

	/** One line for the preference page. */
	public synchronized String status() {
		if (server != null) {
			return "Running at " + McpPreferences.url(server.port());
		}
		return problem != null ? problem : "Off";
	}
}
