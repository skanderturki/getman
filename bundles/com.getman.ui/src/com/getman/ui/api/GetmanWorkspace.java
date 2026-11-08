package com.getman.ui.api;

import java.util.List;
import java.util.Set;

import com.getman.core.history.HistoryEntry;
import com.getman.core.http.HttpService;
import com.getman.core.model.CollectionNode;
import com.getman.core.model.Environment;
import com.getman.core.model.RequestCollection;

/**
 * The collections, environments and history of the open GetMan view, for other GetMan bundles (the optional MCP
 * server). Every method must be called on the UI thread. The lists are the view's live lists: after changing a
 * collection, call {@link #collectionsChanged} so the view refreshes and saves.
 */
public interface GetmanWorkspace {
	/** Live list of collections. */
	List<RequestCollection> collections();

	/** Live list of environments. After changing it or an environment's variables, call {@link #environmentsChanged}. */
	List<Environment> environments();

	/** Refreshes the environment selector and the unknown-variable hints, and saves shortly after. */
	void environmentsChanged();

	/** Selects {@code environment} in the GetMan toolbar ({@code null} for "No Environment"). */
	void selectEnvironment(Environment environment);

	/** The environment selected in the GetMan toolbar, or {@code null} for "No Environment". */
	Environment activeEnvironment();

	/** The HTTP client configured from GetMan's preferences. Thread-safe. */
	HttpService http();

	/** Refreshes the Collections tree, selects {@code reveal} (if not null) and saves shortly after. */
	void collectionsChanged(RequestCollection collection, CollectionNode reveal);

	/** Saved requests were deleted: tabs that show them are no longer linked to the collection. */
	void requestsDeleted(String collectionId, Set<String> requestIds);

	/** Adds a sent request to the History tab (when history is on). */
	void recordHistory(HistoryEntry entry);

	/** Shows a message in the Eclipse status line. */
	void showStatus(String message);
}
