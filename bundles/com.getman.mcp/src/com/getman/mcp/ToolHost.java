package com.getman.mcp;

import com.getman.ui.api.GetmanWorkspace;

/**
 * What the tools need from Eclipse: access to the GetMan workspace on its own thread, and the user's approval
 * for risky requests. The Eclipse implementation runs actions on the UI thread (opening the GetMan view when it
 * is closed) and asks with a dialog; tests use a plain in-memory implementation.
 */
public interface ToolHost {

	/** Runs {@code action} on the thread that owns the workspace and returns its result. */
	<T> T onWorkspace(WorkspaceAction<T> action) throws ToolException;

	/** Asks the user whether to allow a request; blocks until they answer. */
	boolean approve(String message);

	@FunctionalInterface
	interface WorkspaceAction<T> {
		T run(GetmanWorkspace workspace) throws ToolException;
	}
}
