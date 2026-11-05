package com.getman.core.postman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.getman.core.json.Json;
import com.getman.core.model.AuthType;
import com.getman.core.model.BodyType;
import com.getman.core.model.Environment;
import com.getman.core.model.Folder;
import com.getman.core.model.FormField;
import com.getman.core.model.HttpMethod;
import com.getman.core.model.KeyValue;
import com.getman.core.model.RequestCollection;
import com.getman.core.model.RequestModel;

class PostmanImporterTest {

	/** A v2.1 collection exercising folders, inherited auth, every body mode and the skipped features. */
	private static final String V21 = """
			{
			  "info": {"_postman_id": "abc", "name": "Courses API",
			           "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
			  "auth": {"type": "bearer", "bearer": [{"key": "token", "value": "{{token}}", "type": "string"}]},
			  "variable": [{"key": "base", "value": "http://localhost:3000"}, {"key": "off", "value": "x", "disabled": true}],
			  "event": [{"listen": "prerequest", "script": {"exec": ["pm.environment.set('t', 1)"]}}],
			  "item": [
			    {"name": "Courses", "item": [
			      {"name": "List courses",
			       "request": {"method": "GET",
			                   "header": [{"key": "Accept", "value": "application/json"},
			                              {"key": "X-Off", "value": "1", "disabled": true}],
			                   "url": {"raw": "{{base}}/api/v1/courses?page=1",
			                           "host": ["{{base}}"], "path": ["api", "v1", "courses"],
			                           "query": [{"key": "page", "value": "1"}]}},
			       "event": [{"listen": "test", "script": {"exec": ["pm.test('ok')"]}}]},
			      {"name": "Create course",
			       "request": {"method": "POST",
			                   "header": [{"key": "Content-Type", "value": "application/json"}],
			                   "body": {"mode": "raw", "raw": "{\\"code\\": \\"SE201\\"}",
			                            "options": {"raw": {"language": "json"}}},
			                   "url": "{{base}}/api/v1/courses"}},
			      {"name": "Get course",
			       "request": {"method": "GET",
			                   "url": {"raw": "{{base}}/api/v1/courses/:id",
			                           "variable": [{"key": "id", "value": "42"}]}}}
			    ]},
			    {"name": "Public", "auth": {"type": "noauth"}, "item": [
			      {"name": "Login",
			       "request": {"method": "POST",
			                   "body": {"mode": "urlencoded",
			                            "urlencoded": [{"key": "user", "value": "ada lovelace"},
			                                           {"key": "pass", "value": "{{password}}"},
			                                           {"key": "skip", "value": "1", "disabled": true}]},
			                   "url": "{{base}}/login"}},
			      {"name": "Basic", "request": {"method": "GET", "url": "{{base}}/b",
			         "auth": {"type": "basic", "basic": [{"key": "username", "value": "ada"},
			                                             {"key": "password", "value": "pw"}]}}},
			      {"name": "Key", "request": {"method": "GET", "url": "{{base}}/k",
			         "auth": {"type": "apikey", "apikey": [{"key": "key", "value": "X-Api-Key"},
			                                               {"key": "value", "value": "k123"},
			                                               {"key": "in", "value": "header"}]}}},
			      {"name": "OAuth", "request": {"method": "GET", "url": "{{base}}/o",
			         "auth": {"type": "oauth2", "oauth2": []}}},
			      {"name": "Upload", "request": {"method": "POST", "url": "{{base}}/up",
			         "body": {"mode": "formdata", "formdata": [{"key": "title", "value": "cv", "type": "text"},
			                                                   {"key": "file", "src": "/tmp/a.pdf", "type": "file"}]}}},
			      {"name": "GraphQL", "request": {"method": "POST", "url": "{{base}}/graphql",
			         "body": {"mode": "graphql", "graphql": {"query": "{ me { id } }", "variables": "{\\"a\\": 1}"}}}},
			      {"name": "Guid", "request": {"method": "GET", "url": "{{base}}/g?id={{$guid}}"}}
			    ]}
			  ]
			}
			""";

	private static RequestModel find(RequestCollection c, String name) {
		return c.allRequests().stream().filter(r -> r.getName().equals(name)).findFirst().orElseThrow();
	}

