package com.getman.ui.views;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import com.getman.core.http.HttpService;
import com.getman.core.model.RequestModel;
import com.getman.core.run.CollectionRunner;
import com.getman.core.run.CollectionRunner.Outcome;
import com.getman.core.run.CollectionRunner.Result;

/**
 * Sends the saved requests of a collection or folder one after the other and lists, for each, whether its status
 * matched the expected status. Modeless, so the user can keep working; closing it stops the run.
 */
class RunDialog extends Dialog {
	private static final int RUN_AGAIN_ID = IDialogConstants.CLIENT_ID + 1;
	private static final int STOP_ID = IDialogConstants.CLIENT_ID + 2;

	private static final Color GREEN = new Color(46, 160, 67);
	private static final Color ORANGE = new Color(219, 129, 0);
	private static final Color RED = new Color(218, 54, 51);

	private final String title;
	private final List<RequestModel> requests;
	private final Map<String, String> values;
	private final HttpService http;
	private final Consumer<String> open;
	private Label summary;
	private Table table;
	private CollectionRunner runner;
	private final List<Result> results = new ArrayList<>();

	/**
	 * @param requests copies of the saved requests, in run order
	 * @param values   the variables to fill in (collection variables overlaid by the environment)
	 * @param open     opens the saved request with the given id
	 */
	RunDialog(Shell parent, String title, List<RequestModel> requests, Map<String, String> values, HttpService http,
			Consumer<String> open) {
		super(parent);
		this.title = title;
		this.requests = requests;
		this.values = values;
		this.http = http;
		this.open = open;
		setShellStyle((getShellStyle() & ~SWT.APPLICATION_MODAL) | SWT.MODELESS | SWT.RESIZE | SWT.MAX);
		setBlockOnOpen(false);
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText(title);
		shell.addListener(SWT.Dispose, e -> stop());
	}

	@Override
	protected Point getInitialSize() {
		return new Point(900, 460);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite area = (Composite) super.createDialogArea(parent);
		summary = new Label(area, SWT.NONE);
		summary.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		table = new Table(area, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL);
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		column("Result", 90);
		column("Request", 260);
		column("Status", 110);
		column("Expected", 80);
		column("Time", 70);
		column("Details", 260);
		table.addListener(SWT.DefaultSelection, e -> {
			int i = table.getSelectionIndex();
			if (i >= 0 && i < requests.size()) {
				open.accept(requests.get(i).getId());
			}
		});
		table.setToolTipText("Double-click a request to open it.");
		return area;
	}

	private void column(String text, int width) {
		TableColumn c = new TableColumn(table, SWT.NONE);
		c.setText(text);
		c.setWidth(width);
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, RUN_AGAIN_ID, "Run Again", false);
		createButton(parent, STOP_ID, "Stop", false);
		createButton(parent, IDialogConstants.CLOSE_ID, IDialogConstants.CLOSE_LABEL, true);
	}

	@Override
	public int open() {
		int code = super.open();
		start();
		return code;
	}

	@Override
	protected void buttonPressed(int buttonId) {
		if (buttonId == RUN_AGAIN_ID) {
			start();
		} else if (buttonId == STOP_ID) {
			stop();
		} else if (buttonId == IDialogConstants.CLOSE_ID) {
			close();
		} else {
			super.buttonPressed(buttonId);
		}
	}

	private void start() {
		results.clear();
		table.removeAll();
		for (RequestModel r : requests) {
			TableItem item = new TableItem(table, SWT.NONE);
			item.setText(new String[] { "Waiting", label(r), "", r.getExpectedStatus(), "", "" });
		}
		CollectionRunner current = new CollectionRunner(http);
		runner = current;
		setRunning(true);
		updateSummary();
		Thread thread = new Thread(() -> {
			current.run(requests, values, result -> asyncExec(() -> {
				if (runner == current) {
					show(result);
				}
			}));
			asyncExec(() -> {
				if (runner == current) {
					setRunning(false);
					updateSummary();
				}
			});
		}, "GetMan run: " + title);
		thread.setDaemon(true);
		thread.start();
	}

	private void stop() {
		if (runner != null) {
			runner.cancel();
		}
	}

	private void asyncExec(Runnable r) {
		Shell shell = getShell();
		if (shell == null || shell.isDisposed()) {
			return;
		}
		shell.getDisplay().asyncExec(() -> {
			if (getShell() != null && !getShell().isDisposed()) {
				r.run();
			}
		});
	}

	private void show(Result result) {
		int i = results.size();
		results.add(result);
		if (i >= table.getItemCount()) {
			return;
		}
		TableItem item = table.getItem(i);
		String status = result.response() == null || result.response().isError() ? ""
				: result.response().getStatusLine();
		String time = result.response() == null ? "" : result.response().getDurationMs() + " ms";
		item.setText(new String[] { outcomeLabel(result.outcome()), label(result.request()), status,
				result.request().getExpectedStatus(), time, result.message() == null ? "" : result.message() });
		item.setForeground(0, switch (result.outcome()) {
		case PASSED -> GREEN;
		case FAILED, ERROR -> RED;
		case STOPPED -> ORANGE;
		case NOT_CHECKED -> null;
		});
		table.showItem(item);
		updateSummary();
	}

	private void updateSummary() {
		boolean running = getButton(STOP_ID) != null && getButton(STOP_ID).isEnabled();
		String counts = CollectionRunner.Summary.of(results).toString();
		summary.setText(running ? "Running " + (results.size() + 1) + " of " + requests.size() + "…  "
				+ (results.isEmpty() ? "" : counts)
				: "Finished: " + counts + ". Requests without an expected status (Checks tab) are not checked.");
		summary.getParent().layout(true);
	}

	private void setRunning(boolean running) {
		getButton(STOP_ID).setEnabled(running);
		getButton(RUN_AGAIN_ID).setEnabled(!running);
	}

	private static String label(RequestModel r) {
		return r.getMethod().name() + "  " + (r.getName().isBlank() ? r.getUrl() : r.getName());
	}

	private static String outcomeLabel(Outcome outcome) {
		return switch (outcome) {
		case PASSED -> "✓ Passed";
		case FAILED -> "✗ Failed";
		case NOT_CHECKED -> "Sent";
		case ERROR -> "✗ Error";
		case STOPPED -> "Not sent";
		};
	}
}
