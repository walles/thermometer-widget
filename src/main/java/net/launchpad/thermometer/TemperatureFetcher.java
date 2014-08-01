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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.Locale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

/**
 * A background task fetching the current outdoor temperature from the
 * Internet.
 */
public class TemperatureFetcher extends Thread implements Callback {
    /**
     * When should we at the earliest make the next attempt at fetching the weather?
     */
    private long nextFetch = 0;

    /**
     * Widget controller.
     */
    private final WidgetManager widgetManager;

    /**
     * Message handler.
     */
    private Handler handler;

    /**
     * Construct a new temperature fetcher.
     *
     * @param widgetManager The widget manager for which we're fetching weather.
     */
    public TemperatureFetcher(WidgetManager widgetManager) {
        super("Temperature Fetcher");

        this.widgetManager = widgetManager;
    }

    @NotNull
    static String censorAppid(@NotNull String urlWithAppid) {
        int appIdIndex = urlWithAppid.indexOf("APPID=");
        if (appIdIndex < 0) {
            return urlWithAppid;
        }

        return urlWithAppid.substring(0, appIdIndex) + "APPID=XXXXXX";
    }

    private static void saveJsonWeather(JSONObject jsonWeather, File jsonFile) {
        PrintWriter printWriter = null;
        try {
            printWriter = new PrintWriter(new FileWriter(jsonFile));
            printWriter.println(jsonWeather.toString());
            printWriter.close();
            Log.i(TAG, "JSON weather cached into " + jsonFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Unable to cache weather into " + jsonFile.getAbsolutePath());
        } finally {
            if (printWriter != null) {
                printWriter.close();
            }
        }
    }

    /**
     * Fetch the weather for a given location.
     * <p>
     * Has default protection for testing purposes.
     *
     * @param latitude The latitude to get weather for.
     *
     * @param longitude The longitude to get weather for.
     *
     * @return Information from the nearest weather station, or null.
     */
    @Nullable
    Weather fetchWeather(double latitude, double longitude) {
        long minutesToNextFetch;
        synchronized (this) {
            minutesToNextFetch =
                (nextFetch - System.currentTimeMillis()) / (60 * 1000);
        }
        if (minutesToNextFetch > 0) {
            Log.d(TAG, "Last successful fetch valid for " + minutesToNextFetch + " more minutes, skipping");
            return null;
        }

        // Create something like:
        // http://api.openweathermap.org/data/2.5/weather?lat=43&lon=-2&APPID=something
        // More info here:
        // http://api.openweathermap.org/API#weather

        URL url;
        String urlString = null;
        try {
            // Constants is by design not in the source code repo.
            //
            // Create your own by just making a class containing a single string constant with your APPID String in it.
            // The APPID string can be empty, or you can get your own at http://openweathermap.org/appid.
            urlString =
                String.format(Locale.ENGLISH,
                        "http://api.openweathermap.org/data/2.5/weather?lat=%.4f&lon=%.4f&APPID=%s",
                        latitude, longitude, Constants.APPID);

            url = new URL(urlString);
            Log.v(TAG, "JSON URL created: " + censorAppid(url.toString()));
        } catch (MalformedURLException e) {
            Log.e(TAG, "Internal error creating JSON URL from: " + urlString, e);
            widgetManager.setStatus("Internal error JSON URL");
            return null;
        }

        String jsonString = null;
        int attempt = 0;
        while (true) {
            if (!hasDataConnectivity()) {
                widgetManager.setStatus("No data connection");
                Log.e(TAG, "No data connection, not retrying");
                return null;
            }

            attempt++;
            boolean mightRetry = (attempt <= 10);

            try {
                jsonString = fetchUrl(url);

                JSONObject jsonWeather = new JSONObject(jsonString);
                Weather weather = new Weather(jsonWeather);
                saveJsonWeather(jsonWeather, widgetManager.getWeatherJsonFile());

                // If weather is 40 minutes old, wait at least 20 minutes until next fetch
                int fetchValidMinutes = weather.getAgeMinutes() / 2;
                if (fetchValidMinutes < 30) {
                    fetchValidMinutes = 30;
                }
                if (fetchValidMinutes > 60) {
                    fetchValidMinutes = 60;
                }
                synchronized (this) {
                    nextFetch = System.currentTimeMillis() + fetchValidMinutes * 60 * 1000;
                }

                return weather;
            } catch (UnknownHostException e) {
                widgetManager.setStatus("Network down, retry in 30min");
                Log.e(TAG, "Network probably down, not retrying", e);
                return null;
            } catch (IOException e) {
                widgetManager.setStatus("Weather service error, retry in "
                    + (mightRetry ? "1min" : "25min"));
                Log.w(TAG, "Error reading weather data on attempt "
                    + attempt + ": " + url,
                    e);
            } catch (JSONException e) {
                widgetManager.setStatus("Bad data from weather server");
                Log.w(TAG, "Bad data from weather server: " + jsonString);
            } catch (IllegalArgumentException e) {
                widgetManager.setStatus(e.getMessage());
                Log.w(TAG, "Error parsing weather", e);
            }

            if (!mightRetry) {
                // We've done our best and failed, give up.

                // At this point the user visible message should already have
                // been set, and we shouldn't overwrite it by calling
                // widgetManager.setStatus() with anything.
                Log.w(TAG, "Failed after 10 attempts, trying again in 30min");
                return null;
            }

            try {
                // A fetch takes about 7s, wait 23 more to retry twice
                // per minute
                Thread.sleep(23000);
            } catch (InterruptedException e) {
                widgetManager.setStatus("Weather fetch interrupted");
                Log.w(TAG, "Interrupted waiting for weather from server", e);
                return null;
            }
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean hasDataConnectivity() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager)widgetManager.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo == null) {
            Log.d(TAG, "TemperatureFetcher: No active data network");
            return false;
        }

        if (!networkInfo.isConnected()) {
            Log.d(TAG, "TemperatureFetcher: Active network not connected");
            return false;
        }

        return true;
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
    @NotNull
    private String fetchUrl(@NotNull URL url) throws IOException {
        Log.d(TAG, "Fetching data from: " + censorAppid(url.toString()));
        widgetManager.setStatus("Downloading weather data...");

        StringBuilder jsonBuilder = new StringBuilder();

        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(60000);
        connection.setReadTimeout(60000);
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
        }
        return jsonBuilder.toString();
    }

