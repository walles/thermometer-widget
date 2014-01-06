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

import android.os.Looper;
import net.launchpad.thermometer.WidgetManager.UpdateReason;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.util.Log;

import java.io.Closeable;
import java.util.List;
import java.util.Map;

/**
 * Listens for events and requests widget updates as required.
 */
public class UpdateListener
implements LocationListener, Closeable {
    private boolean closed = false;

    /**
     * Widget controller.
     */
    private final WidgetManager widgetManager;

    /**
     * Our most recently received location update.
     */
    private Location cachedLocation;

    /**
     * Updates widget when preferences change.
     */
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;

    /**
     * Create a new update listener.
     *
     * @param widgetManager The widget manager that will be informed about
     * updates.
     */
    public UpdateListener(final WidgetManager widgetManager) {
        if (widgetManager == null) {
            throw new NullPointerException("widgetManager must be non-null");
        }
        this.widgetManager = widgetManager;

        registerLocationListener(widgetManager);

        Log.d(TAG, "Registering preferences change notification listener");

        preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
                Log.d(TAG, String.format("Preference changed, updating UI: %s=>%s",
                        key,
                        describePreference(preferences, key)));

                if (preferences != widgetManager.getPreferences()) {
                    Log.w(TAG, "Preference changed in unexpected SharedPreferences instance");
                }

                widgetManager.updateUi();
            }
        };
        widgetManager.getPreferences().registerOnSharedPreferenceChangeListener(preferenceChangeListener);
    }

    private void triggerLocationUpdate(String issue) {
        Log.w(TAG, "Location " + issue + ", triggering update...");

        Looper looper = Looper.myLooper();
        if (looper == null) {
            Log.e(TAG, "Have no looper, can't trigger location update");
            return;
        }
        getLocationManager().requestSingleUpdate(
                LocationManager.NETWORK_PROVIDER,
                this,
                looper);
    }

    /**
     * Get the phone's last known location.
     *
     * @return the phone's last known location.
     */
    public Location getLocation() {
        Location bestLocation;

        LocationManager locationManager = getLocationManager();
        Location lastKnownLocation = null;
        if (networkLocationAvailable()) {
            lastKnownLocation =
                    locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        } else {
            Log.w(TAG, "Network positioning not available on this device");
        }

        if (lastKnownLocation == null && cachedLocation == null) {
            Log.w(TAG, "No cached location and no last known one");
            bestLocation = null;
        } else if (cachedLocation == null) {
            bestLocation = lastKnownLocation;
        } else if (lastKnownLocation == null) {
            Log.w(TAG, "Have cached location but no last known");
            bestLocation = cachedLocation;
        } else if (cachedLocation.getTime() > lastKnownLocation.getTime()) {
            long ageDifferenceSeconds =
                    (cachedLocation.getTime() - lastKnownLocation.getTime()) / 1000L;
            Log.w(TAG, "Cached location is " + ageDifferenceSeconds + "s newer than last known location");
            bestLocation = cachedLocation;
        } else {
            bestLocation = lastKnownLocation;
        }
        cachedLocation = bestLocation;

        if (bestLocation == null && Util.isRunningOnEmulator()) {
            Log.i(TAG,
                    "Location unknown but running on emulator, hard coding coordinates to Johan's place");
            bestLocation = new Location("Johan");
            bestLocation.setLatitude(59.3190);
            bestLocation.setLongitude(18.0518);
            bestLocation.setTime(System.currentTimeMillis());
        }

        if (bestLocation == null) {
            Log.w(TAG, LocationManager.NETWORK_PROVIDER + " location is unknown");
            triggerLocationUpdate("unknown");

            return null;
        }

        long ageMs = System.currentTimeMillis() - bestLocation.getTime();
        int ageMinutes = (int)(ageMs / (60 * 1000));
        Log.d(TAG, String.format("Got a %s location from %s",
                Util.minutesToTimeOldString(ageMinutes),
                bestLocation.getProvider()));

        if (ageMinutes > 60) {
            triggerLocationUpdate("too old");
        }

        return bestLocation;
    }

    private void registerLocationListener(WidgetManager widgetManager) {
        Log.d(TAG, "Registering location listener...");
        LocationManager locationManager = getLocationManager();

        if (!networkLocationAvailable()) {
            widgetManager.setStatus("Network position not available on device");
            Log.e(TAG, "Network positioning not available on this device");

            return;
        }

        widgetManager.setStatus("Locating phone...");
        locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                41 * 60 * 1000, // Drift a bit relative to the periodic widget update
                50000, // Every 50km we move
                this);
        Log.d(TAG, "Location listener registered");
    }

    private boolean networkLocationAvailable() {
        LocationManager locationManager = getLocationManager();

        List<String> allProviders = locationManager.getAllProviders();
        return allProviders.contains(LocationManager.NETWORK_PROVIDER);
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
            (LocationManager)widgetManager.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            widgetManager.setStatus("Location unavailable");
            throw new RuntimeException("Location manager not found, cannot continue");
        }
        return locationManager;
    }

    private String describePreference(SharedPreferences preferences, String key) {
        Map<String, ?> map = preferences.getAll();
        assert map != null;

        if (!map.containsKey(key)) {
            return "<unset>";
        }

        Object value = map.get(key);
        if (value instanceof Integer) {
            return String.format("0x%06x", (Integer)value);
        }
        return value.toString();
    }

    /**
     * Free up system resources and stop listening.
     */
    @Override
    public void close() {
        if (closed) {
            throw new IllegalStateException("Already closed");
        }
        closed = true;

        Log.d(TAG, "Deregistering preferences change listener...");
        widgetManager.getPreferences().unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        getLocationManager().removeUpdates(this);
    }

    @Override
    public void onLocationChanged(Location networkLocation) {
        Log.d(TAG, String.format("Location updated to lat=%.4f, lon=%.4f",
            networkLocation.getLatitude(),
            networkLocation.getLongitude()));

        try {
            if (cachedLocation != null) {
                int lastLocationAgeMinutes =
                        (int)((System.currentTimeMillis() - cachedLocation.getTime())
                                / (1000L * 60L));

                if (lastLocationAgeMinutes < 10) {
                    Log.i(TAG, String.format("Not updating widget; old location %s",
                            Util.minutesToTimeOldString(lastLocationAgeMinutes)));
                    return;
                }
            }
        } finally {
            cachedLocation = networkLocation;
        }

        // Take a new measurement at our new location
        widgetManager.updateMeasurement(UpdateReason.LOCATION_CHANGED);
    }

    @Override
    public void onProviderDisabled(String provider) {
        if (LocationManager.NETWORK_PROVIDER.equals(provider)) {
            Log.e(TAG, "Location provider disabled: " + provider);
            widgetManager.setStatus("Click to enable network positioning");
        }
    }

    @Override
    public void onProviderEnabled(String provider) {
        if (LocationManager.NETWORK_PROVIDER.equals(provider)) {
            Log.i(TAG, "Location provider enabled: " + provider);
            widgetManager.setStatus("Locating phone...");
        }
    }

    @Override
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
                Log.e(TAG, "Location provider out of service: "
                    + provider);
                widgetManager.setStatus("Location services unavailable");
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
