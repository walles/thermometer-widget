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
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Listens for events and requests widget updates as required.
 */
public class UpdateListener
implements SharedPreferences.OnSharedPreferenceChangeListener, LocationListener
{
    /**
     * Widget controller.
     */
    private WidgetManager widgetManager;

    /**
     * Our most recently received location update.
     */
    private Location lastKnownLocation;

    /**
     * Create a new update listener.
     *
     * @param widgetManager The widget manager that will be informed about
     * updates.
     */
    public UpdateListener(WidgetManager widgetManager) {
        if (widgetManager == null) {
            throw new NullPointerException("widgetManager must be non-null");
        }
        this.widgetManager = widgetManager;

        registerLocationListener(widgetManager);

        Log.d(TAG, "Registering preferences change notification listener");
        SharedPreferences preferences =
            PreferenceManager.getDefaultSharedPreferences(widgetManager);
        preferences.registerOnSharedPreferenceChangeListener(this);
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
        LocationManager locationManager = getLocationManager();

        if (lastKnownLocation == null) {
            lastKnownLocation =
                    locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }

        if (lastKnownLocation == null && Util.isRunningOnEmulator()) {
            Log.i(TAG,
                    "Location unknown but running on emulator, hard coding coordinates to Johan's place");
            lastKnownLocation = new Location("Johan");
            lastKnownLocation.setLatitude(59.3190);
            lastKnownLocation.setLongitude(18.0518);
            lastKnownLocation.setTime(System.currentTimeMillis());
        }

        if (lastKnownLocation == null) {
            Log.w(TAG, LocationManager.NETWORK_PROVIDER + " location is unknown");
            triggerLocationUpdate("unknown");

            return null;
        }

        long ageMs = System.currentTimeMillis() - lastKnownLocation.getTime();
        int ageMinutes = (int)(ageMs / (60 * 1000));
        Log.d(TAG, String.format("Got a %s location from %s",
                Util.minutesToTimeOldString(ageMinutes),
                lastKnownLocation.getProvider()));

        if (ageMinutes > 60) {
            triggerLocationUpdate("too old");
        }

        return lastKnownLocation;
    }

    private void registerLocationListener(WidgetManager widgetManager) {
        Log.d(TAG, "Registering location listener...");
        LocationManager locationManager = getLocationManager();

        if (!locationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER)) {
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

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences,
        String key)
    {
        getLocationManager().removeUpdates(this);

        Log.d(TAG, "Preference changed, updating UI: " + key);
        widgetManager.updateUi();
    }

    /**
     * Free up system resources and stop listening.
     */
    public void close() {
        Log.d(TAG, "Deregistering preferences change listener...");
        SharedPreferences preferences =
            PreferenceManager.getDefaultSharedPreferences(widgetManager);
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        getLocationManager().removeUpdates(this);
    }

    @Override
    public void onLocationChanged(Location networkLocation) {
        Log.d(TAG, String.format("Location updated to lat=%.4f, lon=%.4f",
            networkLocation.getLatitude(),
            networkLocation.getLongitude()));

        try {
            if (lastKnownLocation != null) {
                int lastLocationAgeMinutes =
                        (int)((System.currentTimeMillis() - lastKnownLocation.getTime())
                                / (1000L * 60L));

                if (lastLocationAgeMinutes < 10) {
                    Log.i(TAG, String.format("Not updating widget; old location %s",
                            Util.minutesToTimeOldString(lastLocationAgeMinutes)));
                    return;
                }
            }
        } finally {
            lastKnownLocation = networkLocation;
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
