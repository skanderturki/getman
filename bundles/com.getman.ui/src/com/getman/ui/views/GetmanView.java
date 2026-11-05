package com.getman.ui.views;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ControlContribution;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabFolder2Adapter;
import org.eclipse.swt.custom.CTabFolderEvent;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ListDialog;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.part.ViewPart;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import com.getman.core.curl.CurlExporter;
import com.getman.core.curl.CurlImporter;
import com.getman.core.http.HttpService;
import com.getman.core.model.Environment;
import com.getman.core.model.RequestCollection;
import com.getman.core.model.RequestModel;
import com.getman.core.store.StateStore;
import com.getman.core.store.WorkspaceState;
import com.getman.core.variables.VariableResolver;

/**
 * The GetMan view: collections on the left, one tab per open request on the right. Collections and open tabs are
 * saved to the plugin's state folder shortly after every edit and when the view closes, and restored the next
 * time Eclipse starts.
 */
public class GetmanView extends ViewPart implements CollectionsPanel.Host {
	public static final String ID = "com.getman.ui.views.GetmanView";

	private static final int SAVE_DELAY_MS = 800;
	private static final String STATE_FILE = "state.json";
	private static final String NEW_COLLECTION_CHOICE = "New collection…";
	private static final String MANAGE_ENVIRONMENTS = "Manage Environments…";

	private final HttpService http = new HttpService();
	private final Runnable saveNow = this::save;
	private final List<RequestCollection> collections = new ArrayList<>();
	private final List<Environment> environments = new ArrayList<>();
	private String activeEnvironmentId = "";
	private Combo environmentCombo;
	private StateStore store;
	private ILog log;
	private SashForm sash;
	private CollectionsPanel collectionsPanel;
	private CTabFolder tabs;
	private boolean restoring;

	@Override
	public void createPartControl(Composite parent) {
		Bundle bundle = FrameworkUtil.getBundle(GetmanView.class);
		log = Platform.getLog(bundle);
		Path stateFile = Platform.getStateLocation(bundle).append(STATE_FILE).toFile().toPath();
		store = new StateStore(stateFile);

		sash = new SashForm(parent, SWT.HORIZONTAL);
		collectionsPanel = new CollectionsPanel(sash, collections, this);

		tabs = new CTabFolder(sash, SWT.BORDER | SWT.CLOSE | SWT.FLAT);
		tabs.setUnselectedCloseVisible(false);
		tabs.setMRUVisible(true);
		tabs.addCTabFolder2Listener(new CTabFolder2Adapter() {
			@Override
			public void close(CTabFolderEvent event) {
				// CTabItem does not dispose its control; do it so an in-flight request is cancelled.
				Control control = ((CTabItem) event.item).getControl();
				// Let the item close, then make sure at least one tab remains.
				tabs.getDisplay().asyncExec(() -> {
					if (control != null && !control.isDisposed()) {
						control.dispose();
					}
					if (tabs.isDisposed()) {
						return;
					}
					if (tabs.getItemCount() == 0) {
						addTab(newRequest(), true);
					}
					scheduleSave();
				});
			}
		});
		tabs.addListener(SWT.Selection, e -> scheduleSave());
		tabs.addListener(SWT.MouseDoubleClick, e -> {
			CTabItem item = tabs.getItem(new Point(e.x, e.y));
			if (item != null) {
				rename(item);
			}
		});
		// Save while the tabs still exist: the Dispose event fires before child widgets are released,
		// which is not guaranteed by the time ViewPart.dispose() runs.
		tabs.addListener(SWT.Dispose, e -> {
			tabs.getDisplay().timerExec(-1, saveNow);
			save();
		});
		sash.setWeights(new int[] { 22, 78 });

		installTabMenu();
		contributeToolbar();
		activateSaveHandlers();

		restore();
	}

	@Override
	public void setFocus() {
		CTabItem item = tabs.getSelection();
		if (item != null && item.getControl() != null) {
			item.getControl().setFocus();
		} else {
			tabs.setFocus();
		}
	}

