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
import com.getman.core.model.Environment;
import com.getman.core.model.Folder;
import com.getman.core.model.FormField;
import com.getman.core.model.HttpMethod;
import com.getman.core.model.KeyValue;
import com.getman.core.model.RequestCollection;
import com.getman.core.model.RequestModel;

/**
 * Converts {@link WorkspaceState} to and from JSON. Unknown fields are ignored so older versions can read newer
 * files.
 * <p>
 * Secrets (auth tokens and passwords, values of variables marked secret) go to a {@link Secrets} store when one is
 * given: the JSON then holds a reference ({@code tokenRef}, {@code passwordRef}, {@code valueRef}) instead of the
 * value. Without a store (collection exports, tests) values are written inline, as in versions before 0.8, and
 * inline values in old files are read as usual, which is how existing secrets migrate on the next save.
 */
public final class StateCodec {
	/** 2: collections are trees ("items") and have variables. Version-1 files (flat "requests") still load. */
	static final int FORMAT_VERSION = 2;

	private StateCodec() {
	}

	// --- workspace state -------------------------------------------------------------------------

	public static String toJson(WorkspaceState state) {
		return toJson(state, null);
	}

	public static String toJson(WorkspaceState state, Secrets secrets) {
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("formatVersion", FORMAT_VERSION);
		root.put("selectedIndex", state.getSelectedIndex());
		List<Object> tabs = new ArrayList<>();
		List<RequestModel> openTabs = state.getOpenTabs();
		for (int i = 0; i < openTabs.size(); i++) {
			tabs.add(requestToMap(openTabs.get(i), secrets, "tab:" + i + ":" + openTabs.get(i).getId()));
		}
		root.put("openTabs", tabs);
		List<Object> collections = new ArrayList<>();
		for (RequestCollection c : state.getCollections()) {
			collections.add(collectionToMap(c, secrets));
		}
		root.put("collections", collections);
		List<Object> environments = new ArrayList<>();
		for (Environment e : state.getEnvironments()) {
			Map<String, Object> em = new LinkedHashMap<>();
			em.put("id", e.getId());
			em.put("name", e.getName());
			if (e.isProduction()) {
				em.put("production", true);
			}
			em.put("variables", kvToList(e.getVariables(), secrets, "env:" + e.getId()));
			environments.add(em);
		}
		root.put("environments", environments);
		root.put("activeEnvironmentId", state.getActiveEnvironmentId());
		return Json.writePretty(root);
	}

	public static WorkspaceState fromJson(String json) {
		return fromJson(json, null);
	}

