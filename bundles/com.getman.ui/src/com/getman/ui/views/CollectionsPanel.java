package com.getman.ui.views;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

import com.getman.core.json.JsonException;
import com.getman.core.model.RequestCollection;
import com.getman.core.model.RequestModel;
import com.getman.core.store.CollectionFile;

/** Tree of collections and their saved requests, shown on the left of the GetMan view. */
class CollectionsPanel extends Composite {

	/** What the panel needs from the view. */
	interface Host {
		void openRequest(RequestCollection collection, RequestModel saved);

		/** A collection or saved request was added, renamed, reordered or removed. */
		void collectionsChanged();

		/** Open tabs linked to these saved requests are no longer in a collection. */
		void unlink(String collectionId, String requestIdOrNull);

		/** A saved request was renamed in the tree; open tabs of it should follow. */
		void savedRequestRenamed(RequestModel saved);

		void showStatus(String message);
	}

	private final List<RequestCollection> collections;
	private final Host host;
	private final TreeViewer viewer;
	private final Label emptyHint;

	CollectionsPanel(Composite parent, List<RequestCollection> collections, Host host) {
		super(parent, SWT.NONE);
		this.collections = collections;
		this.host = host;
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		setLayout(layout);

		Label title = new Label(this, SWT.NONE);
		title.setText("Collections");
		title.setFont(JFaceResources.getBannerFont());
		GridData titleData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		titleData.horizontalIndent = 4;
		title.setLayoutData(titleData);

		emptyHint = new Label(this, SWT.WRAP);
		emptyHint.setText("No collections yet.\n\nPress Ctrl+S in a request tab to save it to a collection, "
				+ "or right-click here to create or import one.");
		GridData hintData = new GridData(SWT.FILL, SWT.TOP, true, false);
		hintData.horizontalIndent = 4;
		hintData.widthHint = 150;
		emptyHint.setLayoutData(hintData);

		viewer = new TreeViewer(this, SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL);
		viewer.getTree().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		viewer.setContentProvider(new ContentProvider());
		viewer.setLabelProvider(new DelegatingStyledCellLabelProvider(new Labels()));
		viewer.setInput(collections);
		viewer.addOpenListener(e -> openSelection());
		viewer.getTree().addListener(SWT.KeyDown, e -> {
			if (e.keyCode == SWT.DEL) {
				deleteSelection();
			} else if (e.keyCode == SWT.F2) {
				renameSelection();
			}
		});
		// Clicking empty space clears the selection, so "New Collection" is not mistaken for an item action.
		viewer.getTree().addListener(SWT.MouseDown, e -> {
			if (viewer.getTree().getItem(new org.eclipse.swt.graphics.Point(e.x, e.y)) == null) {
				viewer.setSelection(StructuredSelection.EMPTY);
			}
		});
		installMenu();
		updateEmptyHint();
	}

	void refresh() {
		viewer.refresh();
		updateEmptyHint();
	}

	void reveal(RequestCollection collection, RequestModel saved) {
		refresh();
		viewer.setExpandedState(collection, true);
		viewer.setSelection(new StructuredSelection(saved != null ? saved : collection), true);
	}

	private void updateEmptyHint() {
		boolean empty = collections.isEmpty();
		((GridData) emptyHint.getLayoutData()).exclude = !empty;
		emptyHint.setVisible(empty);
		layout(true);
	}

	// --- actions -----------------------------------------------------------------------------

	RequestCollection newCollection() {
		InputDialog dialog = new InputDialog(getShell(), "New Collection", "Collection name:",
				"Collection " + (collections.size() + 1), text -> text.isBlank() ? "Name cannot be empty." : null);
		if (dialog.open() != Window.OK) {
			return null;
		}
		RequestCollection c = new RequestCollection(dialog.getValue().trim());
		collections.add(c);
		reveal(c, null);
		host.collectionsChanged();
		return c;
	}

	void importCollection() {
		FileDialog dialog = new FileDialog(getShell(), SWT.OPEN);
		dialog.setText("Import GetMan Collection");
		dialog.setFilterExtensions(new String[] { "*.json", "*.*" });
		String file = dialog.open();
		if (file == null) {
			return;
		}
		try {
			RequestCollection c = CollectionFile.read(Path.of(file));
			collections.add(c);
			reveal(c, null);
			host.collectionsChanged();
			host.showStatus("Imported " + c.getRequests().size() + " request(s) into \"" + c.getName() + "\".");
		} catch (IOException | JsonException e) {
			MessageDialog.openError(getShell(), "Import Collection", "Could not import " + file + ":\n" + e.getMessage());
		}
	}

	private void exportCollection(RequestCollection c) {
		FileDialog dialog = new FileDialog(getShell(), SWT.SAVE);
		dialog.setText("Export Collection");
		dialog.setFilterExtensions(new String[] { "*.json" });
		dialog.setFileName(c.getName().replaceAll("[\\\\/:*?\"<>|]", "_") + ".getman.json");
		dialog.setOverwrite(true);
		String file = dialog.open();
		if (file == null) {
			return;
		}
		try {
			CollectionFile.write(c, Path.of(file));
			host.showStatus("Exported \"" + c.getName() + "\" to " + file);
		} catch (IOException e) {
			MessageDialog.openError(getShell(), "Export Collection", "Could not write " + file + ":\n" + e.getMessage());
		}
	}

	private Object selected() {
		return viewer.getStructuredSelection().getFirstElement();
	}

	private RequestCollection collectionOf(RequestModel saved) {
		for (RequestCollection c : collections) {
			if (c.getRequests().contains(saved)) {
				return c;
			}
		}
		return null;
	}