	// --- tabs --------------------------------------------------------------------------------

	private CTabItem addTab(RequestModel model, boolean select) {
		CTabItem item = new CTabItem(tabs, SWT.NONE);
		item.setData(model);
		item.setControl(createRequestTab(item, model));
		updateTabTitle(item);
		if (select) {
			tabs.setSelection(item);
		}
		return item;
	}

	private RequestTab createRequestTab(CTabItem item, RequestModel model) {
		return new RequestTab(tabs, model, http, () -> {
			updateTabTitle(item);
			scheduleSave();
		}, result -> replaceWithCurl(item, result), () -> scopeFor((RequestModel) item.getData()));
	}

	// --- environments --------------------------------------------------------------------------

	private Environment activeEnvironment() {
		for (Environment e : environments) {
			if (e.getId().equals(activeEnvironmentId)) {
				return e;
			}
		}
		return null;
	}

	/** Variables requests are resolved against right now. */
	private VariableScope scopeFor(RequestModel model) {
		Environment env = activeEnvironment();
		RequestCollection collection = findCollection(model.getCollectionId());
		Map<String, String> values = new LinkedHashMap<>();
		String collectionName = "";
		if (collection != null && !collection.getVariables().isEmpty()) {
			values.putAll(collection.activeVariables());
			collectionName = collection.getName();
		}
		if (env != null) {
			values.putAll(env.activeValues()); // the environment overrides collection variables
		}
		return new VariableScope(env == null ? "" : env.getName(), collectionName, values);
	}

	private void refreshAllVariableHints() {
		for (CTabItem item : tabs.getItems()) {
			if (item.getControl() instanceof RequestTab tab) {
				tab.refreshVariableHint();
			}
		}
	}

	private void selectEnvironment(String id) {
		activeEnvironmentId = id == null ? "" : id;
		refreshEnvironmentCombo();
		refreshAllVariableHints();
		scheduleSave();
	}

	private void manageEnvironments() {
		EnvironmentsDialog dialog = new EnvironmentsDialog(tabs.getShell(), environments, activeEnvironmentId);
		if (dialog.open() == Window.OK) {
			environments.clear();
			environments.addAll(dialog.getResult());
			if (activeEnvironment() == null) {
				// The active one was deleted, or there was none: pick the first so variables work right away.
				activeEnvironmentId = environments.isEmpty() ? "" : environments.get(0).getId();
			}
			selectEnvironment(activeEnvironmentId);
		} else {
			refreshEnvironmentCombo();
		}
	}

	/** Combo entries: "No Environment", each environment, then "Manage Environments…". */
	private void refreshEnvironmentCombo() {
		if (environmentCombo == null || environmentCombo.isDisposed()) {
			return;
		}
		environmentCombo.removeAll();
		environmentCombo.add("No Environment");
		int selected = 0;
		for (int i = 0; i < environments.size(); i++) {
			Environment e = environments.get(i);
			environmentCombo.add(e.getName().isBlank() ? "Untitled" : e.getName());
			if (e.getId().equals(activeEnvironmentId)) {
				selected = i + 1;
			}
		}
		environmentCombo.add(MANAGE_ENVIRONMENTS);
		environmentCombo.select(selected);
		environmentCombo.setToolTipText(selected == 0 ? "No environment: {{variables}} are not replaced"
				: "Environment used to fill {{variables}}");
	}

	private final class EnvironmentSelector extends ControlContribution {
		EnvironmentSelector() {
			super("com.getman.ui.environment");
		}

		@Override
		protected Control createControl(Composite parent) {
			environmentCombo = new Combo(parent, SWT.READ_ONLY | SWT.DROP_DOWN);
			environmentCombo.addListener(SWT.Selection, e -> {
				int i = environmentCombo.getSelectionIndex();
				if (i == environmentCombo.getItemCount() - 1) {
					// "Manage Environments…" is an action, not a choice: open the dialog.
					environmentCombo.getDisplay().asyncExec(GetmanView.this::manageEnvironments);
				} else {
					selectEnvironment(i == 0 ? "" : environments.get(i - 1).getId());
				}
			});
			refreshEnvironmentCombo();
			return environmentCombo;
		}

