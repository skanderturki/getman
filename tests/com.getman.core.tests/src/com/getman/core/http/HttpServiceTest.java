package com.getman.core.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.getman.core.model.BodyType;
import com.getman.core.model.HttpMethod;
import com.getman.core.model.KeyValue;
import com.getman.core.model.RequestModel;
import com.getman.core.model.ResponseModel;

class HttpServiceTest {
	private final HttpService http = new HttpService();

	@Test
	void capturesStatusHeadersBodyAndSize() throws Exception {
		String body = "{\"ok\":true}";
		try (TestServer server = new TestServer("HTTP/1.1 201 Created\r\n"
				+ "Content-Type: application/json; charset=utf-8\r\n"
				+ "X-Trace: abc\r\n"
				+ "Content-Length: " + body.length() + "\r\n"
				+ "Connection: close\r\n\r\n" + body)) {
			RequestModel request = new RequestModel();
			request.setUrl(server.url("/items"));
			request.getParams().add(new KeyValue("page", "2"));
			request.getHeaders().add(new KeyValue("Accept", "application/json"));

			ResponseModel response = http.send(request).get(10, TimeUnit.SECONDS);

			assertFalse(response.isError(), response.getError());
			assertEquals(201, response.getStatusCode());
			assertEquals("201 Created", response.getStatusLine());
			assertEquals(body, response.getBody());
			assertEquals(body.length(), response.getSizeBytes());
			assertEquals("abc", response.getHeader("x-trace"));
			assertEquals("HTTP/1.1", response.getHttpVersion());
			assertTrue(response.getDurationMs() >= 0);

			TestServer.Received received = server.received.get(5, TimeUnit.SECONDS);
			assertEquals("GET /items?page=2 HTTP/1.1", received.requestLine());
			assertEquals("application/json", received.headers().get("accept"));
		}
	}

	@Test
	void sendsBodyWithDefaultContentType() throws Exception {
		try (TestServer server = new TestServer(
				"HTTP/1.1 204 No Content\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")) {
			RequestModel request = new RequestModel();
			request.setMethod(HttpMethod.POST);
			request.setUrl(server.url("/items"));
			request.setBodyType(BodyType.JSON);
			request.setBody("{\"name\":\"é\"}");

			ResponseModel response = http.send(request).get(10, TimeUnit.SECONDS);

			assertEquals(204, response.getStatusCode());
			TestServer.Received received = server.received.get(5, TimeUnit.SECONDS);
			assertEquals("POST /items HTTP/1.1", received.requestLine());
			assertEquals("application/json", received.headers().get("content-type"));
			assertEquals("{\"name\":\"é\"}", received.body());
		}
	}

	@Test
	void explicitContentTypeWins() throws Exception {
		try (TestServer server = new TestServer("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")) {
			RequestModel request = new RequestModel();
			request.setMethod(HttpMethod.PUT);
			request.setUrl(server.url("/"));
			request.setBodyType(BodyType.JSON);
			request.setBody("{}");
			request.getHeaders().add(new KeyValue("content-type", "application/vnd.api+json"));

			http.send(request).get(10, TimeUnit.SECONDS);

			assertEquals("application/vnd.api+json",
					server.received.get(5, TimeUnit.SECONDS).headers().get("content-type"));
		}
	}

	@Test
	void reportsConnectionRefused() throws Exception {
		int freePort;
		try (ServerSocket s = new ServerSocket(0)) {
			freePort = s.getLocalPort();
		}
		RequestModel request = new RequestModel();
		request.setUrl("http://127.0.0.1:" + freePort + "/");

		ResponseModel response = http.send(request).get(15, TimeUnit.SECONDS);

		assertTrue(response.isError());
		assertTrue(response.getError().toLowerCase().contains("connect"), response.getError());
	}

	@Test
	void reportsInvalidInputWithoutThrowing() throws Exception {
		RequestModel noUrl = new RequestModel();
		assertTrue(http.send(noUrl).get(1, TimeUnit.SECONDS).isError());

		RequestModel restricted = new RequestModel();
		restricted.setUrl("http://127.0.0.1:1/");
		restricted.getHeaders().add(new KeyValue("Host", "example.com"));
		ResponseModel response = http.send(restricted).get(1, TimeUnit.SECONDS);
		assertTrue(response.isError());
		assertTrue(response.getError().contains("Host"), response.getError());
	}

	@Test
	void parsesCharsetFromContentType() {
		assertEquals("ISO-8859-1", HttpService.charsetOf("text/plain; charset=ISO-8859-1").name());
		assertEquals("UTF-8", HttpService.charsetOf("text/plain").name());
		assertEquals("UTF-8", HttpService.charsetOf("text/plain; charset=bogus-x").name());
		assertEquals("UTF-8", HttpService.charsetOf(null).name());
	}
}
