package com.getman.core.store;

import java.util.ArrayList;
import java.util.List;

import com.getman.core.model.Environment;
import com.getman.core.model.RequestCollection;
import com.getman.core.model.RequestModel;

/** Everything GetMan restores after an Eclipse restart. */
public final class WorkspaceState {
	private final List<RequestModel> openTabs = new ArrayList<>();
	private final List<RequestCollection> collections = new ArrayList<>();
	private final List<Environment> environments = new ArrayList<>();
	private String activeEnvironmentId = "";
	private int selectedIndex;

	/** Live, mutable list of environments in display order. */
	public List<Environment> getEnvironments() {
		return environments;
	}

	/** Id of the selected environment; empty for "No Environment". */
	public String getActiveEnvironmentId() {
		return activeEnvironmentId;
	}

	public void setActiveEnvironmentId(String id) {
		this.activeEnvironmentId = id == null ? "" : id;
	}

	/** Live, mutable list of collections in display order. */
	public List<RequestCollection> getCollections() {
		return collections;
	}

	public RequestCollection findCollection(String id) {
		for (RequestCollection c : collections) {
			if (c.getId().equals(id)) {
				return c;
			}
		}
		return null;
	}

	/** Live, mutable list of tabs in display order. */
	public List<RequestModel> getOpenTabs() {
		return openTabs;
	}

	public int getSelectedIndex() {
		return selectedIndex;
	}

	public void setSelectedIndex(int selectedIndex) {
		this.selectedIndex = selectedIndex;
	}
}
