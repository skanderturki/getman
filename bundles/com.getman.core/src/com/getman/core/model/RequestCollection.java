package com.getman.core.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A named tree of folders and saved requests, with its own variables. */
public final class RequestCollection implements NodeContainer {
	private String id = UUID.randomUUID().toString();
	private String name = "New Collection";
	private final List<CollectionNode> items = new ArrayList<>();
	private final List<KeyValue> variables = new ArrayList<>();

	public RequestCollection() {
	}

	public RequestCollection(String name) {
		setName(name);
	}

	@Override
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
	}

	@Override
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name == null ? "" : name;
	}

	@Override
	public List<CollectionNode> getItems() {
		return items;
	}

	/** Live, mutable list of collection variables (used when the active environment does not define a name). */
	public List<KeyValue> getVariables() {
		return variables;
	}

	/** Enabled variables with a non-blank name; later rows win when a name repeats. */
	public Map<String, String> activeVariables() {
		Map<String, String> values = new LinkedHashMap<>();
		for (KeyValue v : variables) {
			if (v.isActive()) {
				values.put(v.getKey().trim(), v.getValue());
			}
		}
		return values;
	}

	public RequestModel findRequest(String requestId) {
		for (RequestModel r : allRequests()) {
			if (r.getId().equals(requestId)) {
				return r;
			}
		}
		return null;
	}

	/** The collection or folder that directly contains {@code node} (by identity), or {@code null}. */
	public NodeContainer parentOf(CollectionNode node) {
		return parentOf(this, node);
	}

	private static NodeContainer parentOf(NodeContainer container, CollectionNode node) {
		for (CollectionNode child : container.getItems()) {
			if (child == node) {
				return container;
			}
			if (child instanceof Folder f) {
				NodeContainer found = parentOf(f, node);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	/** Whether {@code node} is anywhere in this collection (by identity). */
	public boolean contains(CollectionNode node) {
		return parentOf(node) != null;
	}

	/**
	 * Stores a copy of {@code request}. When a saved request with the same id exists anywhere in the tree it is
	 * replaced in place (keeping its folder); otherwise the copy is added at the top level.
	 */
	public RequestModel put(RequestModel request) {
		RequestModel saved = request.copy();
		saved.setCollectionId(id);
		RequestModel existing = findRequest(saved.getId());
		if (existing != null) {
			List<CollectionNode> siblings = parentOf(existing).getItems();
			siblings.set(siblings.indexOf(existing), saved);
		} else {
			items.add(saved);
		}
		return saved;
	}

	public boolean removeRequest(String requestId) {
		RequestModel existing = findRequest(requestId);
		return existing != null && remove(existing);
	}

	/** Removes {@code node} (and, for a folder, everything in it) from wherever it is in the tree. */
	public boolean remove(CollectionNode node) {
		NodeContainer parent = parentOf(node);
		return parent != null && parent.getItems().remove(node);
	}
}