    @Override
    public void run() {
        Looper.prepare();

        // As a side effect of this constructor, this Handler binds to our
        // current thread.
        synchronized (this) {
            handler = new Handler(this);
        }

        Looper.loop();
    }

    /**
     * Initiates a temperature fetch.
     *
     * @param latitude The latitude for which to fetch the temperature
     *
     * @param longitude The longitude for which to fetch the temperature
     */
    public void fetchTemperature(double latitude, double longitude) {
        Message message = Message.obtain();
        assert message != null;

        Bundle bundle = message.getData();
        assert bundle != null;

        bundle.putDouble("latitude", latitude);
        bundle.putDouble("longitude", longitude);

        while (true) {
            synchronized (this) {
                if (handler != null) {
                    handler.sendMessage(message);
                    break;
                }
            }

            // Wait for the message handler to become available in #run()
            try {
                Log.d(TAG, "Waiting 1s for message handler to become available");
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted waiting for message handler to become available", e);
            }
        }
    }

    @Override
    public boolean handleMessage(@NotNull Message message) {
        Log.d(TAG, "Fetcher got temperature request...");
        Bundle extras = message.peekData();
        if (extras == null) {
            Log.w(TAG, "Got message with no bundle, can't handle it: " + message);
            widgetManager.setStatus("Internal error in handleMessage()");
            return false;
        }
        if (!extras.containsKey("latitude") || !extras.containsKey("longitude")) {
            Log.w(TAG, "Message didn't contain both lat and lon, can't handle it: " + message);
            widgetManager.setStatus("Missing lat/lon in handleMessage()");
            return false;
        }

        Weather weather =
            fetchWeather(extras.getDouble("latitude"), extras.getDouble("longitude"));
        if (weather != null) {
            widgetManager.setWeather(
                    weather,
                    String.format("%s weather from %s",
                            Util.minutesToTimeOldString(weather.getAgeMinutes()),
                            weather.getStationName()));
        } else {
            Log.w(TAG, "Got null weather from fetchWeather()");
        }

        // Returning true means that we have handled the message according to:
        // http://code.google.com/p/android/issues/detail?id=6464
        return true;
    }
}
