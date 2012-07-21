package org.tristategt.gis;

import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

public class PrefsActivity extends PreferenceFragment {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		PreferenceManager.setDefaultValues(getActivity(),
                R.xml.prefs, false);
		
		addPreferencesFromResource(R.xml.prefs);
	}
}
