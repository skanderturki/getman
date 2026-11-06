package com.getman.mcp.ui;

import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import com.getman.mcp.ToolException;
import com.getman.mcp.ToolHost;
import com.getman.ui.api.GetmanWorkspace;
import com.getman.ui.api.GetmanWorkspaces;

/** Runs tool actions on the UI thread against the GetMan view (opening it when needed) and asks with dialogs. */
final class EclipseToolHost implements ToolHost {

	@Override
	public <T> T onWorkspace(WorkspaceAction<T> action) throws ToolException {
		AtomicReference<T> result = new AtomicReference<>();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		syncExec(() -> {
			try {
				result.set(action.run(workspace()));
			} catch (Throwable t) {
				failure.set(t);
			}
		});
		Throwable t = failure.get();
		if (t instanceof ToolException e) {
			throw e;
		}
		if (t instanceof RuntimeException e) {
			throw e;
		}
		if (t != null) {
			throw new IllegalStateException(t);
		}
		return result.get();
	}

	@Override
	public boolean approve(String message) {
		AtomicReference<Boolean> allowed = new AtomicReference<>(false);
		try {
			syncExec(() -> {
				IWorkbenchWindow window = window();
				Shell parent = window == null ? null : window.getShell();
				MessageDialog dialog = new MessageDialog(parent, "GetMan: Allow AI Assistant Request?", null, message,
						MessageDialog.WARNING, 1, "Allow", "Deny");
				allowed.set(dialog.open() == 0);
			});
		} catch (ToolException e) {
			return false;
		}
		return allowed.get();
	}

	private static void syncExec(Runnable runnable) throws ToolException {
		if (!PlatformUI.isWorkbenchRunning()) {
			throw new ToolException("Eclipse is not running.");
		}
		Display display = PlatformUI.getWorkbench().getDisplay();
		try {
			display.syncExec(runnable);
		} catch (SWTException e) {
			throw new ToolException("Eclipse is shutting down.");
		}
	}

	/** The open GetMan view, opened first (without taking focus) when it is closed. */
	private static GetmanWorkspace workspace() throws ToolException {
		GetmanWorkspace ws = GetmanWorkspaces.current();
		if (ws != null) {
			return ws;
		}
		IWorkbenchWindow window = window();
		IWorkbenchPage page = window == null ? null : window.getActivePage();
		if (page == null) {
			throw new ToolException("No Eclipse window is open.");
		}
		try {
			page.showView(GetmanWorkspaces.VIEW_ID, null, IWorkbenchPage.VIEW_VISIBLE);
		} catch (PartInitException e) {
			throw new ToolException("Could not open the GetMan view: " + e.getMessage());
		}
		ws = GetmanWorkspaces.current();
		if (ws == null) {
			throw new ToolException("Could not open the GetMan view.");
		}
		return ws;
	}

	/** The active workbench window, or the first one when Eclipse is in the background (the usual case here). */
	private static IWorkbenchWindow window() {
		IWorkbench workbench = PlatformUI.getWorkbench();
		IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
		if (window == null && workbench.getWorkbenchWindows().length > 0) {
			window = workbench.getWorkbenchWindows()[0];
		}
		return window;
	}
}
