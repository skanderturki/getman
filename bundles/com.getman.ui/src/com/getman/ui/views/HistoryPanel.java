package com.getman.ui.views;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

import com.getman.core.history.HistoryEntry;
import com.getman.core.run.RunRecord;

/**
 * Recently sent requests and collection runs, grouped by day, newest first. Each run is one group holding the
 * results of its requests. Double-click opens a request in a new tab, or a run's results.
 */
class HistoryPanel extends Composite {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

	/** A day heading with its entries and runs, newest first. */
	private record Day(String label, List<Object> entries) {
	}

	/** One request's result in a run. */
	private record RunItem(RunRecord run, int index) {
		RunRecord.Item item() {
			return run.items().get(index);
		}
	}

	private final List<HistoryEntry> entries;
	private final List<RunRecord> runs;
	private final Consumer<HistoryEntry> onOpen;
	private final Consumer<RunRecord> onOpenRun;
	private final BiConsumer<RunRecord, RunRecord.Item> onOpenRunItem;
	private final Runnable onChanged;
	private final TreeViewer viewer;
	private final Label emptyHint;
	private List<Day> days = List.of();
	private boolean historyOff;

	/**
	 * @param onOpenRun     shows a run's results
	 * @param onOpenRunItem opens the saved request of one result of a run
	 */
	HistoryPanel(Composite parent, List<HistoryEntry> entries, List<RunRecord> runs, Consumer<HistoryEntry> onOpen,
			Consumer<RunRecord> onOpenRun, BiConsumer<RunRecord, RunRecord.Item> onOpenRunItem, Runnable onChanged) {
		super(parent, SWT.NONE);
		this.entries = entries;
		this.runs = runs;
		this.onOpen = onOpen;
		this.onOpenRun = onOpenRun;
		this.onOpenRunItem = onOpenRunItem;
		this.onChanged = onChanged;
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		setLayout(layout);

		emptyHint = new Label(this, SWT.WRAP);
		GridData hintData = new GridData(SWT.FILL, SWT.TOP, true, false);
		hintData.horizontalIndent = 4;
		hintData.verticalIndent = 4;
		hintData.widthHint = 150;
		emptyHint.setLayoutData(hintData);

		viewer = new TreeViewer(this, SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL);
		viewer.getTree().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		viewer.setContentProvider(new ContentProvider());
		viewer.setLabelProvider(new DelegatingStyledCellLabelProvider(new Labels()));
		viewer.setInput(entries);
		viewer.addOpenListener(e -> openSelection());
		viewer.getTree().addListener(SWT.KeyDown, e -> {
			if (e.keyCode == SWT.DEL) {
				deleteSelection();
			}
		});
		installMenu();
		refresh();
	}

	/** Re-reads the entry list (newest first) and expands today. */
	void refresh() {
		Object[] expanded = viewer.getExpandedElements();
		days = group(entries, runs);
		viewer.refresh();
		viewer.setExpandedElements(expanded);
		if (!days.isEmpty()) {
			viewer.setExpandedState(days.get(0), true);
		}
		boolean empty = entries.isEmpty() && runs.isEmpty();
		emptyHint.setText(historyOff ? "History is turned off (Window › Preferences › GetMan)."
				: "No requests sent yet.\n\nEvery request you send and every collection run appears here, so you "
						+ "can open it again later.");
		((GridData) emptyHint.getLayoutData()).exclude = !empty;
		emptyHint.setVisible(empty);
		layout(true);
	}

	void setHistoryOff(boolean off) {
		historyOff = off;
		refresh();
	}

	private static long time(Object element) {
		return element instanceof HistoryEntry e ? e.sentAtMillis()
				: element instanceof RunRecord r ? r.startedAtMillis() : 0;
	}

