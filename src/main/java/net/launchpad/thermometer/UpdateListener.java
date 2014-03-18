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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.provider.Settings;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import net.launchpad.thermometer.WidgetManager.UpdateReason;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.util.Map;

/**
 * Listens for events and requests widget updates as required.
 */
public class UpdateListener
implements LocationListener, Closeable
{
    private Handler handler = new Handler();

    private boolean closed = false;

    /**
     * Notification ID for "Google Play Services need upgrading or installing".
     */
    private final static int GPSA_NOTIFICATION = 1;

    /**
     * Widget controller.
     */
    @NotNull
    private final WidgetManager widgetManager;

    /**
     * Our most recently received location update.
     */
    @Nullable
    private Location cachedLocation;

    /**
     * Updates widget when preferences change.
     */
    @NotNull
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;

    @NotNull
    private final LocationClient locationClient;

    /**
     * Create a new update listener.
     *
     * @param widgetManager The widget manager that will be informed about
     * updates.
     */
    public UpdateListener(@NotNull final WidgetManager widgetManager) {
        this.widgetManager = widgetManager;

        widgetManager.setStatus("Location service starting...");
        locationClient = new LocationClient(widgetManager, new GooglePlayServicesClient.ConnectionCallbacks() {
            @Override
            public void onConnected(Bundle bundle) {
                Log.i(TAG, "Connected, cancelling GPSA trouble / resolution notification");
                NotificationManager notificationManager =
                        (NotificationManager)widgetManager.getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.cancel(GPSA_NOTIFICATION);

                registerLocationListener(widgetManager);
            }

            @Override
            public void onDisconnected() {
                Log.i(TAG, "Disconnected from location service");
                widgetManager.setStatus("Location service disconnected");

                if (!closed) {
                    Log.w(TAG, "Disconnected but not shutting down, reconnecting...");
                    reconnectGpsa();
                }
            }
        }, new GooglePlayServicesClient.OnConnectionFailedListener() {
            @Override
            public void onConnectionFailed(ConnectionResult connectionResult) {
                if (Util.isRunningOnEmulator()) {
                    Log.i(TAG, "Not resolving GPSA connectivity when running on emulator");
                } else {
                    repairGpscConnection(connectionResult);
                }
            }
        });
        reconnectGpsa();

        Log.d(TAG, "Registering preferences change notification listener");
        preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(@NotNull SharedPreferences preferences, String key) {
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

        registerNetworkPositioningListener();
    }

    /**
     * Listen for enabling of the network location provider so that we can drop the "Click to enable network
     * positioning" text after the user has done that.
     */
    private void registerNetworkPositioningListener() {
        LocationManager locationManager =
                (LocationManager)widgetManager.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager.getProvider(LocationManager.NETWORK_PROVIDER) == null) {
            Log.w(TAG, "Network provider not available on this device");
            return;
        }

        locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                30 * 60 * 1000,
                0,
                new android.location.LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        // Intentionally ignored; we get this from the Google Play Services Location API because of
                        // Android bug 57707
                        Log.i(TAG, "Location provider location updated: " + location);
                    }

                    @Override
                    public void onStatusChanged(String s, int i, Bundle bundle) {
                        // Intentionally ignored; let the Google Play Services Location API deal with these events
                        Log.i(TAG, "Location provider status changed: " + s + " " + i + " " + bundle);
                    }

                    @Override
                    public void onProviderEnabled(String s) {
                        Log.i(TAG, "Location provider enabled: " + s);

                        // This will give us a new location and remove the "Click to enable network positioning" text
                        reconnectGpsa();
                    }

                    @Override
                    public void onProviderDisabled(String s) {
                        // Intentionally ignored; requesting the user to enable network positioning is done from
                        // getLocation() if needed
                        Log.i(TAG, "Location provider disabled: " + s);
                    }
                });
    }

    /**
     * Retry connecting to Google Play Services API; this can be useful to do after it has been upgraded on the device.
     */
    public void reconnectGpsa() {
        if (locationClient.isConnecting() || locationClient.isConnected()) {
            locationClient.disconnect();
        }

        try {
            locationClient.connect();
        } catch (Exception e) {
            Log.w(TAG, "Connecting to GPSA failed, retrying in 5...", e);
            widgetManager.setStatus("Waiting for location service...");

            Runnable reconnect = new Runnable() {
                @Override
                public void run() {
                    reconnectGpsa();
                }
            };
            handler.postDelayed(reconnect, 5000);
        }
    }

    private void repairGpscConnection(ConnectionResult problem) {
        Log.e(TAG, "Failed connecting to location service: " + problem);

        PendingIntent resolution = FixGpsaActivity.createPendingIntent(widgetManager);
        widgetManager.setStatus("Click to fix location error " + problem.getErrorCode(),
                resolution);

        Notification.Builder resolutionBuilder = new Notification.Builder(widgetManager);
        resolutionBuilder.setContentTitle("Upgrade / Install location services");
        resolutionBuilder.setContentText("Thermometer Widget needs needs location services upgraded or installed");
        resolutionBuilder.setContentIntent(resolution);
        resolutionBuilder.setTicker("Thermometer Widget needs to have location services upgraded or installed");
        resolutionBuilder.setSmallIcon(R.drawable.icon);

        NotificationManager notificationManager =
                (NotificationManager)widgetManager.getSystemService(Context.NOTIFICATION_SERVICE);
        // The (deprecated) getNotification() method is the only one available in the oldest Android API we support
        //noinspection deprecation
        notificationManager.notify(GPSA_NOTIFICATION, resolutionBuilder.getNotification());
        Log.i(TAG, "Notifying about GPSA trouble / resolution");
    }

    /**
     * Is network positioning enabled?
     *
     * @return True if network positioning is enabled.  False otherwise.
     */
    @NotNull
    private ProviderStatus getNetworkPositioningStatus() {
        LocationManager locationManager =
                (LocationManager)widgetManager.getSystemService(Context.LOCATION_SERVICE);

        if (locationManager.getProvider(LocationManager.NETWORK_PROVIDER) == null) {
            return ProviderStatus.UNAVAILABLE;
        }

        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            return ProviderStatus.ENABLED;
        }

        return ProviderStatus.DISABLED;
    }

    /**
     * Get the phone's last known location.
     *
     * @return the phone's last known location.
     */
    @Nullable
    public Location getLocation() {
        Location bestLocation;

        String locationClientStatus;
        if (locationClient.isConnected()) {
            locationClientStatus = "connected";
        } else if (locationClient.isConnecting()) {
            locationClientStatus = "connecting";
        } else {
            locationClientStatus = "disconnected";
        }

        Location lastKnownLocation = null;
        if (locationClient.isConnected()) {
            lastKnownLocation = locationClient.getLastLocation();
        } else {
            Log.w(TAG, "Location client " + locationClientStatus);
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

        if (bestLocation == null && getNetworkPositioningStatus() == ProviderStatus.DISABLED) {
            requestEnableNetworkPositioning();
            return null;
        }

        if (bestLocation == null) {
            Log.w(TAG, String.format("Location is unknown, location client is %s, network positioning is %s",
                    locationClientStatus,
                    getNetworkPositioningStatus().toString()));

            return null;
        }

        long ageMs = System.currentTimeMillis() - bestLocation.getTime();
        int ageMinutes = (int)(ageMs / (60 * 1000));
        Log.d(TAG, String.format("Got a %s location from %s, lat=%f, lon=%f, accuracy=%dm",
                Util.minutesToTimeOldString(ageMinutes),
                bestLocation.getProvider(),
                bestLocation.getLatitude(),
                bestLocation.getLongitude(),
                Math.round(bestLocation.getAccuracy())));

        if (ageMinutes > 150 && getNetworkPositioningStatus() == ProviderStatus.DISABLED) {
            requestEnableNetworkPositioning();
        }

        return bestLocation;
    }

    /**
     * Ask user to enable network positioning.
     */
    private void requestEnableNetworkPositioning() {
        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(widgetManager, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        widgetManager.setStatus("Click to enable network positioning", pendingIntent);
    }

    private void registerLocationListener(@NotNull WidgetManager widgetManager) {
        Log.d(TAG, "Registering location listener...");

        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(41 * 60 * 1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_LOW_POWER);
        locationClient.requestLocationUpdates(locationRequest, this);

        widgetManager.setStatus("Locating phone...", null);
        Log.d(TAG, "Location listener registered");

        // This will detect things like positioning services being disabled
        getLocation();
    }

    private String describePreference(@NotNull SharedPreferences preferences, String key) {
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
        locationClient.disconnect();
    }

    @Override
    public void onLocationChanged(@NotNull Location location) {
        long locationAgeMillis = System.currentTimeMillis() - location.getTime();
        long locationAgeMinutes = locationAgeMillis / (1000 * 60);
        Log.d(TAG, String.format("Location updated to lat=%.4f, lon=%.4f, accuracy=%dm, age=%s",
                location.getLatitude(),
                location.getLongitude(),
                Math.round(location.getAccuracy()),
                Util.minutesToTimeOldString((int)locationAgeMinutes)));

        if (cachedLocation == null) {
            cachedLocation = location;
            widgetManager.updateMeasurement(UpdateReason.LOCATION_CHANGED);
            return;
        }

        if (cachedLocation.getTime() > location.getTime()) {
            long ageDifferenceSeconds = (cachedLocation.getTime() - location.getTime()) / 1000L;
            Log.i(TAG, String.format("Cached location is %ds newer than location update, ignoring location update",
                    ageDifferenceSeconds));
            return;
        }

        try {
            int cachedLocationAgeMinutes =
                    (int)((System.currentTimeMillis() - cachedLocation.getTime())
                            / (1000L * 60L));

            if (cachedLocationAgeMinutes < 10 && widgetManager.hasWeather()) {
                Log.i(TAG, String.format("Not updating widget; old location %s",
                        Util.minutesToTimeOldString(cachedLocationAgeMinutes)));
                return;
            }
        } finally {
            cachedLocation = location;
        }

        // Take a new measurement at our new location
        widgetManager.updateMeasurement(UpdateReason.LOCATION_CHANGED);
    }

    private enum ProviderStatus {
        UNAVAILABLE,
        DISABLED,
        ENABLED
    }
}