		@Override
		protected int computeWidth(Control control) {
			return Math.max(160, control.computeSize(SWT.DEFAULT, SWT.DEFAULT, true).x);
		}
	}

	/** A cURL command was pasted into a tab's URL field: that tab now shows the imported request. */
	private void replaceWithCurl(CTabItem item, CurlImporter.Result result) {
		if (item.isDisposed()) {
			return;
		}
		RequestModel old = (RequestModel) item.getData();
		RequestModel imported = result.request();
		// Same tab, same identity: a saved request stays linked to its collection (and shows *).
		imported.setId(old.getId());
		imported.setCollectionId(old.getCollectionId());
		if (old.isInCollection()) {
			imported.setName(old.getName());
		}
		Control oldControl = item.getControl();
		item.setData(imported);
		item.setControl(createRequestTab(item, imported));
		if (oldControl != null) {
			oldControl.dispose();
		}
		updateTabTitle(item);
		scheduleSave();
		reportImport(result);
	}

	private void importCurl() {
		CurlImportDialog dialog = new CurlImportDialog(tabs.getShell());
		if (dialog.open() == Window.OK && dialog.getResult() != null) {
			addTab(dialog.getResult().request(), true);
			scheduleSave();
			reportImport(dialog.getResult());
		}
	}

	private void reportImport(CurlImporter.Result result) {
		showStatus("Imported cURL command: " + result.request().getMethod() + " " + result.request().getUrl());
		if (!result.warnings().isEmpty()) {
			MessageDialog.openWarning(tabs.getShell(), "Import cURL Command",
					"The request was imported, with these notes:\n\n- " + String.join("\n- ", result.warnings()));
		}
	}

	private RequestModel newRequest() {
		RequestModel model = new RequestModel();
		model.setName("Request " + (tabs.getItemCount() + 1));
		return model;
	}

	private void rename(CTabItem item) {
		RequestModel model = (RequestModel) item.getData();
		InputDialog dialog = new InputDialog(tabs.getShell(), "Rename Request", "Name:", model.getName(),
				text -> text.isBlank() ? "Name cannot be empty." : null);
		if (dialog.open() == Window.OK) {
			model.setName(dialog.getValue().trim());
			updateTabTitle(item);
			scheduleSave();
		}
	}

	private void duplicate(CTabItem item) {
		RequestModel copy = ((RequestModel) item.getData()).duplicate();
		addTab(copy, true);
		scheduleSave();
	}

	private void closeOthers(CTabItem keep) {
		for (CTabItem item : tabs.getItems()) {
			if (item != keep) {
				disposeTab(item);
			}
		}
		scheduleSave();
	}

	private static void disposeTab(CTabItem item) {
		if (item.getControl() != null) {
			item.getControl().dispose();
		}
		item.dispose();
	}

	/** Title is the request name, prefixed with {@code *} when it differs from its saved copy in a collection. */
	private void updateTabTitle(CTabItem item) {
		if (item.isDisposed()) {
			return;
		}
		RequestModel model = (RequestModel) item.getData();
		String name = model.getName().isBlank() ? "Untitled" : model.getName();
		if (name.length() > 40) {
			name = name.substring(0, 37) + "…";
		}
		RequestCollection collection = findCollection(model.getCollectionId());
		if (collection == null) {
			item.setText(name);
			item.setToolTipText("Not saved in a collection — press Ctrl+S to save it.\nDouble-click to rename.");
			return;
		}
		RequestModel saved = collection.findRequest(model.getId());
		boolean dirty = !model.sameContent(saved);
		item.setText(dirty ? "*" + name : name);
		item.setToolTipText("In collection \"" + collection.getName() + "\""
				+ (dirty ? " — unsaved changes, press Ctrl+S to save." : ".") + "\nDouble-click to rename.");
	}

	private void updateAllTabTitles() {
		for (CTabItem item : tabs.getItems()) {
			updateTabTitle(item);
		}
	}

