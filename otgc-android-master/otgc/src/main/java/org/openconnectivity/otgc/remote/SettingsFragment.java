package org.openconnectivity.otgc.remote;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import org.openconnectivity.otgc.R;

import androidx.annotation.Nullable;

public class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);

        final ListPreference listSettings = (ListPreference) findPreference("list_actuators");
        setListPreferenceData(listSettings);
        final ListPreference listRooms = (ListPreference) findPreference("room_id");

        listSettings.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                setListPreferenceData(listSettings);
                return false;
            }
        });
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        /* get preference */
        Preference preference = findPreference(key);
        /* update summary */
        if (key.equals("room_id")) {
            preference.setSummary(((ListPreference) preference).getEntry());
        }

        if (key.equals("list_actuators")) {
            preference.setSummary(((ListPreference) preference).getEntry());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    protected static void setListPreferenceData(ListPreference lp) {
        CharSequence[] entries = new CharSequence[]{"actuator_bed", "actuator-bed-kid", "actuator-kitchen", "actuator-bath"};
        CharSequence[] entryValues = {"1", "2", "3", "4"};
        lp.setEntries(entries);
        lp.setDefaultValue("1");
        lp.setEntryValues(entryValues);
    }
}