	private static String header(RequestModel r, String name) {
		return r.getHeaders().stream().filter(h -> h.getKey().equalsIgnoreCase(name)).map(KeyValue::getValue)
				.findFirst().orElse(null);
	}

	@Test
	void detectsKinds() {
		assertEquals(PostmanImporter.Kind.POSTMAN_COLLECTION, PostmanImporter.detect(Json.parse(V21)));
		assertEquals(PostmanImporter.Kind.POSTMAN_ENVIRONMENT, PostmanImporter.detect(Json.parse(
				"{\"name\":\"Local\",\"values\":[],\"_postman_variable_scope\":\"environment\"}")));
		assertEquals(PostmanImporter.Kind.OTHER, PostmanImporter.detect(Json.parse("{\"getmanCollection\":2}")));
	}

	@Test
	void importsTreeNamesAndVariables() {
		RequestCollection c = PostmanImporter.importCollection(V21).collection();
		assertEquals("Courses API", c.getName());
		Folder courses = assertInstanceOf(Folder.class, c.getItems().get(0));
		assertEquals("Courses", courses.getName());
		assertEquals(3, courses.getItems().size());
		assertEquals(10, c.allRequests().size());
		assertEquals(List.of(new KeyValue("base", "http://localhost:3000"), new KeyValue("off", "x", false)),
				c.getVariables());
		c.allRequests().forEach(r -> assertEquals(c.getId(), r.getCollectionId()));
	}

	@Test
	void requestDetails() {
		RequestCollection c = PostmanImporter.importCollection(V21).collection();

		RequestModel list = find(c, "List courses");
		assertEquals(HttpMethod.GET, list.getMethod());
		assertEquals("{{base}}/api/v1/courses?page=1", list.getUrl());
		assertEquals("application/json", header(list, "Accept"));
		assertEquals(new KeyValue("X-Off", "1", false), list.getHeaders().get(1));

		RequestModel create = find(c, "Create course");
		assertEquals(BodyType.JSON, create.getBodyType());
		assertEquals("{\"code\": \"SE201\"}", create.getBody());
		assertEquals(null, header(create, "Content-Type")); // same as the JSON default: dropped

		assertEquals("{{base}}/api/v1/courses/42", find(c, "Get course").getUrl());
	}

	@Test
	void authIsInheritedOverriddenAndConverted() {
		RequestCollection c = PostmanImporter.importCollection(V21).collection();
		RequestModel list = find(c, "List courses");
		assertEquals(AuthType.BEARER, list.getAuth().getType()); // from the collection
		assertEquals("{{token}}", list.getAuth().getToken());
		assertEquals(AuthType.NONE, find(c, "Login").getAuth().getType()); // folder says noauth

		RequestModel basic = find(c, "Basic");
		assertEquals(AuthType.BASIC, basic.getAuth().getType());
		assertEquals("ada", basic.getAuth().getUsername());
		assertEquals("pw", basic.getAuth().getPassword());

		assertEquals("k123", header(find(c, "Key"), "X-Api-Key"));
		assertEquals(AuthType.NONE, find(c, "OAuth").getAuth().getType());
	}

	@Test
	void bodies() {
		RequestCollection c = PostmanImporter.importCollection(V21).collection();
		RequestModel login = find(c, "Login");
		assertEquals(BodyType.FORM, login.getBodyType());
		assertEquals("user=ada+lovelace&pass={{password}}", login.getBody());

		RequestModel upload = find(c, "Upload");
		assertEquals(BodyType.MULTIPART, upload.getBodyType());
		assertEquals(List.of(FormField.text("title", "cv"), FormField.file("file", "/tmp/a.pdf")),
				upload.getFormFields());

		RequestModel gql = find(c, "GraphQL");
		assertEquals(BodyType.JSON, gql.getBodyType());
		Object payload = Json.parse(gql.getBody());
		assertEquals("{ me { id } }", ((java.util.Map<?, ?>) payload).get("query"));
	}

	@Test
	void skippedFeaturesAreReported() {
		List<String> w = PostmanImporter.importCollection(V21).warnings();
		assertTrue(w.stream().anyMatch(s -> s.startsWith("2 pre-request or test script")), w.toString());
		assertTrue(w.stream().anyMatch(s -> s.contains("oauth2")), w.toString());
		assertTrue(w.stream().anyMatch(s -> s.contains("{{$guid}}")), w.toString());
		assertEquals(3, w.size(), w.toString());
	}

