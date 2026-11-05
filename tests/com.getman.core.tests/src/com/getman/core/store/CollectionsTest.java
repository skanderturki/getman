package com.getman.core.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.getman.core.json.JsonException;
import com.getman.core.model.BodyType;
import com.getman.core.model.HttpMethod;
import com.getman.core.model.KeyValue;
import com.getman.core.model.RequestCollection;
import com.getman.core.model.RequestModel;

class CollectionsTest {

	private static RequestModel sample() {
		RequestModel r = new RequestModel();
		r.setName("Create user");
		r.setMethod(HttpMethod.POST);
		r.setUrl("https://api.example.com/users");
		r.getHeaders().add(new KeyValue("Accept", "application/json"));
		r.setBodyType(BodyType.JSON);
		r.setBody("{}");
		return r;
	}

	@Test
	void putAddsThenReplacesBySameId() {
		RequestCollection c = new RequestCollection("API");
		RequestModel tab = sample();

		RequestModel saved = c.put(tab);
		assertNotSame(tab, saved);
		assertEquals(c.getId(), saved.getCollectionId());
		assertEquals(1, c.allRequests().size());

		tab.setUrl("https://api.example.com/v2/users");
		c.put(tab);
		assertEquals(1, c.allRequests().size());
		assertEquals("https://api.example.com/v2/users", c.findRequest(tab.getId()).getUrl());

		assertTrue(c.removeRequest(tab.getId()));
		assertTrue(c.allRequests().isEmpty());
	}

	@Test
	void savedCopyIsIndependentOfTheTab() {
		RequestCollection c = new RequestCollection("API");
		RequestModel tab = sample();
		RequestModel saved = c.put(tab);

		assertTrue(tab.sameContent(saved));
		tab.getHeaders().get(0).setValue("text/plain");
		assertFalse(tab.sameContent(saved));
		assertEquals("application/json", saved.getHeaders().get(0).getValue());
	}

	@Test
	void duplicateDropsCollectionLink() {
		RequestModel r = sample();
		r.setCollectionId("c1");
		RequestModel dup = r.duplicate();
		assertFalse(dup.isInCollection());
		assertNotEquals(r.getId(), dup.getId());
		RequestModel copy = r.copy();
		assertEquals("c1", copy.getCollectionId());
		assertEquals(r.getId(), copy.getId());
	}

	@Test
	void stateRoundTripsCollectionsAndTabLinks() {
		WorkspaceState state = new WorkspaceState();
		RequestCollection c = new RequestCollection("Users API");
		RequestModel saved = c.put(sample());
		state.getCollections().add(c);
		state.getOpenTabs().add(saved.copy());

		WorkspaceState loaded = StateCodec.fromJson(StateCodec.toJson(state));

		assertEquals(1, loaded.getCollections().size());
		RequestCollection lc = loaded.findCollection(c.getId());
		assertEquals("Users API", lc.getName());
		assertEquals(1, lc.allRequests().size());
		RequestModel lr = lc.allRequests().get(0);
		assertTrue(lr.sameContent(saved));
		assertEquals(c.getId(), lr.getCollectionId());
		RequestModel tab = loaded.getOpenTabs().get(0);
		assertEquals(c.getId(), tab.getCollectionId());
		assertEquals(saved.getId(), tab.getId());
	}

	@Test
	void oldStateFilesWithoutCollectionsStillLoad() {
		WorkspaceState state = StateCodec.fromJson("{\"openTabs\":[{\"url\":\"http://x\"}]}");
		assertTrue(state.getCollections().isEmpty());
		assertFalse(state.getOpenTabs().get(0).isInCollection());
	}

	@Test
	void exportImportGivesFreshIds() {
		RequestCollection c = new RequestCollection("Users API");
		c.put(sample());

		RequestCollection imported = CollectionFile.fromJson(CollectionFile.toJson(c));

		assertEquals("Users API", imported.getName());
		assertNotEquals(c.getId(), imported.getId());
		RequestModel r = imported.allRequests().get(0);
		assertTrue(r.sameContent(c.allRequests().get(0)));
		assertNotEquals(c.allRequests().get(0).getId(), r.getId());
		assertEquals(imported.getId(), r.getCollectionId());
	}

	@Test
	void importRejectsOtherJson() {
		assertThrows(JsonException.class, () -> CollectionFile.fromJson("{\"openTabs\":[]}"));
	}
}
