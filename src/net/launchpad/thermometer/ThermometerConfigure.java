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

import static net.launchpad.thermometer.ThermometerWidget.TAG;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Configuration dialog for the {@link ThermometerWidget}.
 *
 * @author johan.walles@gmail.com
 */
public class ThermometerConfigure extends PreferenceActivity {
    /**
     * Request code for text color.
     */
    private final static int REQUEST_TEXT_COLOR = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the view layout resource to use.
        addPreferencesFromResource(R.xml.preferences);

        // Fill in unset preferences from defaults
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        // Set up the temperature unit selection list
        ListPreference temperatureUnits =
            (ListPreference)findPreference("temperatureUnitPref");
        temperatureUnits.setSummary(temperatureUnits.getValue());
        temperatureUnits.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                ((ListPreference)preference).setSummary(value.toString());

                // true == accept the new value
                return true;
            }
        });

        // Set up the text color preference
        ColorPreferenceHandler.handle(
            findPreference("textColorPref"), REQUEST_TEXT_COLOR, this, Color.WHITE);

        // Register "success" as a result for once the user is done
        setResult(RESULT_OK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (resultCode == RESULT_CANCELED) {
            Log.d(TAG, "Ignoring CANCEL result from request " + requestCode);
            return;
        }

        if (data != null && data.hasExtra("org.openintents.extra.COLOR")) {
            int color = data.getIntExtra("org.openintents.extra.COLOR", 0);

            SharedPreferences.Editor preferencesEditor =
                PreferenceManager.getDefaultSharedPreferences(this).edit();
            if (requestCode == REQUEST_TEXT_COLOR) {
                Log.d(TAG, String.format("Text color updated to: 0x%06x", color));
                preferencesEditor.putInt("textColorPref", color);
            } else {
                Log.w(TAG,
                    String.format("Got color selection 0x%06x in unknown request code %d",
                        color,
                        requestCode));
            }
            if (!preferencesEditor.commit()) {
                Log.w(TAG, "Failed updating preferences with new color");
            }
        } else {
            Log.w(TAG,
                String.format("Ignoring activity result: request=%d, result=%d, data=%s",
                    requestCode, resultCode, data != null ? data.toString() : null));
        }
    }
}
