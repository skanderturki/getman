package com.getman.ui.views;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.util.LocalSelectionTransfer;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.ViewerDropAdapter;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSourceAdapter;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.dnd.TransferData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.TextStyle;
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
import org.eclipse.ui.dialogs.ListDialog;

import com.getman.core.json.Json;
import com.getman.core.json.JsonException;
import com.getman.core.model.CollectionNode;
import com.getman.core.model.Environment;
import com.getman.core.model.Folder;
import com.getman.core.model.KeyValue;
import com.getman.core.model.NodeContainer;
import com.getman.core.model.RequestCollection;
import com.getman.core.model.RequestModel;
import com.getman.core.postman.PostmanImportException;
import com.getman.core.postman.PostmanImporter;
import com.getman.core.store.CollectionFile;

/**
 * Tree of collections, folders and saved requests, shown on the left of the GetMan view. Folders and requests
 * can be dragged to reorder them or move them between folders and collections.
 */
class CollectionsPanel extends Composite {

	/** What the panel needs from the view. */
	interface Host {
		void openRequest(RequestCollection collection, RequestModel saved);

		/** Collections, folders, saved requests or collection variables changed. */
		void collectionsChanged();

		/** Open tabs of these saved requests (all of the collection's when {@code null}) leave the collection. */
		void unlink(String collectionId, Set<String> requestIdsOrNull);

		/** Saved requests moved to another collection; open tabs of them should follow. */
		void relink(Set<String> requestIds, String newCollectionId);

		/** A saved request was renamed in the tree; open tabs of it should follow. */
		void savedRequestRenamed(RequestModel saved);

		void importEnvironment(Environment environment);

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

