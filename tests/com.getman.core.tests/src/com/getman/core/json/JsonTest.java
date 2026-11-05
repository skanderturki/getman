package com.getman.core.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JsonTest {

	@Test
	void parsesAllValueTypes() {
		Object v = Json.parse("{\"s\":\"x\",\"i\":42,\"d\":1.5,\"t\":true,\"f\":false,\"n\":null,\"a\":[1,\"two\"]}");
		Map<?, ?> m = assertInstanceOf(Map.class, v);
		assertEquals("x", m.get("s"));
		assertEquals(42L, m.get("i"));
		assertEquals(new BigDecimal("1.5"), m.get("d"));
		assertEquals(Boolean.TRUE, m.get("t"));
		assertEquals(Boolean.FALSE, m.get("f"));
		assertTrue(m.containsKey("n"));
		assertNull(m.get("n"));
		assertEquals(List.of(1L, "two"), m.get("a"));
	}

	@Test
	void keepsKeyOrder() {
		assertEquals("{\"z\":1,\"a\":2,\"m\":3}", Json.write(Json.parse("{\"z\":1,\"a\":2,\"m\":3}")));
	}

	@Test
	void roundTripsEscapes() {
		String original = "quote \" backslash \\ newline \n tab \t unicode é \u0001";
		Object parsed = Json.parse(Json.write(original));
		assertEquals(original, parsed);
		assertEquals("é", Json.parse("\"\\u00e9\""));
	}

	@Test
	void keepsLargeNumbersExact() {
		assertEquals(new BigDecimal("123456789012345678901234567890"),
				Json.parse("123456789012345678901234567890"));
		assertEquals(new BigDecimal("1E+400"), Json.parse("1e400"));
	}

	@Test
	void prettifiesWithTwoSpaces() {
		assertEquals("{\n  \"a\": [\n    1,\n    2\n  ],\n  \"b\": {}\n}", Json.prettify("{\"a\":[1,2],\"b\":{}}"));
	}

	@Test
	void reportsErrorPosition() {
		JsonException e = assertThrows(JsonException.class, () -> Json.parse("{\n  \"a\": 1,\n  \"b\" 2\n}"));
		assertTrue(e.getMessage().contains("line 3"), e.getMessage());
	}

	@Test
	void rejectsInvalidDocuments() {
		for (String bad : new String[] { "", "{", "[1,]", "{\"a\":}", "tru", "01", "\"unterminated", "{} x",
				"{'a':1}" }) {
			assertThrows(JsonException.class, () -> Json.parse(bad), bad);
		}
	}
}
