package com.getman.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExpectedStatusTest {

	@Test
	void normalizesCodesAndClasses() {
		assertEquals("", ExpectedStatus.normalize("  "));
		assertEquals("201", ExpectedStatus.normalize(" 201 "));
		assertEquals("200, 204, 3xx", ExpectedStatus.normalize("200,204  3XX, 200"));
		assertNull(ExpectedStatus.normalize("ok"));
		assertNull(ExpectedStatus.normalize("2000"));
		assertNull(ExpectedStatus.normalize("600"));
		assertNull(ExpectedStatus.normalize("2x"));
	}

	@Test
	void matchesCodesAndClasses() {
		assertTrue(ExpectedStatus.matches("201", 201));
		assertTrue(ExpectedStatus.matches("200, 204", 204));
		assertTrue(ExpectedStatus.matches("2xx", 299));
		assertFalse(ExpectedStatus.matches("2xx", 301));
		assertFalse(ExpectedStatus.matches("", 200), "nothing expected is not a match");
		assertFalse(ExpectedStatus.matches("ok", 200));
	}

	@Test
	void isPartOfTheRequestContent() {
		RequestModel r = new RequestModel();
		r.setExpectedStatus("2XX");
		assertEquals("2xx", r.getExpectedStatus());
		RequestModel copy = r.copy();
		assertTrue(r.sameContent(copy));
		copy.setExpectedStatus("201");
		assertFalse(r.sameContent(copy), "changing the expected status makes a tab dirty");
		assertEquals("2xx", r.duplicate().getExpectedStatus());
	}
}
