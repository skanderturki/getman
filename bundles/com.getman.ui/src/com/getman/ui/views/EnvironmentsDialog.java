package com.getman.ui.views;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import com.getman.core.model.Environment;
import com.getman.ui.widgets.KeyValueTable;

/**
 * Edits the list of environments and their variables. Works on copies; the caller takes {@link #getResult()}
 * only after OK, so Cancel discards everything.
 */
class EnvironmentsDialog extends Dialog {
	private final List<Environment> working = new ArrayList<>();
	private final String initialId;
	private org.eclipse.swt.widgets.List list;
	private Composite tableHolder;
	private Label tableTitle;
	private Button duplicateButton;
	private Button renameButton;
	private Button deleteButton;

	EnvironmentsDialog(Shell parent, List<Environment> environments, String selectedId) {
		super(parent);
		environments.forEach(e -> working.add(e.copy()));
		this.initialId = selectedId;
	}

	/** The edited environments, after OK. */
	List<Environment> getResult() {
		return working;
	}

	@Override
	protected boolean isResizable() {
		return true;
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText("Manage Environments");
	}

	@Override
	protected Point getInitialSize() {
		return new Point(820, 480);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite area = (Composite) super.createDialogArea(parent);
		Label intro = new Label(area, SWT.WRAP);
		intro.setText("Use {{name}} in a request's URL, params, headers, body or auth, and pick an environment in "
				+ "the GetMan toolbar to fill it in. Values may use other variables, e.g. base = "
				+ "https://{{host}}:{{port}}. Click a variable's Secret cell to mask it and keep its value in "
				+ "Eclipse secure storage.");
		GridData introData = new GridData(SWT.FILL, SWT.TOP, true, false);
		introData.widthHint = 760;
		intro.setLayoutData(introData);

		SashForm sash = new SashForm(area, SWT.HORIZONTAL);
		sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		Composite left = new Composite(sash, SWT.NONE);
		left.setLayout(new GridLayout(1, false));
		list = new org.eclipse.swt.widgets.List(left, SWT.SINGLE | SWT.BORDER | SWT.V_SCROLL);
		list.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		list.addListener(SWT.Selection, e -> showSelected());
		list.addListener(SWT.DefaultSelection, e -> rename());
		Composite buttons = new Composite(left, SWT.NONE);
		buttons.setLayout(new GridLayout(2, true));
		buttons.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		button(buttons, "Add…", this::add);
		duplicateButton = button(buttons, "Duplicate", this::duplicate);
		renameButton = button(buttons, "Rename…", this::rename);
		deleteButton = button(buttons, "Delete", this::delete);

		Composite right = new Composite(sash, SWT.NONE);
		right.setLayout(new GridLayout(1, false));
		tableTitle = new Label(right, SWT.NONE);
		tableTitle.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		tableHolder = new Composite(right, SWT.NONE);
		tableHolder.setLayout(new FillLayout());
		tableHolder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		sash.setWeights(new int[] { 28, 72 });

		refreshList();
		int initial = indexOf(initialId);
		list.select(initial >= 0 ? initial : 0);
		showSelected();
		return area;
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
	}

	private static Button button(Composite parent, String text, Runnable action) {
		Button b = new Button(parent, SWT.PUSH);
		b.setText(text);
		b.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		b.addListener(SWT.Selection, e -> action.run());
		return b;
	}

	private int indexOf(String id) {
		for (int i = 0; i < working.size(); i++) {
			if (working.get(i).getId().equals(id)) {
				return i;
			}
		}
		return -1;
	}

	private Environment selected() {
		int i = list.getSelectionIndex();
		return i >= 0 && i < working.size() ? working.get(i) : null;
	}

	private void refreshList() {
		int keep = list.getSelectionIndex();
		list.removeAll();
		for (Environment e : working) {
			list.add(e.getName().isBlank() ? "Untitled" : e.getName());
		}
		if (keep >= 0 && keep < working.size()) {
			list.select(keep);
		}
	}

	private void showSelected() {
		for (Control c : tableHolder.getChildren()) {
			c.dispose();
		}
		Environment env = selected();
		boolean has = env != null;
		duplicateButton.setEnabled(has);
		renameButton.setEnabled(has);
		deleteButton.setEnabled(has);
		if (has) {
			tableTitle.setText("Variables of \"" + env.getName() + "\"");
			new KeyValueTable(tableHolder, env.getVariables(), "Variable", () -> {
			}, true);
		} else {
			tableTitle.setText("No environments yet: click Add… to create one (e.g. Local, Staging, Production).");
		}
		tableHolder.layout(true, true);
		tableTitle.getParent().layout(true);
	}

	private String askName(String title, String initial) {
		InputDialog d = new InputDialog(getShell(), title, "Environment name:", initial, text -> {
			if (text.isBlank()) {
				return "Name cannot be empty.";
			}
			return null;
		});
		return d.open() == Window.OK ? d.getValue().trim() : null;
	}

	private void add() {
		String name = askName("Add Environment", working.isEmpty() ? "Local" : "Environment " + (working.size() + 1));
		if (name != null) {
			working.add(new Environment(name));
			refreshList();
			list.select(working.size() - 1);
			showSelected();
		}
	}

	private void duplicate() {
		Environment env = selected();
		if (env == null) {
			return;
		}
		String name = askName("Duplicate Environment", env.getName() + " (copy)");
		if (name != null) {
			Environment copy = env.copy();
			copy.setId(null); // fresh id
			copy.setName(name);
			working.add(list.getSelectionIndex() + 1, copy);
			int at = list.getSelectionIndex() + 1;
			refreshList();
			list.select(at);
			showSelected();
		}
	}

	private void rename() {
		Environment env = selected();
		if (env == null) {
			return;
		}
		String name = askName("Rename Environment", env.getName());
		if (name != null) {
			env.setName(name);
			refreshList();
			showSelected();
		}
	}

	private void delete() {
		Environment env = selected();
		if (env == null || !MessageDialog.openConfirm(getShell(), "Delete Environment",
				"Delete environment \"" + env.getName() + "\" and its " + env.getVariables().size() + " variable(s)?")) {
			return;
		}
		int i = list.getSelectionIndex();
		working.remove(env);
		refreshList();
		if (!working.isEmpty()) {
			list.select(Math.min(i, working.size() - 1));
		}
		showSelected();
	}
}
