package com.getman.ui.views;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.getman.core.curl.CurlImporter;
import com.getman.core.curl.CurlParseException;

/** Asks for a cURL command and parses it; prefilled from the clipboard when it holds one. */
class CurlImportDialog extends Dialog {
	private Text commandText;
	private Label errorLabel;
	private CurlImporter.Result result;

	CurlImportDialog(Shell parent) {
		super(parent);
	}

	/** The parsed command after OK, else {@code null}. */
	CurlImporter.Result getResult() {
		return result;
	}

	@Override
	protected boolean isResizable() {
		return true;
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText("Import cURL Command");
	}

	@Override
	protected Point getInitialSize() {
		return new Point(640, 380);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite area = (Composite) super.createDialogArea(parent);
		Label intro = new Label(area, SWT.WRAP);
		intro.setText("Paste a cURL command, for example from your browser's developer tools "
				+ "(Network › right-click a request › Copy › Copy as cURL) or from API documentation.\n"
				+ "Tip: you can also paste it straight into a request's URL field.");
		GridData introData = new GridData(SWT.FILL, SWT.TOP, true, false);
		introData.widthHint = 560;
		intro.setLayoutData(introData);

		commandText = new Text(area, SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
		commandText.setFont(JFaceResources.getTextFont());
		commandText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		commandText.setMessage("curl https://api.example.com/users -H 'Accept: application/json'");
		String clip = clipboardText();
		if (CurlImporter.looksLikeCurl(clip)) {
			commandText.setText(clip.strip());
			commandText.selectAll();
		}
		commandText.addListener(SWT.Modify, e -> errorLabel.setText(""));

		errorLabel = new Label(area, SWT.WRAP);
		errorLabel.setForeground(area.getDisplay().getSystemColor(SWT.COLOR_RED));
		errorLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		return area;
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, "Import", true);
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
	}

	@Override
	protected void okPressed() {
		try {
			result = CurlImporter.parse(commandText.getText());
		} catch (CurlParseException e) {
			errorLabel.setText(e.getMessage());
			errorLabel.getParent().layout(true);
			return;
		}
		super.okPressed();
	}

	private String clipboardText() {
		Clipboard clipboard = new Clipboard(getParentShell().getDisplay());
		try {
			Object contents = clipboard.getContents(TextTransfer.getInstance());
			return contents instanceof String s ? s : null;
		} finally {
			clipboard.dispose();
		}
	}
}
