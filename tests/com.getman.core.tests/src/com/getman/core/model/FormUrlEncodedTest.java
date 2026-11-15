package com.getman.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.getman.core.variables.VariableResolver;

class FormUrlEncodedTest {

	@Test
	void encodesValuesLikeABrowser() {
		List<KeyValue> fields = List.of(new KeyValue("user", "ada lovelace"), new KeyValue("password", "p&ss=+%é"),
				new KeyValue("off", "1", false), new KeyValue(" ", "no key"));
		assertEquals("user=ada+lovelace&password=p%26ss%3D%2B%25%C3%A9", FormUrlEncoded.encode(fields));
	}

	@Test
	void keepsPlaceholdersUntilResolved() {
		assertEquals("a+b%26{{x y}}c=1", FormUrlEncoded.encode(List.of(new KeyValue("a b&{{x y}}c", "1"))));
	}

	@Test
	void parseDecodesAndRoundTrips() {
		List<KeyValue> fields = FormUrlEncoded.parse("user=ada+lovelace&password=p%26ss&flag&&bad=%zz");
		assertEquals(List.of(new KeyValue("user", "ada lovelace"), new KeyValue("password", "p&ss"),
				new KeyValue("flag", ""), new KeyValue("bad", "%zz")), fields);
		assertEquals("user=ada+lovelace&password=p%26ss", FormUrlEncoded.encode(fields.subList(0, 2)));
	}

	@Test
	void requestSendsActiveFieldsAndEncodesResolvedVariables() {
		RequestModel r = new RequestModel();
		r.setBodyType(BodyType.FORM);
		assertFalse(r.hasBody());
		r.getUrlEncodedFields().add(new KeyValue("username", "usera"));
		r.getUrlEncodedFields().add(new KeyValue("password", "{{pwd}}"));
		assertTrue(r.hasBody());
		assertEquals("username=usera&password={{pwd}}", r.bodyText());

		RequestModel resolved = new VariableResolver(Map.of("pwd", "a&b c")).resolve(r);
		assertEquals("username=usera&password=a%26b+c", resolved.bodyText());
		assertEquals("username=usera&password={{pwd}}", r.bodyText());
	}

	@Test
	void textBodiesConvertToFields() {
		RequestModel r = new RequestModel();
		r.setBodyType(BodyType.FORM);
		r.setBody("old");
		r.setUrlEncodedFieldsFromText("a=1&b=2");
		assertEquals("", r.getBody());
		assertEquals(2, r.getUrlEncodedFields().size());
		assertEquals("a=1&b=2", r.copy().bodyText());
	}
}
