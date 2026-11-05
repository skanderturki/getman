package com.getman.core.model;

import java.util.ArrayList;
import java.util.List;

/** A collection or folder: an ordered list of folders and requests. */
public interface NodeContainer {
	String getId();

	String getName();

	/** Live, mutable list of direct children in display order. */
	List<CollectionNode> getItems();

	/** Every request in this container and its sub-folders, depth-first in display order. */
	default List<RequestModel> allRequests() {
		List<RequestModel> out = new ArrayList<>();
		collect(this, out);
		return out;
	}

	private static void collect(NodeContainer container, List<RequestModel> out) {
		for (CollectionNode node : container.getItems()) {
			if (node instanceof RequestModel r) {
				out.add(r);
			} else if (node instanceof Folder f) {
				collect(f, out);
			}
		}
	}
}
