package com.getman.ui.views;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;

import com.getman.core.curl.CurlImporter;
import com.getman.core.curl.CurlParseException;
import com.getman.core.http.HttpService;
import com.getman.core.json.Json;
import com.getman.core.json.JsonException;
import com.getman.core.model.AuthType;
import com.getman.core.model.BodyType;
import com.getman.core.model.HttpMethod;
import com.getman.core.model.KeyValue;
import com.getman.core.model.RequestModel;
import com.getman.core.model.ResponseModel;
import com.getman.core.variables.VariableResolver;
import com.getman.ui.widgets.AuthPanel;
import com.getman.ui.widgets.KeyValueTable;
import com.getman.ui.widgets.ResponsePanel;

/** Content of one request tab: request editor on top, its response below. */
class RequestTab extends Composite {
	private final RequestModel model;
	private final HttpService http;
	private final Runnable onChange;
	private final Consumer<CurlImporter.Result> onCurlPasted;
	private final Supplier<VariableScope> scope;
	private final Label variableHint;

	private final Button sendButton;
	private final CTabItem paramsTab;
	private final CTabItem authTab;
	private final CTabItem headersTab;
	private final Label bodyError;
	private final ResponsePanel responsePanel;
	private CompletableFuture<ResponseModel> inFlight;

