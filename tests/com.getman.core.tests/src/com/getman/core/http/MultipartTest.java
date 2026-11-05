package com.getman.core.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.getman.core.curl.CurlExporter;
import com.getman.core.curl.CurlImporter;
import com.getman.core.model.BodyType;
import com.getman.core.model.FormField;
import com.getman.core.model.HttpMethod;
import com.getman.core.model.KeyValue;
import com.getman.core.model.RequestModel;
import com.getman.core.model.ResponseModel;
import com.getman.core.store.StateCodec;
import com.getman.core.store.WorkspaceState;
import com.getman.core.variables.VariableResolver;

class MultipartTest {
	private static final String OK = "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";

	private final HttpService http = new HttpService();
	private Path dir;
	private Path textFile;

	@BeforeEach
	void setUp() throws IOException {
		dir = Files.createTempDirectory("getman-multipart");
		textFile = dir.resolve("note one.txt");
		Files.writeString(textFile, "hello file\nline 2", StandardCharsets.UTF_8);
	}

	@AfterEach
	void tearDown() throws IOException {
		Files.deleteIfExists(textFile);
		Files.deleteIfExists(dir);
	}

	private RequestModel multipart(String url) {
		RequestModel r = new RequestModel();
		r.setMethod(HttpMethod.POST);
		r.setUrl(url);
		r.setBodyType(BodyType.MULTIPART);
		r.getFormFields().add(FormField.text("title", "Résumé"));
		r.getFormFields().add(FormField.file("doc", textFile.toString()));
		r.getFormFields().add(new FormField("off", "x", false, false));
		return r;
	}

	@Test
	void sendsRfc7578MultipartWithFileContent() throws Exception {
		try (TestServer server = new TestServer(OK)) {
			RequestModel r = multipart(server.url("/upload"));
			r.getHeaders().add(new KeyValue("Content-Type", "multipart/form-data")); // no boundary: replaced

			ResponseModel response = http.send(r).get(10, TimeUnit.SECONDS);
			assertFalse(response.isError(), response.getError());

			TestServer.Received got = server.received.get(5, TimeUnit.SECONDS);
			String contentType = got.headers().get("content-type");
			assertTrue(contentType.startsWith("multipart/form-data; boundary=----GetManBoundary"), contentType);
			String boundary = contentType.substring(contentType.indexOf("boundary=") + 9);
			String expected = "--" + boundary + "\r\n"
					+ "Content-Disposition: form-data; name=\"title\"\r\n\r\nRésumé\r\n"
					+ "--" + boundary + "\r\n"
					+ "Content-Disposition: form-data; name=\"doc\"; filename=\"note one.txt\"\r\n";
			assertTrue(got.body().startsWith(expected), got.body());
			assertTrue(got.body().contains("\r\n\r\nhello file\nline 2\r\n--" + boundary + "--\r\n"), got.body());
			assertFalse(got.body().contains("name=\"off\""));
		}
	}

	@Test
	void headerValuesAreEscaped() {
		assertEquals("a%22b%0D%0Ac", BodyEncoder.quoted("a\"b" + (char) 13 + (char) 10 + "c"));
	}

	@Test
	void sendsBinaryFileAsIs() throws Exception {
		try (TestServer server = new TestServer(OK)) {
			RequestModel r = new RequestModel();
			r.setMethod(HttpMethod.PUT);
			r.setUrl(server.url("/raw"));
			r.setBodyType(BodyType.BINARY);
			r.setBinaryFile(textFile.toString());

			http.send(r).get(10, TimeUnit.SECONDS);

			TestServer.Received got = server.received.get(5, TimeUnit.SECONDS);
			assertEquals("application/octet-stream", got.headers().get("content-type"));
			assertEquals("hello file\nline 2", got.body());
		}
	}

	@Test
	void explicitContentTypeWinsForBinary() throws Exception {
		try (TestServer server = new TestServer(OK)) {
			RequestModel r = new RequestModel();
			r.setMethod(HttpMethod.POST);
			r.setUrl(server.url("/raw"));
			r.setBodyType(BodyType.BINARY);
			r.setBinaryFile(textFile.toString());
			r.getHeaders().add(new KeyValue("Content-Type", "text/plain; charset=utf-8"));

			http.send(r).get(10, TimeUnit.SECONDS);

			assertEquals("text/plain; charset=utf-8", server.received.get(5, TimeUnit.SECONDS).headers().get("content-type"));
		}
	}

	@Test
	void missingFileIsAReadableErrorNotAnException() throws Exception {
		RequestModel r = multipart("http://127.0.0.1:9/x");
		r.getFormFields().get(1).setValue(dir.resolve("nope.bin").toString());
		ResponseModel response = http.send(r).get(10, TimeUnit.SECONDS);
		assertTrue(response.isError());
		assertTrue(response.getError().contains("\"doc\"") && response.getError().contains("not found"),
				response.getError());
	}

