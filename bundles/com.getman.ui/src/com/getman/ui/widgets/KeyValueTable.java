package com.getman.ui.widgets;

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
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import com.getman.core.model.KeyValue;

/**
 * Editable key/value table bound to a live {@code List<KeyValue>}. The checkbox enables or disables a row. The
 * last row is always an empty placeholder: typing into it adds a new row, as in Postman. Click a cell to edit it;
 * Enter or Tab commits, Esc cancels, Delete removes the selected rows.
 */
public class KeyValueTable extends Composite {
	private static final int KEY_COL = 0;
	private static final int VALUE_COL = 1;

	private final List<KeyValue> rows;
	private final Runnable onChange;
	private final Table table;
	private final TableEditor editor;
	private Text activeEditor;

	public KeyValueTable(Composite parent, List<KeyValue> rows, String keyLabel, Runnable onChange) {
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
		TableColumn keyColumn = new TableColumn(table, SWT.NONE);
		keyColumn.setText(keyLabel);
		keyColumn.setWidth(200);
		TableColumn valueColumn = new TableColumn(table, SWT.NONE);
		valueColumn.setText("Value");
		valueColumn.setWidth(400);

		Composite buttons = new Composite(this, SWT.NONE);
		GridLayout buttonLayout = new GridLayout(1, false);
		buttonLayout.marginWidth = 0;
		buttonLayout.marginHeight = 0;
		buttons.setLayout(buttonLayout);
		buttons.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
		Button remove = new Button(buttons, SWT.PUSH);
		remove.setText("Remove");
		remove.setToolTipText("Remove selected rows (Delete)");
		remove.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		remove.addListener(SWT.Selection, e -> removeSelected());

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
			int width = table.getClientArea().width - keyColumn.getWidth();
			if (width > 100) {
				valueColumn.setWidth(width);
			}
		});

		refresh();
	}

	/** Rebuilds the table from the bound list. */
	public void refresh() {
		closeEditor(false);
		table.removeAll();
		for (KeyValue kv : rows) {
			TableItem item = new TableItem(table, SWT.NONE);
			item.setText(new String[] { kv.getKey(), kv.getValue() });
			item.setChecked(kv.isEnabled());
			if (!kv.isEnabled()) {
				item.setForeground(getDisplay().getSystemColor(SWT.COLOR_WIDGET_DISABLED_FOREGROUND));
			}
		}
		TableItem placeholder = new TableItem(table, SWT.NONE);
		placeholder.setText(new String[] { "", "" });
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
			item.setChecked(false); // nothing to enable yet
			return;
		}
		rows.get(index).setEnabled(item.getChecked());
		item.setForeground(item.getChecked() ? null
				: getDisplay().getSystemColor(SWT.COLOR_WIDGET_DISABLED_FOREGROUND));
		onChange.run();
	}

	private void onMouseDown(Event e) {
		if (e.button != 1) {
			return;
		}
		Point pt = new Point(e.x, e.y);
		for (int i = table.getTopIndex(); i < table.getItemCount(); i++) {
			TableItem item = table.getItem(i);
			for (int col = KEY_COL; col <= VALUE_COL; col++) {
				Rectangle bounds = item.getBounds(col);
				if (bounds.contains(pt)) {
					// Leave clicks on the checkbox (left of the key cell's text area) to the table.
					if (col == KEY_COL && e.x < item.getTextBounds(KEY_COL).x) {
						return;
					}
					openEditor(i, col);
					return;
				}
			}
		}
	}

	private void openEditor(int rowIndex, int column) {
		closeEditor(true);
		TableItem item = table.getItem(rowIndex);
		Text text = new Text(table, SWT.SINGLE);
		text.setText(item.getText(column));
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
			case SWT.TRAVERSE_TAB_PREVIOUS -> {
				e.doit = false;
				int row = indexOfEditedRow();
				closeEditor(true);
				if (column == VALUE_COL) {
					openEditor(row, KEY_COL);
				} else if (row > 0) {
					openEditor(row - 1, VALUE_COL);
				}
			}
			default -> {
				// keep default behaviour
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
		activeEditor = null; // guard against re-entry from FocusOut during dispose
		TableItem item = editor.getItem();
		int column = editor.getColumn();
		String value = text.getText();
		editor.setEditor(null);
		text.dispose();
		if (!commit || item == null || item.isDisposed()) {
			return;
		}
		int index = table.indexOf(item);
		if (isPlaceholder(index)) {
			if (value.isEmpty()) {
				return;
			}
			KeyValue kv = new KeyValue();
			rows.add(kv);
			applyCell(kv, column, value);
			refresh();
			table.setSelection(index);
		} else {
			KeyValue kv = rows.get(index);
			String old = column == KEY_COL ? kv.getKey() : kv.getValue();
			if (old.equals(value)) {
				return;
			}
			applyCell(kv, column, value);
			item.setText(column, value);
		}
		onChange.run();
	}

	private static void applyCell(KeyValue kv, int column, String value) {
		if (column == KEY_COL) {
			kv.setKey(value);
		} else {
			kv.setValue(value);
		}
	}

	private void removeSelected() {
		closeEditor(true);
		int[] selected = table.getSelectionIndices();
		java.util.Arrays.sort(selected);
		boolean changed = false;
		for (int i = selected.length - 1; i >= 0; i--) {
			int index = selected[i];
			if (!isPlaceholder(index)) {
				rows.remove(index);
				changed = true;
			}
		}
		if (changed) {
			refresh();
			onChange.run();
		}
	}
}
