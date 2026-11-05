package com.getman.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A named group of requests and sub-folders inside a collection. */
public final class Folder implements CollectionNode, NodeContainer {
	private String id = UUID.randomUUID().toString();
	private String name = "New Folder";
	private final List<CollectionNode> items = new ArrayList<>();

	public Folder() {
	}

	public Folder(String name) {
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
}
