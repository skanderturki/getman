package com.getman.core.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.getman.core.model.KeyValue;
import com.getman.core.model.RequestModel;

class UrlBuilderTest {

	private static RequestModel request(String url, KeyValue... params) {
		RequestModel r = new RequestModel();
		r.setUrl(url);
		for (KeyValue p : params) {
			r.getParams().add(p);
		}
		return r;
	}

	@Test
	void addsHttpSchemeWhenMissing() {
		assertEquals("http://localhost:8080/api", UrlBuilder.buildUrl(request("localhost:8080/api")));
		assertEquals("https://x.io", UrlBuilder.buildUrl(request("  https://x.io  ")));
	}

	@Test
	void appendsEnabledParamsEncoded() {
		RequestModel r = request("https://x.io/search", new KeyValue("q", "a b&c"), new KeyValue("skip", "1", false),
				new KeyValue("", "ignored"), new KeyValue("lang", "fr"));
		assertEquals("https://x.io/search?q=a%20b%26c&lang=fr", UrlBuilder.buildUrl(r));
	}

	@Test
	void mergesWithExistingQueryAndKeepsFragment() {
		assertEquals("https://x.io/p?a=1&b=2#top",
				UrlBuilder.buildUrl(request("https://x.io/p?a=1#top", new KeyValue("b", "2"))));
		assertEquals("https://x.io/p?b=2", UrlBuilder.buildUrl(request("https://x.io/p?", new KeyValue("b", "2"))));
	}

	@Test
	void rejectsUnusableUrls() {
		assertThrows(IllegalArgumentException.class, () -> UrlBuilder.buildUri(request("")));
		assertThrows(IllegalArgumentException.class, () -> UrlBuilder.buildUri(request("ftp://x.io/file")));
		assertThrows(IllegalArgumentException.class, () -> UrlBuilder.buildUri(request("http://bad host/")));
	}
}
