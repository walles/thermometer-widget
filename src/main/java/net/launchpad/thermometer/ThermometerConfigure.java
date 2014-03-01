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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.SVBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Configuration dialog for the {@link ThermometerWidget}.
 *
 * @author johan.walles@gmail.com
 */
public class ThermometerConfigure extends PreferenceFragment {
    /**
     * Request code for text color.
     */
    private ColorPicker colorPicker;
    @NotNull
    private Preference colorPreference;

    @Nullable
    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inspired by http://stackoverflow.com/questions/12305563/add-buttons-to-preferencefragment
        return inflater.inflate(R.layout.preference_frame, null);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the view layout resource to use.
        addPreferencesFromResource(R.xml.preferences);

        final Activity activity = getActivity();
        assert activity != null;

        // Set up the temperature unit selection list
        ListPreference temperatureUnits =
            (ListPreference)findPreference("temperatureUnitPref");
        assert temperatureUnits != null;

        temperatureUnits.setSummary(temperatureUnits.getValue());
        temperatureUnits.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(@NotNull Preference temperaturePreference, @NotNull Object value) {
                Log.d(TAG, String.format("Temperature unit set to: %s", value));

                temperaturePreference.setSummary(value.toString());

                // true == accept the new value
                return true;
            }
        });

        setUpColorPreference();
    }

    private void setUpColorPreference() {
        // Set up the text color preference
        final Activity activity = getActivity();
        assert activity != null;

        final View colorPickerView = activity.getLayoutInflater().inflate(R.layout.color_picker, null);
        assert colorPickerView != null;

        colorPicker = (ColorPicker)colorPickerView.findViewById(R.id.picker);

        // Connect the color picker with the Saturation + Value bar
        SVBar svBar = (SVBar)colorPickerView.findViewById(R.id.svbar);
        colorPicker.addSVBar(svBar);

        Preference preference = findPreference("textColorPref");
        assert preference != null;
        colorPreference = preference;

        final SharedPreferences preferences = colorPreference.getSharedPreferences();
        assert preferences != null;

        int initialColor = preferences.getInt(colorPreference.getKey(), Color.WHITE);
        setColorPreferenceSummary(initialColor);

        colorPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(@NotNull final Preference preference) {
                int currentColor = preferences.getInt(preference.getKey(), Color.WHITE);

                // Inspired by http://stackoverflow.com/questions/6526874/call-removeview-on-the-childs-parent-first
                ViewGroup parent = (ViewGroup)colorPickerView.getParent();
                if (parent != null) {
                    parent.removeView(colorPickerView);
                }

                colorPicker.setColor(currentColor);
                colorPicker.setOldCenterColor(currentColor);

                final AlertDialog.Builder aBuilder = new AlertDialog.Builder(activity);
                aBuilder.setView(colorPickerView);
                aBuilder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        setColor(colorPicker.getColor());
                    }
                });
                aBuilder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // This method intentionally left blank
                    }
                });
                aBuilder.show();
                return true;
            }
        });

    }

    private void setColorPreferenceSummary(int color) {
        String colorString = String.format("0x%06x", color & 0xffffff);
        colorPreference.setSummary("Current color: " + colorString);
    }

    private void setColor(int color) {
        SharedPreferences sharedPreferences = colorPreference.getSharedPreferences();
        assert sharedPreferences != null;

        boolean colorUpdated =
                sharedPreferences
                        .edit()
                        .putInt(colorPreference.getKey(), color)
                        .commit();
        if (!colorUpdated) {
            Log.e(TAG, String.format("Failed to commit color preference change: 0x%06x", color));
            return;
        }
        Log.d(TAG, String.format("New color picked: 0x%06x", color));

        setColorPreferenceSummary(color);
    }
}
