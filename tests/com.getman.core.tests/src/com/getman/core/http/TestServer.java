package com.getman.core.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * One-shot HTTP/1.1 server on a loopback port, for tests. Answers the first request with a canned response and
 * records what it received. Uses plain sockets so the test fragment needs no extra package imports.
 */
final class TestServer implements AutoCloseable {
	final ServerSocket socket;
	final CompletableFuture<Received> received = new CompletableFuture<>();

	record Received(String requestLine, Map<String, String> headers, String body) {
	}

	TestServer(String rawResponse) throws IOException {
		socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
		Thread thread = new Thread(() -> serve(rawResponse), "test-http-server");
		thread.setDaemon(true);
		thread.start();
	}

	String url(String path) {
		return "http://127.0.0.1:" + socket.getLocalPort() + path;
	}

	private void serve(String rawResponse) {
		try (Socket client = socket.accept()) {
			InputStream in = client.getInputStream();
			String head = readHead(in);
			String[] lines = head.split("\r\n");
			Map<String, String> headers = new LinkedHashMap<>();
			for (int i = 1; i < lines.length; i++) {
				int colon = lines[i].indexOf(':');
				if (colon > 0) {
					headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT),
							lines[i].substring(colon + 1).trim());
				}
			}
			int length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
			byte[] body = in.readNBytes(length);
			received.complete(new Received(lines[0], headers, new String(body, StandardCharsets.UTF_8)));
			OutputStream out = client.getOutputStream();
			out.write(rawResponse.getBytes(StandardCharsets.UTF_8));
			out.flush();
		} catch (IOException e) {
			received.completeExceptionally(e);
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

	@Override
	public void close() throws IOException {
		socket.close();
	}
}
