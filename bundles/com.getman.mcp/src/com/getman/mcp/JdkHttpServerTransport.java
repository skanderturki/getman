package com.getman.mcp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import reactor.core.publisher.Mono;

/**
 * MCP "Streamable HTTP" transport (stateless variant: every POST carries one JSON-RPC message and gets a plain JSON
 * reply, no sessions, no SSE) on the JDK's built-in HTTP server. Replaces the SDK's servlet transport, so GetMan
 * does not depend on the servlet/Jetty version shipped with each Eclipse release.
 * <p>
 * Security: listens on the loopback address only, requires {@code Authorization: Bearer <token>}, and refuses any
 * request carrying an {@code Origin} header (browsers always send one), so web pages cannot reach the server.
 */
public final class JdkHttpServerTransport implements McpStatelessServerTransport {
	private static final String ENDPOINT = "/mcp";
	private static final int MAX_BODY = 4 * 1024 * 1024;

	private final McpJsonMapper json;
	private final byte[] token;
	private volatile McpStatelessServerHandler handler;
	private volatile boolean closing;
	private HttpServer server;
	private ExecutorService executor;

	public JdkHttpServerTransport(McpJsonMapper json, String token) {
		this.json = json;
		this.token = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
	}

	/** Starts listening on 127.0.0.1; port 0 picks a free port. Returns the actual port. */
	public int start(int port) throws IOException {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
		executor = Executors.newCachedThreadPool(r -> {
			Thread t = new Thread(r, "GetMan MCP");
			t.setDaemon(true);
			return t;
		});
		server.setExecutor(executor);
		server.createContext(ENDPOINT, this::handle);
		server.start();
		return server.getAddress().getPort();
	}

	@Override
	public void setMcpHandler(McpStatelessServerHandler mcpHandler) {
		this.handler = mcpHandler;
	}

	@Override
	public Mono<Void> closeGracefully() {
		return Mono.fromRunnable(() -> {
			closing = true;
			if (server != null) {
				server.stop(0);
				executor.shutdownNow();
			}
		});
	}

	private void handle(HttpExchange exchange) throws IOException {
		try (exchange) {
			if (!ENDPOINT.equals(exchange.getRequestURI().getPath())) {
				sendError(exchange, 404, "Not found");
				return;
			}
			if (exchange.getRequestHeaders().containsKey("Origin")) {
				sendError(exchange, 403, "Browser requests are not allowed");
				return;
			}
			if (!authorized(exchange.getRequestHeaders().getFirst("Authorization"))) {
				sendError(exchange, 401, "Missing or wrong GetMan MCP token");
				return;
			}
			if (!"POST".equals(exchange.getRequestMethod())) {
				// GET would open an SSE stream and DELETE would end a session; a stateless server has neither.
				exchange.getResponseHeaders().set("Allow", "POST");
				sendError(exchange, 405, "Method not allowed");
				return;
			}
			if (closing) {
				sendError(exchange, 503, "Server is shutting down");
				return;
			}
			String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
			if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
				sendError(exchange, 415, "Content-Type must be application/json");
				return;
			}
			String body = readBody(exchange.getRequestBody());
			if (body == null) {
				sendError(exchange, 413, "Request too large");
				return;
			}
			McpSchema.JSONRPCMessage message;
			try {
				message = McpSchema.deserializeJsonRpcMessage(json, body);
			} catch (IOException | IllegalArgumentException e) {
				sendError(exchange, 400, "Invalid JSON-RPC message");
				return;
			}
			McpTransportContext context = McpTransportContext.EMPTY;
			if (message instanceof McpSchema.JSONRPCRequest request) {
				McpSchema.JSONRPCResponse response = handler.handleRequest(context, request)
						.contextWrite(ctx -> ctx.put(McpTransportContext.KEY, context))
						.block();
				send(exchange, 200, json.writeValueAsString(response));
			} else if (message instanceof McpSchema.JSONRPCNotification notification) {
				handler.handleNotification(context, notification)
						.contextWrite(ctx -> ctx.put(McpTransportContext.KEY, context))
						.block();
				exchange.sendResponseHeaders(202, -1);
			} else {
				sendError(exchange, 400, "Expected a request or a notification");
			}
		} catch (RuntimeException e) {
			// Response may already be committed; nothing more we can do for this exchange.
		}
	}

	private boolean authorized(String header) {
		return header != null && MessageDigest.isEqual(token, header.getBytes(StandardCharsets.UTF_8));
	}

	private static String readBody(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int n;
		while ((n = in.read(buffer)) != -1) {
			out.write(buffer, 0, n);
			if (out.size() > MAX_BODY) {
				return null;
			}
		}
		return out.toString(StandardCharsets.UTF_8);
	}

	private static void sendError(HttpExchange exchange, int status, String message) throws IOException {
		String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"");
		send(exchange, status, "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32600,\"message\":\"" + escaped
				+ "\"}}");
	}

	private static void send(HttpExchange exchange, int status, String jsonText) throws IOException {
		byte[] bytes = jsonText.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(bytes);
		}
	}
}
