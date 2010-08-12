package net.launchpad.thermometer;

import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

/**
 * This class contains all the logic for {@link ThermometerWidget}.
 *
 * @author johan.walles@gmail.com
 */
public class WidgetManager extends Service {
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
    private Object lock = new Object();

    /**
     * The widget IDs that we know about.
     */
    private Set<Integer> appWidgetIds = new HashSet<Integer>();

    /**
     * The latest weather measurement.
     * <p>
     * You must synchronize on {@link #lock} before accessing this.
     */
    private JSONObject weather;

    /**
     * Our last known position.
     */
    private Location location;

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
     * This will be called regularly by the {@link AlarmManager}.
     */
    private PendingIntent updateIntent;

    /**
     * Marker field to say that this is an update requested by the
     * {@link AlarmManager}.
     */
    private final static String ALARM_CALL = "AlarmManager says hello";

    /**
     * Create a new widget manager.
     */
    public WidgetManager() {
        super();

        setStatus("Initializing...");

        temperatureFetcher = new TemperatureFetcher(this);
        temperatureFetcher.start();
    }

    private final static String UPDATED_IDS_EXTRA = "updatedIds";

    /**
     * Update / initialize widgets.
     *
     * @param updatedIds IDs of the updated widgets.
     */
    public static void onUpdate(Context context, int updatedIds[]) {
        Intent intent = new Intent(context, WidgetManager.class);
        intent.putExtra(UPDATED_IDS_EXTRA, updatedIds);
        context.startService(intent);
    }

    private final static String DELETED_IDS_EXTRA = "updatedIds";

    /**
     * Delete widgets.
     *
     * @param deletedIds IDs of the updated widgets.
     */
    public static void onDeleted(Context context, int deletedIds[]) {
        Intent intent = new Intent(context, WidgetManager.class);
        intent.putExtra(DELETED_IDS_EXTRA, deletedIds);
        context.startService(intent);
    }

    /**
     * Tell us what the weather is like.
     *
     * @param weather What the weather is like.
     */
    public void setWeather(JSONObject weather) {
        synchronized (lock) {
            this.weather = weather;
        }
    }

    /**
     * What's the weather like around here?
     *
     * @return What the weather is like around here.
     */
    public JSONObject getWeather() {
        synchronized (lock) {
            return this.weather;
        }
    }

    /**
     * Tell us where we are.
     *
     * @param location Where we are.
     */
    public void setLocation(Location location) {
        synchronized (lock) {
            this.location = location;
        }
    }

    /**
     * Where are we?
     *
     * @return Where we are.
     */
    public Location getLocation() {
        synchronized (lock) {
            return this.location;
        }
    }

