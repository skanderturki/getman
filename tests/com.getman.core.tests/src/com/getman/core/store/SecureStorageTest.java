package com.getman.core.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.getman.core.history.HistoryEntry;
import com.getman.core.history.HistoryStore;
import com.getman.core.model.AuthType;
import com.getman.core.model.Environment;
import com.getman.core.model.KeyValue;
import com.getman.core.model.RequestCollection;
import com.getman.core.model.RequestModel;
import com.getman.core.model.ResponseModel;

class SecureStorageTest {

	/** In-memory secret store; can be told to fail. */
	static final class MemoryStore implements SecretStore {
		final Map<String, String> values = new TreeMap<>();
		boolean fail;

		@Override
		public String get(String key) throws SecretStoreException {
			check();
			return values.get(key);
		}

		@Override
		public void put(String key, String value) throws SecretStoreException {
			check();
			values.put(key, value);
		}

		@Override
		public void retainOnly(Set<String> keep) throws SecretStoreException {
			check();
			values.keySet().retainAll(keep);
		}

		private void check() throws SecretStoreException {
			if (fail) {
				throw new SecretStoreException("master password prompt cancelled", null);
			}
		}
	}

	private Path dir;
	private MemoryStore memory;
	private StateStore store;

	@BeforeEach
	void setUp() throws IOException {
		dir = Files.createTempDirectory("getman-secure");
		memory = new MemoryStore();
		store = new StateStore(dir.resolve("state.json"), memory);
	}

	@AfterEach
	void tearDown() throws IOException {
		try (Stream<Path> files = Files.walk(dir)) {
			files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		}
	}

	private static RequestModel bearer(String token) {
		RequestModel r = new RequestModel();
		r.setUrl("https://api.test/me");
		r.getAuth().setType(AuthType.BEARER);
		r.getAuth().setToken(token);
		return r;
	}

	private static WorkspaceState sampleState() {
		WorkspaceState state = new WorkspaceState();
		RequestModel basic = new RequestModel();
		basic.getAuth().setType(AuthType.BASIC);
		basic.getAuth().setUsername("ada");
		basic.getAuth().setPassword("pa55word");
		state.getOpenTabs().add(basic);
		RequestCollection c = new RequestCollection("API");
		c.put(bearer("tok-collection"));
		c.getVariables().add(new KeyValue("apiKey", "cv-secret", true, true));
		c.getVariables().add(new KeyValue("base", "https://api.test"));
		state.getCollections().add(c);
		Environment env = new Environment("Prod");
		env.getVariables().add(new KeyValue("token", "env-secret", true, true));
		env.getVariables().add(new KeyValue("emptySecret", "", true, true));
		state.getEnvironments().add(env);
		return state;
	}

	private String json() throws IOException {
		return Files.readString(store.getFile());
	}

	@Test
	void secretsLeaveTheStateFileAndComeBack() throws IOException {
		WorkspaceState state = sampleState();
		assertTrue(store.save(state).isEmpty());

		String json = json();
		for (String secret : List.of("pa55word", "tok-collection", "cv-secret", "env-secret")) {
			assertFalse(json.contains(secret), secret + " leaked into state.json");
			assertTrue(memory.values.containsValue(secret), secret + " not in the secret store");
		}
		assertTrue(json.contains("\"username\": \"ada\"")); // usernames are not secret
		assertTrue(json.contains("https://api.test")); // nor are normal variables

		StateStore.LoadResult loaded = store.load();
		assertNull(loaded.warning());
		WorkspaceState back = loaded.state();
		assertEquals("pa55word", back.getOpenTabs().get(0).getAuth().getPassword());
		assertEquals("tok-collection", back.getCollections().get(0).allRequests().get(0).getAuth().getToken());
		assertEquals(state.getCollections().get(0).getVariables(), back.getCollections().get(0).getVariables());
		assertEquals(state.getEnvironments().get(0).getVariables(), back.getEnvironments().get(0).getVariables());
	}