	private static List<Day> group(List<HistoryEntry> entries, List<RunRecord> runs) {
		LocalDate today = LocalDate.now();
		List<Object> all = new ArrayList<>(entries);
		all.addAll(runs);
		all.sort(Comparator.comparingLong(HistoryPanel::time).reversed());
		Map<LocalDate, List<Object>> byDay = new LinkedHashMap<>();
		for (Object e : all) {
			LocalDate d = Instant.ofEpochMilli(time(e)).atZone(ZoneId.systemDefault()).toLocalDate();
			byDay.computeIfAbsent(d, k -> new ArrayList<>()).add(e);
		}
		List<Day> out = new ArrayList<>();
		byDay.forEach((d, list) -> out.add(new Day(d.equals(today) ? "Today"
				: d.equals(today.minusDays(1)) ? "Yesterday" : DAY.format(d), list)));
		return out;
	}

	private Object selected() {
		return viewer.getStructuredSelection().getFirstElement();
	}

	private void openSelection() {
		Object element = selected();
		if (element instanceof HistoryEntry e) {
			onOpen.accept(e);
		} else if (element instanceof RunRecord r) {
			onOpenRun.accept(r);
		} else if (element instanceof RunItem i) {
			onOpenRunItem.accept(i.run(), i.item());
		} else if (element != null) {
			viewer.setExpandedState(element, !viewer.getExpandedState(element));
		}
	}

	private void deleteSelection() {
		Object element = selected();
		if (element instanceof HistoryEntry e) {
			entries.remove(e);
		} else if (element instanceof RunRecord r) {
			runs.remove(r);
		} else if (element instanceof Day d) {
			entries.removeAll(d.entries());
			runs.removeAll(d.entries());
		} else {
			return;
		}
		refresh();
		onChanged.run();
	}

	private void clearAll() {
		int count = entries.size() + runs.size();
		if (count > 0 && MessageDialog.openConfirm(getShell(), "Clear History",
				"Remove all " + count + " entries (requests and collection runs) from the history?")) {
			entries.clear();
			runs.clear();
			refresh();
			onChanged.run();
		}
	}

	private void installMenu() {
		Menu menu = new Menu(viewer.getTree());
		menu.addListener(SWT.Show, e -> {
			for (MenuItem mi : menu.getItems()) {
				mi.dispose();
			}
			Object element = selected();
			if (element instanceof HistoryEntry) {
				menuItem(menu, "Open in New Tab", this::openSelection);
				menuItem(menu, "Delete\tDel", this::deleteSelection);
			} else if (element instanceof RunRecord r) {
				menuItem(menu, "Show Results", this::openSelection);
				menuItem(menu, "Export…", () -> RunDialog.export(getShell(), r));
				menuItem(menu, "Delete\tDel", this::deleteSelection);
			} else if (element instanceof RunItem) {
				menuItem(menu, "Open Request", this::openSelection);
			} else if (element instanceof Day d) {
				menuItem(menu, "Delete " + d.label() + "'s Entries", this::deleteSelection);
			}
			if (menu.getItemCount() > 0) {
				new MenuItem(menu, SWT.SEPARATOR);
			}
			MenuItem clear = menuItem(menu, "Clear History…", this::clearAll);
			clear.setEnabled(!entries.isEmpty() || !runs.isEmpty());
		});
		viewer.getTree().setMenu(menu);
	}

	private static MenuItem menuItem(Menu menu, String text, Runnable action) {
		MenuItem mi = new MenuItem(menu, SWT.PUSH);
		mi.setText(text);
		mi.addListener(SWT.Selection, (Event e) -> action.run());
		return mi;
	}

	private final class ContentProvider implements ITreeContentProvider {
		@Override
		public Object[] getElements(Object input) {
			return days.toArray();
		}

		@Override
		public Object[] getChildren(Object parent) {
			if (parent instanceof Day d) {
				return d.entries().toArray();
			}
			if (parent instanceof RunRecord r) {
				Object[] items = new Object[r.items().size()];
				for (int i = 0; i < items.length; i++) {
					items[i] = new RunItem(r, i);
				}
				return items;
			}
			return new Object[0];
		}

		@Override
		public Object getParent(Object element) {
			if (element instanceof RunItem i) {
				return i.run();
			}
			for (Day d : days) {
				if (d.entries().contains(element)) {
					return d;
				}
			}
			return null;
		}