	public static WorkspaceState fromJson(String json, Secrets secrets) {
		Object parsed = Json.parse(json);
		if (!(parsed instanceof Map<?, ?> root)) {
			throw new JsonException("State file is not a JSON object");
		}
		WorkspaceState state = new WorkspaceState();
		for (Object tab : list(root.get("openTabs"))) {
			if (tab instanceof Map<?, ?> m) {
				state.getOpenTabs().add(requestFromMap(m, secrets));
			}
		}
		for (Object c : list(root.get("collections"))) {
			if (c instanceof Map<?, ?> m) {
				state.getCollections().add(collectionFromMap(m, secrets));
			}
		}
		for (Object o : list(root.get("environments"))) {
			if (o instanceof Map<?, ?> em) {
				Environment e = new Environment();
				e.setId(string(em.get("id")));
				e.setName(string(em.get("name")));
				e.setProduction(em.get("production") instanceof Boolean b && b);
				e.getVariables().addAll(kvFromList(em.get("variables"), secrets));
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

	// --- collections -------------------------------------------------------------------------------

	public static Map<String, Object> collectionToMap(RequestCollection c) {
		return collectionToMap(c, null);
	}

	public static Map<String, Object> collectionToMap(RequestCollection c, Secrets secrets) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("id", c.getId());
		m.put("name", c.getName());
		m.put("variables", kvToList(c.getVariables(), secrets, "cvar:" + c.getId()));
		m.put("items", itemsToList(c.getItems(), secrets, c.getId()));
		// Flat copy for GetMan 0.4 and older, which only read "requests": downgrading keeps every request
		// (without folders) instead of showing an empty collection. Ignored when "items" is present.
		List<Object> flat = new ArrayList<>();
		for (RequestModel r : c.allRequests()) {
			flat.add(savedRequestToMap(r, secrets, c.getId()));
		}
		m.put("requests", flat);
		return m;
	}

	private static List<Object> itemsToList(List<CollectionNode> items, Secrets secrets, String collectionId) {
		List<Object> out = new ArrayList<>();
		for (CollectionNode node : items) {
			if (node instanceof Folder f) {
				Map<String, Object> fm = new LinkedHashMap<>();
				fm.put("type", "folder");
				fm.put("id", f.getId());
				fm.put("name", f.getName());
				fm.put("items", itemsToList(f.getItems(), secrets, collectionId));
				out.add(fm);
			} else if (node instanceof RequestModel r) {
				out.add(savedRequestToMap(r, secrets, collectionId));
			}
		}
		return out;
	}

	private static Map<String, Object> savedRequestToMap(RequestModel r, Secrets secrets, String collectionId) {
		Map<String, Object> rm = requestToMap(r, secrets, "col:" + collectionId + ":" + r.getId());
		rm.remove("collectionId"); // implied by nesting
		return rm;
	}

	public static RequestCollection collectionFromMap(Map<?, ?> m) {
		return collectionFromMap(m, null);
	}

	public static RequestCollection collectionFromMap(Map<?, ?> m, Secrets secrets) {
		RequestCollection c = new RequestCollection();
		c.setId(string(m.get("id")));
		c.setName(string(m.get("name")));
		c.getVariables().addAll(kvFromList(m.get("variables"), secrets));
		Object items = m.get("items");
		if (items instanceof List<?>) {
			itemsFromList(items, c.getItems(), c.getId(), secrets);
		} else {
			itemsFromList(m.get("requests"), c.getItems(), c.getId(), secrets); // format version 1
		}
		return c;
	}

	private static void itemsFromList(Object value, List<CollectionNode> into, String collectionId, Secrets secrets) {
		for (Object o : list(value)) {
			if (!(o instanceof Map<?, ?> nm)) {
				continue;
			}
			if ("folder".equals(nm.get("type"))) {
				Folder f = new Folder();
				f.setId(string(nm.get("id")));
				f.setName(string(nm.get("name")));
				itemsFromList(nm.get("items"), f.getItems(), collectionId, secrets);
				into.add(f);
			} else {
				RequestModel r = requestFromMap(nm, secrets);
				r.setCollectionId(collectionId);
				into.add(r);
			}
		}
	}

	// --- requests ----------------------------------------------------------------------------------

	public static Map<String, Object> requestToMap(RequestModel r) {
		return requestToMap(r, null, null);
	}

	/**
	 * @param secretKey where this request's auth secrets go in {@code secrets} (ignored when {@code secrets} is
	 *                  null); must be unique among everything saved in the same pass
	 */
	public static Map<String, Object> requestToMap(RequestModel r, Secrets secrets, String secretKey) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("id", r.getId());
		m.put("name", r.getName());
		m.put("method", r.getMethod().name());
		m.put("url", r.getUrl());
		m.put("params", kvToList(r.getParams(), null, null));
		m.put("headers", kvToList(r.getHeaders(), null, null));
		m.put("bodyType", r.getBodyType().name());
		m.put("body", r.getBody());
		if (!r.getFormFields().isEmpty()) {
			List<Object> fields = new ArrayList<>();
			for (FormField f : r.getFormFields()) {
				Map<String, Object> fm = new LinkedHashMap<>();
				fm.put("key", f.getKey());
				fm.put("value", f.getValue());
				fm.put("enabled", f.isEnabled());
				fm.put("file", f.isFile());
				fields.add(fm);
			}
			m.put("formFields", fields);
		}
		if (!r.getBinaryFile().isEmpty()) {
			m.put("binaryFile", r.getBinaryFile());
		}
		Auth auth = r.getAuth();
		if (auth.getType() != AuthType.NONE || !auth.getUsername().isEmpty() || auth.hasSecrets()) {
			Map<String, Object> am = new LinkedHashMap<>();
			am.put("type", auth.getType().name());
			putSecret(am, "token", auth.getToken(), secrets, secretKey + ":token");
			am.put("username", auth.getUsername());
			putSecret(am, "password", auth.getPassword(), secrets, secretKey + ":password");
			m.put("auth", am);
		}
		if (r.isInCollection()) {
			m.put("collectionId", r.getCollectionId());
		}
		if (r.isCreatedByAssistant()) {
			m.put("createdBy", "assistant");
		}
		return m;
	}

	public static RequestModel requestFromMap(Map<?, ?> m) {
		return requestFromMap(m, null);
	}

	public static RequestModel requestFromMap(Map<?, ?> m, Secrets secrets) {
		RequestModel r = new RequestModel();
		r.setId(string(m.get("id")));
		r.setName(string(m.get("name")));
		r.setMethod(HttpMethod.parse(string(m.get("method"))));
		r.setUrl(string(m.get("url")));
		r.getParams().addAll(kvFromList(m.get("params"), null));
		r.getHeaders().addAll(kvFromList(m.get("headers"), null));
		r.setBodyType(BodyType.parse(string(m.get("bodyType"))));
		r.setBody(string(m.get("body")));
		for (Object o : list(m.get("formFields"))) {
			if (o instanceof Map<?, ?> fm) {
				r.getFormFields().add(new FormField(string(fm.get("key")), string(fm.get("value")),
						!(fm.get("enabled") instanceof Boolean b) || b, fm.get("file") instanceof Boolean f && f));
			}
		}
		r.setBinaryFile(string(m.get("binaryFile")));
		r.setCollectionId(string(m.get("collectionId")));
		r.setCreatedByAssistant("assistant".equals(m.get("createdBy")));
		if (m.get("auth") instanceof Map<?, ?> am) {
			Auth auth = r.getAuth();
			auth.setType(AuthType.parse(string(am.get("type"))));
			auth.setToken(getSecret(am, "token", secrets));
			auth.setUsername(string(am.get("username")));
			auth.setPassword(getSecret(am, "password", secrets));
		}
		return r;
	}

	// --- key/value rows ----------------------------------------------------------------------------

	/**
	 * Rows as JSON. Rows marked secret (variables) with a value are stored in {@code secrets} under
	 * {@code keyPrefix:index} when a store is given; otherwise every value is inline.
	 */
	static List<Object> kvToList(List<KeyValue> rows, Secrets secrets, String keyPrefix) {
		List<Object> out = new ArrayList<>();
		for (int i = 0; i < rows.size(); i++) {
			KeyValue kv = rows.get(i);
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("key", kv.getKey());
			if (kv.isSecret()) {
				putSecret(m, "value", kv.getValue(), secrets, keyPrefix + ":" + i);
				m.put("secret", true);
			} else {
				m.put("value", kv.getValue());
			}
			m.put("enabled", kv.isEnabled());
			out.add(m);
		}
		return out;
	}

	static List<KeyValue> kvFromList(Object value, Secrets secrets) {
		List<KeyValue> out = new ArrayList<>();
		for (Object o : list(value)) {
			if (o instanceof Map<?, ?> m) {
				Object enabled = m.get("enabled");
				out.add(new KeyValue(string(m.get("key")), getSecret(m, "value", secrets),
						!(enabled instanceof Boolean b) || b, m.get("secret") instanceof Boolean s && s));
			}
		}
		return out;
	}

	// --- secrets -----------------------------------------------------------------------------------

	/** Writes {@code name: value} inline, or {@code nameRef: key} after storing a non-empty value securely. */
	private static void putSecret(Map<String, Object> m, String name, String value, Secrets secrets, String key) {
		if (secrets != null && !value.isEmpty() && secrets.put(key, value)) {
			m.put(name + "Ref", key);
		} else {
			m.put(name, value);
		}
	}

	/** Reads an inline {@code name}, or resolves {@code nameRef} from the store ("" when no store is given). */
	private static String getSecret(Map<?, ?> m, String name, Secrets secrets) {
		Object ref = m.get(name + "Ref");
		if (ref instanceof String key && !key.isEmpty()) {
			return secrets == null ? "" : secrets.get(key);
		}
		return string(m.get(name));
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
