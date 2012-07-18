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
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.RemoteViews;

/**
 * This class contains all the logic for {@link ThermometerWidget}.
 *
 * @author johan.walles@gmail.com
 */
public class WidgetManager extends Service {
    /**
     * We don't want to show weather observations older than this.
     */
    private static final int MAX_WEATHER_AGE_MINUTES = 190;

    /**
     * Used for tagging log messages.
     */
    private final static String TAG = ThermometerWidget.TAG;

    /**
     * Used for tagging update intents with why they were sent.
     */
    private final static String UPDATE_REASON = "Update Reason";

    /**
     * Listens for events and requests widget updates as required.
     */
    private UpdateListener updateListener;

    /**
     * You need to synchronize on this before accessing any of the other
     * fields of this class.
     */
    private Object weatherLock = new Object();

    /**
     * The latest weather measurement.
     * <p>
     * You must synchronize on {@link #weatherLock} before accessing this.
     */
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
    private TemperatureFetcher temperatureFetcher;

    /**
     * When was the last time we were informed that some network came on-line?
     *
     * @see System#currentTimeMillis()
     */
    private long lastNetworkAvailableMs = 0;

    /**
     * Create a new widget manager.
     */
    public WidgetManager() {
        temperatureFetcher = new TemperatureFetcher(this);
        temperatureFetcher.start();
    }

    /**
     * Update / initialize / shut down widgets.
     *
     * @param context Used for creating a widget update {@link Intent}.
     *
     * @param why Why the update is wanted.
     */
    public static void onUpdate(Context context, UpdateReason why) {
        Intent intent = new Intent(context, WidgetManager.class);
        intent.putExtra(UPDATE_REASON, why.name());
        context.startService(intent);
    }

    /**
     * Tell us what the weather is like.
     *
     * @param weather What the weather is like.
     */
    public void setWeather(Weather weather) {
        if (weather != null) {
            if (weather.getObservationTime() == null) {
                Log.e(TAG, "New weather observation has no time stamp, dropping it");
                return;
            }

            if (weather.getAgeMinutes() > MAX_WEATHER_AGE_MINUTES) {
                Log.w(TAG, "Ignoring observation from " + weather.getAgeMinutes() + " minutes ago");
                weather = null;
            }
        }

        synchronized (weatherLock) {
            if (weather != null) {
                // Non-null weather update, take it!
                this.weather = weather;
                updateUi();
            } else if (this.weather == null) {
                // This block intentionally left blank; weather is already null
            } else {
                // Null weather update, only take it if our most recent
                // observation is getting too aged.

                if (this.weather.getObservationTime() == null) {
                    Log.e(TAG, "Last weather observation has no time stamp, keeping it and hoping for the best");
                    return;
                }

                if (this.weather.getAgeMinutes() > MAX_WEATHER_AGE_MINUTES) {
                    // Last observation is too old.  Give up and null out our
                    // weather observation.
                    this.weather = null;
                    updateUi();
                }
            }
        }
    }

    /**
     * What's the weather like around here?
     *
     * @return What the weather is like around here.
     */
    public Weather getWeather() {
        synchronized (weatherLock) {
            return this.weather;
        }
    }

    /**
     * Return a String representing the given time of day (hours and minutes)
     * according to the user's system settings.
     *
     * @param time A time of day.
     *
     * @return Either "15:42" or "3:42PM".
     */
    private CharSequence toHoursString(Calendar time) {
        String format;

        if (DateFormat.is24HourFormat(this)) {
            format = "kk:mm";
        } else {
            format = "h:mma";
        }

        return DateFormat.format(format, time);
    }

