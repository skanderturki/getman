package com.getman.core.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.getman.core.model.KeyValue;
import com.getman.core.run.CollectionRunner.Outcome;

class RunStoreTest {

	static RunRecord.Item item(String name, Outcome outcome, int status, String message) {
		return new RunRecord.Item("id-" + name, "POST", name, "http://localhost:8080/api/" + name, "201", outcome,
				status, status == 0 ? "" : status + " Status", 12, message);
	}

	static RunRecord run(String collectionId, long at) {
		return new RunRecord(null, at, collectionId, "orders-api", "f1", "Customers", "Local", false,
				List.of(item("create", Outcome.PASSED, 201, ""), item("bad", Outcome.FAILED, 500, "Expected 201, got 500."),
						item("down", Outcome.ERROR, 0, "Connection refused"), item("later", Outcome.STOPPED, 0, "")));
	}

	@Test
	void roundTrip() throws Exception {
		Path dir = Files.createTempDirectory("getman-runs");
		RunStore store = new RunStore(dir.resolve("runs.json"));
		RunRecord ai = new RunRecord("r2", 2000, "c1", "orders-api", "", "", "", true, List.of());
		List<RunRecord> runs = List.of(ai, run("c1", 1000));
		store.save(runs);
		List<RunRecord> loaded = store.load();
		assertEquals(runs, loaded);
		assertEquals("orders-api", loaded.get(0).title());
		assertEquals("orders-api › Customers", loaded.get(1).title());
		assertTrue(loaded.get(0).viaAssistant());
	}

	@Test
	void damagedOrMissingFileIsEmpty() throws Exception {
		Path dir = Files.createTempDirectory("getman-runs");
		RunStore store = new RunStore(dir.resolve("runs.json"));
		assertTrue(store.load().isEmpty());
		Files.writeString(store.getFile(), "{not json");
		assertTrue(store.load().isEmpty());
	}

	@Test
	void keepsTwentyPerCollectionAndHundredInAll() {
		List<RunRecord> runs = new ArrayList<>();
		for (int i = 0; i < 25; i++) {
			RunStore.add(runs, run("a", i));
		}
		assertEquals(RunStore.MAX_PER_COLLECTION, runs.size());
		assertEquals(24, runs.get(0).startedAtMillis(), "newest first");
		assertEquals(5, runs.get(runs.size() - 1).startedAtMillis(), "oldest dropped");
		for (int c = 0; c < 10; c++) {
			for (int i = 0; i < 15; i++) {
				RunStore.add(runs, run("c" + c, 100 + i));
			}
		}
		assertEquals(RunStore.MAX_TOTAL, runs.size());
		assertFalse(runs.stream().anyMatch(r -> r.collectionId().equals("a")), "the oldest runs go first");
	}

	@Test
	void summaryAndProblems() {
		RunRecord r = run("c", 0);
		assertEquals("1 passed, 1 failed, 1 error, 1 not sent", r.summary().toString());
		assertEquals(4, r.summary().total());
		assertTrue(r.hasProblems());
		assertFalse(new RunRecord(null, 0, "c", "x", "", "", "", false, List.of(item("ok", Outcome.PASSED, 201, "")))
				.hasProblems());
	}

	@Test
	void maskingHidesSecretValuesOnly() {
		var mask = RunRecord.masking(List.of(new KeyValue("token", "s3cr3t-value", true, true),
				new KeyValue("baseUrl", "http://localhost:8080", true, false), new KeyValue("pin", "12", true, true)));
		assertEquals("http://localhost:8080/a?key=[secret]&pin=12",
				mask.apply("http://localhost:8080/a?key=s3cr3t-value&pin=12"));
	}
}
