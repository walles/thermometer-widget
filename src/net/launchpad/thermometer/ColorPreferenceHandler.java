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

import android.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;
import android.net.Uri;
import android.preference.Preference;
import android.util.Log;

/**
 * This class acts as a preference OnClick listener.  It enables the
 * user to select a color.
 *
 * @author johan.walles@gmail.com
 */
public class ColorPreferenceHandler
implements Preference.OnPreferenceClickListener
{
    /**
     * This activity will receive the new color in
     * {@link Activity#onActivityResult(int, int, Intent)}.
     */
    private Activity activity;

    /**
     * This request code will be delivered to
     * {@link Activity#onActivityResult(int, int, Intent)}.
     */
    private int requestCode;

    /**
     * This is the preference that we control.
     */
    private Preference preference;

    /**
     * Color used if no preference set.
     */
    private int defaultColor;

    /**
     * We need a reference to the preferences change listener for it not to
     * disappear on us after a while:
     * http://stackoverflow.com/questions/2542938/sharedpreferences-onsharedpreferencechangelistener-not-being-called-consistently/3104265#3104265
     */
    private OnSharedPreferenceChangeListener preferenceChangeListener;

    /**
     * Make a Preference handle color changes.
     *
     * @param preference The preference that we control.
     *
     * @param requestCode The request code to be delivered to the activity.
     *
     * @param activity This activity will receive results to its
     * {@link Activity#onActivityResult(int, int, Intent)} method.
     *
     * @param defaultColor Color to use if the color preference hasn't been set.
     *
     * @see Color
     */
    public static void handle(Preference preference, int requestCode, Activity activity, int defaultColor) {
        preference.setOnPreferenceClickListener(
            new ColorPreferenceHandler(preference, requestCode, activity, defaultColor));
    }

    /**
     * Create a new color preference handler.
     *
     * @param preference The preference that we control.
     *
     * @param requestCode The request code to be delivered to the activity.
     *
     * @param activity This activity will receive results to its
     * {@link Activity#onActivityResult(int, int, Intent)} method.
     *
     * @param defaultColor Color to use if the color preference hasn't been set.
     */
    private ColorPreferenceHandler(
        final Preference preference,
        int requestCode,
        Activity activity,
        int defaultColor)
    {
        if (preference == null) {
            throw new NullPointerException("Preference must be non-null");
        }
        if (activity == null) {
            throw new NullPointerException("Activity must be non-null");
        }
        this.preference = preference;
        this.activity = activity;
        this.requestCode = requestCode;
        this.defaultColor = defaultColor;

        preferenceChangeListener =
            new SharedPreferences.OnSharedPreferenceChangeListener()
        {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                if (preference.getKey().equals(key)) {
                    Log.d(TAG, "Color preference updated, updating prefs UI: " + key);
                    updateSummary();
                } else {
                    Log.v(TAG, preference.getKey() + " listener ignoring " + key + " update");
                }
            }
        };

        SharedPreferences sharedPreferences = preference.getSharedPreferences();
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
        updateSummary();
    }

    /**
     * Update the summary text.
     */
    private void updateSummary() {
        String colorString = String.format("0x%06x", getColor() & 0xffffff);
        preference.setSummary("Current color: " + colorString);
    }

    /**
     * Get the current color setting.
     *
     * @return The current color setting.
     */
    private int getColor() {
        SharedPreferences preferences = preference.getSharedPreferences();
        return preferences.getInt(preference.getKey(), defaultColor);
    }

    // The variable hiding doesn't matter since the two "preference" variables
    // should both be the same. /Johan - 2010aug19
    @Override
    @SuppressWarnings("hiding")
    public boolean onPreferenceClick(Preference preference) {
        launchColorPicker(getColor());

        // true = Click handled
        return true;
    }

    /**
     * Launch the color picker.
     *
     * @param color The initial color.
     */
    private void launchColorPicker(int color) {
        try {
            // Launch the OpenIntents Color Picker
            Intent intent = new Intent();
            intent.setAction("org.openintents.action.PICK_COLOR");
            intent.putExtra("org.openintents.extra.COLOR", color);
            activity.startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            complainMissingColorPicker();
        }
    }

    /**
     * Complain to the user that we can't launch the Android Market app.
     */
    private void complainMissingMarket() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Market not available");
        builder.setMessage("Android Market not available on this phone, color picker can't be installed.");
        builder.setCancelable(true);
        builder.setNeutralButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.setIcon(R.drawable.ic_dialog_alert);
        builder.show();
    }

    /**
     * Launch Android Market so that the user can install the color picker.
     */
    private void launchColorPickerInstaller() {
        try {
            // Launch the correct market:// URL for installing the color picker
            Uri marketUri =
                Uri.parse("market://details?id=org.openintents.colorpicker");
            Intent marketIntent =
                new Intent(Intent.ACTION_VIEW, marketUri);
            activity.startActivity(marketIntent);
        } catch (ActivityNotFoundException e) {
            complainMissingMarket();
        }
    }

    /**
     * Complain to the user that the color picker is missing, and offer to
     * launch Android Market so that it can be installed.
     */
    private void complainMissingColorPicker() {
        // Pop up a dialog asking if the user wants to install the
        // color picker
        AlertDialog.Builder builder =
            new AlertDialog.Builder(activity);
        builder.setTitle("Color picker not installed");
        builder.setMessage("Install the color picker now?\n"
            + "\n"
            + "The color picker is required for you to configure widget colors.");
        builder.setCancelable(true);
        builder.setPositiveButton("Install", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                launchColorPickerInstaller();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        builder.show();
    }
}
