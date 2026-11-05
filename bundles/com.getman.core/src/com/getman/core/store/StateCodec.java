package com.getman.core.store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.getman.core.json.Json;
import com.getman.core.json.JsonException;
import com.getman.core.model.Auth;
import com.getman.core.model.AuthType;
import com.getman.core.model.BodyType;
import com.getman.core.model.CollectionNode;
import com.getman.core.model.Folder;
import com.getman.core.model.Environment;
import com.getman.core.model.HttpMethod;
import com.getman.core.model.KeyValue;
import com.getman.core.model.RequestCollection;
import com.getman.core.model.RequestModel;

/** Converts {@link WorkspaceState} to and from JSON. Unknown fields are ignored so older versions can read newer files. */
public final class StateCodec {
	/** 2: collections are trees ("items") and have variables. Version-1 files (flat "requests") still load. */
	static final int FORMAT_VERSION = 2;

	private StateCodec() {
	}

	public static String toJson(WorkspaceState state) {
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("formatVersion", FORMAT_VERSION);
		root.put("selectedIndex", state.getSelectedIndex());
		List<Object> tabs = new ArrayList<>();
		for (RequestModel r : state.getOpenTabs()) {
			tabs.add(requestToMap(r));
		}
		root.put("openTabs", tabs);
		List<Object> collections = new ArrayList<>();
		for (RequestCollection c : state.getCollections()) {
			collections.add(collectionToMap(c));
		}
		root.put("collections", collections);
		List<Object> environments = new ArrayList<>();
		for (Environment e : state.getEnvironments()) {
			Map<String, Object> em = new LinkedHashMap<>();
			em.put("id", e.getId());
			em.put("name", e.getName());
			em.put("variables", kvToList(e.getVariables()));
			environments.add(em);
		}
		root.put("environments", environments);
		root.put("activeEnvironmentId", state.getActiveEnvironmentId());
		return Json.writePretty(root);
	}

	public static WorkspaceState fromJson(String json) {
		Object parsed = Json.parse(json);
		if (!(parsed instanceof Map<?, ?> root)) {
			throw new JsonException("State file is not a JSON object");
		}
		WorkspaceState state = new WorkspaceState();
		for (Object tab : list(root.get("openTabs"))) {
			if (tab instanceof Map<?, ?> m) {
				state.getOpenTabs().add(requestFromMap(m));
			}
		}
		for (Object c : list(root.get("collections"))) {
			if (c instanceof Map<?, ?> m) {
				state.getCollections().add(collectionFromMap(m));
			}
		}
		for (Object o : list(root.get("environments"))) {
			if (o instanceof Map<?, ?> em) {
				Environment e = new Environment();
				e.setId(string(em.get("id")));
				e.setName(string(em.get("name")));
				e.getVariables().addAll(kvFromList(em.get("variables")));
				state.getEnvironments().add(e);
			}
		}
		String active = string(root.get("activeEnvironmentId"));
		boolean activeExists = state.getEnvironments().stream().anyMatch(e -> e.getId().equals(active));
		state.setActiveEnvironmentId(activeExists ? active : "");
		int selected = (int) number(root.get("selectedIndex"));
		state.setSelectedIndex(Math.max(0, Math.min(selected, state.getOpenTabs().size() - 1)));
		return state;
	}

