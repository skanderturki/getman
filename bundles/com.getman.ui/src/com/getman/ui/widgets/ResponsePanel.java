package com.getman.ui.widgets;

import java.util.Map;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import com.getman.core.json.Json;
import com.getman.core.json.JsonException;
import com.getman.core.model.ResponseModel;

/** Shows one response: status line, time, size, and Body / Headers tabs. */
public class ResponsePanel extends Composite {
	/** Bodies larger than this are truncated in the viewer; SWT Text gets very slow beyond a few MB. */
	private static final int MAX_DISPLAY_CHARS = 2_000_000;

	// Mid-tone colours that stay readable on both light and dark themes.
	private static final Color GREEN = new Color(46, 160, 67);
	private static final Color BLUE = new Color(56, 132, 244);
	private static final Color ORANGE = new Color(219, 129, 0);
	private static final Color RED = new Color(218, 54, 51);

	private final Label statusLabel;
	private final Label metaLabel;
	private final Button prettyButton;
	private final Button copyButton;
	private final CTabItem headersTab;
	private final Text bodyText;
	private final Table headersTable;
	private ResponseModel response;

	public ResponsePanel(Composite parent) {
		super(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		setLayout(layout);

		Composite header = new Composite(this, SWT.NONE);
		header.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		GridLayout headerLayout = new GridLayout(4, false);
		headerLayout.marginWidth = 2;
		headerLayout.marginHeight = 2;
		header.setLayout(headerLayout);

		statusLabel = new Label(header, SWT.NONE);
		statusLabel.setFont(JFaceResources.getBannerFont());
		metaLabel = new Label(header, SWT.NONE);
		metaLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		prettyButton = new Button(header, SWT.CHECK);
		prettyButton.setText("Pretty");
		prettyButton.setToolTipText("Pretty-print JSON responses");
		prettyButton.setSelection(true);
		prettyButton.addListener(SWT.Selection, e -> renderBody());
		copyButton = new Button(header, SWT.PUSH);
		copyButton.setText("Copy Body");
		copyButton.addListener(SWT.Selection, e -> copyBody());

		CTabFolder tabs = new CTabFolder(this, SWT.BORDER | SWT.FLAT);
		tabs.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		CTabItem bodyTab = new CTabItem(tabs, SWT.NONE);
		bodyTab.setText("Body");
		bodyText = new Text(tabs, SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL);
		bodyText.setFont(JFaceResources.getTextFont());
		bodyTab.setControl(bodyText);

		headersTab = new CTabItem(tabs, SWT.NONE);
		headersTab.setText("Headers");
		headersTable = new Table(tabs, SWT.FULL_SELECTION | SWT.MULTI);
		headersTable.setHeaderVisible(true);
		headersTable.setLinesVisible(true);
		TableColumn nameColumn = new TableColumn(headersTable, SWT.NONE);
		nameColumn.setText("Name");
		nameColumn.setWidth(220);
		TableColumn valueColumn = new TableColumn(headersTable, SWT.NONE);
		valueColumn.setText("Value");
		valueColumn.setWidth(500);
		headersTab.setControl(headersTable);
		installHeadersMenu();

		tabs.setSelection(bodyTab);
		showPlaceholder();
	}

	public void showPlaceholder() {
		response = null;
		setStatus("No response yet", null);
		metaLabel.setText("Send a request (Ctrl+Enter) to see the response here.");
		bodyText.setText("");
		headersTable.removeAll();
		headersTab.setText("Headers");
		copyButton.setEnabled(false);
		layout(true, true);
	}

	public void showLoading() {
		setStatus("Sending…", null);
		metaLabel.setText("");
		copyButton.setEnabled(false);
		layout(true, true);
	}

	public void showCancelled() {
		setStatus("Cancelled", ORANGE);
		metaLabel.setText("The request was cancelled.");
		layout(true, true);
	}

	public void showResponse(ResponseModel r) {
		this.response = r;
		headersTable.removeAll();
		if (r.isError()) {
			setStatus("Error", RED);
			metaLabel.setText(r.getDurationMs() + " ms");
			bodyText.setText(r.getError());
			headersTab.setText("Headers");
			copyButton.setEnabled(true);
			layout(true, true);
			return;
		}
		setStatus(r.getStatusLine(), colorFor(r.getStatusCode()));
		metaLabel.setText(r.getDurationMs() + " ms  ·  " + ResponseModel.formatSize(r.getSizeBytes()) + "  ·  "
				+ r.getHttpVersion());
		metaLabel.setToolTipText(r.getFinalUrl());
		for (Map.Entry<String, String> h : r.getHeaders()) {
			TableItem item = new TableItem(headersTable, SWT.NONE);
			item.setText(new String[] { h.getKey(), h.getValue() });
		}
		headersTab.setText("Headers (" + r.getHeaders().size() + ")");
		renderBody();
		copyButton.setEnabled(true);
		layout(true, true);
	}

	private void renderBody() {
		if (response == null || response.isError()) {
			return;
		}
		String body = response.getBody();
		if (prettyButton.getSelection() && looksLikeJson(response, body)) {
			try {
				body = Json.prettify(body);
			} catch (JsonException e) {
				// not valid JSON after all: show it as received
			}
		}
		if (body.length() > MAX_DISPLAY_CHARS) {
			body = body.substring(0, MAX_DISPLAY_CHARS) + "\n\n… truncated (" + ResponseModel.formatSize(
					response.getSizeBytes()) + " total). Use Copy Body to get the full content.";
		}
		bodyText.setText(body);
	}

	private static boolean looksLikeJson(ResponseModel r, String body) {
		String contentType = r.getHeader("content-type");
		if (contentType != null && contentType.toLowerCase().contains("json")) {
			return true;
		}
		String trimmed = body.stripLeading();
		return trimmed.startsWith("{") || trimmed.startsWith("[");
	}

	private void setStatus(String text, Color color) {
		statusLabel.setText(text);
		statusLabel.setForeground(color);
		metaLabel.setToolTipText(null);
	}

	private static Color colorFor(int status) {
		if (status >= 500) {
			return RED;
		}
		if (status >= 400) {
			return ORANGE;
		}
		if (status >= 300) {
			return BLUE;
		}
		return GREEN;
	}

	private void installHeadersMenu() {
		Menu menu = new Menu(headersTable);
		MenuItem copySelected = new MenuItem(menu, SWT.PUSH);
		copySelected.setText("Copy");
		copySelected.addListener(SWT.Selection, e -> copyToClipboard(headersAsText(headersTable.getSelection())));
		MenuItem copyAll = new MenuItem(menu, SWT.PUSH);
		copyAll.setText("Copy All");
		copyAll.addListener(SWT.Selection, e -> copyToClipboard(headersAsText(headersTable.getItems())));
		headersTable.setMenu(menu);
	}

	private static String headersAsText(TableItem[] items) {
		StringBuilder sb = new StringBuilder();
		for (TableItem item : items) {
			sb.append(item.getText(0)).append(": ").append(item.getText(1)).append(System.lineSeparator());
		}
		return sb.toString();
	}

	private void copyBody() {
		boolean truncated = response != null && !response.isError()
				&& response.getBody().length() > MAX_DISPLAY_CHARS;
		// When the viewer is truncated, copy the full original body instead.
		copyToClipboard(truncated ? response.getBody() : bodyText.getText());
	}

	private void copyToClipboard(String text) {
		if (text == null || text.isEmpty()) {
			return;
		}
		Clipboard clipboard = new Clipboard(getDisplay());
		try {
			clipboard.setContents(new Object[] { text }, new Transfer[] { TextTransfer.getInstance() });
		} finally {
			clipboard.dispose();
		}
	}
}
