package com.getman.core.curl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.getman.core.model.AuthType;
import com.getman.core.model.BodyType;
import com.getman.core.model.HttpMethod;
import com.getman.core.model.KeyValue;
import com.getman.core.model.RequestModel;

class CurlImporterTest {

	private static RequestModel parse(String command) {
		return CurlImporter.parse(command).request();
	}

	private static String header(RequestModel r, String name) {
		return r.getHeaders().stream().filter(h -> h.getKey().equalsIgnoreCase(name)).map(KeyValue::getValue)
				.findFirst().orElse(null);
	}

	@Test
	void simpleGet() {
		RequestModel r = parse("curl https://api.example.com/users?page=2");
		assertEquals(HttpMethod.GET, r.getMethod());
		assertEquals("https://api.example.com/users?page=2", r.getUrl());
		assertEquals("GET /users", r.getName());
		assertEquals(BodyType.NONE, r.getBodyType());
	}

	@Test
	void postJsonWithHeadersMultiline() {
		RequestModel r = parse("curl -X POST 'https://api.example.com/users' \\\n"
				+ "  -H 'Content-Type: application/json' \\\n"
				+ "  -H \"Accept: application/json\" \\\n"
				+ "  -d '{\"name\":\"Ada\"}'");
		assertEquals(HttpMethod.POST, r.getMethod());
		assertEquals(BodyType.JSON, r.getBodyType());
		assertEquals("{\"name\":\"Ada\"}", r.getBody());
		assertEquals("application/json", header(r, "Accept"));
		// Content-Type equals the JSON body type's default, so the header is dropped.
		assertEquals(null, header(r, "Content-Type"));
	}

	@Test
	void dataWithoutMethodMeansPostFormEncoded() {
		RequestModel r = parse("curl -d name=Ada -d age=36 https://x.test/p");
		assertEquals(HttpMethod.POST, r.getMethod());
		assertEquals(BodyType.FORM, r.getBodyType());
		assertEquals("name=Ada&age=36", r.getBody());
	}

	@Test
	void unusualContentTypeIsKept() {
		RequestModel r = parse("curl -H 'content-type: application/json; charset=utf-8' --data-raw '[1]' https://x.test");
		assertEquals(BodyType.JSON, r.getBodyType());
		assertEquals("application/json; charset=utf-8", header(r, "Content-Type"));
	}

	@Test
	void jsonOptionAddsHeaders() {
		RequestModel r = parse("curl --json '{\"a\":1}' https://x.test/a");
		assertEquals(HttpMethod.POST, r.getMethod());
		assertEquals(BodyType.JSON, r.getBodyType());
		assertEquals("application/json", header(r, "Accept"));
	}

	@Test
	void getMovesDataToQuery() {
		RequestModel r = parse("curl -G https://x.test/search?x=1 --data-urlencode 'q=hello world' -d n=5");
		assertEquals(HttpMethod.GET, r.getMethod());
		assertEquals("https://x.test/search?x=1&q=hello+world&n=5", r.getUrl());
		assertEquals(BodyType.NONE, r.getBodyType());
	}

	@Test
	void shortClustersAndAttachedValues() {
		RequestModel r = parse("curl -sSLk -XPUT -H'X-A: 1' https://x.test/i/9");
		assertEquals(HttpMethod.PUT, r.getMethod());
		assertEquals("1", header(r, "X-A"));
		assertEquals("PUT /i/9", r.getName());
	}

	@Test
	void headFlag() {
		assertEquals(HttpMethod.HEAD, parse("curl -I https://x.test").getMethod());
	}

	@Test
	void userBecomesBasicAuth() {
		RequestModel r = parse("curl -u 'ada:p@ss:word' https://x.test");
		assertEquals(AuthType.BASIC, r.getAuth().getType());
		assertEquals("ada", r.getAuth().getUsername());
		assertEquals("p@ss:word", r.getAuth().getPassword());
	}

	@Test
	void authorizationHeadersBecomeAuth() {
		RequestModel bearer = parse("curl -H 'Authorization: Bearer abc.def' https://x.test");
		assertEquals(AuthType.BEARER, bearer.getAuth().getType());
		assertEquals("abc.def", bearer.getAuth().getToken());
		assertTrue(bearer.getHeaders().isEmpty());

		RequestModel basic = parse("curl -H 'authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==' https://x.test");
		assertEquals(AuthType.BASIC, basic.getAuth().getType());
		assertEquals("Aladdin", basic.getAuth().getUsername());
		assertEquals("open sesame", basic.getAuth().getPassword());

		RequestModel other = parse("curl -H 'Authorization: Token xyz' https://x.test");
		assertEquals(AuthType.NONE, other.getAuth().getType());
		assertEquals("Token xyz", header(other, "Authorization"));
	}

	@Test
	void cookieUserAgentReferer() {
		RequestModel r = parse("curl -b 'a=1; b=2' -A 'my-agent/1.0' -e https://ref.test https://x.test");
		assertEquals("a=1; b=2", header(r, "Cookie"));
		assertEquals("my-agent/1.0", header(r, "User-Agent"));
		assertEquals("https://ref.test", header(r, "Referer"));
	}