	public static Map<String, Object> collectionToMap(RequestCollection c) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("id", c.getId());
		m.put("name", c.getName());
		m.put("variables", kvToList(c.getVariables()));
		m.put("items", itemsToList(c.getItems()));
		// Flat copy for GetMan 0.4 and older, which only read "requests": downgrading keeps every request
		// (without folders) instead of showing an empty collection. Ignored when "items" is present.
		List<Object> flat = new ArrayList<>();
		for (RequestModel r : c.allRequests()) {
			flat.add(savedRequestToMap(r));
		}
		m.put("requests", flat);
		return m;
	}

	private static List<Object> itemsToList(List<CollectionNode> items) {
		List<Object> out = new ArrayList<>();
		for (CollectionNode node : items) {
			if (node instanceof Folder f) {
				Map<String, Object> fm = new LinkedHashMap<>();
				fm.put("type", "folder");
				fm.put("id", f.getId());
				fm.put("name", f.getName());
				fm.put("items", itemsToList(f.getItems()));
				out.add(fm);
			} else if (node instanceof RequestModel r) {
				out.add(savedRequestToMap(r));
			}
		}
		return out;
	}

	private static Map<String, Object> savedRequestToMap(RequestModel r) {
		Map<String, Object> rm = requestToMap(r);
		rm.remove("collectionId"); // implied by nesting
		return rm;
	}

	public static RequestCollection collectionFromMap(Map<?, ?> m) {
		RequestCollection c = new RequestCollection();
		c.setId(string(m.get("id")));
		c.setName(string(m.get("name")));
		c.getVariables().addAll(kvFromList(m.get("variables")));
		Object items = m.get("items");
		if (items instanceof List<?>) {
			itemsFromList(items, c.getItems(), c.getId());
		} else {
			itemsFromList(m.get("requests"), c.getItems(), c.getId()); // format version 1
		}
		return c;
	}

	private static void itemsFromList(Object value, List<CollectionNode> into, String collectionId) {
		for (Object o : list(value)) {
			if (!(o instanceof Map<?, ?> nm)) {
				continue;
			}
			if ("folder".equals(nm.get("type"))) {
				Folder f = new Folder();
				f.setId(string(nm.get("id")));
				f.setName(string(nm.get("name")));
				itemsFromList(nm.get("items"), f.getItems(), collectionId);
				into.add(f);
			} else {
				RequestModel r = requestFromMap(nm);
				r.setCollectionId(collectionId);
				into.add(r);
			}
		}
	}

	public static Map<String, Object> requestToMap(RequestModel r) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("id", r.getId());
		m.put("name", r.getName());
		m.put("method", r.getMethod().name());
		m.put("url", r.getUrl());
		m.put("params", kvToList(r.getParams()));
		m.put("headers", kvToList(r.getHeaders()));
		m.put("bodyType", r.getBodyType().name());
		m.put("body", r.getBody());
		Auth auth = r.getAuth();
		if (auth.getType() != AuthType.NONE || !auth.getUsername().isEmpty() || auth.hasSecrets()) {
			Map<String, Object> am = new LinkedHashMap<>();
			am.put("type", auth.getType().name());
			am.put("token", auth.getToken());
			am.put("username", auth.getUsername());
			am.put("password", auth.getPassword());
			m.put("auth", am);
		}
		if (r.isInCollection()) {
			m.put("collectionId", r.getCollectionId());
		}
		return m;
	}

	public static RequestModel requestFromMap(Map<?, ?> m) {
		RequestModel r = new RequestModel();
		r.setId(string(m.get("id")));
		r.setName(string(m.get("name")));
		r.setMethod(HttpMethod.parse(string(m.get("method"))));
		r.setUrl(string(m.get("url")));
		r.getParams().addAll(kvFromList(m.get("params")));
		r.getHeaders().addAll(kvFromList(m.get("headers")));
		r.setBodyType(BodyType.parse(string(m.get("bodyType"))));
		r.setBody(string(m.get("body")));
		r.setCollectionId(string(m.get("collectionId")));
		if (m.get("auth") instanceof Map<?, ?> am) {
			Auth auth = r.getAuth();
			auth.setType(AuthType.parse(string(am.get("type"))));
			auth.setToken(string(am.get("token")));
			auth.setUsername(string(am.get("username")));
			auth.setPassword(string(am.get("password")));
		}
		return r;
	}

	static List<Object> kvToList(List<KeyValue> rows) {
		List<Object> out = new ArrayList<>();
		for (KeyValue kv : rows) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("key", kv.getKey());
			m.put("value", kv.getValue());
			m.put("enabled", kv.isEnabled());
			out.add(m);
		}
		return out;
	}

	static List<KeyValue> kvFromList(Object value) {
		List<KeyValue> out = new ArrayList<>();
		for (Object o : list(value)) {
			if (o instanceof Map<?, ?> m) {
				Object enabled = m.get("enabled");
				out.add(new KeyValue(string(m.get("key")), string(m.get("value")),
						!(enabled instanceof Boolean b) || b));
			}
		}
		return out;
	}

	private static List<?> list(Object o) {
		return o instanceof List<?> l ? l : List.of();
	}

	private static String string(Object o) {
		return o instanceof String s ? s : "";
	}

	private static long number(Object o) {
		return o instanceof Number n ? n.longValue() : 0;
	}
}