		@Override
		public boolean hasChildren(Object element) {
			return element instanceof Day d && !d.entries().isEmpty()
					|| element instanceof RunRecord r && !r.items().isEmpty();
		}
	}

	private static final class Labels extends LabelProvider implements IStyledLabelProvider {
		@Override
		public StyledString getStyledText(Object element) {
			if (element instanceof Day d) {
				return new StyledString(d.label()).append(" (" + d.entries().size() + ")", StyledString.COUNTER_STYLER);
			}
			if (element instanceof HistoryEntry e) {
				String url = e.resolvedUrl().isEmpty() ? e.request().getUrl() : e.resolvedUrl();
				StyledString s = new StyledString(e.request().getMethod().name() + "  ", StyledString.DECORATIONS_STYLER)
						.append(url);
				String outcome = e.isError() ? "  error" : "  " + e.statusCode();
				s.append(outcome, statusStyler(e));
				s.append("  " + e.durationMs() + " ms · " + TIME.format(Instant.ofEpochMilli(e.sentAtMillis())
						.atZone(ZoneId.systemDefault())), StyledString.QUALIFIER_STYLER);
				if (e.viaAssistant()) {
					s.append(" · AI assistant", StyledString.QUALIFIER_STYLER);
				}
				return s;
			}
			if (element instanceof RunRecord r) {
				StyledString s = new StyledString("Run " + r.title() + "  ");
				s.append(r.summary().toString(), styler(r.hasProblems() ? RunDialog.RED : RunDialog.GREEN));
				s.append("  " + (r.environment().isEmpty() ? "no environment" : r.environment()) + " · "
						+ TIME.format(Instant.ofEpochMilli(r.startedAtMillis()).atZone(ZoneId.systemDefault())),
						StyledString.QUALIFIER_STYLER);
				if (r.viaAssistant()) {
					s.append(" · AI assistant", StyledString.QUALIFIER_STYLER);
				}
				return s;
			}
			if (element instanceof RunItem ri) {
				RunRecord.Item i = ri.item();
				StyledString s = new StyledString(RunDialog.outcomeLabel(i.outcome()),
						styler(RunDialog.outcomeColor(i.outcome())));
				s.append("  " + i.method() + "  ", StyledString.DECORATIONS_STYLER).append(i.label());
				String status = i.status() == 0 ? "" : "  " + i.status();
				String detail = i.message().isEmpty() ? "" : " · " + i.message();
				s.append(status + detail, StyledString.QUALIFIER_STYLER);
				return s;
			}
			return new StyledString(String.valueOf(element));
		}

		@Override
		public String getText(Object element) {
			return getStyledText(element).getString();
		}

		private static StyledString.Styler styler(Color color) {
			return new StyledString.Styler() {
				@Override
				public void applyStyles(TextStyle textStyle) {
					textStyle.foreground = color;
				}
			};
		}

		private static StyledString.Styler statusStyler(HistoryEntry e) {
			int color = e.isError() || e.statusCode() >= 500 ? SWT.COLOR_DARK_RED
					: e.statusCode() >= 400 ? SWT.COLOR_DARK_YELLOW
							: e.statusCode() >= 300 ? SWT.COLOR_DARK_BLUE : SWT.COLOR_DARK_GREEN;
			return new StyledString.Styler() {
				@Override
				public void applyStyles(TextStyle textStyle) {
					Color c = PlatformUI.getWorkbench().getDisplay().getSystemColor(color);
					textStyle.foreground = c;
				}
			};
		}

		@Override
		public Image getImage(Object element) {
			ISharedImages images = PlatformUI.getWorkbench().getSharedImages();
			if (element instanceof Day) {
				return images.getImage(ISharedImages.IMG_OBJ_FOLDER);
			}
			if (element instanceof RunRecord r) {
				return images.getImage(r.hasProblems() ? ISharedImages.IMG_OBJS_ERROR_TSK : ISharedImages.IMG_OBJS_INFO_TSK);
			}
			if (element instanceof RunItem) {
				return null;
			}
			return images.getImage(ISharedImages.IMG_OBJ_FILE);
		}
	}
}
