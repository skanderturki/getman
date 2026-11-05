package com.getman.ui.preferences;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.RadioGroupFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

import com.getman.core.http.TrustMode;

/** Window › Preferences › GetMan. Changes apply to the next request; no restart needed. */
public class GetmanPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	public GetmanPreferencePage() {
		super(GRID);
		setDescription("Settings for the GetMan REST client. Changes apply to the next request you send.");
	}

	@Override
	public void init(IWorkbench workbench) {
		setPreferenceStore(new ScopedPreferenceStore(InstanceScope.INSTANCE, GetmanPreferences.QUALIFIER));
	}

	@Override
	protected void createFieldEditors() {
		IntegerFieldEditor connect = new IntegerFieldEditor(GetmanPreferences.CONNECT_TIMEOUT_SECONDS,
				"&Connect timeout (seconds):", getFieldEditorParent());
		connect.setValidRange(1, 600);
		addField(connect);

		IntegerFieldEditor request = new IntegerFieldEditor(GetmanPreferences.REQUEST_TIMEOUT_SECONDS,
				"&Request timeout (seconds):", getFieldEditorParent());
		request.setValidRange(1, 3600);
		addField(request);

		addField(new BooleanFieldEditor(GetmanPreferences.FOLLOW_REDIRECTS, "&Follow redirects (3xx)",
				getFieldEditorParent()));

		String[][] trust = new String[TrustMode.values().length][];
		for (int i = 0; i < trust.length; i++) {
			TrustMode m = TrustMode.values()[i];
			trust[i] = new String[] { m.label(), m.name() };
		}
		addField(new RadioGroupFieldEditor(GetmanPreferences.TRUST_MODE, "HTTPS certificates", 1, trust,
				getFieldEditorParent(), true));

		IntegerFieldEditor history = new IntegerFieldEditor(GetmanPreferences.HISTORY_SIZE,
				"&History: keep the last N requests (0 turns history off):", getFieldEditorParent());
		history.setValidRange(0, 1000);
		addField(history);
	}
}