	/**
	 * @param onCurlPasted called (asynchronously) when a cURL command is pasted into the URL field; the receiver
	 *                     replaces this tab's request with the parsed one
	 */
	RequestTab(Composite parent, RequestModel model, HttpService http, Runnable onChangeListener,
			Consumer<CurlImporter.Result> onCurlPasted, Supplier<VariableScope> scope) {
		super(parent, SWT.NONE);
		this.model = model;
		this.http = http;
		// Every edit also refreshes the unknown-variables hint.
		this.onChange = () -> {
			updateVariableHint();
			onChangeListener.run();
		};
		this.onCurlPasted = onCurlPasted;
		this.scope = scope;
		setLayout(new FillLayout());

		SashForm sash = new SashForm(this, SWT.VERTICAL);
		Composite editor = new Composite(sash, SWT.NONE);
		editor.setLayout(new GridLayout(1, false));

		// --- method + URL + send -------------------------------------------------------------
		Composite urlRow = new Composite(editor, SWT.NONE);
		urlRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		GridLayout urlLayout = new GridLayout(3, false);
		urlLayout.marginWidth = 0;
		urlLayout.marginHeight = 0;
		urlRow.setLayout(urlLayout);

		Combo methodCombo = new Combo(urlRow, SWT.READ_ONLY);
		for (HttpMethod m : HttpMethod.values()) {
			methodCombo.add(m.name());
		}
		methodCombo.select(model.getMethod().ordinal());
		methodCombo.addListener(SWT.Selection, e -> {
			model.setMethod(HttpMethod.values()[methodCombo.getSelectionIndex()]);
			onChange.run();
		});

		Text urlText = new Text(urlRow, SWT.SINGLE | SWT.BORDER);
		urlText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		urlText.setMessage("https://api.example.com/users  — or paste a cURL command");
		urlText.setText(model.getUrl());
		// Pasting a whole cURL command into the URL field imports it instead (typing never triggers this:
		// Verify carries one character per keystroke).
		urlText.addListener(SWT.Verify, e -> {
			if (e.text.length() < 8 || !CurlImporter.looksLikeCurl(e.text)) {
				return;
			}
			e.doit = false;
			// A single-line field may receive only the first line of a multi-line paste; the clipboard has it all.
			String command = e.text;
			String clip = clipboardText();
			if (CurlImporter.looksLikeCurl(clip) && clip.strip().startsWith(firstLine(e.text.strip()))) {
				command = clip;
			}
			try {
				CurlImporter.Result result = CurlImporter.parse(command);
				getDisplay().asyncExec(() -> {
					if (!isDisposed()) {
						onCurlPasted.accept(result);
					}
				});
			} catch (CurlParseException ex) {
				getDisplay().asyncExec(() -> MessageDialog.openError(getShell(), "Paste cURL Command",
						"Could not import the pasted cURL command:\n" + ex.getMessage()));
			}
		});
		urlText.addListener(SWT.Modify, e -> {
			model.setUrl(urlText.getText());
			onChange.run();
		});
		// Enter in the URL field sends, but never cancels: with Ctrl+Enter both this and the
		// shortcut listener may fire, and the second must not abort the request just started.
		urlText.addListener(SWT.DefaultSelection, e -> {
			if (inFlight == null) {
				sendOrCancel();
			}
		});

		sendButton = new Button(urlRow, SWT.PUSH);
		sendButton.setText("Send");
		sendButton.setToolTipText("Send request (Ctrl+Enter)");
		GridData sendData = new GridData(SWT.FILL, SWT.CENTER, false, false);
		sendData.widthHint = 90;
		sendButton.setLayoutData(sendData);
		sendButton.addListener(SWT.Selection, e -> sendOrCancel());

		variableHint = new Label(editor, SWT.WRAP);
		variableHint.setForeground(getDisplay().getSystemColor(SWT.COLOR_DARK_RED));
		GridData hintData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		hintData.exclude = true;
		variableHint.setLayoutData(hintData);
		variableHint.setVisible(false);

		// --- params / headers / body ---------------------------------------------------------
		CTabFolder sections = new CTabFolder(editor, SWT.BORDER | SWT.FLAT);
		sections.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		paramsTab = new CTabItem(sections, SWT.NONE);
		paramsTab.setControl(new KeyValueTable(sections, model.getParams(), "Parameter", this::sectionChanged));
		authTab = new CTabItem(sections, SWT.NONE);
		authTab.setControl(new AuthPanel(sections, model.getAuth(), this::sectionChanged));
		headersTab = new CTabItem(sections, SWT.NONE);
		headersTab.setControl(new KeyValueTable(sections, model.getHeaders(), "Header", this::sectionChanged));

		CTabItem bodyTab = new CTabItem(sections, SWT.NONE);
		bodyTab.setText("Body");
		Composite bodyArea = new Composite(sections, SWT.NONE);
		bodyArea.setLayout(new GridLayout(3, false));
		Combo bodyTypeCombo = new Combo(bodyArea, SWT.READ_ONLY);
		for (BodyType t : BodyType.values()) {
			bodyTypeCombo.add(t.label());
		}
		bodyTypeCombo.select(model.getBodyType().ordinal());
		Button formatButton = new Button(bodyArea, SWT.PUSH);
		formatButton.setText("Format JSON");
		bodyError = new Label(bodyArea, SWT.NONE);
		bodyError.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Text bodyText = new Text(bodyArea, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		bodyText.setFont(JFaceResources.getTextFont());
		bodyText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));
		bodyText.setText(model.getBody());
		bodyText.addListener(SWT.Modify, e -> {
			model.setBody(bodyText.getText());
			bodyError.setText("");
			onChange.run();
		});
		Runnable syncBodyControls = () -> {
			boolean hasBody = model.getBodyType() != BodyType.NONE;
			bodyText.setEnabled(hasBody);
			formatButton.setEnabled(model.getBodyType() == BodyType.JSON);
			bodyText.setMessage(hasBody ? "" : "Choose a body type to send a body.");
		};
		bodyTypeCombo.addListener(SWT.Selection, e -> {
			model.setBodyType(BodyType.values()[bodyTypeCombo.getSelectionIndex()]);
			syncBodyControls.run();
			onChange.run();
		});
		formatButton.addListener(SWT.Selection, e -> {
			try {
				bodyText.setText(Json.prettify(model.getBody()));
				bodyError.setText("");
			} catch (JsonException ex) {
				bodyError.setText(ex.getMessage());
			}
		});
		syncBodyControls.run();
		bodyTab.setControl(bodyArea);

		updateSectionTitles();
		sections.setSelection(model.getBodyType() != BodyType.NONE ? bodyTab : paramsTab);

		// --- response --------------------------------------------------------------------------
		responsePanel = new ResponsePanel(sash);
		sash.setWeights(new int[] { 45, 55 });

