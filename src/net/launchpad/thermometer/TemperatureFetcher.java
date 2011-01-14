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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.Locale;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Bundle;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

/**
 * A background task fetching the current outdoor temperature from the
 * Internet.
 */
public class TemperatureFetcher extends Thread implements Callback {
    /**
     * Used for tagging log messages.
     */
    private final static String TAG = ThermometerWidget.TAG;

    /**
     * Widget controller.
     */
    private WidgetManager widgetManager;

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

    /**
     * Fetch the weather for a given location.
     *
     * @param latitude The latitude to get weather for.
     *
     * @param longitude The longitude to get weather for.
     *
     * @return A JSON object with information from the nearest weather station.
     */
    private JSONObject fetchWeather(double latitude, double longitude) {
        // Create something like:
        // http://ws.geonames.org/findNearByWeatherJSON?lat=43&lng=-2
        // More info here:
        // http://www.geonames.org/export/JSON-webservices.html#findNearByWeatherJSON

        URL url;
        String urlString = null;
        try {
            urlString =
                String.format(Locale.ENGLISH, "http://ws.geonames.org/findNearByWeatherJSON?lat=%.4f&lng=%.4f",
                    latitude, longitude);

            url = new URL(urlString);
            Log.v(TAG, "JSON URL created: " + url);
        } catch (MalformedURLException e) {
            Log.e(TAG, "Internal error creating JSON URL from: " + urlString, e);
            widgetManager.setStatus("Internal error JSON URL");
            return null;
        }

        String jsonString = null;
        int attempt = 0;
        while (jsonString == null) {
            attempt++;
            boolean mightRetry = (attempt <= 10);

            try {
                jsonString = fetchUrl(url);
                break;
            } catch (UnknownHostException e) {
                widgetManager.setStatus("Network down, retry in 30min");
                Log.e(TAG, "Network probably down, not retrying", e);
                break;
            } catch (IOException e) {
                widgetManager.setStatus("Weather service error, retry in "
                    + (mightRetry ? "1min" : "25min"));
                Log.w(TAG, "Error reading weather data on attempt "
                    + attempt + ": " + url,
                    e);
            }

            if (!mightRetry) {
                // We've done our best and failed, give up
                break;
            }
        }
        if (jsonString == null) {
            Log.e(TAG, "Failed reading weather data, giving up");
            return null;
        }

        try {
            JSONObject weatherObservation = new JSONObject(jsonString);

            if (weatherObservation.has("weatherObservation")) {
                // Parse the temperature
                weatherObservation =
                    weatherObservation.getJSONObject("weatherObservation");

                Log.d(TAG, "New weather observation received:\n" + jsonString);
                return weatherObservation;
            }

            // Assume an error message:
            // {"status":{"message":"error parsing parameters for lat/lng","value":14}}
            weatherObservation =
                weatherObservation.getJSONObject("status");
            Log.w(TAG, "Web service trouble: " +
            		"" + weatherObservation.getString("message"));
            widgetManager.setStatus(weatherObservation.getString("message"));
            return null;
        } catch (JSONException e) {
            Log.e(TAG, "Parsing weather data failed:\n"
                + jsonString, e);
            widgetManager.setStatus("Error parsing weather data");
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
            in = null;
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
        Bundle bundle = message.getData();
        bundle.putDouble("latitude", latitude);
        bundle.putDouble("longitude", longitude);

        while (true) {
            synchronized (this) {
                if (handler != null) {
                    handler.sendMessage(message);
                    break;
                }
            }

            // Wait for the message handler to come available in #run()
            try {
                Log.d(TAG, "Waiting 1s for message handler to become available");
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted waiting for message handler to become available");
            }
        }
    }

    public boolean handleMessage(Message message) {
        Bundle extras = message.peekData();
        if (extras == null) {
            Log.w(TAG, "Got message with no bundle, can't handle it: " + message);
            return false;
        }
        if (!extras.containsKey("latitude") || !extras.containsKey("longitude")) {
            Log.w(TAG, "Message didn't contain both lat and lon, can't handle it: " + message);
            return false;
        }

        // Returning true means that we have handled the message according to:
        // http://code.google.com/p/android/issues/detail?id=6464
        JSONObject weather =
            fetchWeather(extras.getDouble("latitude"), extras.getDouble("longitude"));
        widgetManager.setWeather(weather);
        widgetManager.updateUi();
        return true;
    }
}
