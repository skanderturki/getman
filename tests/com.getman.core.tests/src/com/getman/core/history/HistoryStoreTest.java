package com.getman.core.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.getman.core.model.AuthType;
import com.getman.core.model.HttpMethod;
import com.getman.core.model.RequestModel;
import com.getman.core.model.ResponseModel;

class HistoryStoreTest {
	private Path dir;
	private HistoryStore store;

	@BeforeEach
	void setUp() throws IOException {
		dir = Files.createTempDirectory("getman-history");
		store = new HistoryStore(dir.resolve("sub").resolve("history.json"));
	}

	@AfterEach
	void tearDown() throws IOException {
		try (Stream<Path> files = Files.walk(dir)) {
			files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		}
	}

	private static RequestModel request(String path) {
		RequestModel r = new RequestModel();
		r.setName("GET " + path);
		r.setUrl("{{base}}" + path);
		r.setCollectionId("c1");
		r.getAuth().setType(AuthType.BEARER);
		r.getAuth().setToken("{{token}}");
		return r;
	}

	private static ResponseModel ok() {
		return ResponseModel.success(200, "HTTP/1.1", List.of(Map.entry("a", "b")), "{}", 2, 15, "http://x");
	}

	@Test
	void entryKeepsPlaceholdersAndDropsCollectionLink() {
		HistoryEntry e = HistoryEntry.of(request("/a"), "http://localhost/a", ok(), 1000);
		assertEquals("{{base}}/a", e.request().getUrl());
		assertEquals("{{token}}", e.request().getAuth().getToken());
		assertFalse(e.request().isInCollection());
		assertEquals("http://localhost/a", e.resolvedUrl());
		assertEquals(200, e.statusCode());
		assertEquals("200 OK", e.statusLine());
		assertEquals(2, e.sizeBytes());
	}

	@Test
	void errorsAreRecorded() {
		HistoryEntry e = HistoryEntry.of(request("/b"), "http://localhost/b", ResponseModel.failure("refused", 7), 1);
		assertTrue(e.isError());
		assertEquals("refused", e.error());
		assertEquals(0, e.statusCode());
	}

	@Test
	void addKeepsNewestFirstAndTrims() {
		List<HistoryEntry> entries = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			HistoryStore.add(entries, HistoryEntry.of(request("/" + i), "u" + i, ok(), i), 3);
		}
		assertEquals(List.of("u4", "u3", "u2"), entries.stream().map(HistoryEntry::resolvedUrl).toList());
	}

	@Test
	void roundTripsAndSavesAtMostMax() throws IOException {
		List<HistoryEntry> entries = new ArrayList<>();
		HistoryStore.add(entries, HistoryEntry.of(request("/old"), "u-old", ResponseModel.failure("x", 1), 10), 100);
		RequestModel post = request("/new");
		post.setMethod(HttpMethod.POST);
		HistoryStore.add(entries, HistoryEntry.of(post, "u-new", ok(), 20), 100);

		store.save(entries, 100);
		List<HistoryEntry> loaded = store.load();

		assertEquals(2, loaded.size());
		HistoryEntry first = loaded.get(0);
		assertEquals(entries.get(0).id(), first.id());
		assertEquals(20, first.sentAtMillis());
		assertEquals(HttpMethod.POST, first.request().getMethod());
		assertEquals("{{base}}/new", first.request().getUrl());
		assertEquals(15, first.durationMs());
		assertTrue(loaded.get(1).isError());

		store.save(entries, 1);
		assertEquals(1, store.load().size());
		assertFalse(Files.exists(store.getFile().resolveSibling("history.json.tmp")));
	}

	@Test
	void missingOrDamagedFileGivesEmptyHistory() throws IOException {
		assertTrue(store.load().isEmpty());
		Files.createDirectories(store.getFile().getParent());
		Files.writeString(store.getFile(), "{ broken");
		assertTrue(store.load().isEmpty());
	}
}
