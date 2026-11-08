package com.getman.core.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.getman.core.http.HttpService;
import com.getman.core.model.RequestModel;
import com.getman.core.run.CollectionRunner.Outcome;
import com.getman.core.run.CollectionRunner.Result;

class CollectionRunnerTest {
	/** Answers "GET /status/NNN" with status NNN, any number of times. */
	private ServerSocket server;
	private final AtomicInteger received = new AtomicInteger();

	@BeforeEach
	void setUp() throws IOException {
		server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
		Thread t = new Thread(this::serve, "runner-test-server");
		t.setDaemon(true);
		t.start();
	}

	@AfterEach
	void tearDown() throws IOException {
		server.close();
	}

	private void serve() {
		while (!server.isClosed()) {
			try (Socket client = server.accept()) {
				String head = readHead(client.getInputStream());
				received.incrementAndGet();
				String path = head.split(" ")[1];
				String status = path.startsWith("/status/") ? path.substring(8) : "200";
				String body = status.equals("204") ? "" : "ok";
				String response = "HTTP/1.1 " + status + " X\r\nContent-Length: " + body.length()
						+ "\r\nConnection: close\r\n\r\n" + body;
				OutputStream out = client.getOutputStream();
				out.write(response.getBytes(StandardCharsets.ISO_8859_1));
				out.flush();
			} catch (IOException e) {
				// closed
			}
		}
	}

	private static String readHead(InputStream in) throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		int b;
		while ((b = in.read()) != -1) {
			buf.write(b);
			if (buf.toString(StandardCharsets.ISO_8859_1).endsWith("\r\n\r\n")) {
				break;
			}
		}
		return buf.toString(StandardCharsets.ISO_8859_1);
	}

	private static RequestModel request(String path, String expected) {
		RequestModel r = new RequestModel();
		r.setName(path);
		r.setUrl("{{baseUrl}}" + path);
		r.setExpectedStatus(expected);
		return r;
	}

	private Map<String, String> values() {
		return Map.of("baseUrl", "http://127.0.0.1:" + server.getLocalPort());
	}

	@Test
	void checksEachStatusAgainstTheExpectedOne() {
		List<Result> seen = new ArrayList<>();
		List<Result> results = new CollectionRunner(new HttpService()).run(List.of(
				request("/status/201", "201"),
				request("/status/204", "2xx"),
				request("/status/404", "200"),
				request("/status/200", ""),
				request("/x/{{orderId}}", "200")), values(), seen::add);

		assertEquals(results, seen, "every result is reported as it comes");
		assertEquals(List.of(Outcome.PASSED, Outcome.PASSED, Outcome.FAILED, Outcome.NOT_CHECKED, Outcome.ERROR),
				results.stream().map(Result::outcome).toList());
		assertEquals("Expected 200, got 404.", results.get(2).message());
		assertTrue(results.get(4).message().contains("orderId"));
		assertNull(results.get(4).response(), "a request with unknown variables is not sent");
		assertEquals(4, received.get());
		assertEquals("2 passed, 1 failed, 1 not checked, 1 error", CollectionRunner.Summary.of(results).toString());
	}

	@Test
	void aStoppedRunSendsNothingMore() {
		CollectionRunner runner = new CollectionRunner(new HttpService());
		List<Result> results = runner.run(List.of(request("/status/200", "200"), request("/status/200", "200"),
				request("/status/200", "200")), values(), r -> runner.cancel());
		assertEquals(List.of(Outcome.PASSED, Outcome.STOPPED, Outcome.STOPPED),
				results.stream().map(Result::outcome).toList());
		assertEquals(1, received.get());
	}
}
