package com.getman.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A named, ordered group of saved requests. */
public final class RequestCollection {
	private String id = UUID.randomUUID().toString();
	private String name = "New Collection";
	private final List<RequestModel> requests = new ArrayList<>();

	public RequestCollection() {
	}

	public RequestCollection(String name) {
		setName(name);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name == null ? "" : name;
	}

	/** Live, mutable list of saved requests in display order. */
	public List<RequestModel> getRequests() {
		return requests;
	}

	public RequestModel findRequest(String requestId) {
		for (RequestModel r : requests) {
			if (r.getId().equals(requestId)) {
				return r;
			}
		}
		return null;
	}

	/** Stores a copy of {@code request}, replacing the saved request with the same id if there is one. */
	public RequestModel put(RequestModel request) {
		RequestModel saved = request.copy();
		saved.setCollectionId(id);
		for (int i = 0; i < requests.size(); i++) {
			if (requests.get(i).getId().equals(saved.getId())) {
				requests.set(i, saved);
				return saved;
			}
		}
		requests.add(saved);
		return saved;
	}

	public boolean removeRequest(String requestId) {
		return requests.removeIf(r -> r.getId().equals(requestId));
	}
}
