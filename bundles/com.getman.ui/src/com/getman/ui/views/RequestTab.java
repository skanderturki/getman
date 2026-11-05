package com.getman.ui.views;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.SashForm;
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

import com.getman.core.http.HttpService;
import com.getman.core.json.Json;
import com.getman.core.json.JsonException;
import com.getman.core.model.BodyType;
import com.getman.core.model.HttpMethod;
import com.getman.core.model.KeyValue;
import com.getman.core.model.RequestModel;
import com.getman.core.model.ResponseModel;
import com.getman.ui.widgets.KeyValueTable;
import com.getman.ui.widgets.ResponsePanel;

/** Content of one request tab: request editor on top, its response below. */
class RequestTab extends Composite {
	private final RequestModel model;
	private final HttpService http;
	private final Runnable onChange;

	private final Button sendButton;
	private final CTabItem paramsTab;
	private final CTabItem headersTab;
	private final Label bodyError;
	private final ResponsePanel responsePanel;
	private CompletableFuture<ResponseModel> inFlight;

	RequestTab(Composite parent, RequestModel model, HttpService http, Runnable onChange) {
		super(parent, SWT.NONE);
		this.model = model;
		this.http = http;
		this.onChange = onChange;
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
		urlText.setMessage("https://api.example.com/users");
		urlText.setText(model.getUrl());
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

		// --- params / headers / body ---------------------------------------------------------
		CTabFolder sections = new CTabFolder(editor, SWT.BORDER | SWT.FLAT);
		sections.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		paramsTab = new CTabItem(sections, SWT.NONE);
		paramsTab.setControl(new KeyValueTable(sections, model.getParams(), "Parameter", this::sectionChanged));
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

		installSendShortcut(this);
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
		CompletableFuture<ResponseModel> future = http.send(model);
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