	private CTabItem selectedItem() {
		return tabs.getSelection();
	}

	private void installTabMenu() {
		Menu menu = new Menu(tabs);
		menu.addListener(SWT.Show, e -> {
			for (MenuItem mi : menu.getItems()) {
				mi.dispose();
			}
			Point cursor = tabs.toControl(tabs.getDisplay().getCursorLocation());
			CTabItem item = tabs.getItem(cursor);
			if (item == null) {
				item = selectedItem();
			}
			if (item == null) {
				return;
			}
			CTabItem target = item;
			menuItem(menu, "Save to Collection\tCtrl+S", () -> saveToCollection(target, false));
			menuItem(menu, "Save As…", () -> saveToCollection(target, true));
			new MenuItem(menu, SWT.SEPARATOR);
			menuItem(menu, "Rename…", () -> rename(target));
			menuItem(menu, "Duplicate", () -> duplicate(target));
			menuItem(menu, "Copy as cURL", () -> copyAsCurl(target));
			new MenuItem(menu, SWT.SEPARATOR);
			menuItem(menu, "Close", () -> {
				disposeTab(target);
				if (tabs.getItemCount() == 0) {
					addTab(newRequest(), true);
				}
				scheduleSave();
			});
			menuItem(menu, "Close Others", () -> closeOthers(target));
		});
		tabs.setMenu(menu);
	}

	private static void menuItem(Menu menu, String text, Runnable action) {
		MenuItem mi = new MenuItem(menu, SWT.PUSH);
		mi.setText(text);
		mi.addListener(SWT.Selection, (Event e) -> action.run());
	}

	private void contributeToolbar() {
		IToolBarManager toolbar = getViewSite().getActionBars().getToolBarManager();
		ISharedImages images = PlatformUI.getWorkbench().getSharedImages();

		toolbar.add(new EnvironmentSelector());
		toolbar.add(new Separator());
		toolbar.add(action("New Request", images, ISharedImages.IMG_OBJ_ADD, () -> {
			addTab(newRequest(), true);
			scheduleSave();
		}));
		toolbar.add(action("Save to Collection (Ctrl+S)", images, ISharedImages.IMG_ETOOL_SAVE_EDIT,
				() -> saveSelected(false)));
		toolbar.add(action("Import cURL Command…", images, ISharedImages.IMG_TOOL_PASTE, this::importCurl));
		toolbar.add(action("Duplicate Request", images, ISharedImages.IMG_TOOL_COPY, () -> {
			CTabItem item = selectedItem();
			if (item != null) {
				duplicate(item);
			}
		}));
		toolbar.add(action("Copy as cURL", images, ISharedImages.IMG_OBJ_FILE, () -> {
			CTabItem item = selectedItem();
			if (item != null) {
				copyAsCurl(item);
			}
		}));
		toolbar.add(new Separator());
		toolbar.add(action("New Collection", images, ISharedImages.IMG_OBJ_FOLDER, collectionsPanel::newCollection));

		IMenuManager viewMenu = getViewSite().getActionBars().getMenuManager();
		viewMenu.add(action("New Collection…", images, ISharedImages.IMG_OBJ_FOLDER, collectionsPanel::newCollection));
		viewMenu.add(action("Import (GetMan or Postman)…", images, null, collectionsPanel::importFile));
		viewMenu.add(new Separator());
		viewMenu.add(action("Manage Environments…", images, null, this::manageEnvironments));
		viewMenu.add(new Separator());
		viewMenu.add(action("Import cURL Command…", images, ISharedImages.IMG_TOOL_PASTE, this::importCurl));
		viewMenu.add(action("Save As…", images, null, () -> saveSelected(true)));
	}

	private static Action action(String text, ISharedImages images, String imageKey, Runnable run) {
		Action action = new Action(text) {
			@Override
			public void run() {
				run.run();
			}
		};
		if (imageKey != null) {
			action.setImageDescriptor(images.getImageDescriptor(imageKey));
		}
		return action;
	}

