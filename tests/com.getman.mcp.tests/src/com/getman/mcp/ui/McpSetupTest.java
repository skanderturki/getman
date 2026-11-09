package com.getman.mcp.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.getman.core.json.Json;

class McpSetupTest {
	private static final String TOKEN = "abc-DEF_123";

	@SuppressWarnings("unchecked")
	private static Map<String, Object> getman(String json) {
		Map<String, Object> root = (Map<String, Object>) Json.parse(json);
		return (Map<String, Object>) ((Map<String, Object>) root.get("servers")).get("getman");
	}

	@Test
	@SuppressWarnings("unchecked")
	void copilotEclipseConfigIsValidJsonWithHeadersUnderRequestInit() {
		Map<String, Object> server = getman(McpPreferences.copilotEclipseConfig(47200, TOKEN));
		assertEquals("http://127.0.0.1:47200/mcp", server.get("url"));
		Map<String, Object> init = (Map<String, Object>) server.get("requestInit");
		assertEquals(Map.of("Authorization", "Bearer " + TOKEN), init.get("headers"));
	}

	@Test
	void vsCodeConfigIsValidJsonOfTypeHttp() {
		Map<String, Object> server = getman(McpPreferences.vsCodeConfig(47123, TOKEN));
		assertEquals("http", server.get("type"));
		assertEquals("http://127.0.0.1:47123/mcp", server.get("url"));
		assertEquals(Map.of("Authorization", "Bearer " + TOKEN), server.get("headers"));
	}

	@Test
	void codexConfigIsATomlTable() {
		assertEquals("[mcp_servers.getman]\n" //
				+ "url = \"http://127.0.0.1:47123/mcp\"\n" //
				+ "http_headers = { \"Authorization\" = \"Bearer " + TOKEN + "\" }\n",
				McpPreferences.codexConfig(47123, TOKEN));
	}

	@Test
	void claudeCodeCommandUsesUserScope() {
		assertEquals("claude mcp add --transport http --scope user getman http://127.0.0.1:47123/mcp "
				+ "--header \"Authorization: Bearer " + TOKEN + "\"", McpPreferences.claudeCodeCommand(47123, TOKEN));
	}
}