	@Test
	void removedSecretsAreDeletedFromTheStore() throws IOException {
		WorkspaceState state = sampleState();
		store.save(state);
		int before = memory.values.size();

		state.getEnvironments().clear();
		state.getCollections().get(0).getVariables().get(0).setSecret(false); // no longer secret: goes inline
		store.save(state);

		assertEquals(before - 2, memory.values.size());
		assertFalse(memory.values.containsValue("env-secret"));
		assertTrue(json().contains("cv-secret"));
	}

	@Test
	void plainSecretsFromOlderVersionsMigrateOnNextSave() throws IOException {
		Files.writeString(store.getFile(), StateCodec.toJson(sampleState())); // 0.7-style: everything inline
		assertTrue(json().contains("env-secret"));

		WorkspaceState loaded = store.load().state();
		assertEquals("env-secret", loaded.getEnvironments().get(0).getVariables().get(0).getValue());
		store.save(loaded);

		assertFalse(json().contains("env-secret"));
		assertFalse(json().contains("pa55word"));
		assertEquals("env-secret", store.load().state().getEnvironments().get(0).getVariables().get(0).getValue());
	}

	@Test
	void failingStoreFallsBackToPlainTextWithAWarning() throws IOException {
		memory.fail = true;
		List<String> warnings = store.save(sampleState());

		assertEquals(1, warnings.size(), warnings.toString());
		assertTrue(warnings.get(0).contains("plain text"));
		assertTrue(json().contains("pa55word")); // nothing lost
		memory.fail = false;
		assertEquals("pa55word", store.load().state().getOpenTabs().get(0).getAuth().getPassword());
	}

	@Test
	void unreadableStoreLoadsEmptySecretsWithAWarning() throws IOException {
		store.save(sampleState());
		memory.fail = true;
		StateStore.LoadResult loaded = store.load();
		assertTrue(loaded.warning() != null && loaded.warning().contains("secure storage"));
		assertEquals("", loaded.state().getOpenTabs().get(0).getAuth().getPassword());
		assertEquals("ada", loaded.state().getOpenTabs().get(0).getAuth().getUsername());
	}

	@Test
	void historyUsesItsOwnStoreAndDropsSecretsOfTrimmedEntries() throws IOException {
		MemoryStore historySecrets = new MemoryStore();
		HistoryStore history = new HistoryStore(dir.resolve("history.json"), historySecrets);
		ResponseModel ok = ResponseModel.success(200, "HTTP/1.1", List.of(), "", 0, 1, "u");
		List<HistoryEntry> entries = new ArrayList<>();
		HistoryStore.add(entries, HistoryEntry.of(bearer("old-token"), "u1", ok, 1), 10);
		HistoryStore.add(entries, HistoryEntry.of(bearer("new-token"), "u2", ok, 2), 10);

		history.save(entries, 10);
		assertFalse(Files.readString(history.getFile()).contains("token\": \"new-token"));
		assertEquals("new-token", history.load().get(0).request().getAuth().getToken());

		history.save(entries, 1); // the old entry is dropped
		assertEquals(Set.of("new-token"), Set.copyOf(historySecrets.values.values()));
	}

	@Test
	void exportsLeaveOutSecretVariablesUnlessAsked() {
		RequestCollection c = sampleState().getCollections().get(0);
		assertTrue(CollectionFile.hasSecrets(c));
		String safe = CollectionFile.toJson(c);
		assertFalse(safe.contains("cv-secret"));
		assertTrue(safe.contains("\"secret\": true"));
		assertTrue(CollectionFile.toJson(c, true).contains("cv-secret"));
		// The export is self-contained: no references into this machine's secure storage.
		assertFalse(CollectionFile.toJson(c, true).contains("Ref\""));
	}

	@Test
	void secretFlagIsPartOfEqualityButNeverPrinted() {
		KeyValue v = new KeyValue("k", "hunter2", true, true);
		assertFalse(v.equals(new KeyValue("k", "hunter2")));
		assertFalse(v.toString().contains("hunter2"));
		assertTrue(v.copy().isSecret());
	}
}
