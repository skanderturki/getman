package com.getman.core.curl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.getman.core.model.BodyType;
import com.getman.core.model.HttpMethod;
import com.getman.core.model.KeyValue;
import com.getman.core.model.RequestModel;

class CurlExporterTest {

	@Test
	void simpleGet() {
		RequestModel r = new RequestModel();
		r.setUrl("https://x.io/a");
		r.getParams().add(new KeyValue("q", "1"));
		assertEquals("curl 'https://x.io/a?q=1'", CurlExporter.toCurl(r));
	}

	@Test
	void postWithHeadersAndBody() {
		RequestModel r = new RequestModel();
		r.setMethod(HttpMethod.POST);
		r.setUrl("https://x.io/users");
		r.getHeaders().add(new KeyValue("Authorization", "Bearer t"));
		r.getHeaders().add(new KeyValue("X-Off", "1", false));
		r.setBodyType(BodyType.JSON);
		r.setBody("{\"name\":\"O'Brien\"}");
		assertEquals("curl -X POST 'https://x.io/users' \\\n"
				+ "  -H 'Authorization: Bearer t' \\\n"
				+ "  -H 'Content-Type: application/json' \\\n"
				+ "  --data-raw '{\"name\":\"O'\\''Brien\"}'", CurlExporter.toCurl(r));
	}

	@Test
	void headUsesDashI() {
		RequestModel r = new RequestModel();
		r.setMethod(HttpMethod.HEAD);
		r.setUrl("x.io");
		assertEquals("curl -I 'http://x.io'", CurlExporter.toCurl(r));
	}
}
