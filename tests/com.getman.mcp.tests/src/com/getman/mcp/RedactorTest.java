package com.getman.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.getman.core.model.AuthType;
import com.getman.core.model.Environment;
import com.getman.core.model.KeyValue;
import com.getman.core.model.RequestModel;

class RedactorTest {

	@Test
	void masksLiteralCredentialsButNotVariables() {
		assertEquals("", Redactor.maskLiteral(""));
		assertEquals("{{token}}", Redactor.maskLiteral("{{token}}"));
		assertEquals("Bearer {{ token }}", Redactor.maskLiteral("Bearer {{ token }}"));
		assertEquals(Redactor.MASK, Redactor.maskLiteral("Bearer abc123"));
		assertEquals(Redactor.MASK, Redactor.maskLiteral("{{user}}:hunter2"));
	}

	@Test
	void replacesSecretVariableValuesEverywhere() {
		Environment env = new Environment("Local");
		env.getVariables().add(new KeyValue("token", "tok-123456", true, true));
		env.getVariables().add(new KeyValue("host", "api.example.com"));
		env.getVariables().add(new KeyValue("pin", "12", true, true)); // too short to search for
		Redactor r = Redactor.of(List.of(env), List.of(), null);
		assertEquals("a [secret] b api.example.com 12", r.redact("a tok-123456 b api.example.com 12"));
	}

	@Test
	void hidesTheCredentialsOfTheSentRequest() {
		RequestModel sent = new RequestModel();
		sent.getAuth().setType(AuthType.BASIC);
		sent.getAuth().setUsername("ada");
		sent.getAuth().setPassword("p4ssw0rd!");
		sent.getHeaders().add(new KeyValue("X-Api-Key", "key-abcdef"));
		Redactor r = Redactor.of(List.of(), List.of(), sent);
		String basic = Base64.getEncoder().encodeToString("ada:p4ssw0rd!".getBytes());
		String echoed = "{\"auth\":\"Basic " + basic + "\",\"key\":\"key-abcdef\",\"pw\":\"p4ssw0rd!\"}";
		String out = r.redact(echoed);
		assertFalse(out.contains(basic), out);
		assertFalse(out.contains("key-abcdef"), out);
		assertFalse(out.contains("p4ssw0rd!"), out);
		assertTrue(Redactor.isSensitiveHeader(" authorization "));
		assertFalse(Redactor.isSensitiveHeader("Accept"));
	}
}