	@Test
	void fileBodiesAndMissingPaths() {
		String json = """
				{"info": {"name": "Files", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
				 "item": [
				   {"name": "bin", "request": {"method": "PUT", "url": "http://x/up",
				      "body": {"mode": "file", "file": {"src": "C:/data/photo.jpg"}}}},
				   {"name": "multi", "request": {"method": "POST", "url": "http://x/m",
				      "header": [{"key": "Content-Type", "value": "multipart/form-data"}],
				      "body": {"mode": "formdata", "formdata": [
				        {"key": "docs", "type": "file", "src": ["/a.txt", "/b.txt"]},
				        {"key": "empty", "type": "file", "src": null},
				        {"key": "note", "value": "hi", "type": "text", "disabled": true}]}}}]}
				""";
		PostmanImporter.CollectionResult res = PostmanImporter.importCollection(json);
		RequestModel bin = find(res.collection(), "bin");
		assertEquals(BodyType.BINARY, bin.getBodyType());
		assertEquals("C:/data/photo.jpg", bin.getBinaryFile());

		RequestModel multi = find(res.collection(), "multi");
		assertEquals(List.of(FormField.file("docs", "/a.txt"), FormField.file("empty", ""),
				new FormField("note", "hi", false, false)), multi.getFormFields());
		assertTrue(multi.getHeaders().isEmpty()); // our own multipart Content-Type (with boundary) is used
		assertEquals(1, res.warnings().size(), res.warnings().toString());
	}

	@Test
	void v20QuirksHeaderStringAuthObjectAndRebuiltUrl() {
		String v20 = """
				{"info": {"name": "Old", "schema": "https://schema.getpostman.com/json/collection/v2.0.0/collection.json"},
				 "item": [{"name": "r", "request": {"method": "put",
				   "header": "Accept: text/plain\\nX-A: 1",
				   "auth": {"type": "bearer", "bearer": {"token": "t0k"}},
				   "url": {"protocol": "https", "host": ["api", "example", "com"], "port": "8443",
				           "path": ["v1", "items"], "query": [{"key": "q", "value": "a"}, {"key": "x", "disabled": true}]}}},
				   {"name": "s", "request": "https://example.com/plain"}]}
				""";
		RequestCollection c = PostmanImporter.importCollection(v20).collection();
		RequestModel r = find(c, "r");
		assertEquals(HttpMethod.PUT, r.getMethod());
		assertEquals("https://api.example.com:8443/v1/items?q=a", r.getUrl());
		assertEquals("1", header(r, "X-A"));
		assertEquals("t0k", r.getAuth().getToken());
		assertEquals("https://example.com/plain", find(c, "s").getUrl());
	}

	@Test
	void environmentImport() {
		PostmanImporter.EnvironmentResult res = PostmanImporter.importEnvironment("""
				{"id": "e1", "name": "Staging", "_postman_variable_scope": "environment",
				 "values": [{"key": "base", "value": "https://staging.test", "enabled": true},
				            {"key": "old", "value": "x", "enabled": false},
				            {"key": "token", "value": "", "type": "secret", "enabled": true}]}
				""");
		Environment env = res.environment();
		assertEquals("Staging", env.getName());
		assertEquals(List.of(new KeyValue("base", "https://staging.test"), new KeyValue("old", "x", false),
				new KeyValue("token", "")), env.getVariables());
		assertEquals(1, res.warnings().size());
	}

	@Test
	void errors() {
		assertThrows(PostmanImportException.class, () -> PostmanImporter.importCollection("{not json"));
		assertThrows(PostmanImportException.class,
				() -> PostmanImporter.importCollection("{\"requests\":[],\"order\":[],\"name\":\"v1\"}"));
		assertThrows(PostmanImportException.class, () -> PostmanImporter.importCollection("{\"a\":1}"));
		assertThrows(PostmanImportException.class, () -> PostmanImporter.importEnvironment("[]"));
	}

	@Test
	void encodingKeepsVariables() {
		assertEquals("a+b%26{{x y}}c", PostmanImporter.encodeKeepingVariables("a b&{{x y}}c"));
	}
}
