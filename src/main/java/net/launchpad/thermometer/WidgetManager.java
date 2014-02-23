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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.Location;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.RemoteViews;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class contains all the logic for {@link ThermometerWidget}.
 *
 * @author johan.walles@gmail.com
 */
public class WidgetManager extends Service {
    /**
     * Used for tagging update intents with why they were sent.
     */
    private final static String UPDATE_REASON = "Update Reason";

    /**
     * Listens for events and requests widget updates as required.
     */
    @Nullable
    private UpdateListener updateListener;

    /**
     * Must be accessed through {@link #getPreferences()}.
     */
    private SharedPreferences preferences;

    /**
     * You need to synchronize on this before accessing any of the other
     * fields of this class.
     */
    private final Object weatherLock = new Object();

    /**
     * The latest weather measurement.
     * <p>
     * You must synchronize on {@link #weatherLock} before accessing this.
     */
    @Nullable
    private Weather weather;

    /**
     * Has the periodic updates alarm been registered?
     */
    private boolean periodicUpdateSet = false;

    /**
     * How we're currently doing on getting a good temperature reading for
     * the user.
     */
    private String status;

    /**
     * Thread that fetches temperature data for us.
     */
    private final TemperatureFetcher temperatureFetcher;

    /**
     * This thing puts log messages into files for us.
     */
    private Process logcat;

    /**
     * If non-null, we're having problems with the Google Play Services API, and invoking this intent should resolve
     * them.
     */
    private PendingIntent gpsaResolution;

    /**
     * Create a new widget manager.
     */
    public WidgetManager() {
        temperatureFetcher = new TemperatureFetcher(this);
        temperatureFetcher.start();
    }

    /**
     * Ask logcat to start storing logs to disk in the background.
     * <p>
     * Note that the log files will be read by
     * {@link net.launchpad.thermometer.ThermometerLogViewer.ReadLogsTask#getStoredLogs()},
     * so if this method changes, that one needs to change as well.
     */
    @Override
    public void onCreate() {
        super.onCreate();

        File logdir = getDir("logs", MODE_PRIVATE);
        File logfile = new File(logdir, "log");

        // Print a banner to the current log file so the restart can be easily spotted
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(new FileWriter(logfile));
            writer.println();
            writer.println("--- Launching the Thermometer Widget ---");
            writer.println();
        } catch (IOException e) {
            Log.w(TAG, "Writing restarting banner to log file failed: " + logfile.getAbsolutePath(), e);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }

        try {
            logcat = Runtime.getRuntime().exec(new String[] {
                    "logcat",
                    "-v", "time",
                    "-f", logfile.getAbsolutePath(),
                    "-n", "3",
                    "-r", "16"
            });

            Log.i(TAG, "Background logcat started, logging into " + logdir);
        } catch (IOException e) {
            Log.e(TAG, "Executing logcat failed", e);
        }
    }

    public synchronized SharedPreferences getPreferences() {
        if (preferences == null) {
            preferences = PreferenceManager.getDefaultSharedPreferences(this);
        }

        return preferences;
    }

    /**
     * Update / initialize / shut down widgets.
     *
     * @param context Used for creating a widget update {@link Intent}.
     *
     * @param why Why the update is wanted.
     */
    public static void onUpdate(@NotNull Context context, @NotNull UpdateReason why) {
        Intent intent = new Intent(context, WidgetManager.class);
        intent.putExtra(UPDATE_REASON, why.name());
        context.startService(intent);
    }

    /**
     * Tell us what the weather is like.
     *
     * @param weather What the weather is like.
     */
    public void setWeather(@Nullable Weather weather) {
        if (weather == null) {
            return;
        }

        if (weather.getObservationTime() == null) {
            Log.e(TAG, "New weather observation has no time stamp, dropping it");
            return;
        }

        synchronized (weatherLock) {
            if (this.weather != null && this.weather.getAgeMinutes() <= weather.getAgeMinutes()) {
                Log.e(TAG, "New weather older than current weather, dropping it");
                return;
            }

            this.weather = weather;
            updateUi();
        }
    }

    /**
     * What's the weather like around here?
     *
     * @return What the weather is like around here.
     */
    @Nullable
    public Weather getWeather() {
        synchronized (weatherLock) {
            return this.weather;
        }
    }

    /**
     * Do we know what the weather is like?
     *
     * @return True if the weather is known. False otherwise (for a while during startup).
     */
    public boolean hasWeather() {
        return getWeather() != null;
    }

    /**
     * How are we doing on fetching the weather?
     *
     * @param status A status string.
     *
     * @param gpsaResolution A way to resolve Google Play Services API connection problems
     */
    public void setStatus(@NotNull String status, @Nullable PendingIntent gpsaResolution) {
        synchronized (weatherLock) {
            Calendar now = new GregorianCalendar();

            if (gpsaResolution == null) {
                this.status = Util.toHoursString(now, DateFormat.is24HourFormat(this)) + " " + status;
            } else {
                // Google Play Services API problem resolutions are timeless
                this.status = status;
            }
            Log.i(TAG, "Set user visible status: " + status);

            this.gpsaResolution = gpsaResolution;
            Log.i(TAG, "Google Play Services problem resolution is "
                    + (gpsaResolution == null ? "null" : "non-null"));

            // Show the new status to the user
            updateUi();
        }
    }

    /**
     * How are we doing on fetching the weather?
     *
     * @param status A status string.
     */
    public void setStatus(@NotNull String status) {
        synchronized (weatherLock) {
            if (gpsaResolution != null) {
                Log.w(TAG, "Implicitly nulling out GPSA resolution from: " + this.status);
            }
            setStatus(status, null);
        }
    }

    /**
     * How are we doing on fetching the weather?
     *
     * @return A status string.
     */
    public String getStatus() {
        synchronized (weatherLock) {
            if (status == null) {
                setStatus("Initializing...");
            }
            return status;
        }
    }

    private PendingIntent getGpsaResolution() {
        synchronized (weatherLock) {
            return gpsaResolution;
        }
    }

    /**
     * The reason for a call to {@link WidgetManager#updateMeasurement(UpdateReason)}.
     */
    public enum UpdateReason {
        /**
         * Don't know, please don't use.
         */
        UNKNOWN,

        /**
         * We just got network connectivity.
         */
        NETWORK_AVAILABLE,

        /**
         * We moved.
         */
        LOCATION_CHANGED,

        /**
         * The display needs updating, or the regular-updates timer has expired.
         */
        DISPLAY_OR_TIMER,

        /**
         * We should try re-connecting to the Google Play Services API.
         */
        GPSA_RECONNECT
    }

    /**
     * Take a new weather measurement for the widget to display.
     *
     * @param why Why should the temperature be updated?
     */
    public void updateMeasurement(@NotNull UpdateReason why) {
        Log.d(TAG, "Weather observation fetch requested (" + why + ")...");

        Location currentLocation;
        synchronized (weatherLock) {
            if (updateListener == null) {
                Log.e(TAG, "Can't get location, update listener not available");
                return;
            }

            currentLocation = updateListener.getLocation();
        }
        if (currentLocation == null) {
            Log.d(TAG, "Don't know where we are, can't fetch any weather");
            setWeather(null);
            return;
        }

        if (why != UpdateReason.LOCATION_CHANGED) {
            synchronized (weatherLock) {
                if (weather != null && weather.getAgeMinutes() < 30) {
                    Log.d(TAG,
                            String.format("Current observation is %d minutes fresh and we haven't moved, skipping",
                                    weather.getAgeMinutes()));
                    return;
                }
            }
        }

        temperatureFetcher.fetchTemperature(
            currentLocation.getLatitude(),
            currentLocation.getLongitude());
    }

    /**
     * Return widget IDs for all active Thermometer Widgets.
     *
     * @return widget IDs for all active Thermometer Widgets.
     *
     * @see AppWidgetManager#getAppWidgetIds(ComponentName)
     */
    private int[] getWidgetIds() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        assert manager != null;

        int[] appWidgetIds =
            manager.getAppWidgetIds(
                new ComponentName(
                    "net.launchpad.thermometer",
                    ThermometerWidget.class.getCanonicalName()));
        Log.d(TAG, "Got widget IDs: " + Arrays.toString(appWidgetIds));
        return appWidgetIds;
    }

    /**
     * Refresh the widget display.
     */
    public void updateUi() {
        Log.d(TAG, "Updating widget display...");

        WeatherPresenter weatherPresenter = new WeatherPresenter(getWeather(), getStatus());
        weatherPresenter.setShowMetadata(getPreferences().getBoolean("showMetadataPref", false));
        weatherPresenter.setWithWindChill(getPreferences().getBoolean("windChillPref", false));
        weatherPresenter.setForceShowExcuse(getGpsaResolution() != null);
        weatherPresenter.setUseCelsius(!Util.isFahrenheit(getPreferences().getString("temperatureUnitPref", "Celsius")));
        weatherPresenter.setUse24HoursFormat(DateFormat.is24HourFormat(this));

        int textColor = getPreferences().getInt("textColorPref", Color.WHITE);
        RemoteViews remoteViews =
                weatherPresenter.createRemoteViews(this, textColor);

        PendingIntent pendingIntent;
        PendingIntent resolution = getGpsaResolution();
        if (resolution != null) {
            pendingIntent = resolution;
        } else {
            // All is well, tell widget to launch the preferences activity on click
            Intent intent = new Intent(this, ThermometerActions.class);
            pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        remoteViews.setOnClickPendingIntent(R.id.AllOfIt, pendingIntent);

        AppWidgetManager appWidgetManager =
                AppWidgetManager.getInstance(this);
        assert appWidgetManager != null;

        synchronized (weatherLock) {
            int[] widgetIds = getWidgetIds();
            if (widgetIds.length == 0) {
                // No widgets to update, shut down
                close();
            }
            for (int widgetId : widgetIds) {
                appWidgetManager.updateAppWidget(widgetId, remoteViews);
            }
        }

        Log.d(TAG, "UI updated");
    }

    /**
     * Dispatched from {@link #onStart(Intent, int)}, call through
     * {@link #onUpdate(Context, UpdateReason)}.
     *
     * @param intent The intent triggering this request.
     */
    private void onUpdateInternal(Intent intent) {
        Log.d(TAG, "onUpdate() called");

        boolean starting = false;
        synchronized (weatherLock) {
            if (updateListener == null) {
                Log.d(TAG, "Have no update listener, registering a new one");
                updateListener = new UpdateListener(this);
                starting = true;
            } else {
                Log.d(TAG, "Not touching existing update listener");
            }

            setPeriodicUpdatesEnabled(true);
        }

        if (starting) {
            // Show some UI as quickly as possible
            updateUi();
        }

        // Schedule a temperature update
        scheduleTemperatureUpdate(intent);
    }

    /**
     * Schedule a temperature update because of an incoming event.
     * <p>
     * NOTE: This method has default protection for testing purposes
     *
     * @param intent The intent that triggered the update.
     */
    void scheduleTemperatureUpdate(@Nullable Intent intent) {
        UpdateReason why;
        String reasonName = null;
        try {
            if (intent != null) {
                reasonName = intent.getStringExtra(UPDATE_REASON);
            }
            if (reasonName != null) {
                why = UpdateReason.valueOf(reasonName);
            } else {
                why = UpdateReason.UNKNOWN;
            }
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Unsupported update reason: <" + reasonName + ">");
            why = UpdateReason.UNKNOWN;
        }

        if (why == UpdateReason.GPSA_RECONNECT) {
            if (updateListener != null) {
                updateListener.reconnectGpsa();
            } else {
                Log.i(TAG, "Ignoring Google Play Services API change");
            }
            return;
        }

        updateMeasurement(why);
    }

    /**
     * Enable / disable periodic updates.
     *
     * @param enabled True to enable periodic updates, false to disable them.
     */
    private void setPeriodicUpdatesEnabled(boolean enabled) {
        // Set up repeating updates
        Intent intent = new Intent(this, WidgetManager.class);
        PendingIntent updateIntent =
            PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        AlarmManager alarmManager =
            (AlarmManager)getSystemService(Context.ALARM_SERVICE);
        synchronized (weatherLock) {
            if (enabled) {
                if (!periodicUpdateSet) {
                    alarmManager.setInexactRepeating(
                        AlarmManager.ELAPSED_REALTIME,
                        0,
                        AlarmManager.INTERVAL_HALF_HOUR,
                        updateIntent);
                    periodicUpdateSet = true;
                }
            } else {
                alarmManager.cancel(updateIntent);
                periodicUpdateSet = false;
            }
        }
    }

    /**
     * Shut down.
     */
    private void close() {
        Log.d(TAG, "Shutting down...");

        synchronized (weatherLock) {
            setPeriodicUpdatesEnabled(false);

            if (updateListener == null) {
                Log.w(TAG,
                "No preference change listener found, should have been registered in onUpdate()");
            }

            if (updateListener != null) {
                updateListener.close();
                updateListener = null;
            } else {
                Log.w(TAG, "No update listener available, can't shut it down");
            }

            stopSelf();
        }
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (getWidgetIds().length == 0) {
            // We have no widgets, shut down and drop out
            close();
        } else {
            onUpdateInternal(intent);
        }

        // All code expects us to be around continuously, return STICKY here
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (logcat != null) {
            logcat.destroy();
            logcat = null;

            Log.i(TAG, "Background logcat stopped");
        }
    }
}