	@Test
	void hasBodyRules() {
		RequestModel r = new RequestModel();
		r.setBodyType(BodyType.MULTIPART);
		assertFalse(r.hasBody());
		r.getFormFields().add(FormField.file("f", " "));
		assertFalse(r.hasBody()); // a file field without a path is not sent
		r.getFormFields().add(FormField.text("t", ""));
		assertTrue(r.hasBody()); // an empty text value is still a field
		r.setBodyType(BodyType.BINARY);
		assertFalse(r.hasBody());
		r.setBinaryFile("/x");
		assertTrue(r.hasBody());
	}

	@Test
	void curlRoundTripsMultipartAndBinary() {
		RequestModel m = multipart("https://api.test/up");
		m.getFormFields().add(FormField.text("weird", "@not-a-file;x"));
		String curl = CurlExporter.toCurl(m);
		assertTrue(curl.contains("--form-string 'title=Résumé'"), curl);
		assertTrue(curl.contains("-F 'doc=@" + textFile + "'") || curl.contains("doc=@"), curl);
		assertFalse(curl.contains("Content-Type"), curl);
		RequestModel back = CurlImporter.parse(curl).request();
		assertEquals(BodyType.MULTIPART, back.getBodyType());
		assertEquals(List.of(FormField.text("title", "Résumé"), FormField.file("doc", textFile.toString()),
				FormField.text("weird", "@not-a-file;x")), back.getFormFields());

		RequestModel b = new RequestModel();
		b.setMethod(HttpMethod.PUT);
		b.setUrl("https://api.test/raw");
		b.setBodyType(BodyType.BINARY);
		b.setBinaryFile("/tmp/photo.jpg");
		RequestModel binBack = CurlImporter.parse(CurlExporter.toCurl(b)).request();
		assertEquals(BodyType.BINARY, binBack.getBodyType());
		assertEquals("/tmp/photo.jpg", binBack.getBinaryFile());
		assertEquals(HttpMethod.PUT, binBack.getMethod());
	}

	@Test
	void curlImportOfFormOptionsAndUploads() {
		CurlImporter.Result res = CurlImporter.parse(
				"curl -F 'avatar=@me.png;type=image/png' -F name=Ada https://x.test/profile");
		assertEquals(HttpMethod.POST, res.request().getMethod());
		assertEquals(List.of(FormField.file("avatar", "me.png"), FormField.text("name", "Ada")),
				res.request().getFormFields());
		assertTrue(res.warnings().isEmpty(), res.warnings().toString());

		RequestModel put = CurlImporter.parse("curl -T backup.tar https://x.test/files/backup.tar").request();
		assertEquals(HttpMethod.PUT, put.getMethod());
		assertEquals(BodyType.BINARY, put.getBodyType());
		assertEquals("backup.tar", put.getBinaryFile());

		CurlImporter.Result d = CurlImporter.parse("curl -d @body.json https://x.test");
		assertEquals("body.json", d.request().getBinaryFile());
		assertEquals(1, d.warnings().size());
	}

	@Test
	void variablesResolveInFieldsAndPaths() {
		RequestModel r = multipart("{{base}}/up");
		r.getFormFields().add(FormField.file("{{field}}", "{{dir}}/a.txt"));
		r.setBinaryFile("{{dir}}/b.bin");
		Map<String, String> vars = Map.of("base", "http://x", "field", "attachment", "dir", "/data");
		RequestModel resolved = new VariableResolver(vars).resolve(r);
		assertEquals(FormField.file("attachment", "/data/a.txt"), resolved.getFormFields().get(3));
		assertEquals("/data/b.bin", resolved.getBinaryFile());
		assertEquals(java.util.Set.of(), VariableResolver.unresolvedIn(r, vars));
		assertEquals(java.util.Set.of("field", "dir"), VariableResolver.unresolvedIn(r, Map.of("base", "x")));
	}

	@Test
	void savedAndCompared() {
		RequestModel r = multipart("http://x");
		r.setBinaryFile("/kept/even/when/multipart");
		WorkspaceState state = new WorkspaceState();
		state.getOpenTabs().add(r);
		RequestModel back = StateCodec.fromJson(StateCodec.toJson(state)).getOpenTabs().get(0);
		assertTrue(r.sameContent(back));
		assertEquals(r.getFormFields(), back.getFormFields());
		back.getFormFields().get(0).setValue("changed");
		assertFalse(r.sameContent(back));
		assertEquals(r.getFormFields(), r.duplicate().getFormFields());
	}
}
