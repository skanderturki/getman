package com.getman.mcp;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.osgi.framework.FrameworkUtil;

/**
 * Spike only: runs the MCP server inside a headless Eclipse/OSGi runtime so the bundle wiring, the JDK HTTP server
 * and the SDK can be tested without the IDE UI. Properties: {@code getman.mcp.port} (default 47123),
 * {@code getman.mcp.token} (required), {@code getman.mcp.seconds} (how long to run, default 600).
 */
public final class SpikeApplication implements IApplication {
	private volatile boolean stopped;

	@Override
	public Object start(IApplicationContext context) throws Exception {
		int port = Integer.getInteger("getman.mcp.port", 47123);
		String token = System.getProperty("getman.mcp.token");
		if (token == null || token.isBlank()) {
			System.err.println("Set -Dgetman.mcp.token=<token>");
			return Integer.valueOf(1);
		}
		long seconds = Long.getLong("getman.mcp.seconds", 600);
		String version = FrameworkUtil.getBundle(getClass()).getVersion().toString();
		GetmanMcpServer server = new GetmanMcpServer(port, token, version);
		System.out.println("GetMan MCP spike listening on http://127.0.0.1:" + server.port() + "/mcp");
		long end = System.currentTimeMillis() + seconds * 1000;
		while (!stopped && System.currentTimeMillis() < end) {
			Thread.sleep(200);
		}
		server.stop();
		System.out.println("GetMan MCP spike stopped");
		return IApplication.EXIT_OK;
	}

	@Override
	public void stop() {
		stopped = true;
	}
}
