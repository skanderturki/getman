package com.getman.ui.views;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
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
import com.getman.core.run.RunRecord;
import com.getman.core.run.RunReport;

/**
 * The results of a collection or folder run. Either runs the requests itself, one after the other, and reports
 * each finished run so it can be kept in the History tab; or shows a run from the History tab. Modeless, so the
 * user can keep working; closing it stops a run in progress.
 */
class RunDialog extends Dialog {
	private static final int RUN_AGAIN_ID = IDialogConstants.CLIENT_ID + 1;
	private static final int STOP_ID = IDialogConstants.CLIENT_ID + 2;
	private static final int EXPORT_ID = IDialogConstants.CLIENT_ID + 3;
	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	static final Color GREEN = new Color(46, 160, 67);
	static final Color ORANGE = new Color(219, 129, 0);
	static final Color RED = new Color(218, 54, 51);

	// a live run
	private final RunRecord template;
	private final List<RequestModel> requests;
	private final Map<String, String> values;
	private final UnaryOperator<String> mask;
	private final HttpService http;
	private final Consumer<RunRecord> onFinished;
	// a run from the History tab
	private final Runnable rerun;

	private final Consumer<String> open;
	private final String title;
	private Label summary;
	private Table table;
	private CollectionRunner runner;
	private final List<RunRecord.Item> items = new ArrayList<>();
	private RunRecord finished;

	/**
	 * Runs {@code requests}.
	 *
	 * @param template   the run's collection, folder and environment
	 * @param requests   copies of the saved requests, in run order
	 * @param values     the variables to fill in (collection variables overlaid by the environment)
	 * @param mask       masks secret values in what is kept of the run
	 * @param open       opens the saved request with the given id
	 * @param onFinished receives each finished run, on the UI thread
	 */
	RunDialog(Shell parent, RunRecord template, List<RequestModel> requests, Map<String, String> values,
			UnaryOperator<String> mask, HttpService http, Consumer<String> open, Consumer<RunRecord> onFinished) {
		super(parent);
		this.template = template;
		this.requests = requests;
		this.values = values;
		this.mask = mask;
		this.http = http;
		this.onFinished = onFinished;
		this.rerun = null;
		this.open = open;
		this.title = "Run " + template.title()
				+ (template.environment().isEmpty() ? " (no environment)" : " in " + template.environment());
		init();
	}

	/**
	 * Shows {@code run} from the History tab.
	 *
	 * @param rerun runs the same collection or folder again; null when it no longer exists
	 */
	RunDialog(Shell parent, RunRecord run, Consumer<String> open, Runnable rerun) {
		super(parent);
		this.template = run;
		this.requests = null;
		this.values = null;
		this.mask = null;
		this.http = null;
		this.onFinished = null;
		this.rerun = rerun;
		this.open = open;
		this.finished = run;
		this.title = "Run " + run.title()
				+ (run.environment().isEmpty() ? " (no environment)" : " in " + run.environment()) + " · "
				+ WHEN.format(Instant.ofEpochMilli(run.startedAtMillis()).atZone(ZoneId.systemDefault()))
				+ (run.viaAssistant() ? " · AI assistant" : "");
		init();
	}

	private void init() {
		setShellStyle((getShellStyle() & ~SWT.APPLICATION_MODAL) | SWT.MODELESS | SWT.RESIZE | SWT.MAX);
		setBlockOnOpen(false);
	}