		updateVariableHint();
		installSendShortcut(this);
	}

	private String clipboardText() {
		Clipboard clipboard = new Clipboard(getDisplay());
		try {
			Object contents = clipboard.getContents(TextTransfer.getInstance());
			return contents instanceof String s ? s : null;
		} finally {
			clipboard.dispose();
		}
	}

	private static String firstLine(String s) {
		int nl = s.indexOf('\n');
		int cr = s.indexOf('\r');
		int end = nl < 0 ? cr : (cr < 0 ? nl : Math.min(nl, cr));
		return end < 0 ? s : s.substring(0, end);
	}

	/**
	 * Shows "Unknown variables: …" under the URL when the parts that will be sent use {{names}} the active
	 * environment does not define. Called on every edit and when the environment changes.
	 */
	void refreshVariableHint() {
		updateVariableHint();
	}

	private void updateVariableHint() {
		if (variableHint == null || variableHint.isDisposed()) {
			return;
		}
		VariableScope s = scope.get();
		Set<String> unknown = VariableResolver.unresolvedIn(model, s.values());
		String text = "";
		if (!unknown.isEmpty()) {
			String names = unknown.stream().map(n -> "{{" + n + "}}").collect(Collectors.joining(", "));
			text = (unknown.size() == 1 ? "Unknown variable " : "Unknown variables ") + names
					+ (s.environmentName().isEmpty() ? ": no environment is selected in the GetMan toolbar."
							: ": not defined in environment \"" + s.environmentName() + "\".");
		}
		boolean show = !text.isEmpty();
		if (show == variableHint.getVisible() && text.equals(variableHint.getText())) {
			return;
		}
		variableHint.setText(text);
		variableHint.setVisible(show);
		((GridData) variableHint.getLayoutData()).exclude = !show;
		variableHint.getParent().layout(true);
	}

	RequestModel getModel() {
		return model;
	}

	void sendOrCancel() {
		if (inFlight != null) {
			inFlight.cancel(true);
			return;
		}
		responsePanel.showLoading();
		sendButton.setText("Cancel");
		CompletableFuture<ResponseModel> future = http.send(new VariableResolver(scope.get().values()).resolve(model));
		inFlight = future;
		future.whenComplete((response, error) -> {
			if (isDisposed()) {
				return;
			}
			getDisplay().asyncExec(() -> {
				if (isDisposed()) {
					return;
				}
				if (inFlight == future) {
					inFlight = null;
					sendButton.setText("Send");
				}
				if (error instanceof CancellationException) {
					responsePanel.showCancelled();
				} else if (error != null) {
					responsePanel.showResponse(ResponseModel.failure(String.valueOf(error.getMessage()), 0));
				} else {
					responsePanel.showResponse(response);
				}
			});
		});
	}

	@Override
	public void dispose() {
		if (inFlight != null) {
			inFlight.cancel(true);
		}
		super.dispose();
	}

	private void sectionChanged() {
		updateSectionTitles();
		onChange.run();
	}

	private void updateSectionTitles() {
		paramsTab.setText(withCount("Params", model.getParams().stream().filter(KeyValue::isActive).count()));
		headersTab.setText(withCount("Headers", model.getHeaders().stream().filter(KeyValue::isActive).count()));
		AuthType auth = model.getAuth().getType();
		authTab.setText(auth == AuthType.NONE ? "Auth" : "Auth (" + (auth == AuthType.BEARER ? "Bearer" : "Basic") + ")");
	}

	private static String withCount(String label, long count) {
		return count == 0 ? label : label + " (" + count + ")";
	}

	/** Ctrl+Enter (Cmd+Enter on macOS) anywhere in this tab sends the request. */
	private void installSendShortcut(Control control) {
		Listener listener = (Event e) -> {
			if ((e.stateMask & SWT.MOD1) != 0 && (e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR)) {
				e.doit = false;
				sendOrCancel();
			}
		};
		addKeyListenerRecursively(control, listener);
	}

	private static void addKeyListenerRecursively(Control control, Listener listener) {
		control.addListener(SWT.KeyDown, listener);
		if (control instanceof Composite composite) {
			for (Control child : composite.getChildren()) {
				addKeyListenerRecursively(child, listener);
			}
		}
	}
}
