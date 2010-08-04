package net.launchpad.thermometer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.IntentService;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

    private static Set<Integer> appWidgetIds = new HashSet<Integer>();

    /**
     * A background task fetching the current outdoor temperature from the
     * Internet.
     */
    public static class TemperatureFetcher extends IntentService {
        public TemperatureFetcher() {
            super("Temperature Fetcher");
        }

        @Override
        protected void onHandleIntent(Intent intent) {
            try {
                JSONObject weather = fetchWeather();
                updateUi(weather);
            } catch (Throwable t) {
                Log.e(TAG, "Fetching / updating weather failed", t);
            }
        }

        private JSONObject fetchWeather() {
            double latitude = 59.3190;
            double longitude = 18.0518;

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
                JSONObject weather = new JSONObject(jsonString);
                return weather.getJSONObject("weatherObservation");
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

        private void updateUi(JSONObject weatherObservation) {
            String degrees = "--";

            if (weatherObservation != null) {
                try {
                    double centigrades =
                        weatherObservation.getInt("temperature");
                    degrees = Long.toString(Math.round(centigrades));
                    Log.d(TAG,
                        String.format("Got %dC, %dkts observed %sUTC at %s",
                            Math.round(centigrades),
                            weatherObservation.getInt("windSpeed"),
                            weatherObservation.getString("datetime"),
                            weatherObservation.getString("stationName")));

                    SharedPreferences preferences =
                        PreferenceManager.getDefaultSharedPreferences(this);
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
                AppWidgetManager.getInstance(this);

            remoteViews.setTextViewText(R.id.TextView, degrees + "°");
            for (int widgetId : appWidgetIds) {
                appWidgetManager.updateAppWidget(widgetId, remoteViews);
            }

            Log.d(TAG, "UI updated");
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager,
        int[] updatedAppWidgetIds)
    {
        Log.d(TAG, "onUpdate() called with ids: "
            + Arrays.toString(updatedAppWidgetIds));

        for (int updatedId : updatedAppWidgetIds) {
            appWidgetIds.add(updatedId);
        }

        // Tell all widgets to launch the preferences activity on click
        for (int id : appWidgetIds) {
            Log.d(TAG, "Registering onClick() listener for widget " + id);

            // Create an Intent to launch the preferences activity
            Intent intent = new Intent(context, ThermometerConfigure.class);
            PendingIntent pendingIntent =
                PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

            // Get the layout for the App Widget and attach an on-click listener to the button
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.main);
            views.setOnClickPendingIntent(R.id.TextView, pendingIntent);

            // Tell the AppWidgetManager to go live with the new pending intent
            appWidgetManager.updateAppWidget(id, views);
        }
        update(context);
    }

    /**
     * Update what the widget displays.
     */
    public static void update(Context context) {
        Log.d(TAG, "update() called");
        context.startService(new Intent(context, TemperatureFetcher.class));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive() called");
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
    public void onDeleted(Context context, int[] deletedIds) {
        Log.d(TAG, "onDeleted() called");

        // Forget deleted widget IDs
        for (Integer deletedId : deletedIds) {
            appWidgetIds.remove(deletedId);

            Log.d(TAG, "Forgetting deleted widget " + deletedId);
        }
        Log.d(TAG, "Still active widgets: " + appWidgetIds);

        super.onDeleted(context, deletedIds);
    }
}
