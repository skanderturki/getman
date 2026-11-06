package com.getman.mcp;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getman.core.curl.CurlImporter;
import com.getman.core.model.KeyValue;
import com.getman.core.model.RequestModel;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.json.schema.jackson2.DefaultJsonSchemaValidator;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

/**
 * Spike: an MCP server with two tools, {@code ping} and {@code parse_curl} (the latter proves that tools can call
 * GetMan core). The JSON mapper and schema validator are created directly instead of through the SDK's
 * ServiceLoader lookup, which does not see other bundles' services under OSGi.
 */
public final class GetmanMcpServer {
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final McpJsonMapper json = new JacksonMcpJsonMapper(objectMapper);
	private final JdkHttpServerTransport transport;
	private final McpStatelessSyncServer server;
	private final int port;

	public GetmanMcpServer(int port, String token, String version) throws IOException {
		transport = new JdkHttpServerTransport(json, token);
		server = McpServer.sync(transport)
				.serverInfo("getman", version)
				.instructions("GetMan is a REST client running inside the user's Eclipse IDE.")
				.jsonMapper(json)
				.jsonSchemaValidator(new DefaultJsonSchemaValidator(objectMapper))
				.capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
				.toolCall(McpSchema.Tool.builder()
						.name("ping")
						.description("Check that GetMan is running; returns its version.")
						.inputSchema(json, "{\"type\":\"object\",\"properties\":{}}")
						.build(),
						(ctx, request) -> text("GetMan " + version + " is running inside Eclipse."))
				.toolCall(McpSchema.Tool.builder()
						.name("parse_curl")
						.description("Parse a curl command into a GetMan request (method, URL, headers, body).")
						.inputSchema(json, """
								{"type":"object",
								 "properties":{"command":{"type":"string","description":"A curl command line"}},
								 "required":["command"]}""")
						.build(),
						(ctx, request) -> parseCurl(String.valueOf(request.arguments().get("command"))))
				.build();
		this.port = transport.start(port);
	}

	public int port() {
		return port;
	}

	public void stop() {
		server.close();
		transport.closeGracefully().block();
	}

	private CallToolResult parseCurl(String command) {
		CurlImporter.Result result = CurlImporter.parse(command);
		RequestModel r = result.request();
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("method", r.getMethod().name());
		out.put("url", r.getUrl());
		out.put("headers", pairs(r.getHeaders()));
		out.put("bodyType", r.getBodyType().name());
		out.put("body", r.getBody());
		out.put("warnings", result.warnings());
		try {
			return text(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(out));
		} catch (IOException e) {
			return CallToolResult.builder().addTextContent(e.getMessage()).isError(true).build();
		}
	}

	private static List<String> pairs(List<KeyValue> rows) {
		return rows.stream().map(kv -> kv.getKey() + ": " + kv.getValue()).toList();
	}

	private static CallToolResult text(String text) {
		return CallToolResult.builder().addTextContent(text).build();
	}
}