    /**
     * How are we doing on fetching the weather?
     *
     * @param status A status string.
     */
    public void setStatus(String status) {
        synchronized (lock) {
            Calendar now = new GregorianCalendar();
            this.status =  String.format("%02d:%02d %s",
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE),
                status);
        }
    }

    /**
     * How are we doing on fetching the weather?
     *
     * @return A status string.
     */
    public String getStatus() {
        synchronized (lock) {
            return this.status;
        }
    }

    /**
     * Take a new weather measurement for the widget to display.
     */
    public void updateMeasurement() {
        Log.d(TAG, "Initiating new weather observation fetch...");

        Location currentLocation = getLocation();
        if (currentLocation == null) {
            Log.d(TAG, "Don't know where we are, can't fetch any weather");
        } else {
            temperatureFetcher.fetchTemperature(
                currentLocation.getLatitude(),
                currentLocation.getLongitude());
        }
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

        String degrees = "--";
        String metadata = getStatus();
        if (weatherObservation != null) {
            try {
                double centigrades =
                    weatherObservation.getInt("temperature");
                double windKnots = weatherObservation.getDouble("windSpeed");
                degrees = Long.toString(Math.round(centigrades));
                Calendar date =
                    parseDateTime(weatherObservation.getString("datetime"));
                metadata =
                    String.format("%02d:%02d %s",
                        date.get(Calendar.HOUR_OF_DAY),
                        date.get(Calendar.MINUTE),
                        weatherObservation.getString("stationName"));

                Log.d(TAG,
                    String.format("Weather data is %dC, %dkts observed %sUTC at %s",
                        Math.round(centigrades),
                        Math.round(windKnots),
                        weatherObservation.getString("datetime"),
                        weatherObservation.getString("stationName")));

                SharedPreferences preferences =
                    PreferenceManager.getDefaultSharedPreferences(this);

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
                        centigrades =
                            13.12
                            + 0.6215 * centigrades
                            - 11.37 *  windKmhTo0_16
                            + 0.3965 * centigrades * windKmhTo0_16;

                        Log.d(TAG,
                            "Temperature adjusted for wind chill to "
                            + Math.round(centigrades) + "C");
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
        remoteViews.setTextViewText(R.id.TemperatureView, degrees + "°");
        remoteViews.setTextViewText(R.id.MetadataView, metadata);

        // Tell widget to launch the preferences activity on click
        Intent intent = new Intent(this, ThermometerConfigure.class);
        PendingIntent pendingIntent =
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.AllOfIt, pendingIntent);

        AppWidgetManager appWidgetManager =
            AppWidgetManager.getInstance(this);
        synchronized (lock) {
            for (int widgetId : appWidgetIds) {
                appWidgetManager.updateAppWidget(widgetId, remoteViews);
            }
        }

        Log.d(TAG, "UI updated");
    }

    /**
     * Dispatched from {@link #onStart(Intent, int)}, call through
     * {@link #onUpdate(Context, int[])}.
     *
     * @param updatedIds IDs for updated widgets.
     */
    private void onUpdateInternal(int[] updatedIds) {
        Log.d(TAG, "onUpdate() called with widget ids: "
            + Arrays.toString(updatedIds));

        synchronized (lock) {
            if (updateListener == null) {
                Log.d(TAG, "Have no update listener, registering a new one");
                updateListener = new UpdateListener(this);
            } else {
                Log.d(TAG, "Not touching existing update listener");
            }

            for (int updatedId : updatedIds) {
                appWidgetIds.add(updatedId);
            }

            if (updateIntent == null) {
                // Set up repeating updates
                Intent intent = new Intent(this, WidgetManager.class);
                intent.putExtra(ALARM_CALL, true);
                updateIntent =
                    PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

                AlarmManager alarmManager =
                    (AlarmManager)getSystemService(Context.ALARM_SERVICE);
                alarmManager.setInexactRepeating(
                    AlarmManager.ELAPSED_REALTIME,
                    0,
                    AlarmManager.INTERVAL_HALF_HOUR,
                    updateIntent);
            }
        }

        // Show some UI as quickly as possible
        updateUi();

        // Schedule a temperature update
        updateMeasurement();
    }

    /**
     * Dispatched from {@link #onStart(Intent, int)}, call through
     * {@link #onDeleted(Context, int[])}.
     *
     * @param deletedIds IDs for deleted widgets.
     */
    private void onDeletedInternal(int[] deletedIds) {
        Log.d(TAG, "onDeleted() called with widget ids: " +
            Arrays.toString(deletedIds));

        synchronized (lock) {
            if (updateListener == null) {
                Log.w(TAG,
                "No preference change listener found, should have been registered in onUpdate()");
            }

            // Forget deleted widget IDs
            for (Integer deletedId : deletedIds) {
                if (appWidgetIds.contains(deletedId)) {
                    Log.d(TAG,
                        "Forgetting deleted widget: " + deletedId);
                    appWidgetIds.remove(deletedId);
                } else {
                    Log.w(TAG,
                        "Can't forget unknown widget: " + deletedId);
                }
            }
            Log.d(TAG,
                "Still active widgets: " + appWidgetIds);

            if (appWidgetIds.isEmpty()) {
                Log.d(TAG, "No more widgets left...");

                if (updateIntent != null) {
                    Log.d(TAG, "Shutting down periodic updates");
                    AlarmManager alarmManager =
                        (AlarmManager)getSystemService(Context.ALARM_SERVICE);
                    alarmManager.cancel(updateIntent);
                    updateIntent = null;
                } else {
                    Log.w(TAG, "Periodic updates not active, can't shut them down");
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
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public void onStart(Intent intent, int startId) {
        if (intent.hasExtra(UPDATED_IDS_EXTRA)) {
            onUpdateInternal(intent.getIntArrayExtra(UPDATED_IDS_EXTRA));
        } else if (intent.hasExtra(DELETED_IDS_EXTRA)) {
            onDeletedInternal(intent.getIntArrayExtra(DELETED_IDS_EXTRA));
        } else if (intent.hasExtra(ALARM_CALL)) {
            Log.d(TAG, "Periodic alarm received");
            updateMeasurement();
        } else {
            super.onStart(intent, startId);
        }
    }
}