package com.getman.ui.widgets;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import com.getman.core.model.FormField;

/**
 * Editable multipart form table bound to a live {@code List<FormField>}: checkbox, Key, Value (text or file
 * path) and Type. Click the Type cell to switch a row between Text and File; "Add File…" and "Choose File…" pick
 * files from disk. Like {@link KeyValueTable}, the last row is an empty placeholder for adding a text field.
 */
public class FormDataTable extends Composite {
	private static final int KEY_COL = 0;
	private static final int VALUE_COL = 1;
	private static final int TYPE_COL = 2;

	private final List<FormField> rows;
	private final Runnable onChange;
	private final Table table;
	private final TableEditor editor;
	private Text activeEditor;

	public FormDataTable(Composite parent, List<FormField> rows, Runnable onChange) {
		super(parent, SWT.NONE);
		this.rows = rows;
		this.onChange = onChange;
		GridLayout layout = new GridLayout(2, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		setLayout(layout);

		table = new Table(this, SWT.CHECK | SWT.FULL_SELECTION | SWT.MULTI | SWT.BORDER);
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		TableColumn keyColumn = column("Key", 180);
		TableColumn valueColumn = column("Value (text, or file path)", 360);
		TableColumn typeColumn = column("Type", 60);
		typeColumn.setToolTipText("Click to switch between Text and File");

		Composite buttons = new Composite(this, SWT.NONE);
		GridLayout buttonLayout = new GridLayout(1, false);
		buttonLayout.marginWidth = 0;
		buttonLayout.marginHeight = 0;
		buttons.setLayout(buttonLayout);
		buttons.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
		button(buttons, "Add File…", "Add a file field", this::addFile);
		button(buttons, "Choose File…", "Pick the file for the selected row", this::chooseFileForSelection);
		button(buttons, "Remove", "Remove selected rows (Delete)", this::removeSelected);

		editor = new TableEditor(table);
		editor.horizontalAlignment = SWT.LEFT;
		editor.grabHorizontal = true;

		table.addListener(SWT.Selection, this::onSelection);
		table.addListener(SWT.MouseDown, this::onMouseDown);
		table.addListener(SWT.KeyDown, e -> {
			if (e.keyCode == SWT.DEL) {
				removeSelected();
			}
		});
		table.addListener(SWT.Resize, e -> {
			int width = table.getClientArea().width - keyColumn.getWidth() - typeColumn.getWidth();
			if (width > 100) {
				valueColumn.setWidth(width);
			}
		});
		refresh();
	}

	private TableColumn column(String text, int width) {
		TableColumn c = new TableColumn(table, SWT.NONE);
		c.setText(text);
		c.setWidth(width);
		return c;
	}

	private static void button(Composite parent, String text, String tooltip, Runnable action) {
		Button b = new Button(parent, SWT.PUSH);
		b.setText(text);
		b.setToolTipText(tooltip);
		b.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		b.addListener(SWT.Selection, e -> action.run());
	}

	/** Rebuilds the table from the bound list. */
	public void refresh() {
		closeEditor(false);
		table.removeAll();
		for (FormField f : rows) {
			TableItem item = new TableItem(table, SWT.NONE);
			item.setText(new String[] { f.getKey(), f.getValue(), f.isFile() ? "File" : "Text" });
			item.setChecked(f.isEnabled());
			if (!f.isEnabled()) {
				item.setForeground(getDisplay().getSystemColor(SWT.COLOR_WIDGET_DISABLED_FOREGROUND));
			} else if (f.isFile() && f.getValue().isBlank()) {
				item.setForeground(VALUE_COL, getDisplay().getSystemColor(SWT.COLOR_DARK_RED));
				item.setText(VALUE_COL, "(choose a file)");
			}
		}
		TableItem placeholder = new TableItem(table, SWT.NONE);
		placeholder.setText(new String[] { "", "", "" });
		placeholder.setGrayed(true);
	}

	private boolean isPlaceholder(int index) {
		return index >= rows.size();
	}

	private void onSelection(Event e) {
		if (e.detail != SWT.CHECK) {
			return;
		}
		TableItem item = (TableItem) e.item;
		int index = table.indexOf(item);
		if (isPlaceholder(index)) {
			item.setChecked(false);
			return;
		}
		rows.get(index).setEnabled(item.getChecked());
		refresh();
		onChange.run();
	}

	private void onMouseDown(Event e) {
		if (e.button != 1) {
			return;
		}
		Point pt = new Point(e.x, e.y);
		for (int i = table.getTopIndex(); i < table.getItemCount(); i++) {
			TableItem item = table.getItem(i);
			for (int col = KEY_COL; col <= TYPE_COL; col++) {
				Rectangle bounds = item.getBounds(col);
				if (!bounds.contains(pt)) {
					continue;
				}
				if (col == KEY_COL && e.x < item.getTextBounds(KEY_COL).x) {
					return; // the checkbox
				}
				if (col == TYPE_COL) {
					toggleType(i);
				} else if (col == VALUE_COL && !isPlaceholder(i) && rows.get(i).isFile()
						&& rows.get(i).getValue().isBlank()) {
					chooseFile(i);
				} else {
					openEditor(i, col);
				}
				return;
			}
		}
	}

	private void toggleType(int index) {
		closeEditor(true);
		if (isPlaceholder(index)) {
			return;
		}
		FormField f = rows.get(index);
		f.setFile(!f.isFile());
		if (f.isFile()) {
			f.setValue(""); // text is not a path: ask for the file next
			refresh();
			chooseFile(index);
		} else {
			refresh();
		}
		onChange.run();
	}

	private void addFile() {
		closeEditor(true);
		String path = askFile();
		if (path == null) {
			return;
		}
		String name = Path.of(path).getFileName().toString();
		int dot = name.lastIndexOf('.');
		rows.add(FormField.file(dot > 0 ? name.substring(0, dot) : name, path));
		refresh();
		onChange.run();
	}

	private void chooseFileForSelection() {
		int index = table.getSelectionIndex();
		if (index >= 0 && !isPlaceholder(index)) {
			chooseFile(index);
		} else {
			addFile();
		}
	}

	private void chooseFile(int index) {
		String path = askFile();
		if (path == null) {
			return;
		}
		FormField f = rows.get(index);
		f.setFile(true);
		f.setValue(path);
		refresh();
		table.setSelection(index);
		onChange.run();
	}

	private String askFile() {
		FileDialog dialog = new FileDialog(getShell(), SWT.OPEN);
		dialog.setText("Choose a File to Upload");
		return dialog.open();
	}

	private void openEditor(int rowIndex, int column) {
		closeEditor(true);
		TableItem item = table.getItem(rowIndex);
		Text text = new Text(table, SWT.SINGLE);
		text.setText(isPlaceholder(rowIndex) ? "" : column == KEY_COL ? rows.get(rowIndex).getKey()
				: rows.get(rowIndex).getValue());
		text.selectAll();
		activeEditor = text;
		editor.setEditor(text, item, column);
		text.setFocus();
		text.addListener(SWT.Traverse, e -> {
			switch (e.detail) {
			case SWT.TRAVERSE_RETURN -> {
				e.doit = false;
				closeEditor(true);
			}
			case SWT.TRAVERSE_ESCAPE -> {
				e.doit = false;
				closeEditor(false);
			}
			case SWT.TRAVERSE_TAB_NEXT -> {
				e.doit = false;
				int row = indexOfEditedRow();
				closeEditor(true);
				if (column == KEY_COL) {
					openEditor(row, VALUE_COL);
				} else if (row + 1 < table.getItemCount()) {
					openEditor(row + 1, KEY_COL);
				}
			}
			default -> {
			}
			}
		});
		text.addListener(SWT.FocusOut, e -> closeEditor(true));
	}

	private int indexOfEditedRow() {
		TableItem item = editor.getItem();
		return item == null || item.isDisposed() ? -1 : table.indexOf(item);
	}

	private void closeEditor(boolean commit) {
		Text text = activeEditor;
		if (text == null || text.isDisposed()) {
			activeEditor = null;
			return;
		}
		activeEditor = null;
		TableItem item = editor.getItem();
		int column = editor.getColumn();
		String value = text.getText();
		editor.setEditor(null);
		text.dispose();
		if (!commit || item == null || item.isDisposed()) {
			return;
		}
		int index = table.indexOf(item);
		FormField f;
		if (isPlaceholder(index)) {
			if (value.isEmpty()) {
				return;
			}
			f = new FormField();
			rows.add(f);
		} else {
			f = rows.get(index);
			String old = column == KEY_COL ? f.getKey() : f.getValue();
			if (old.equals(value)) {
				return;
			}
		}
		if (column == KEY_COL) {
			f.setKey(value);
		} else {
			f.setValue(value);
		}
		refresh();
		table.setSelection(index);
		onChange.run();
	}

	private void removeSelected() {
		closeEditor(true);
		int[] selected = table.getSelectionIndices();
		Arrays.sort(selected);
		boolean changed = false;
		for (int i = selected.length - 1; i >= 0; i--) {
			if (!isPlaceholder(selected[i])) {
				rows.remove(selected[i]);
				changed = true;
			}
		}
		if (changed) {
			refresh();
			onChange.run();
		}
	}
}
