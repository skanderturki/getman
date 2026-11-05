package com.getman.ui.views;

import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import com.getman.core.model.KeyValue;
import com.getman.ui.widgets.KeyValueTable;

/** Edits a list of variables in place; the caller passes a copy and keeps it only after OK. */
class VariablesDialog extends Dialog {
	private final String title;
	private final String message;
	private final List<KeyValue> variables;

	VariablesDialog(Shell parent, String title, String message, List<KeyValue> variables) {
		super(parent);
		this.title = title;
		this.message = message;
		this.variables = variables;
	}

	@Override
	protected boolean isResizable() {
		return true;
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText(title);
	}

	@Override
	protected Point getInitialSize() {
		return new Point(620, 420);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite area = (Composite) super.createDialogArea(parent);
		Label intro = new Label(area, SWT.WRAP);
		intro.setText(message);
		GridData introData = new GridData(SWT.FILL, SWT.TOP, true, false);
		introData.widthHint = 560;
		intro.setLayoutData(introData);
		KeyValueTable table = new KeyValueTable(area, variables, "Variable", () -> {
		});
		table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		return area;
	}
}
