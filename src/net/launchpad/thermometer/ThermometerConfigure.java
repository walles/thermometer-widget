package net.launchpad.thermometer;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;

/**
 * Configuration dialog for the {@link ThermometerWidget}.
 *
 * @author johan.walles@gmail.com
 */
public class ThermometerConfigure extends PreferenceActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the view layout resource to use.
        addPreferencesFromResource(R.xml.preferences);

        // Set up the temperature unit selection list
        ListPreference temperatureUnits =
            (ListPreference)findPreference("temperatureUnitPref");
        temperatureUnits.setSummary(temperatureUnits.getValue());
        temperatureUnits.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object value) {
                ((ListPreference)preference).setSummary(value.toString());

                // true == accept the new value
                return true;
            }
        });

        // Register "success" as a result for once the user is done
        setResult(RESULT_OK);
    }
}