	/** Ctrl+S / Ctrl+Shift+S while the view is active save the selected tab to a collection. */
	private void activateSaveHandlers() {
		IHandlerService handlers = getSite().getService(IHandlerService.class);
		if (handlers == null) {
			return;
		}
		handlers.activateHandler(IWorkbenchCommandConstants.FILE_SAVE, new AbstractHandler() {
			@Override
			public Object execute(ExecutionEvent event) {
				saveSelected(false);
				return null;
			}
		});
		handlers.activateHandler(IWorkbenchCommandConstants.FILE_SAVE_AS, new AbstractHandler() {
			@Override
			public Object execute(ExecutionEvent event) {
				saveSelected(true);
				return null;
			}
		});
	}

	private void copyAsCurl(CTabItem item) {
		// Copy what would be sent: variables filled in from the active environment.
		RequestModel model = (RequestModel) item.getData();
		RequestModel resolved = new VariableResolver(scopeFor(model).values()).resolve(model);
		String curl = CurlExporter.toCurl(resolved);
		Clipboard clipboard = new Clipboard(tabs.getDisplay());
		try {
			clipboard.setContents(new Object[] { curl }, new Transfer[] { TextTransfer.getInstance() });
		} finally {
			clipboard.dispose();
		}
		showStatus("cURL command copied to clipboard.");
	}

	// --- collections -------------------------------------------------------------------------

	private RequestCollection findCollection(String id) {
		if (id == null || id.isEmpty()) {
			return null;
		}
		for (RequestCollection c : collections) {
			if (c.getId().equals(id)) {
				return c;
			}
		}
		return null;
	}

	private void saveSelected(boolean saveAs) {
		CTabItem item = selectedItem();
		if (item != null) {
			saveToCollection(item, saveAs);
		}
	}

	/**
	 * Saves the tab's request into its collection. The first time (or with Save As) the user picks the collection
	 * and the name; afterwards Ctrl+S just updates the saved copy.
	 */
	private void saveToCollection(CTabItem item, boolean saveAs) {
		RequestModel model = (RequestModel) item.getData();
		RequestCollection target = saveAs ? null : findCollection(model.getCollectionId());
		if (target == null) {
			target = chooseCollection(saveAs ? "Save Request As" : "Save Request to Collection");
			if (target == null) {
				return;
			}
			InputDialog nameDialog = new InputDialog(tabs.getShell(), "Save Request",
					"Request name in \"" + target.getName() + "\":", model.getName(),
					text -> text.isBlank() ? "Name cannot be empty." : null);
			if (nameDialog.open() != Window.OK) {
				return;
			}
			model.setName(nameDialog.getValue().trim());
			if (model.isInCollection()) {
				// Save As: leave the original saved request untouched and make this tab a new one.
				model.setId(UUID.randomUUID().toString());
			}
		}
		RequestModel saved = target.put(model);
		model.setCollectionId(target.getId());
		collectionsPanel.reveal(target, saved);
		updateAllTabTitles();
		scheduleSave();
		showStatus("Saved \"" + saved.getName() + "\" to collection \"" + target.getName() + "\".");
	}