    /**
     * How are we doing on fetching the weather?
     *
     * @param status A status string.
     */
    public void setStatus(String status) {
        synchronized (weatherLock) {
            Calendar now = new GregorianCalendar();

            this.status =  toHoursString(now) + " " + status;

            Log.i(TAG, "Set user visible status: " + status);

            // Show the new status to the user
            updateUi();
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

    /**
     * Find out if we're running on emulated hardware.
     *
     * @return true if we're running on the emulator, false otherwise
     */
    private boolean isRunningOnEmulator() {
        return "unknown".equals(Build.BOARD);
    }

    /**
     * Get the phone's last known location.
     *
     * @return the phone's last known location.
     */
    private Location getLocation() {
        LocationManager locationManager =
            (LocationManager)getSystemService(Context.LOCATION_SERVICE);

        Location lastKnownLocation =
            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

        if (lastKnownLocation == null && isRunningOnEmulator()) {
            Log.i(TAG,
                "Location unknown but running on emulator, hard coding coordinates to Johan's place");
            lastKnownLocation = new Location("Johan");
            lastKnownLocation.setLatitude(59.3190);
            lastKnownLocation.setLongitude(18.0518);
        }
        return lastKnownLocation;
    }

    /**
     * Is network positioning enabled?
     *
     * @return True if network positioning is enabled.  False otherwise.
     */
    private boolean isPositioningEnabled() {
        LocationManager locationManager =
            (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
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
        DISPLAY_OR_TIMER
    }

    /**
     * Take a new weather measurement for the widget to display.
     *
     * @param why Why should the temperature be updated?
     */
    public void updateMeasurement(UpdateReason why) {
        if (why == null) {
            why = UpdateReason.UNKNOWN;
        }
        Log.d(TAG, "Initiating new weather observation fetch (" + why + ")...");

        Location currentLocation = getLocation();
        if (currentLocation == null) {
            Log.d(TAG, "Don't know where we are, can't fetch any weather");
            setStatus("Locating phone...");
            setWeather(null);
            return;
        }

        if (why == UpdateReason.NETWORK_AVAILABLE) {
            synchronized (weatherLock) {
                long lastUpdateMsAgo =
                    System.currentTimeMillis() - lastNetworkAvailableMs;
                long lastUpdateMinutesAgo = lastUpdateMsAgo / (60 * 1000);

                if (lastUpdateMinutesAgo < 30) {
                    Log.d(TAG,
                        "Last network-available update "
                            + lastUpdateMinutesAgo
                            + " minutes ago, want at least 30, skipping");
                    return;
                }
                lastNetworkAvailableMs = System.currentTimeMillis();

                if (weather != null && weather.getAgeMinutes() < 45) {
                    Log.d(TAG, String.format("Network available, but current observation is %d minutes fresh, skipping",
                            weather.getAgeMinutes()));
                    return;
                }
            }

            Log.d(TAG, "Network available, updating!");
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
        Weather weatherObservation = getWeather();

        SharedPreferences preferences =
            PreferenceManager.getDefaultSharedPreferences(this);
        boolean windChillComputed = false;

        String degrees = "--";
        String metadata = getStatus();
        if (weatherObservation != null) {
            Log.d(TAG, "Weather data is " + weatherObservation);

            if (preferences.getBoolean("showMetadataPref", false)) {
                metadata =
                    toHoursString(weatherObservation.getObservationTime()).toString();
                if (weatherObservation.getStationName() != null) {
                    metadata += " " + weatherObservation.getStationName();
                }
            } else {
                metadata = "";
            }

            boolean withWindChill =
                preferences.getBoolean("windChillPref", false);
            boolean inFarenheit =
                "Farenheit".equals(preferences.getString("temperatureUnitPref", "Celsius"));

            int unchilledDegrees;
            int chilledDegrees;
            if (inFarenheit) {
                // Liberian users and some others
                chilledDegrees = weatherObservation.getFarenheit(withWindChill);
                unchilledDegrees = weatherObservation.getFarenheit(false);
            } else {
                chilledDegrees = weatherObservation.getCentigrades(withWindChill);
                unchilledDegrees = weatherObservation.getCentigrades(false);
            }
            degrees = Integer.toString(chilledDegrees);
            windChillComputed = (chilledDegrees != unchilledDegrees);
        }

        // Publish the fetched temperature
        RemoteViews remoteViews =
            new RemoteViews(ThermometerWidget.class.getPackage().getName(),
                R.layout.main);

        String temperatureString;
        if (windChillComputed) {
            temperatureString = degrees + "*";
        } else {
            temperatureString = degrees + "°";
        }

        Log.d(TAG, "Displaying temperature: <" + temperatureString + ">");
        remoteViews.setTextViewText(R.id.TemperatureView, temperatureString);
        remoteViews.setTextColor(R.id.TemperatureView,
            preferences.getInt("textColorPref", Color.WHITE));

        Log.d(TAG, "Displaying metadata: <" + metadata + ">");
        remoteViews.setTextViewText(R.id.MetadataView, metadata);
        remoteViews.setTextColor(R.id.MetadataView,
            preferences.getInt("textColorPref", Color.WHITE));

        Intent intent;
        if (isPositioningEnabled() || isRunningOnEmulator()) {
            // Tell widget to launch the preferences activity on click
            intent = new Intent(this, ThermometerConfigure.class);
        } else {
            // Don't know where we are, launch positioning settings on click
            intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        }
        PendingIntent pendingIntent =
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.AllOfIt, pendingIntent);

        AppWidgetManager appWidgetManager =
            AppWidgetManager.getInstance(this);
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

        synchronized (weatherLock) {
            if (updateListener == null) {
                Log.d(TAG, "Have no update listener, registering a new one");
                updateListener = new UpdateListener(this);
            } else {
                Log.d(TAG, "Not touching existing update listener");
            }

            setPeriodicUpdatesEnabled(true);
        }

        // Show some UI as quickly as possible
        updateUi();

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
    void scheduleTemperatureUpdate(Intent intent) {
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

    /**
     * Handle the intent from {@link #onStart(Intent, int)} /
     * {@link #onStartCommand(Intent, int, int)}.
     *
     * @param intent the intent from onStart() / onStartCommand().
     *
     * @return True if the intent was handled, false otherwise
     */
    private boolean handleStart(Intent intent) {
        if (getWidgetIds().length == 0) {
            // We have no widgets, shut down and drop out
            close();
            return true;
        } else {
            onUpdateInternal(intent);
            return true;
        }
    }

    @Override
    public void onStart(Intent intent, int startId) {
        handleStart(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handleStart(intent);

        // FIXME: Should we return sticky here on unknown intents? /JW-2010aug19
        return START_STICKY;
    }
}