		emptyHint = new Label(this, SWT.WRAP);
		emptyHint.setText("No collections yet.\n\nPress Ctrl+S in a request tab to save it to a collection, "
				+ "or right-click here to create one or import a GetMan or Postman file.");
		GridData hintData = new GridData(SWT.FILL, SWT.TOP, true, false);
		hintData.horizontalIndent = 4;
		hintData.verticalIndent = 4;
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
			if (viewer.getTree().getItem(new Point(e.x, e.y)) == null) {
				viewer.setSelection(StructuredSelection.EMPTY);
			}
		});
		installDragAndDrop();
		installMenu();
		updateEmptyHint();
	}

	void refresh() {
		viewer.refresh();
		updateEmptyHint();
	}

	/** Refreshes and selects {@code element} (expanding its parents); falls back to the collection. */
	void reveal(RequestCollection collection, Object element) {
		refresh();
		viewer.setExpandedState(collection, true);
		viewer.setSelection(new StructuredSelection(element != null ? element : collection), true);
	}

	private void updateEmptyHint() {
		boolean empty = collections.isEmpty();
		((GridData) emptyHint.getLayoutData()).exclude = !empty;
		emptyHint.setVisible(empty);
		layout(true);
	}

	// --- lookups -------------------------------------------------------------------------------

	private Object selected() {
		return viewer.getStructuredSelection().getFirstElement();
	}

	private RequestCollection collectionOf(CollectionNode node) {
		for (RequestCollection c : collections) {
			if (c.contains(node)) {
				return c;
			}
		}
		return null;
	}

	/** Collection of a tree element (a collection is its own). */
	private RequestCollection collectionOfElement(Object element) {
		if (element instanceof RequestCollection c) {
			return c;
		}
		return element instanceof CollectionNode n ? collectionOf(n) : null;
	}

	private static Set<String> requestIds(CollectionNode node) {
		if (node instanceof RequestModel r) {
			return Set.of(r.getId());
		}
		return ((Folder) node).allRequests().stream().map(RequestModel::getId).collect(Collectors.toSet());
	}

	/** Whether {@code container} is {@code folder} itself or somewhere inside it. */
	private static boolean isInside(NodeContainer container, Folder folder) {
		if (container == folder) {
			return true;
		}
		for (CollectionNode child : folder.getItems()) {
			if (child instanceof Folder f && isInside(container, f)) {
				return true;
			}
		}
		return false;
	}

	// --- actions -------------------------------------------------------------------------------

	RequestCollection newCollection() {
		String name = askName("New Collection", "Collection name:", "Collection " + (collections.size() + 1));
		if (name == null) {
			return null;
		}
		RequestCollection c = new RequestCollection(name);
		collections.add(c);
		reveal(c, null);
		host.collectionsChanged();
		return c;
	}

	private void newFolder(NodeContainer container) {
		String name = askName("New Folder", "Folder name:", "New Folder");
		if (name == null) {
			return;
		}
		Folder f = new Folder(name);
		container.getItems().add(f);
		reveal(collectionOfElement(container), f);
		host.collectionsChanged();
	}

	private void newRequest(NodeContainer container) {
		RequestCollection c = collectionOfElement(container);
		RequestModel r = new RequestModel();
		r.setName("New Request");
		r.setCollectionId(c.getId());
		container.getItems().add(r);
		reveal(c, r);
		host.collectionsChanged();
		host.openRequest(c, r);
	}

	/** Imports a GetMan collection, a Postman collection (v2.0/2.1) or a Postman environment. */
	void importFile() {
		FileDialog dialog = new FileDialog(getShell(), SWT.OPEN);
		dialog.setText("Import Collection or Environment");
		dialog.setFilterExtensions(new String[] { "*.json", "*.*" });
		dialog.setFilterNames(new String[] { "GetMan or Postman files (*.json)", "All files" });
		String file = dialog.open();
		if (file == null) {
			return;
		}
		try {
			String json = Files.readString(Path.of(file), StandardCharsets.UTF_8);
			Object parsed = Json.parse(json);
			if (parsed instanceof Map<?, ?> root && root.containsKey("getmanCollection")) {
				addImported(CollectionFile.fromJson(json), List.of());
				return;
			}
			switch (PostmanImporter.detect(parsed)) {
			case POSTMAN_COLLECTION -> {
				PostmanImporter.CollectionResult result = PostmanImporter.importCollection(json);
				addImported(result.collection(), result.warnings());
			}
			case POSTMAN_ENVIRONMENT -> {
				PostmanImporter.EnvironmentResult result = PostmanImporter.importEnvironment(json);
				host.importEnvironment(result.environment());
				report("Imported environment \"" + result.environment().getName() + "\" with "
						+ result.environment().getVariables().size() + " variable(s). It is now selected in the toolbar.",
						result.warnings());
			}
			default -> MessageDialog.openError(getShell(), "Import", "This file is not a GetMan collection, a Postman "
					+ "collection (v2.0 or v2.1) or a Postman environment.");
			}
		} catch (IOException | JsonException | PostmanImportException e) {
			MessageDialog.openError(getShell(), "Import", "Could not import " + file + ":\n" + e.getMessage());
		}
	}

	private void addImported(RequestCollection c, List<String> warnings) {
		collections.add(c);
		reveal(c, null);
		host.collectionsChanged();
		long folders = countFolders(c);
		report("Imported \"" + c.getName() + "\": " + c.allRequests().size() + " request(s)"
				+ (folders > 0 ? " in " + folders + " folder(s)" : "")
				+ (c.getVariables().isEmpty() ? "" : ", " + c.getVariables().size() + " collection variable(s)") + ".",
				warnings);
	}

	private static long countFolders(NodeContainer container) {
		long n = 0;
		for (CollectionNode child : container.getItems()) {
			if (child instanceof Folder f) {
				n += 1 + countFolders(f);
			}
		}
		return n;
	}

	private void report(String summary, List<String> warnings) {
		host.showStatus(summary);
		if (warnings.isEmpty()) {
			MessageDialog.openInformation(getShell(), "Import", summary);
		} else {
			MessageDialog.openWarning(getShell(), "Import",
					summary + "\n\nNotes:\n- " + String.join("\n- ", warnings));
		}
	}

	private void exportCollection(RequestCollection c) {
		boolean includeSecrets = false;
		if (CollectionFile.hasSecrets(c)) {
			MessageDialog question = new MessageDialog(getShell(), "Export Collection", null,
					"Some requests in \"" + c.getName() + "\" store auth tokens or passwords.\n\n"
							+ "Leave them out to share the file safely: the auth type and username are kept, so "
							+ "whoever imports it only has to fill in the secret.",
					MessageDialog.QUESTION, 0, "Leave Out Secrets", "Include Secrets", "Cancel");
			int choice = question.open();
			if (choice == 2 || choice == -1) {
				return;
			}
			includeSecrets = choice == 1;
		}
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
			CollectionFile.write(c, Path.of(file), includeSecrets);
			host.showStatus("Exported \"" + c.getName() + "\" to " + file);
			long withFiles = c.allRequests().stream().filter(CollectionsPanel::usesFiles).count();
			if (withFiles > 0) {
				MessageDialog.openInformation(getShell(), "Export Collection", withFiles + " request(s) in \""
						+ c.getName() + "\" upload local files. The export contains their file paths, not the files "
						+ "themselves; whoever imports it needs the files at the same paths (or can pick them again).");
			}
		} catch (IOException e) {
			MessageDialog.openError(getShell(), "Export Collection", "Could not write " + file + ":\n" + e.getMessage());
		}
	}

	private static boolean usesFiles(RequestModel r) {
		return switch (r.getBodyType()) {
		case BINARY -> !r.getBinaryFile().isBlank();
		case MULTIPART -> r.getFormFields().stream().anyMatch(f -> f.isFile() && !f.getValue().isBlank());
		default -> false;
		};
	}

	private void editVariables(RequestCollection c) {
		List<KeyValue> working = new ArrayList<>();
		c.getVariables().forEach(v -> working.add(v.copy()));
		VariablesDialog dialog = new VariablesDialog(getShell(), "Collection Variables",
				"Variables of \"" + c.getName() + "\". They apply to the requests of this collection; a variable with "
						+ "the same name in the active environment takes precedence.",
				working);
		if (dialog.open() == Window.OK) {
			c.getVariables().clear();
			c.getVariables().addAll(working);
			viewer.refresh(c);
			host.collectionsChanged();
		}
	}

	private void openSelection() {
		Object element = selected();
		if (element instanceof RequestModel r) {
			RequestCollection c = collectionOf(r);
			if (c != null) {
				host.openRequest(c, r);
			}
		} else if (element != null) {
			viewer.setExpandedState(element, !viewer.getExpandedState(element));
		}
	}

	private void renameSelection() {
		Object element = selected();
		if (element instanceof RequestCollection c) {
			String name = askName("Rename Collection", "Name:", c.getName());
			if (name != null) {
				c.setName(name);
				viewer.refresh(c);
				host.collectionsChanged();
			}
		} else if (element instanceof Folder f) {
			String name = askName("Rename Folder", "Name:", f.getName());
			if (name != null) {
				f.setName(name);
				viewer.refresh(f);
				host.collectionsChanged();
			}
		} else if (element instanceof RequestModel r) {
			String name = askName("Rename Request", "Name:", r.getName());
			if (name != null) {
				r.setName(name);
				viewer.refresh(r);
				host.savedRequestRenamed(r);
				host.collectionsChanged();
			}
		}
	}

	private String askName(String title, String label, String current) {
		InputDialog dialog = new InputDialog(getShell(), title, label, current,
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
		List<CollectionNode> siblings = c.parentOf(r).getItems();
		siblings.add(siblings.indexOf(r) + 1, copy);
		reveal(c, copy);
		host.collectionsChanged();
	}

	private void moveWithinParent(CollectionNode node, int delta) {
		RequestCollection c = collectionOf(node);
		if (c == null) {
			return;
		}
		List<CollectionNode> siblings = c.parentOf(node).getItems();
		int from = siblings.indexOf(node);
		int to = from + delta;
		if (to < 0 || to >= siblings.size()) {
			return;
		}
		siblings.remove(from);
		siblings.add(to, node);
		reveal(c, node);
		host.collectionsChanged();
	}

	/**
	 * Moves {@code node} into {@code target} at {@code index} ({@code -1} = end). Works across folders and
	 * collections; returns false (and changes nothing) when a folder would be moved into itself.
	 */
	private boolean move(CollectionNode node, NodeContainer target, int index) {
		RequestCollection from = collectionOf(node);
		RequestCollection to = collectionOfElement(target);
		if (from == null || to == null || (node instanceof Folder f && isInside(target, f))) {
			return false;
		}
		List<CollectionNode> oldSiblings = from.parentOf(node).getItems();
		int oldIndex = oldSiblings.indexOf(node);
		oldSiblings.remove(oldIndex);
		List<CollectionNode> newSiblings = target.getItems();
		int at = index < 0 ? newSiblings.size() : index;
		if (oldSiblings == newSiblings && oldIndex < at) {
			at--; // the list shrank in front of the drop point
		}
		newSiblings.add(Math.max(0, Math.min(at, newSiblings.size())), node);
		if (from != to) {
			Set<String> ids = requestIds(node);
			if (node instanceof RequestModel r) {
				r.setCollectionId(to.getId());
			} else {
				((Folder) node).allRequests().forEach(r -> r.setCollectionId(to.getId()));
			}
			host.relink(ids, to.getId());
		}
		reveal(to, node);
		host.collectionsChanged();
		return true;
	}

	/** "Move to…": pick any collection or folder (except the node itself and what is inside it). */
	private void moveTo(CollectionNode node) {
		record Target(NodeContainer container, String label) {
		}
		List<Target> targets = new ArrayList<>();
		for (RequestCollection c : collections) {
			addTargets(c, c.getName(), node, targets, (container, label) -> new Target(container, label));
		}
		ListDialog dialog = new ListDialog(getShell());
		dialog.setTitle("Move \"" + node.getName() + "\"");
		dialog.setMessage("Move to:");
		dialog.setContentProvider(ArrayContentProvider.getInstance());
		dialog.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				return ((Target) element).label();
			}
		});
		dialog.setInput(targets);
		if (dialog.open() == Window.OK && dialog.getResult() != null && dialog.getResult().length > 0) {
			move(node, ((Target) dialog.getResult()[0]).container(), -1);
		}
	}

	private static <T> void addTargets(NodeContainer container, String label, CollectionNode moving, List<T> out,
			java.util.function.BiFunction<NodeContainer, String, T> make) {
		if (container == moving) {
			return;
		}
		out.add(make.apply(container, label));
		for (CollectionNode child : container.getItems()) {
			if (child instanceof Folder f) {
				addTargets(f, label + " / " + f.getName(), moving, out, make);
			}
		}
	}

	private void deleteSelection() {
		Object element = selected();
		if (element instanceof RequestCollection c) {
			if (MessageDialog.openConfirm(getShell(), "Delete Collection", "Delete collection \"" + c.getName()
					+ "\" and its " + c.allRequests().size() + " saved request(s)?\n\nOpen tabs stay open.")) {
				collections.remove(c);
				host.unlink(c.getId(), null);
				refresh();
				host.collectionsChanged();
			}
		} else if (element instanceof Folder f) {
			RequestCollection c = collectionOf(f);
			if (c != null && MessageDialog.openConfirm(getShell(), "Delete Folder", "Delete folder \"" + f.getName()
					+ "\" and the " + f.allRequests().size() + " saved request(s) in it?\n\nOpen tabs stay open.")) {
				Set<String> ids = requestIds(f);
				c.remove(f);
				host.unlink(c.getId(), ids);
				refresh();
				host.collectionsChanged();
			}
		} else if (element instanceof RequestModel r) {
			RequestCollection c = collectionOf(r);
			if (c != null && MessageDialog.openConfirm(getShell(), "Delete Request",
					"Delete \"" + r.getName() + "\" from \"" + c.getName() + "\"?")) {
				c.remove(r);
				host.unlink(c.getId(), Set.of(r.getId()));
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
				new MenuItem(menu, SWT.SEPARATOR);
				menuItem(menu, "Move Up", () -> moveWithinParent(r, -1));
				menuItem(menu, "Move Down", () -> moveWithinParent(r, 1));
				menuItem(menu, "Move To…", () -> moveTo(r));
				new MenuItem(menu, SWT.SEPARATOR);
				menuItem(menu, "Delete\tDel", this::deleteSelection);
			} else if (element instanceof Folder f) {
				menuItem(menu, "New Request", () -> newRequest(f));
				menuItem(menu, "New Folder…", () -> newFolder(f));
				new MenuItem(menu, SWT.SEPARATOR);
				menuItem(menu, "Rename…\tF2", this::renameSelection);
				menuItem(menu, "Move Up", () -> moveWithinParent(f, -1));
				menuItem(menu, "Move Down", () -> moveWithinParent(f, 1));
				menuItem(menu, "Move To…", () -> moveTo(f));
				new MenuItem(menu, SWT.SEPARATOR);
				menuItem(menu, "Delete\tDel", this::deleteSelection);
			} else if (element instanceof RequestCollection c) {
				menuItem(menu, "New Request", () -> newRequest(c));
				menuItem(menu, "New Folder…", () -> newFolder(c));
				new MenuItem(menu, SWT.SEPARATOR);
				menuItem(menu, "Variables…", () -> editVariables(c));
				menuItem(menu, "Rename…\tF2", this::renameSelection);
				menuItem(menu, "Export…", () -> exportCollection(c));
				new MenuItem(menu, SWT.SEPARATOR);
				menuItem(menu, "Delete\tDel", this::deleteSelection);
			}
			if (menu.getItemCount() > 0) {
				new MenuItem(menu, SWT.SEPARATOR);
			}
			menuItem(menu, "New Collection…", this::newCollection);
			menuItem(menu, "Import (GetMan or Postman)…", this::importFile);
		});
		viewer.getTree().setMenu(menu);
	}

	private static void menuItem(Menu menu, String text, Runnable action) {
		MenuItem mi = new MenuItem(menu, SWT.PUSH);
		mi.setText(text);
		mi.addListener(SWT.Selection, (Event e) -> action.run());
	}

	// --- drag and drop -------------------------------------------------------------------------

	private void installDragAndDrop() {
		Transfer[] transfers = { LocalSelectionTransfer.getTransfer() };
		viewer.addDragSupport(DND.DROP_MOVE, transfers, new DragSourceAdapter() {
			@Override
			public void dragStart(DragSourceEvent event) {
				Object element = selected();
				event.doit = element instanceof CollectionNode; // collections themselves are not dragged
				if (event.doit) {
					LocalSelectionTransfer.getTransfer().setSelection(viewer.getStructuredSelection());
				}
			}

			@Override
			public void dragFinished(DragSourceEvent event) {
				LocalSelectionTransfer.getTransfer().setSelection(null);
			}
		});
		viewer.addDropSupport(DND.DROP_MOVE, transfers, new ViewerDropAdapter(viewer) {
			@Override
			public boolean validateDrop(Object target, int operation, TransferData type) {
				CollectionNode dragged = draggedNode();
				if (dragged == null || target == null || target == dragged) {
					return false;
				}
				DropPoint point = dropPoint(target, getCurrentLocation());
				return point != null && !(dragged instanceof Folder f && isInside(point.container(), f));
			}

			@Override
			public boolean performDrop(Object data) {
				CollectionNode dragged = draggedNode();
				DropPoint point = dropPoint(getCurrentTarget(), getCurrentLocation());
				return dragged != null && point != null && move(dragged, point.container(), point.index());
			}
		});
	}

	private record DropPoint(NodeContainer container, int index) {
	}

	private static CollectionNode draggedNode() {
		if (LocalSelectionTransfer.getTransfer().getSelection() instanceof IStructuredSelection s
				&& s.getFirstElement() instanceof CollectionNode node) {
			return node;
		}
		return null;
	}

	/**
	 * Where a drop lands: onto a collection or folder means "inside, at the end"; before/after an item means
	 * "next to it, in its parent".
	 */
	private DropPoint dropPoint(Object target, int location) {
		if (target instanceof RequestCollection c) {
			return new DropPoint(c, -1);
		}
		if (target instanceof Folder f && location == ViewerDropAdapter.LOCATION_ON) {
			return new DropPoint(f, -1);
		}
		if (target instanceof CollectionNode node) {
			RequestCollection c = collectionOf(node);
			if (c == null) {
				return null;
			}
			NodeContainer parent = c.parentOf(node);
			int i = parent.getItems().indexOf(node);
			boolean before = location == ViewerDropAdapter.LOCATION_BEFORE;
			return new DropPoint(parent, before ? i : i + 1);
		}
		return null;
	}

	// --- viewer plumbing -----------------------------------------------------------------------

	private final class ContentProvider implements ITreeContentProvider {
		@Override
		public Object[] getElements(Object input) {
			return collections.toArray();
		}

		@Override
		public Object[] getChildren(Object parent) {
			return parent instanceof NodeContainer c ? c.getItems().toArray() : new Object[0];
		}

		@Override
		public Object getParent(Object element) {
			if (element instanceof CollectionNode node) {
				RequestCollection c = collectionOf(node);
				return c == null ? null : c.parentOf(node);
			}
			return null;
		}

		@Override
		public boolean hasChildren(Object element) {
			return element instanceof NodeContainer c && !c.getItems().isEmpty();
		}
	}

	private static final class Labels extends LabelProvider implements IStyledLabelProvider {
		private static final StyledString.Styler BOLD = new StyledString.Styler() {
			@Override
			public void applyStyles(TextStyle textStyle) {
				textStyle.font = JFaceResources.getFontRegistry().getBold(JFaceResources.DEFAULT_FONT);
			}
		};

		@Override
		public StyledString getStyledText(Object element) {
			if (element instanceof RequestCollection c) {
				return new StyledString(c.getName(), BOLD).append(" (" + c.allRequests().size() + ")",
						StyledString.COUNTER_STYLER);
			}
			if (element instanceof Folder f) {
				return new StyledString(f.getName()).append(" (" + f.allRequests().size() + ")",
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
			return images.getImage(element instanceof NodeContainer ? ISharedImages.IMG_OBJ_FOLDER
					: ISharedImages.IMG_OBJ_FILE);
		}
	}
}