	private RequestCollection chooseCollection(String title) {
		if (collections.isEmpty()) {
			return collectionsPanel.newCollection();
		}
		List<Object> choices = new ArrayList<>(collections);
		choices.add(NEW_COLLECTION_CHOICE);
		ListDialog dialog = new ListDialog(tabs.getShell());
		dialog.setTitle(title);
		dialog.setMessage("Choose a collection:");
		dialog.setContentProvider(ArrayContentProvider.getInstance());
		dialog.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				return element instanceof RequestCollection c ? c.getName() : String.valueOf(element);
			}
		});
		dialog.setInput(choices);
		dialog.setInitialSelections(collections.get(0));
		if (dialog.open() != Window.OK || dialog.getResult() == null || dialog.getResult().length == 0) {
			return null;
		}
		Object choice = dialog.getResult()[0];
		return choice instanceof RequestCollection c ? c : collectionsPanel.newCollection();
	}

	@Override
	public void openRequest(RequestCollection collection, RequestModel saved) {
		for (CTabItem item : tabs.getItems()) {
			RequestModel model = (RequestModel) item.getData();
			if (model.getId().equals(saved.getId()) && collection.getId().equals(model.getCollectionId())) {
				tabs.setSelection(item);
				return;
			}
		}
		addTab(saved.copy(), true);
		scheduleSave();
	}

	@Override
	public void collectionsChanged() {
		updateAllTabTitles();
		refreshAllVariableHints(); // collection variables may have changed
		scheduleSave();
	}

	@Override
	public void unlink(String collectionId, Set<String> requestIdsOrNull) {
		for (CTabItem item : tabs.getItems()) {
			RequestModel model = (RequestModel) item.getData();
			if (collectionId.equals(model.getCollectionId())
					&& (requestIdsOrNull == null || requestIdsOrNull.contains(model.getId()))) {
				model.setCollectionId("");
			}
		}
	}

	@Override
	public void relink(Set<String> requestIds, String newCollectionId) {
		for (CTabItem item : tabs.getItems()) {
			RequestModel model = (RequestModel) item.getData();
			if (model.isInCollection() && requestIds.contains(model.getId())) {
				model.setCollectionId(newCollectionId);
			}
		}
	}

	@Override
	public void importEnvironment(Environment environment) {
		environments.add(environment);
		selectEnvironment(environment.getId());
	}

	@Override
	public void savedRequestRenamed(RequestModel saved) {
		for (CTabItem item : tabs.getItems()) {
			RequestModel model = (RequestModel) item.getData();
			if (model.getId().equals(saved.getId()) && saved.getCollectionId().equals(model.getCollectionId())) {
				model.setName(saved.getName());
			}
		}
	}

	@Override
	public void showStatus(String message) {
		getViewSite().getActionBars().getStatusLineManager().setMessage(message);
	}

	// --- persistence -------------------------------------------------------------------------

	private void restore() {
		restoring = true;
		try {
			StateStore.LoadResult result = store.load();
			WorkspaceState state = result.state();
			collections.addAll(state.getCollections());
			environments.addAll(state.getEnvironments());
			activeEnvironmentId = state.getActiveEnvironmentId();
			refreshEnvironmentCombo();
			collectionsPanel.refresh();
			for (RequestModel model : state.getOpenTabs()) {
				if (findCollection(model.getCollectionId()) == null) {
					model.setCollectionId("");
				}
				addTab(model, false);
			}
			if (tabs.getItemCount() == 0) {
				addTab(newRequest(), false);
			}
			tabs.setSelection(Math.min(state.getSelectedIndex(), tabs.getItemCount() - 1));
			if (result.warning() != null) {
				log.log(Status.warning(result.warning()));
				tabs.getDisplay().asyncExec(() -> {
					if (!tabs.isDisposed()) {
						MessageDialog.openWarning(tabs.getShell(), "GetMan", result.warning());
					}
				});
			}
		} finally {
			restoring = false;
		}
	}

	/** Saves shortly after the last edit, so typing does not write the file on every keystroke. */
	private void scheduleSave() {
		if (restoring || tabs == null || tabs.isDisposed()) {
			return;
		}
		tabs.getDisplay().timerExec(SAVE_DELAY_MS, saveNow);
	}

	private void save() {
		if (tabs == null || tabs.isDisposed() || store == null) {
			return;
		}
		WorkspaceState state = new WorkspaceState();
		state.getCollections().addAll(collections);
		state.getEnvironments().addAll(environments);
		state.setActiveEnvironmentId(activeEnvironmentId);
		for (CTabItem item : tabs.getItems()) {
			if (item.getData() instanceof RequestModel model) {
				state.getOpenTabs().add(model);
			}
		}
		state.setSelectedIndex(Math.max(0, tabs.getSelectionIndex()));
		try {
			store.save(state);
		} catch (IOException e) {
			log.log(Status.error("Could not save GetMan requests to " + store.getFile(), e));
		}
	}
}
