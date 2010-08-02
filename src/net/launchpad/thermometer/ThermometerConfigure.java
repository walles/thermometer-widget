package net.launchpad.thermometer;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
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
    int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

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

        // Find the widget id from the intent.
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        // If they gave us an intent without the widget id, just bail.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
        }

        // Register "success" as a result for once the user is done
        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, resultValue);
    }

    @Override
    protected void onStop() {
        super.onStop();

        // FIXME: The intention here is that the widget should be displayed
        // no matter how the user exits the preferences screen.  Pressing the
        // back button or the home button should both work.  But it doesn't
        // work when pressing the home button.
        //
        // It might be that we should call the other finish() variant with
        // the requestId, but I don't know where to find the requestId.  It's
        // not in the extras bundle, I've already checked.  Reading the Android
        // source code could help...
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        ThermometerWidget.update(this, new int[] { appWidgetId });
    }
}