	private void openSelection() {
		if (selected() instanceof RequestModel r) {
			RequestCollection c = collectionOf(r);
			if (c != null) {
				host.openRequest(c, r);
			}
		} else if (selected() instanceof RequestCollection c) {
			viewer.setExpandedState(c, !viewer.getExpandedState(c));
		}
	}

	private void renameSelection() {
		Object element = selected();
		if (element instanceof RequestCollection c) {
			String name = askName("Rename Collection", c.getName());
			if (name != null) {
				c.setName(name);
				viewer.refresh(c);
				host.collectionsChanged();
			}
		} else if (element instanceof RequestModel r) {
			String name = askName("Rename Request", r.getName());
			if (name != null) {
				r.setName(name);
				viewer.refresh(r);
				host.savedRequestRenamed(r);
				host.collectionsChanged();
			}
		}
	}

	private String askName(String title, String current) {
		InputDialog dialog = new InputDialog(getShell(), title, "Name:", current,
				text -> text.isBlank() ? "Name cannot be empty." : null);
		return dialog.open() == Window.OK ? dialog.getValue().trim() : null;
	}

	private void duplicate(RequestModel r) {
		RequestCollection c = collectionOf(r);
		if (c == null) {
			return;
		}
		RequestModel copy = r.duplicate();
		copy.setCollectionId(c.getId());
		c.getRequests().add(c.getRequests().indexOf(r) + 1, copy);
		reveal(c, copy);
		host.collectionsChanged();
	}

	private void move(RequestModel r, int delta) {
		RequestCollection c = collectionOf(r);
		if (c == null) {
			return;
		}
		List<RequestModel> list = c.getRequests();
		int from = list.indexOf(r);
		int to = from + delta;
		if (to < 0 || to >= list.size()) {
			return;
		}
		list.remove(from);
		list.add(to, r);
		reveal(c, r);
		host.collectionsChanged();
	}

	private void deleteSelection() {
		Object element = selected();
		if (element instanceof RequestCollection c) {
			if (MessageDialog.openConfirm(getShell(), "Delete Collection", "Delete collection \"" + c.getName()
					+ "\" and its " + c.getRequests().size() + " saved request(s)?\n\nOpen tabs stay open.")) {
				collections.remove(c);
				host.unlink(c.getId(), null);
				refresh();
				host.collectionsChanged();
			}
		} else if (element instanceof RequestModel r) {
			RequestCollection c = collectionOf(r);
			if (c != null && MessageDialog.openConfirm(getShell(), "Delete Request",
					"Delete \"" + r.getName() + "\" from \"" + c.getName() + "\"?")) {
				c.getRequests().remove(r);
				host.unlink(c.getId(), r.getId());
				refresh();
				host.collectionsChanged();
			}
		}
	}

	private void installMenu() {
		Menu menu = new Menu(viewer.getTree());
		menu.addListener(SWT.Show, e -> {
			for (MenuItem mi : menu.getItems()) {
				mi.dispose();
			}
			Object element = selected();
			if (element instanceof RequestModel r) {
				menuItem(menu, "Open", this::openSelection);
				menuItem(menu, "Rename…\tF2", this::renameSelection);
				menuItem(menu, "Duplicate", () -> duplicate(r));
				menuItem(menu, "Move Up", () -> move(r, -1));
				menuItem(menu, "Move Down", () -> move(r, 1));
				new MenuItem(menu, SWT.SEPARATOR);
				menuItem(menu, "Delete\tDel", this::deleteSelection);
			} else if (element instanceof RequestCollection c) {
				menuItem(menu, "Rename…\tF2", this::renameSelection);
				menuItem(menu, "Export…", () -> exportCollection(c));
				new MenuItem(menu, SWT.SEPARATOR);
				menuItem(menu, "Delete\tDel", this::deleteSelection);
			}
			if (menu.getItemCount() > 0) {
				new MenuItem(menu, SWT.SEPARATOR);
			}
			menuItem(menu, "New Collection…", this::newCollection);
			menuItem(menu, "Import Collection…", this::importCollection);
		});
		viewer.getTree().setMenu(menu);
	}

	private static void menuItem(Menu menu, String text, Runnable action) {
		MenuItem mi = new MenuItem(menu, SWT.PUSH);
		mi.setText(text);
		mi.addListener(SWT.Selection, (Event e) -> action.run());
	}

	// --- viewer plumbing ---------------------------------------------------------------------

	private final class ContentProvider implements ITreeContentProvider {
		@Override
		public Object[] getElements(Object input) {
			return collections.toArray();
		}

		@Override
		public Object[] getChildren(Object parent) {
			return parent instanceof RequestCollection c ? c.getRequests().toArray() : new Object[0];
		}

		@Override
		public Object getParent(Object element) {
			return element instanceof RequestModel r ? collectionOf(r) : null;
		}

		@Override
		public boolean hasChildren(Object element) {
			return element instanceof RequestCollection c && !c.getRequests().isEmpty();
		}
	}

	private static final class Labels extends LabelProvider implements IStyledLabelProvider {
		@Override
		public StyledString getStyledText(Object element) {
			if (element instanceof RequestCollection c) {
				return new StyledString(c.getName()).append(" (" + c.getRequests().size() + ")",
						StyledString.COUNTER_STYLER);
			}
			if (element instanceof RequestModel r) {
				return new StyledString(r.getMethod().name() + "  ", StyledString.DECORATIONS_STYLER)
						.append(r.getName().isBlank() ? "Untitled" : r.getName());
			}
			return new StyledString(String.valueOf(element));
		}

		@Override
		public Image getImage(Object element) {
			ISharedImages images = PlatformUI.getWorkbench().getSharedImages();
			return images.getImage(element instanceof RequestCollection ? ISharedImages.IMG_OBJ_FOLDER
					: ISharedImages.IMG_OBJ_FILE);
		}
	}
}