	private boolean isLive() {
		return requests != null;
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
			String id = isLive() ? i >= 0 && i < requests.size() ? requests.get(i).getId() : null
					: i >= 0 && i < finished.items().size() ? finished.items().get(i).requestId() : null;
			if (id != null) {
				open.accept(id);
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
		createButton(parent, EXPORT_ID, "Export…", false).setToolTipText("Save these results as an HTML report, "
				+ "a CSV file or a JUnit XML file (for CI servers).");
		createButton(parent, RUN_AGAIN_ID, "Run Again", false);
		if (isLive()) {
			createButton(parent, STOP_ID, "Stop", false);
		}
		createButton(parent, IDialogConstants.CLOSE_ID, IDialogConstants.CLOSE_LABEL, true);
	}

	@Override
	public int open() {
		int code = super.open();
		if (isLive()) {
			start();
		} else {
			showSaved();
		}
		return code;
	}

	@Override
	protected void buttonPressed(int buttonId) {
		if (buttonId == RUN_AGAIN_ID) {
			if (isLive()) {
				start();
			} else if (rerun != null) {
				close();
				rerun.run();
			}
		} else if (buttonId == STOP_ID) {
			stop();
		} else if (buttonId == EXPORT_ID) {
			if (finished != null) {
				export(getShell(), finished);
			}
		} else if (buttonId == IDialogConstants.CLOSE_ID) {
			close();
		} else {
			super.buttonPressed(buttonId);
		}
	}

	private void showSaved() {
		for (RunRecord.Item item : finished.items()) {
			show(new TableItem(table, SWT.NONE), item);
		}
		items.addAll(finished.items());
		getButton(RUN_AGAIN_ID).setEnabled(rerun != null);
		if (rerun == null) {
			getButton(RUN_AGAIN_ID).setToolTipText("The collection or folder no longer exists.");
		}
		updateSummary(false);
	}

	private void start() {
		items.clear();
		finished = null;
		table.removeAll();
		for (RequestModel r : requests) {
			TableItem item = new TableItem(table, SWT.NONE);
			item.setText(new String[] { "Waiting", label(r.getMethod().name(), r.getName(), r.getUrl()), "",
					r.getExpectedStatus(), "", "" });
		}
		CollectionRunner current = new CollectionRunner(http);
		runner = current;
		setRunning(true);
		updateSummary(true);
		Display display = getShell().getDisplay();
		long startedAt = System.currentTimeMillis();
		Thread thread = new Thread(() -> {
			List<Result> results = current.run(requests, values, result -> {
				RunRecord.Item item = RunRecord.Item.of(result, mask);
				asyncExec(() -> {
					if (runner == current) {
						showNext(item);
					}
				});
			});
			RunRecord record = template.withResults(startedAt, results, mask);
			if (!display.isDisposed()) {
				// Keep the run even if the window was closed meanwhile.
				display.asyncExec(() -> {
					onFinished.accept(record);
					Shell shell = getShell();
					if (runner == current && shell != null && !shell.isDisposed()) {
						finished = record;
						setRunning(false);
						updateSummary(false);
					}
				});
			}
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

	private void showNext(RunRecord.Item item) {
		int i = items.size();
		items.add(item);
		if (i < table.getItemCount()) {
			TableItem row = table.getItem(i);
			show(row, item);
			table.showItem(row);
		}
		updateSummary(true);
	}

	private static void show(TableItem row, RunRecord.Item item) {
		String status = item.status() == 0 ? "" : item.statusLine().isEmpty() ? String.valueOf(item.status())
				: item.statusLine();
		String time = item.outcome() == Outcome.STOPPED || item.status() == 0 && item.durationMs() == 0 ? ""
				: item.durationMs() + " ms";
		row.setText(new String[] { outcomeLabel(item.outcome()), label(item.method(), item.name(), item.url()), status,
				item.expected(), time, item.message() });
		row.setForeground(0, outcomeColor(item.outcome()));
	}

	private void updateSummary(boolean running) {
		String counts = CollectionRunner.Summary.count(items.stream().map(RunRecord.Item::outcome).toList())
				.toString();
		summary.setText(running ? "Running " + Math.min(items.size() + 1, requests.size()) + " of " + requests.size()
				+ "…  " + (items.isEmpty() ? "" : counts)
				: "Finished: " + counts + ". Requests without an expected status (Checks tab) are not checked."
						+ (isLive() ? " Kept in the History tab." : ""));
		summary.getParent().layout(true);
	}

	private void setRunning(boolean running) {
		getButton(STOP_ID).setEnabled(running);
		getButton(RUN_AGAIN_ID).setEnabled(!running);
		getButton(EXPORT_ID).setEnabled(!running && finished != null);
	}

	/** Asks for a file and writes {@code run} to it; the format follows the chosen file type. */
	static void export(Shell shell, RunRecord run) {
		FileDialog dialog = new FileDialog(shell, SWT.SAVE);
		dialog.setText("Export Run Results");
		dialog.setFilterNames(new String[] { "HTML report (*.html)", "CSV, for spreadsheets (*.csv)",
				"JUnit XML, for CI servers (*.xml)" });
		dialog.setFilterExtensions(new String[] { "*.html", "*.csv", "*.xml" });
		dialog.setFileName(RunReport.fileName(run, RunReport.Format.HTML));
		dialog.setOverwrite(true);
		String chosen = dialog.open();
		if (chosen == null) {
			return;
		}
		RunReport.Format format = RunReport.Format.forFileName(chosen);
		if (!chosen.toLowerCase(Locale.ROOT).endsWith("." + format.extension())) {
			// No known extension typed: use the selected file type.
			format = RunReport.Format.values()[Math.max(0, dialog.getFilterIndex())];
			chosen = chosen + "." + format.extension();
		}
		try {
			Files.writeString(Path.of(chosen), RunReport.write(run, format), StandardCharsets.UTF_8);
		} catch (IOException | RuntimeException e) {
			MessageDialog.openError(shell, "Export Run Results", "Could not write " + chosen + ":\n" + e.getMessage());
		}
	}

	static String label(String method, String name, String url) {
		return method + "  " + (name.isBlank() ? url : name);
	}

	static String outcomeLabel(Outcome outcome) {
		return switch (outcome) {
		case PASSED, FAILED, ERROR -> (outcome == Outcome.PASSED ? "✓ " : "✗ ") + RunReport.label(outcome);
		case NOT_CHECKED, STOPPED -> RunReport.label(outcome);
		};
	}

	static Color outcomeColor(Outcome outcome) {
		return switch (outcome) {
		case PASSED -> GREEN;
		case FAILED, ERROR -> RED;
		case STOPPED -> ORANGE;
		case NOT_CHECKED -> null;
		};
	}
}
