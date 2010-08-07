package net.launchpad.thermometer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
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

import android.app.IntentService;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

/**
 * A widget displaying the current outdoor temperature.
 *
 * @author johan.walles@gmail.com
 */
public class ThermometerWidget extends AppWidgetProvider {
    /**
     * Used for tagging log messages.
     */
    public static final String TAG = "ThermWidget";

    /**
     * The widget IDs that we know about.
     */
    private static Set<Integer> appWidgetIds = new HashSet<Integer>();

    /**
     * The latest weather measurement.
     * <p>
     * You must synchronize on {@link #weatherLock} before accessing this.
     */
    private static JSONObject weather;

    /**
     * You need to synchronize on this before accessing the {@link #weather}.
     */
    private static Object weatherLock = new Object();

    /**
     * Our last known position.
     */
    private static Location location;

    /**
     * You need to synchronize on this before accessing the {@link #location}.
     */
    private static Object locationLock = new Object();

    /**
     * A background task fetching the current outdoor temperature from the
     * Internet.
     */
    public static class TemperatureFetcher extends IntentService {
        /**
         * Construct a new temperature fetcher.
         */
        public TemperatureFetcher() {
            super("Temperature Fetcher");
        }

        @Override
        protected void onHandleIntent(Intent intent) {
            try {
                ThermometerWidget.setWeather(fetchWeather());
                updateUi(this);
            } catch (Throwable t) {
                Log.e(TAG, "Fetching / updating weather failed", t);
            }
        }

        /**
         * Fetch the weather for the current location.
         *
         * @return A JSON object with information from the nearest weather station.
         */
        private JSONObject fetchWeather() {
            Location currentLocation = ThermometerWidget.getLocation();
            if (currentLocation == null) {
                // We don't know where we are
                Log.w(TAG, "We don't know where we are, can't ask for any weather observation");
                return null;
            }

            double latitude = currentLocation.getLatitude();
            double longitude = currentLocation.getLongitude();

            // Create something like:
            // http://ws.geonames.org/findNearByWeatherJSON?lat=43&lng=-2
            // More info here:
            // http://www.geonames.org/export/JSON-webservices.html#findNearByWeatherJSON

            URL url;
            String urlString = null;
            try {
                urlString =
                    String.format("http://ws.geonames.org/findNearByWeatherJSON?lat=%.4f&lng=%.4f",
                        latitude, longitude);

                url = new URL(urlString);
                Log.v(TAG, "JSON URL created: " + url);
            } catch (MalformedURLException e) {
                Log.e(TAG, "Internal error creating JSON URL from: " + urlString, e);
                return null;
            }

            String jsonString = null;
            int attempt = 0;
            long delayMs = 5000;
            while (jsonString == null) {
                attempt++;
                boolean mightRetry = (attempt <= 5);

                try {
                    jsonString = fetchUrl(url);
                    break;
                } catch (IOException e) {
                    String retryString = "";
                    if (mightRetry) {
                        retryString =
                            ", will retry in " + (delayMs / 1000L) + "s";
                    }
                    Log.w(TAG, "Error reading weather data on attempt "
                        + attempt + retryString + ": " + url,
                        e);
                }

                if (!mightRetry) {
                    // We've done our best and failed, give up
                    break;
                }

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Log.w(TAG, "Interrupted waiting to retry temperature download, giving up", e);
                    break;
                }
                delayMs *= 2;
            }
            if (jsonString == null) {
                Log.e(TAG, "Failed reading weather data, giving up");
                return null;
            }

            try {
                JSONObject weatherObservation = new JSONObject(jsonString);
                return weatherObservation.getJSONObject("weatherObservation");
            } catch (JSONException e) {
                Log.e(TAG, "Parsing weather data failed:\n"
                    + jsonString, e);
                return null;
            }
        }

