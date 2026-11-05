package com.getman.core.model;

/** Something that sits in a collection tree: a {@link Folder} or a saved {@link RequestModel}. */
public sealed interface CollectionNode permits Folder, RequestModel {
	String getId();

	String getName();
}
