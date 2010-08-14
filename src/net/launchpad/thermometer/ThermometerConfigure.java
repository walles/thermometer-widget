/*
 * Thermomether Widget - An Android widget showing the outdoor temperature.
 * Copyright (C) 2010  Johan Walles, johan.walles@gmail.com
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