        /**
         * Download an URL into a String.
         *
         * @param url The URL to download from.
         *
         * @return The data downloaded from the URL.
         *
         * @throws IOException if downloading data from the URL fails.
         */
        private String fetchUrl(URL url) throws IOException {
            Log.d(TAG, "Fetching data from: " + url);

            StringBuilder jsonBuilder = new StringBuilder();

            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(5000);
            BufferedReader in =
                new BufferedReader(new InputStreamReader(connection.getInputStream()), 1024);
            try {
                String data;
                while ((data = in.readLine()) != null) {
                    jsonBuilder.append(data);
                    jsonBuilder.append('\n');
                }
            } finally {
                try {
                    in.close();
                } catch (IOException e) {
                    Log.w(TAG,
                        "Unable to close JSON input stream from " + url);
                }
                in = null;
            }
            return jsonBuilder.toString();
        }
    }

    /**
     * Listens for events and requests widget updates as required.
     */
    private static UpdateListener updateListener;

    /**
     * Listens for events and requests widget updates as required.
     */
    private static class UpdateListener
    implements SharedPreferences.OnSharedPreferenceChangeListener, LocationListener
    {
        private Context context;

        /**
         * Find out if we're running on emulated hardware.
         *
         * @return true if we're running on the emulator, false otherwise
         */
        private boolean isRunningOnEmulator() {
            return "unknown".equals(Build.BOARD);
        }

        public UpdateListener(Context context) {
            if (context == null) {
                throw new NullPointerException("context must be non-null");
            }
            this.context = context;

            Log.d(TAG, "Registering location listener");
            LocationManager locationManager = getLocationManager();

            // Set up an initial location, this will often be good enough
            Location lastKnownLocation =
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (lastKnownLocation == null && isRunningOnEmulator()) {
                Log.i(TAG,
                    "Location unknown but running on emulator, hard coding coordinates to Johan's place");
                lastKnownLocation = new Location("Johan");
                lastKnownLocation.setLatitude(59.3190);
                lastKnownLocation.setLongitude(18.0518);
            }
            setLocation(lastKnownLocation);

            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                47 * 60 * 1000, // Drift a bit relative to the periodic widget update
                50000, // Every 50km we move
                this);

            Log.d(TAG, "Registering preferences change notification listener");
            SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);
            preferences.registerOnSharedPreferenceChangeListener(this);
        }

        /**
         * Get a non-null location manager.
         *
         * @return A non-null location manager.
         *
         * @throws RuntimeException if the location manager cannot be found.
         */
        private LocationManager getLocationManager() {
            LocationManager locationManager =
                (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) {
                throw new RuntimeException("Location manager not found, cannot continue");
            }
            return locationManager;
        }

        public void onSharedPreferenceChanged(SharedPreferences preferences,
            String key)
        {
            getLocationManager().removeUpdates(this);

            Log.d(TAG, "Preference changed, updating UI: " + key);
            updateUi(context);
        }

        /**
         * Free up system resources and stop listening.
         */
        public void close() {
            Log.d(TAG, "Deregistering preferences change listener...");
            SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);
            preferences.unregisterOnSharedPreferenceChangeListener(this);
        }

        public void onLocationChanged(Location networkLocation) {
            ThermometerWidget.setLocation(networkLocation);
            ThermometerWidget.updateMeasurement(context);
        }

        public void onProviderDisabled(String provider) {
            if (LocationManager.NETWORK_PROVIDER.equals(provider)) {
                Log.e(TAG, "Location provider disabled: " + provider);
                // FIXME: What do we do about this?
            }
        }

        public void onProviderEnabled(String provider) {
            if (LocationManager.NETWORK_PROVIDER.equals(provider)) {
                Log.i(TAG, "Location provider enabled: " + provider);
            }
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
            if (LocationManager.NETWORK_PROVIDER.equals(provider)) {
                switch (status) {
                case LocationProvider.AVAILABLE:
                    Log.d(TAG, "Location provider available: " + provider);
                    break;
                case LocationProvider.TEMPORARILY_UNAVAILABLE:
                    Log.w(TAG, "Location provider temporarily unavailable: "
                        + provider);
                    break;
                case LocationProvider.OUT_OF_SERVICE:
                    // FIXME: What do we do about this?
                    Log.e(TAG, "Location provider out of service: "
                        + provider);
                    break;
                default:
                    Log.w(TAG, "Location provider switched to unknown status "
                        + status
                        + ": "
                        + provider);
                }
            }
        }
    }

    /**
     * Tell us what the weather is like.
     *
     * @param weather What the weather is like.
     */
    public static void setWeather(JSONObject weather) {
        synchronized (weatherLock) {
            ThermometerWidget.weather = weather;
        }
    }

    /**
     * What's the weather like around here?
     *
     * @return What the weather is like around here.
     */
    public static JSONObject getWeather() {
        synchronized (weatherLock) {
            return ThermometerWidget.weather;
        }
    }

    /**
     * Tell us where we are.
     *
     * @param location Where we are.
     */
    public static void setLocation(Location location) {
        synchronized (locationLock) {
            ThermometerWidget.location = location;
        }
    }

    /**
     * Where are we?
     *
     * @return Where we are.
     */
    public static Location getLocation() {
        synchronized (locationLock) {
            return ThermometerWidget.location;
        }
    }

    @Override
    public synchronized void onUpdate(Context context, AppWidgetManager appWidgetManager,
        int[] updatedAppWidgetIds)
    {
        Log.d(TAG, "onUpdate() called with widget ids: "
            + Arrays.toString(updatedAppWidgetIds));

        if (updateListener == null) {
            updateListener = new UpdateListener(context);
        } else {
            Log.d(TAG, "Not touching existing preferences change notification listener");
        }

        for (int updatedId : updatedAppWidgetIds) {
            appWidgetIds.add(updatedId);
        }

        // Show some UI as quickly as possible
        updateUi(context);

        // Tell all widgets to launch the preferences activity on click
        for (int id : appWidgetIds) {
            Log.d(TAG, "Registering onClick() listener for widget " + id);

            // Create an Intent to launch the preferences activity
            Intent intent = new Intent(context, ThermometerConfigure.class);
            PendingIntent pendingIntent =
                PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

            // Get the layout for the App Widget and attach an on-click listener to the widget
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.main);
            views.setOnClickPendingIntent(R.id.AllOfIt, pendingIntent);

            // Tell the AppWidgetManager to go live with the new pending intent
            appWidgetManager.updateAppWidget(id, views);
        }

        updateMeasurement(context);
    }

    /**
     * Take a new weather measurement for the widget to display.
     */
    public static void updateMeasurement(Context context) {
        Log.d(TAG, "Initiating new weather observation fetch...");
        context.startService(new Intent(context, TemperatureFetcher.class));
    }

    /**
     * Parser for timestamp strings in the following format: "2010-07-29 10:20:00"
     */
    private final static Pattern DATE_PARSE =
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
    private static Calendar parseDateTime(String timeString) {
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
    public static void updateUi(Context context) {
        Log.d(TAG, "Updating widget display...");
        JSONObject weatherObservation = getWeather();

        String degrees = "--";
        String metadata = "";
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
                    PreferenceManager.getDefaultSharedPreferences(context);

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
        AppWidgetManager appWidgetManager =
            AppWidgetManager.getInstance(context);

        remoteViews.setTextViewText(R.id.TemperatureView, degrees + "°");
        remoteViews.setTextViewText(R.id.MetadataView, metadata);
        for (int widgetId : appWidgetIds) {
            appWidgetManager.updateAppWidget(widgetId, remoteViews);
        }

        Log.d(TAG, "UI updated");
    }

    @Override
    public synchronized void onReceive(Context context, Intent intent) {
        // v1.5 fix that doesn't call onDelete Action
        final String action = intent.getAction();
        if (AppWidgetManager.ACTION_APPWIDGET_DELETED.equals(action)) {
            Log.d(TAG, "onReceive() got DELETED event");
            final int appWidgetId = intent.getExtras().getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                Log.d(TAG, "onReceive() faking call to onDeleted()");
                this.onDeleted(context, new int[] { appWidgetId });
            }
        } else {
            super.onReceive(context, intent);
        }
    }

    @Override
    public synchronized void onDeleted(Context context, int[] deletedIds) {
        Log.d(TAG, "onDeleted() called with widget ids: " +
            Arrays.toString(deletedIds));

        if (updateListener == null) {
            Log.w(TAG, "No preference change listener found, should have been registered in onUpdate()");
        }

        // Forget deleted widget IDs
        for (Integer deletedId : deletedIds) {
            if (appWidgetIds.contains(deletedId)) {
                Log.d(TAG, "Forgetting deleted widget: " + deletedId);
                appWidgetIds.remove(deletedId);
            } else {
                Log.w(TAG, "Can't forget unknown widget: " + deletedId);
            }
        }
        Log.d(TAG, "Still active widgets: " + appWidgetIds);

        if (appWidgetIds.isEmpty()) {
            Log.d(TAG, "No more widgets left...");
            if (updateListener != null) {
                updateListener.close();
                updateListener = null;
            }
        }

        super.onDeleted(context, deletedIds);
    }
}
