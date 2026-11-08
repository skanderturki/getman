package com.getman.mcp;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.getman.core.history.HistoryEntry;
import com.getman.core.http.HttpService;
import com.getman.core.model.CollectionNode;
import com.getman.core.model.Environment;
import com.getman.core.model.RequestCollection;
import com.getman.core.run.RunRecord;
import com.sun.net.httpserver.HttpServer;

/** In-memory stand-ins for Eclipse: a workspace, a host that answers approvals as told, and an echo server. */
final class Fakes {
	private Fakes() {
	}

	static final class Workspace implements com.getman.ui.api.GetmanWorkspace {
		final List<RequestCollection> collections = new ArrayList<>();
		final List<Environment> environments = new ArrayList<>();
		final List<HistoryEntry> history = new ArrayList<>();
		final List<RunRecord> runs = new ArrayList<>();
		final HttpService http = new HttpService();
		Environment active;
		int changes;

		@Override
		public List<RequestCollection> collections() {
			return collections;
		}

		@Override
		public List<Environment> environments() {
			return environments;
		}

		@Override
		public Environment activeEnvironment() {
			return active;
		}

		int environmentChanges;

		@Override
		public void environmentsChanged() {
			environmentChanges++;
		}

		@Override
		public void selectEnvironment(Environment environment) {
			active = environment;
		}

		@Override
		public HttpService http() {
			return http;
		}

		@Override
		public void collectionsChanged(RequestCollection collection, CollectionNode reveal) {
			changes++;
		}

		@Override
		public void requestsDeleted(String collectionId, Set<String> requestIds) {
			// no tabs here
		}

		@Override
		public void recordHistory(HistoryEntry entry) {
			history.add(0, entry);
		}

		@Override
		public void recordRun(RunRecord run) {
			runs.add(0, run);
		}

		@Override
		public void showStatus(String message) {
			// no status line here
		}
	}

	static final class Host implements ToolHost {
		final Workspace workspace = new Workspace();
		final List<String> asked = new ArrayList<>();
		boolean allow;

		@Override
		public <T> T onWorkspace(WorkspaceAction<T> action) throws ToolException {
			return action.run(workspace);
		}

		@Override
		public boolean approve(String message) {
			asked.add(message);
			return allow;
		}
	}

	/**
	 * Answers every request with "METHOD /path" and the Authorization header it received, as JSON; with status NNN
	 * for paths ending in /status/NNN, 200 otherwise.
	 */
	static final class EchoServer implements AutoCloseable {
		final HttpServer server;
		final AtomicInteger requests = new AtomicInteger();

		EchoServer() throws IOException {
			server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
			server.createContext("/", exchange -> {
				requests.incrementAndGet();
				String auth = String.valueOf(exchange.getRequestHeaders().getFirst("Authorization"));
				String body = "{\"saw\":\"" + exchange.getRequestMethod() + " " + exchange.getRequestURI()
						+ "\",\"authorization\":\"" + auth + "\"}";
				byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
				String path = exchange.getRequestURI().getPath();
				int status = path.matches(".*/status/[0-9]{3}") ? Integer.parseInt(path.substring(path.length() - 3)) : 200;
				exchange.getResponseHeaders().set("Content-Type", "application/json");
				exchange.sendResponseHeaders(status, bytes.length);
				try (OutputStream out = exchange.getResponseBody()) {
					out.write(bytes);
				}
			});
			server.start();
		}

		String baseUrl() {
			return "http://127.0.0.1:" + server.getAddress().getPort();
		}

		@Override
		public void close() {
			server.stop(0);
		}
	}
}
