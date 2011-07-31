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
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;

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
    private JSONObject weather;

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
     */
    public static void onUpdate(Context context) {
        Intent intent = new Intent(context, WidgetManager.class);
        context.startService(intent);
    }

    /**
     * Tell us what the weather is like.
     *
     * @param weather What the weather is like.
     */
    public void setWeather(JSONObject weather) {
        if (weather != null) {
            Calendar observationTime;
            try {
                observationTime = parseDateTime(weather);
            } catch (JSONException e) {
                Log.e(TAG, "Can't parse time from new weather observation, dropping it", e);
                return;
            }
            long observationAgeMinutes =
                (System.currentTimeMillis() - observationTime.getTimeInMillis()) / (1000 * 60);
            if (observationAgeMinutes > MAX_WEATHER_AGE_MINUTES) {
                Log.w(TAG, "Ignoring observation from " + observationAgeMinutes + " minutes ago");
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

                Calendar lastObservationTime;
                try {
                    lastObservationTime = parseDateTime(this.weather);
                } catch (JSONException e) {
                    Log.e(TAG, "Can't parse time from last weather observation, keeping it and hoping for the best", e);
                    return;
                }

                long lastObservationAgeMs =
                    System.currentTimeMillis()
                    - lastObservationTime.getTimeInMillis();
                long lastObservationAgeMinutes =
                    lastObservationAgeMs / (60 * 1000);
                if (lastObservationAgeMinutes > MAX_WEATHER_AGE_MINUTES) {
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
    public JSONObject getWeather() {
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
     * Take a new weather measurement for the widget to display.
     */
    public void updateMeasurement() {
        Log.d(TAG, "Initiating new weather observation fetch...");

        Location currentLocation = getLocation();
        if (currentLocation == null) {
            Log.d(TAG, "Don't know where we are, can't fetch any weather");
            setStatus("Locating phone...");
            setWeather(null);
        } else {
            temperatureFetcher.fetchTemperature(
                currentLocation.getLatitude(),
                currentLocation.getLongitude());
        }
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
     * Parser for timestamp strings in the following format: "2010-07-29 10:20:00"
     */
    private final Pattern DATE_PARSE =
        Pattern.compile("([0-9]+).([0-9]+).([0-9]+).([0-9]+).([0-9]+).([0-9]+)");

    /**
     * Convert a string to a Calendar object.
     *
     * @param timeString An UTC time stamp in the following format: "2010-07-29 10:20:00"
     *
     * @return A calendar object representing the same time as the timeString
     *
     * @throws RuntimeException if the string cannot be parsed
     */
    private Calendar parseDateTime(String timeString) {
        Matcher match = DATE_PARSE.matcher(timeString);
        if (!match.matches()) {
            throw new RuntimeException("Can't parse time string: " + timeString);
        }

        int year = Integer.valueOf(match.group(1));
        int month = Integer.valueOf(match.group(2));
        int day = Integer.valueOf(match.group(3));

        int hour = Integer.valueOf(match.group(4));
        int minute = Integer.valueOf(match.group(5));
        int second = Integer.valueOf(match.group(6));

        Calendar utcCalendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        utcCalendar.set(year, month, day, hour, minute, second);

        // Convert from UTC to the local time zone
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(utcCalendar.getTime());
        return calendar;
    }

    /**
     * Refresh the widget display.
     */
    public void updateUi() {
        Log.d(TAG, "Updating widget display...");
        JSONObject weatherObservation = getWeather();

        SharedPreferences preferences =
            PreferenceManager.getDefaultSharedPreferences(this);
        boolean windChillComputed = false;

        String degrees = "--";
        String metadata = getStatus();
        if (weatherObservation != null) {
            try {
                double centigrades =
                    weatherObservation.getInt("temperature");
                double windKnots = weatherObservation.getDouble("windSpeed");
                degrees = Long.toString(Math.round(centigrades));
                Calendar date =
                    parseDateTime(weatherObservation);
                Log.d(TAG,
                    String.format("Weather data is %dC, %dkts observed %sUTC at %s",
                        Math.round(centigrades),
                        Math.round(windKnots),
                        weatherObservation.getString("datetime"),
                        weatherObservation.getString("stationName")));

                if (preferences.getBoolean("showMetadataPref", false)) {
                    metadata =
                        toHoursString(date)
                        + " "
                        + weatherObservation.getString("stationName");
                } else {
                    metadata = "";
                }

                if (preferences.getBoolean("windChillPref", false)) {
                    double windKmh = 1.85 * windKnots;

                    // From: http://en.wikipedia.org/wiki/Wind_chill#North_American_wind_chill_index
                    if (centigrades > 10.0) {
                        Log.d(TAG, "Not computing wind chill over 10C: "
                            + Math.round(centigrades) + "C");
                    } else if (windKmh < 4.8) {
                        Log.d(TAG, "Not computing wind chill under 4.8km/h: "
                            + Math.round(windKmh) + "km/h");
                    } else {
                        double windKmhTo0_16 = Math.pow(windKmh, 0.16);
                        double adjustedCentigrades =
                            13.12
                            + 0.6215 * centigrades
                            - 11.37 *  windKmhTo0_16
                            + 0.3965 * centigrades * windKmhTo0_16;

                        if (Math.round(adjustedCentigrades) != Math.round(centigrades)) {
                            centigrades = adjustedCentigrades;
                            Log.d(TAG,
                                "Temperature adjusted for wind chill to "
                                + Math.round(centigrades) + "C");
                            windChillComputed = true;
                        } else {
                            Log.d(TAG,
                                "Wind chill adjustment didn't change centigrades: "
                                + Math.round(centigrades));
                        }
                        degrees = Long.toString(Math.round(centigrades));
                    }
                } else {
                    Log.d(TAG, "Wind chill calculations not enabled, sticking to "
                        + Math.round(centigrades) + "C");
                }

                if ("Farenheit".equals(preferences.getString("temperatureUnitPref", "Celsius"))) {
                    double farenheit = centigrades * 9.0 / 5.0 + 32.0;
                    degrees = Long.toString(Math.round(farenheit));
                }
            } catch (JSONException e) {
                Log.e(TAG, "Parsing weather data failed:\n"
                    + weatherObservation, e);
            }
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
        if (isPositioningEnabled()) {
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
     * Extract a time stamp from a weather observation.
     *
     * @param weatherObservation The weather observation
     *
     * @return When the weather was observed.
     *
     * @throws JSONException If parsing the JSON object fails.
     */
    private Calendar parseDateTime(JSONObject weatherObservation)
        throws JSONException
    {
        return parseDateTime(weatherObservation.getString("datetime"));
    }

    /**
     * Dispatched from {@link #onStart(Intent, int)}, call through
     * {@link #onUpdate(Context)}.
     */
    private void onUpdateInternal() {
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
        updateMeasurement();
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
     * @return True if the intent was handled, false otherwise
     */
    private boolean handleStart() {
        if (getWidgetIds().length == 0) {
            // We have no widgets, shut down and drop out
            close();
            return true;
        } else {
            onUpdateInternal();
            return true;
        }
    }

    @Override
    public void onStart(Intent intent, int startId) {
        handleStart();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handleStart();

        // FIXME: Should we return sticky here on unknown intents? /JW-2010aug19
        return START_STICKY;
    }
}
