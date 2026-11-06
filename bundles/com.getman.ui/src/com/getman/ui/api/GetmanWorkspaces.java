package com.getman.ui.api;

/** Where other bundles find the open GetMan view. The view registers itself when it opens and leaves on close. */
public final class GetmanWorkspaces {
	/** Id of the GetMan view, to open it with {@code IWorkbenchPage.showView}. */
	public static final String VIEW_ID = "com.getman.ui.views.GetmanView";

	private static volatile GetmanWorkspace current;

	private GetmanWorkspaces() {
	}

	/** The open GetMan view, or {@code null} when it is not open. */
	public static GetmanWorkspace current() {
		return current;
	}

	public static void register(GetmanWorkspace workspace) {
		current = workspace;
	}

	public static void unregister(GetmanWorkspace workspace) {
		if (current == workspace) {
			current = null;
		}
	}
}
