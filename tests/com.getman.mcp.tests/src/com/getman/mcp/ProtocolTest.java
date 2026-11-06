package com.getman.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getman.core.model.Environment;
import com.getman.core.model.KeyValue;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.json.schema.jackson2.DefaultJsonSchemaValidator;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

/** The real server over HTTP, driven by the SDK's own client: what Claude Code and other clients do. */
class ProtocolTest {
	private static final String TOKEN = "test-token-0123456789";

	private Fakes.Host host;
	private Fakes.EchoServer echo;
	private GetmanMcpServer server;
	private McpSyncClient client;

	@BeforeEach
	void setUp() throws Exception {
		host = new Fakes.Host();
		echo = new Fakes.EchoServer();
		Environment local = new Environment("Local");
		local.getVariables().add(new KeyValue("baseUrl", echo.baseUrl()));
		host.workspace.environments.add(local);
		host.workspace.active = local;
		server = new GetmanMcpServer(0, TOKEN, "0.9.0.test", host);

		ObjectMapper mapper = new ObjectMapper();
		HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(base())
				.endpoint("/mcp")
				.jsonMapper(new JacksonMcpJsonMapper(mapper))
				.requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + TOKEN))
				.build();
		client = McpClient.sync(transport)
				.jsonSchemaValidator(new DefaultJsonSchemaValidator(mapper))
				.requestTimeout(Duration.ofSeconds(30))
				.build();
	}

	@AfterEach
	void tearDown() {
		client.close();
		server.stop();
		echo.close();
	}

	private String base() {
		return "http://127.0.0.1:" + server.port();
	}

	@Test
	void clientCanInitializeListCreateAndSend() throws Exception {
		McpSchema.InitializeResult init = client.initialize();
		assertEquals("getman", init.serverInfo().name());
		assertTrue(init.instructions().contains("{{baseUrl}}"));
		assertEquals(8, client.listTools().tools().size());

		CallToolResult created = client.callTool(new CallToolRequest("create_request", Map.of("collection",
				"demo (test)", "name", "Ping", "url", "{{baseUrl}}/ping")));
		assertFalse(Boolean.TRUE.equals(created.isError()));
		String id = (String) new ObjectMapper().readValue(text(created), Map.class).get("id");

		CallToolResult sent = client.callTool(new CallToolRequest("send_request", Map.of("id", id)));
		assertFalse(Boolean.TRUE.equals(sent.isError()), text(sent));
		assertTrue(text(sent).contains("GET /ping"));
		assertEquals(1, echo.requests.get());
	}

	@Test
	void schemaViolationsAreToolErrors() {
		client.initialize();
		CallToolResult result = client.callTool(new CallToolRequest("send_request", Map.of("method", "BREW",
				"url", "http://x")));
		assertTrue(Boolean.TRUE.equals(result.isError()));
	}

	@Test
	void requestsWithoutTheTokenOrFromBrowsersAreRefused() throws Exception {
		HttpClient http = HttpClient.newHttpClient();
		String init = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base() + "/mcp"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(init));
		assertEquals(401, http.send(request.copy().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
		assertEquals(401, http.send(request.copy().header("Authorization", "Bearer wrong").build(),
				HttpResponse.BodyHandlers.ofString()).statusCode());
		assertEquals(403, http.send(request.copy().header("Authorization", "Bearer " + TOKEN)
				.header("Origin", "https://evil.example.com").build(), HttpResponse.BodyHandlers.ofString()).statusCode());
		assertEquals(200, http.send(request.copy().header("Authorization", "Bearer " + TOKEN).build(),
				HttpResponse.BodyHandlers.ofString()).statusCode());
	}

	private static String text(CallToolResult result) {
		return ((McpSchema.TextContent) result.content().get(0)).text();
	}
}
