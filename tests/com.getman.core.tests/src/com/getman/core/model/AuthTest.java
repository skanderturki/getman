package com.getman.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.getman.core.curl.CurlExporter;
import com.getman.core.store.CollectionFile;
import com.getman.core.store.StateCodec;
import com.getman.core.store.WorkspaceState;

class AuthTest {

	private static RequestModel bearer(String token) {
		RequestModel r = new RequestModel();
		r.setUrl("https://api.example.com/me");
		r.getAuth().setType(AuthType.BEARER);
		r.getAuth().setToken(token);
		return r;
	}

	private static RequestModel basic(String user, String password) {
		RequestModel r = new RequestModel();
		r.setUrl("https://api.example.com/me");
		r.getAuth().setType(AuthType.BASIC);
		r.getAuth().setUsername(user);
		r.getAuth().setPassword(password);
		return r;
	}

	@Test
	void bearerHeaderTrimsToken() {
		assertEquals("Bearer abc.def", bearer("  abc.def \n").getAuth().headerValue());
	}

	@Test
	void basicHeaderMatchesRfc7617Example() {
		assertEquals("Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==", basic("Aladdin", "open sesame").getAuth().headerValue());
	}

	@Test
	void basicHeaderUsesUtf8() {
		// "test:123£" in UTF-8, from RFC 7617 section 2.1
		assertEquals("Basic dGVzdDoxMjPCow==", basic("test", "123£").getAuth().headerValue());
	}

	@Test
	void emptyFieldsSendNothing() {
		assertNull(bearer("   ").getAuth().headerValue());
		assertNull(basic("", "").getAuth().headerValue());
		assertNull(new RequestModel().getAuth().headerValue());
	}

	@Test
	void fieldsOfOtherTypesAreKeptButNotSent() {
		RequestModel r = basic("ada", "pw");
		r.getAuth().setType(AuthType.NONE);
		assertNull(r.authorizationFromAuth());
		r.getAuth().setType(AuthType.BASIC);
		assertEquals("ada", r.getAuth().getUsername());
	}

	@Test
	void explicitAuthorizationHeaderWins() {
		RequestModel r = bearer("from-auth");
		r.getHeaders().add(new KeyValue("authorization", "Custom xyz"));
		assertNull(r.authorizationFromAuth());
		r.getHeaders().get(0).setEnabled(false);
		assertEquals("Bearer from-auth", r.authorizationFromAuth());
	}

	@Test
	void curlUsesHeaderForBearerAndDashUForBasic() {
		assertTrue(CurlExporter.toCurl(bearer("t0k")).contains("-H 'Authorization: Bearer t0k'"));
		assertTrue(CurlExporter.toCurl(basic("ada", "it's")).contains("-u 'ada:it'\\''s'"));
		RequestModel overridden = bearer("t0k");
		overridden.getHeaders().add(new KeyValue("Authorization", "Custom"));
		String curl = CurlExporter.toCurl(overridden);
		assertTrue(curl.contains("-H 'Authorization: Custom'"));
		assertFalse(curl.contains("Bearer"));
	}

	@Test
	void copyDuplicateAndSameContentIncludeAuth() {
		RequestModel r = basic("ada", "pw");
		RequestModel copy = r.copy();
		assertTrue(r.sameContent(copy));
		copy.getAuth().setPassword("changed");
		assertFalse(r.sameContent(copy));
		assertEquals("pw", r.getAuth().getPassword());
		assertEquals(r.getAuth(), r.duplicate().getAuth());
	}

	@Test
	void stateRoundTripsAuth() {
		WorkspaceState state = new WorkspaceState();
		state.getOpenTabs().add(basic("ada", "pw"));
		state.getOpenTabs().add(new RequestModel());

		WorkspaceState loaded = StateCodec.fromJson(StateCodec.toJson(state));

		assertEquals(state.getOpenTabs().get(0).getAuth(), loaded.getOpenTabs().get(0).getAuth());
		assertEquals(AuthType.NONE, loaded.getOpenTabs().get(1).getAuth().getType());
		assertFalse(StateCodec.toJson(state).contains("\"auth\": {\n      \"type\": \"NONE\""));
	}

	@Test
	void exportLeavesOutSecretsUnlessAsked() {
		RequestCollection c = new RequestCollection("API");
		c.put(basic("ada", "s3cret"));
		c.put(bearer("tok-123"));
		assertTrue(CollectionFile.hasSecrets(c));

		String safe = CollectionFile.toJson(c);
		assertFalse(safe.contains("s3cret"));
		assertFalse(safe.contains("tok-123"));
		RequestCollection imported = CollectionFile.fromJson(safe);
		assertEquals(AuthType.BASIC, imported.getRequests().get(0).getAuth().getType());
		assertEquals("ada", imported.getRequests().get(0).getAuth().getUsername());
		assertEquals("", imported.getRequests().get(0).getAuth().getPassword());
		// The live collection is untouched.
		assertEquals("s3cret", c.getRequests().get(0).getAuth().getPassword());

		String full = CollectionFile.toJson(c, true);
		assertTrue(full.contains("s3cret"));
		assertTrue(full.contains("tok-123"));
	}

	@Test
	void toStringNeverShowsSecrets() {
		RequestModel r = basic("ada", "s3cret");
		r.getAuth().setToken("tok");
		assertFalse(r.getAuth().toString().contains("s3cret"));
		assertFalse(r.getAuth().toString().contains("tok"));
	}
}