	@Test
	void chromeBashCopyWithAnsiCQuoting() {
		RequestModel r = parse("curl 'https://x.test/api' \\\n"
				+ "  -H 'accept: */*' \\\n"
				+ "  -H 'content-type: application/json' \\\n"
				+ "  --data-raw $'{\"msg\":\"it\\'s\\\\n ok \\u00e9\"}' \\\n"
				+ "  --compressed");
		assertEquals(HttpMethod.POST, r.getMethod());
		assertEquals("{\"msg\":\"it's\\n ok \u00e9\"}", r.getBody());
		assertEquals("*/*", header(r, "accept"));
	}

	@Test
	void chromeCmdCopy() {
		RequestModel r = parse("curl \"https://x.test/api?a=1^&b=2\" ^\n"
				+ "  -H \"accept: application/json\" ^\n"
				+ "  -H \"content-type: application/json\" ^\n"
				+ "  --data-raw \"^{^\\^\"name^\\^\":^\\^\"Ada^\\^\"^}\"");
		assertEquals("https://x.test/api?a=1&b=2", r.getUrl());
		assertEquals("{\"name\":\"Ada\"}", r.getBody());
		assertEquals(BodyType.JSON, r.getBodyType());
	}

	@Test
	void powershellContinuationAndCurlExe() {
		RequestModel r = parse("curl.exe -X DELETE `\n  https://x.test/items/3");
		assertEquals(HttpMethod.DELETE, r.getMethod());
		assertEquals("https://x.test/items/3", r.getUrl());
	}

	@Test
	void promptAndPipeAreHandled() {
		CurlImporter.Result res = CurlImporter.parse("$ curl -s https://x.test/a | jq .");
		assertEquals("https://x.test/a", res.request().getUrl());
		assertEquals(1, res.warnings().size());
	}

	@Test
	void unsupportedPartsBecomeWarnings() {
		CurlImporter.Result res = CurlImporter.parse(
				"curl -F file=@a.png -d @body.json --frobnicate https://x.test/up -o out.txt --max-time 5");
		assertEquals("https://x.test/up", res.request().getUrl());
		List<String> w = res.warnings();
		assertTrue(w.stream().anyMatch(s -> s.contains("-F")), w.toString());
		assertTrue(w.stream().anyMatch(s -> s.contains("body.json")), w.toString());
		assertTrue(w.stream().anyMatch(s -> s.contains("--frobnicate")), w.toString());
		assertEquals(3, w.size(), w.toString());
	}

	@Test
	void errors() {
		assertThrows(CurlParseException.class, () -> CurlImporter.parse("wget https://x.test"));
		assertThrows(CurlParseException.class, () -> CurlImporter.parse("curl -s"));
		assertThrows(CurlParseException.class, () -> CurlImporter.parse("curl 'https://x.test"));
		assertThrows(CurlParseException.class, () -> CurlImporter.parse("curl https://x.test -H"));
	}

	@Test
	void looksLikeCurl() {
		assertTrue(CurlImporter.looksLikeCurl("curl https://x"));
		assertTrue(CurlImporter.looksLikeCurl("  $ curl -s x"));
		assertTrue(CurlImporter.looksLikeCurl("CURL.EXE x"));
		assertTrue(CurlImporter.looksLikeCurl("/usr/bin/curl x"));
		assertFalse(CurlImporter.looksLikeCurl("https://curl.se"));
		assertFalse(CurlImporter.looksLikeCurl("curly"));
		assertFalse(CurlImporter.looksLikeCurl("curl"));
	}

	@Test
	void exportThenImportRoundTrips() {
		RequestModel original = new RequestModel();
		original.setMethod(HttpMethod.PATCH);
		original.setUrl("https://api.example.com/users/7?dry=1");
		original.getHeaders().add(new KeyValue("X-Note", "it's \"quoted\" $HOME"));
		original.getAuth().setType(AuthType.BEARER);
		original.getAuth().setToken("tok-123");
		original.setBodyType(BodyType.JSON);
		original.setBody("{\n  \"name\": \"O'Brien\"\n}");

		RequestModel back = parse(CurlExporter.toCurl(original));

		assertEquals(original.getMethod(), back.getMethod());
		assertEquals(original.getUrl(), back.getUrl());
		assertEquals(original.getHeaders(), back.getHeaders());
		assertEquals(original.getAuth(), back.getAuth());
		assertEquals(original.getBodyType(), back.getBodyType());
		assertEquals(original.getBody(), back.getBody());
	}

	@Test
	void basicAuthRoundTrips() {
		RequestModel original = new RequestModel();
		original.setUrl("http://localhost:3000/api");
		original.getAuth().setType(AuthType.BASIC);
		original.getAuth().setUsername("ada");
		original.getAuth().setPassword("p'w");
		assertEquals(original.getAuth(), parse(CurlExporter.toCurl(original)).getAuth());
	}
}
