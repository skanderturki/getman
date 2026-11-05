package com.getman.core.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.getman.core.model.Folder;
import com.getman.core.model.KeyValue;
import com.getman.core.model.RequestCollection;
import com.getman.core.model.RequestModel;

class FoldersTest {

	private static RequestModel req(String name) {
		RequestModel r = new RequestModel();
		r.setName(name);
		r.setUrl("https://x.test/" + name);
		return r;
	}

	/** API ─┬─ list (top level) ─ Users ─┬─ create ─ Admin ─ ban */
	private static RequestCollection tree() {
		RequestCollection c = new RequestCollection("API");
		RequestModel list = req("list");
		list.setCollectionId(c.getId());
		Folder users = new Folder("Users");
		RequestModel create = req("create");
		create.setCollectionId(c.getId());
		Folder admin = new Folder("Admin");
		RequestModel ban = req("ban");
		ban.setCollectionId(c.getId());
		admin.getItems().add(ban);
		users.getItems().add(create);
		users.getItems().add(admin);
		c.getItems().add(list);
		c.getItems().add(users);
		return c;
	}

	@Test
	void allRequestsIsDepthFirstInDisplayOrder() {
		assertEquals(List.of("list", "create", "ban"), tree().allRequests().stream().map(RequestModel::getName).toList());
	}

	@Test
	void findParentAndRemoveWorkAtAnyDepth() {
		RequestCollection c = tree();
		RequestModel ban = c.allRequests().get(2);
		Folder admin = (Folder) c.parentOf(ban);
		assertEquals("Admin", admin.getName());
		assertEquals("Users", ((Folder) c.parentOf(admin)).getName());
		assertSame(c, c.parentOf(c.getItems().get(0)));
		assertSame(ban, c.findRequest(ban.getId()));

		assertTrue(c.removeRequest(ban.getId()));
		assertTrue(admin.getItems().isEmpty());
		assertNull(c.findRequest(ban.getId()));
		assertFalse(c.remove(ban));
	}

	@Test
	void putReplacesInsideItsFolder() {
		RequestCollection c = tree();
		RequestModel tab = c.allRequests().get(1).copy(); // "create", lives in Users
		tab.setUrl("https://x.test/v2/create");
		c.put(tab);
		Folder users = (Folder) c.getItems().get(1);
		assertEquals("https://x.test/v2/create", ((RequestModel) users.getItems().get(0)).getUrl());
		assertEquals(3, c.allRequests().size());
	}

	@Test
	void stateRoundTripsTreeAndVariables() {
		RequestCollection c = tree();
		c.getVariables().add(new KeyValue("base", "https://x.test"));
		WorkspaceState state = new WorkspaceState();
		state.getCollections().add(c);

		RequestCollection back = StateCodec.fromJson(StateCodec.toJson(state)).getCollections().get(0);

		assertEquals(List.of("list", "create", "ban"), back.allRequests().stream().map(RequestModel::getName).toList());
		Folder users = assertInstanceOf(Folder.class, back.getItems().get(1));
		assertEquals(((Folder) c.getItems().get(1)).getId(), users.getId());
		assertInstanceOf(Folder.class, users.getItems().get(1));
		assertEquals(c.getVariables(), back.getVariables());
		back.allRequests().forEach(r -> assertEquals(c.getId(), r.getCollectionId()));
	}

	@Test
	void stateKeepsFlatRequestsForOlderVersions() {
		WorkspaceState state = new WorkspaceState();
		state.getCollections().add(tree());
		String json = StateCodec.toJson(state);
		assertTrue(json.contains("\"requests\""));
		assertTrue(json.contains("\"formatVersion\": 2"));
	}

	@Test
	void formatVersion1CollectionsLoadFlat() {
		WorkspaceState s = StateCodec.fromJson("{\"formatVersion\":1,\"collections\":[{\"id\":\"c1\",\"name\":\"Old\","
				+ "\"requests\":[{\"id\":\"r1\",\"name\":\"a\",\"url\":\"http://x\"},{\"id\":\"r2\",\"name\":\"b\"}]}]}");
		RequestCollection c = s.getCollections().get(0);
		assertEquals(2, c.getItems().size());
		assertEquals("c1", c.allRequests().get(1).getCollectionId());
	}

	@Test
	void exportImportKeepsTreeWithFreshIds() {
		RequestCollection c = tree();
		RequestCollection imported = CollectionFile.fromJson(CollectionFile.toJson(c));
		Folder users = assertInstanceOf(Folder.class, imported.getItems().get(1));
		assertNotEquals(((Folder) c.getItems().get(1)).getId(), users.getId());
		assertEquals(3, imported.allRequests().size());
		imported.allRequests().forEach(r -> assertEquals(imported.getId(), r.getCollectionId()));
		assertFalse(CollectionFile.toJson(c).contains("\"requests\""));
	}

	@Test
	void version1ExportFilesStillImport() {
		RequestCollection c = CollectionFile.fromJson("{\"getmanCollection\":1,\"name\":\"Old\","
				+ "\"requests\":[{\"name\":\"a\",\"url\":\"http://x\"}]}");
		assertEquals(1, c.allRequests().size());
	}
}
