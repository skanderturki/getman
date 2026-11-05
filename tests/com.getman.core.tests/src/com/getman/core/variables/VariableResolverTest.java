package com.getman.core.variables;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.getman.core.model.AuthType;
import com.getman.core.model.BodyType;
import com.getman.core.model.Environment;
import com.getman.core.model.HttpMethod;
import com.getman.core.model.KeyValue;
import com.getman.core.model.RequestModel;
import com.getman.core.store.StateCodec;
import com.getman.core.store.WorkspaceState;

class VariableResolverTest {

	@Test
	void replacesKnownNamesWithOptionalSpaces() {
		VariableResolver r = new VariableResolver(Map.of("host", "api.test", "v", "2"));
		assertEquals("https://api.test/v2/x", r.resolve("https://{{host}}/v{{ v }}/x"));
		assertTrue(r.getUnresolved().isEmpty());
	}

	@Test
	void unknownNamesStayAndAreReported() {
		VariableResolver r = new VariableResolver(Map.of("a", "1"));
		assertEquals("1-{{b}}-{{ c }}-{{b}}", r.resolve("{{a}}-{{b}}-{{ c }}-{{b}}"));
		assertEquals(List.of("b", "c"), List.copyOf(r.getUnresolved()));
	}

	@Test
	void nonVariableBracesAreUntouched() {
		VariableResolver r = new VariableResolver(Map.of());
		String template = "{{#each items}}{{/each}} {{ bad name }} {{}}";
		assertEquals(template, r.resolve(template));
		assertTrue(r.getUnresolved().isEmpty());
	}

	@Test
	void valuesMayReferenceOtherVariables() {
		VariableResolver r = new VariableResolver(Map.of("base", "https://{{host}}:{{port}}", "host", "localhost",
				"port", "3000"));
		assertEquals("https://localhost:3000/api", r.resolve("{{base}}/api"));
	}

	@Test
	void cyclesStopInsteadOfLooping() {
		VariableResolver r = new VariableResolver(Map.of("a", "x{{b}}", "b", "y{{a}}", "self", "{{self}}!"));
		assertEquals("xy{{a}}", r.resolve("{{a}}"));
		assertEquals("{{self}}!", r.resolve("{{self}}"));
	}

	@Test
	void dollarAndBackslashInValuesAreLiteral() {
		VariableResolver r = new VariableResolver(Map.of("p", "a$1\\b"));
		assertEquals("a$1\\b", r.resolve("{{p}}"));
	}

	@Test
	void resolvesWholeRequestWithoutChangingOriginal() {
		RequestModel req = new RequestModel();
		req.setMethod(HttpMethod.POST);
		req.setUrl("{{base}}/users");
		req.getParams().add(new KeyValue("{{pk}}", "{{pv}}"));
		req.getHeaders().add(new KeyValue("X-Tenant", "{{tenant}}"));
		req.setBodyType(BodyType.JSON);
		req.setBody("{\"tenant\":\"{{tenant}}\"}");
		req.getAuth().setType(AuthType.BEARER);
		req.getAuth().setToken("{{token}}");

		Map<String, String> vars = Map.of("base", "https://api.test", "pk", "page", "pv", "2", "tenant", "acme",
				"token", "t0k");
		RequestModel resolved = new VariableResolver(vars).resolve(req);

		assertEquals("https://api.test/users", resolved.getUrl());
		assertEquals(new KeyValue("page", "2"), resolved.getParams().get(0));
		assertEquals("acme", resolved.getHeaders().get(0).getValue());
		assertEquals("{\"tenant\":\"acme\"}", resolved.getBody());
		assertEquals("t0k", resolved.getAuth().getToken());
		assertEquals(req.getId(), resolved.getId());
		// The stored request keeps its placeholders.
		assertEquals("{{base}}/users", req.getUrl());
		assertEquals("{{token}}", req.getAuth().getToken());
	}

	@Test
	void unresolvedInOnlyLooksAtWhatIsSent() {
		RequestModel req = new RequestModel();
		req.setUrl("{{base}}/x");
		req.getHeaders().add(new KeyValue("X-Off", "{{disabled}}", false));
		req.setBody("{{noBodyType}}"); // body type NONE: not sent
		req.getAuth().setToken("{{tokenButNoAuth}}"); // auth type NONE: not sent
		req.getAuth().setType(AuthType.NONE);
		assertEquals(Set.of("base"), VariableResolver.unresolvedIn(req, Map.of()));

		req.getAuth().setType(AuthType.BEARER);
		assertEquals(Set.of("base", "tokenButNoAuth"), VariableResolver.unresolvedIn(req, Map.of()));
	}

	@Test
	void environmentActiveValuesSkipDisabledAndBlankRows() {
		Environment env = new Environment("Local");
		env.getVariables().add(new KeyValue("host", "localhost"));
		env.getVariables().add(new KeyValue("port", "3000", false));
		env.getVariables().add(new KeyValue("  ", "ignored"));
		env.getVariables().add(new KeyValue("host", "127.0.0.1"));
		assertEquals(Map.of("host", "127.0.0.1"), env.activeValues());
	}

	@Test
	void stateRoundTripsEnvironmentsAndActiveSelection() {
		WorkspaceState state = new WorkspaceState();
		Environment local = new Environment("Local");
		local.getVariables().add(new KeyValue("base", "http://localhost:3000"));
		Environment prod = new Environment("Production");
		prod.getVariables().add(new KeyValue("base", "https://api.example.com", false));
		state.getEnvironments().add(local);
		state.getEnvironments().add(prod);
		state.setActiveEnvironmentId(prod.getId());

		WorkspaceState loaded = StateCodec.fromJson(StateCodec.toJson(state));

		assertEquals(2, loaded.getEnvironments().size());
		assertEquals(prod.getId(), loaded.getActiveEnvironmentId());
		assertEquals("Local", loaded.getEnvironments().get(0).getName());
		assertEquals(local.getVariables(), loaded.getEnvironments().get(0).getVariables());
		assertEquals(prod.getVariables(), loaded.getEnvironments().get(1).getVariables());
	}

	@Test
	void oldStateAndDanglingActiveIdLoadSafely() {
		assertTrue(StateCodec.fromJson("{\"openTabs\":[]}").getEnvironments().isEmpty());
		WorkspaceState s = StateCodec.fromJson("{\"environments\":[],\"activeEnvironmentId\":\"gone\"}");
		assertEquals("", s.getActiveEnvironmentId());
	}
}
