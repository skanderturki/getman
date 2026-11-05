package com.getman.ui.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;

/** Registers GetMan's default preference values (see plugin.xml). */
public class PreferenceInitializer extends AbstractPreferenceInitializer {
	@Override
	public void initializeDefaultPreferences() {
		GetmanPreferences.initializeDefaults();
	}
}
