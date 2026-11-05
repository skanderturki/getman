package com.getman.core.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.getman.core.model.BodyType;
import com.getman.core.model.HttpMethod;
import com.getman.core.model.KeyValue;
import com.getman.core.model.RequestModel;

class StateStoreTest {
	private Path dir;
	private StateStore store;

	@BeforeEach
	void setUp() throws IOException {
		dir = Files.createTempDirectory("getman-test");
		store = new StateStore(dir.resolve("sub").resolve("state.json"));
	}

	@AfterEach
	void tearDown() throws IOException {
		try (Stream<Path> files = Files.walk(dir)) {
			files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		}
	}

	@Test
	void missingFileGivesEmptyState() {
		StateStore.LoadResult result = store.load();
		assertTrue(result.state().getOpenTabs().isEmpty());
		assertNull(result.warning());
	}

	@Test
	void roundTripsEveryField() throws IOException {
		RequestModel r = new RequestModel();
		r.setName("Create user");
		r.setMethod(HttpMethod.POST);
		r.setUrl("https://api.example.com/users");
		r.getParams().add(new KeyValue("dry", "true", false));
		r.getHeaders().add(new KeyValue("Authorization", "Bearer x\"y"));
		r.setBodyType(BodyType.JSON);
		r.setBody("{\n  \"name\": \"Ada\"\n}");
		WorkspaceState state = new WorkspaceState();
		state.getOpenTabs().add(new RequestModel());
		state.getOpenTabs().add(r);
		state.setSelectedIndex(1);

		store.save(state);
		StateStore.LoadResult result = store.load();

		assertNull(result.warning());
		assertEquals(2, result.state().getOpenTabs().size());
		assertEquals(1, result.state().getSelectedIndex());
		RequestModel loaded = result.state().getOpenTabs().get(1);
		assertEquals(r.getId(), loaded.getId());
		assertEquals("Create user", loaded.getName());
		assertEquals(HttpMethod.POST, loaded.getMethod());
		assertEquals(r.getUrl(), loaded.getUrl());
		assertEquals(r.getParams(), loaded.getParams());
		assertEquals(r.getHeaders(), loaded.getHeaders());
		assertEquals(BodyType.JSON, loaded.getBodyType());
		assertEquals(r.getBody(), loaded.getBody());
		assertFalse(Files.exists(store.getFile().resolveSibling("state.json.tmp")));
	}

	@Test
	void corruptFileIsKeptAsBackupAndReported() throws IOException {
		Files.createDirectories(store.getFile().getParent());
		Files.writeString(store.getFile(), "{ not json");

		StateStore.LoadResult result = store.load();

		assertTrue(result.state().getOpenTabs().isEmpty());
		assertNotNull(result.warning());
		assertFalse(Files.exists(store.getFile()));
		try (Stream<Path> files = Files.list(store.getFile().getParent())) {
			assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("state.json.corrupt-")));
		}
	}

	@Test
	void toleratesMissingAndUnknownFields() {
		WorkspaceState state = StateCodec.fromJson(
				"{\"futureField\":1,\"selectedIndex\":9,\"openTabs\":[{\"url\":\"http://x\",\"method\":\"BREW\"}]}");
		assertEquals(1, state.getOpenTabs().size());
		assertEquals(0, state.getSelectedIndex());
		RequestModel r = state.getOpenTabs().get(0);
		assertEquals("http://x", r.getUrl());
		assertEquals(HttpMethod.GET, r.getMethod());
		assertEquals(BodyType.NONE, r.getBodyType());
		assertFalse(r.getId().isBlank());
	}
}
