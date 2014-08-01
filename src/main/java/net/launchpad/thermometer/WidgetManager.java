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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Scanner;

import android.annotation.SuppressLint;
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
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.RemoteViews;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This class contains all the logic for {@link ThermometerWidget}.
 *
 * @author johan.walles@gmail.com
 */
// Need to suppress warning about GENERATE_TRACEFILES always being false / true
@SuppressWarnings("ConstantConditions")
public class WidgetManager extends Service {
    final long serviceStartTimestamp;
    int display_or_timer_count = 0;

    /**
     * Enable this to generate a trace file from when the widget is added to when it is removed.
     *
     * @see #TRACE_FILE_NAME
     */
    private final boolean GENERATE_TRACEFILES = false;

    /**
     * This path needs to be hard coded since {@link #getCacheDir()} returns null when called from the constructor
     * where this is used.
     *
     * @see #GENERATE_TRACEFILES
     */
    @SuppressWarnings("FieldCanBeLocal")
    @SuppressLint("SdCardPath")
    private final String TRACE_FILE_NAME = "/data/data/net.launchpad.thermometer/johan.trace";

    private final Handler handler = new Handler();

    /**
     * Used for tagging update intents with why they were sent.
     */
    private final static String UPDATE_REASON = "Update Reason";

    /**
     * Listens for events and requests widget updates as required.
     */
    @Nullable
    private UpdateListener updateListener;

    /**
     * Has the UI ever been updated?
     */
    private boolean uiUpdated = false;

    /**
     * Must be accessed through {@link #getPreferences()}.
     */
    private SharedPreferences preferences;

    /**
     * You need to synchronize on this before accessing any of the other
     * fields of this class.
     */
    private final Object weatherLock = new Object();

    /**
     * The latest weather measurement.
     * <p>
     * You must synchronize on {@link #weatherLock} before accessing this.
     */
    @Nullable
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
    private final TemperatureFetcher temperatureFetcher;

    /**
     * This thing puts log messages into files for us.
     */
    private java.lang.Process logcat;

    /**
     * If non-null, we're having problems with the Google Play Services API, and invoking this intent should resolve
     * them.
     */
    private PendingIntent gpsaResolution;

    /**
     * Create a new widget manager.
     */
    public WidgetManager() {
        serviceStartTimestamp = System.currentTimeMillis();

        if (GENERATE_TRACEFILES) {
            Debug.startMethodTracing(TRACE_FILE_NAME);
        }

        temperatureFetcher = new TemperatureFetcher(this);
        temperatureFetcher.start();
    }

    /**
     * Ask logcat to start storing logs to disk in the background.
     * <p>
     * Note that the log files will be read by
     * {@link net.launchpad.thermometer.ThermometerLogViewer.ReadLogsTask#getStoredLogs()},
     * so if this method changes, that one needs to change as well.
     */
    @Override
    public void onCreate() {
        super.onCreate();

        killOldLogcat();

        File logfile = getLogFile();

        // Print a banner to the current log file so the restart can be easily spotted
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(new FileWriter(logfile));
            writer.println();
            writer.println("--- Launching the Thermometer Widget ---");
            writer.println();
        } catch (IOException e) {
            Log.w(TAG, "Writing restarting banner to log file failed: " + logfile.getAbsolutePath(), e);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }

        try {
            logcat = Runtime.getRuntime().exec(createLogcatCommandLine());

            Log.i(TAG, "Background logcat started, logging into " + logfile.getParent());
        } catch (IOException e) {
            Log.e(TAG, "Executing logcat failed", e);
        }
    }

    /**
     * Create a logcat command line for rotating logs into where {@link #getLogFile()} points.
     */
    private String[] createLogcatCommandLine() {
        return new String[] {
                "logcat",
                "-v", "time",
                "-f", getLogFile().getAbsolutePath(),
                "-n", "3",
                "-r", "16"
        };
    }

    /**
     * This method decides where logcat should rotate its logs into.
     */
    private File getLogFile() {
        File logdir = getDir("logs", MODE_PRIVATE);
        return new File(logdir, "log");
    }

    /**
     * Find and kill any old logcat invocations by us that are running on the system.
     */
    private void killOldLogcat() {
        long t0 = System.currentTimeMillis();
        Log.d(TAG, "Cleaning up old logcat processes...");

        final String logcatCommandLine[] = createLogcatCommandLine();

        int killed = 0;
        for (File directory : new File("/proc").listFiles()) {
            if (!directory.isDirectory()) {
                continue;
            }

            File cmdline = new File(directory, "cmdline");
            if (!cmdline.exists()) {
                continue;
            }
            if (!cmdline.canRead()) {
                continue;
            }

            BufferedReader cmdlineReader;
            try {
                cmdlineReader = new BufferedReader(new FileReader(cmdline));
            } catch (FileNotFoundException e) {
                continue;
            }
            try {
                String line = cmdlineReader.readLine();
                if (line == null) {
                    continue;
                }
                String processCommandLine[] = line.split("\0");
                if (!Arrays.equals(logcatCommandLine, processCommandLine)) {
                    continue;
                }

                int pid;
                try {
                    pid = Integer.parseInt(directory.getName());
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Couldn't parse into pid: " + directory.getName());
                    continue;
                }

                Log.i(TAG, "Killing old logcat process: " + pid);
                android.os.Process.killProcess(pid);
                killed++;
            } catch (IOException e) {
                Log.w(TAG, "Reading command line failed: " + cmdline.getAbsolutePath());
                //noinspection UnnecessaryContinue
                continue;
            } finally {
                try {
                    cmdlineReader.close();
                } catch (IOException e) {
                    // Closing is a best-effort operation, this exception intentionally ignored
                    Log.w(TAG, "Failed to close " + cmdline, e);
                }
            }
        }

        long t1 = System.currentTimeMillis();
        Log.i(TAG, "Killed " + killed + " old logcats in " + Util.msToTimeString(t1 - t0));
    }

    public synchronized SharedPreferences getPreferences() {
        if (preferences == null) {
            preferences = PreferenceManager.getDefaultSharedPreferences(this);
        }

        return preferences;
    }

    /**
     * Update / initialize / shut down widgets.
     *
     * @param context Used for creating a widget update {@link Intent}.
     *
     * @param why Why the update is wanted.
     */
    public static void onUpdate(@NotNull Context context, @NotNull UpdateReason why) {
        Intent intent = new Intent(context, WidgetManager.class);
        intent.putExtra(UPDATE_REASON, why.name());
        context.startService(intent);
    }

    /**
     * Tell us what the weather is like.
     *
     * @param weather What the weather is like.
     */
    public void setWeather(@NotNull Weather weather, @NotNull String status) {
        if (weather.getObservationTime() == null) {
            Log.e(TAG, "New weather observation has no time stamp, dropping it");
            return;
        }

        synchronized (weatherLock) {
            if (this.weather != null && this.weather.getAgeMinutes() <= weather.getAgeMinutes()) {
                Log.e(TAG, "New weather older than current weather, dropping it");
                return;
            }

            this.weather = weather;

            // Setting the status here will implicitly update the UI
            setStatus(status);
        }
    }

    public File getWeatherJsonFile() {
        return new File(getFilesDir(), "last-weather.json");
    }

    @Nullable
    private static Weather loadJsonWeather(File jsonFile) {
        if (!jsonFile.exists()) {
            // Will happen the first time the widget is started on a device
            return null;
        }

        String jsonString = null;
        try {
            // From http://stackoverflow.com/questions/326390/how-to-create-a-java-string-from-the-contents-of-a-file
            jsonString = new Scanner(jsonFile).useDelimiter("\\A").next();
            Weather weather = new Weather(new JSONObject(jsonString));
            Log.i(TAG, "Cached weather loaded from " + jsonFile.getAbsolutePath());
            return weather;
        } catch (IOException e) {
            Log.e(TAG, "Unable to read cached weather from " + jsonFile.getAbsolutePath(), e);
            return null;
        } catch (JSONException e) {
            Log.e(TAG, "Bad JSON in weather cache file " + jsonFile.getAbsolutePath(), e);
            return null;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Cached JSON is no weather: <" + jsonString + ">", e);
            return null;
        }
    }

    /**
     * What's the weather like around here?
     *
     * @return What the weather is like around here.
     */
    @Nullable
    public Weather getWeather() {
        synchronized (weatherLock) {
            if (this.weather == null) {
                this.weather = loadJsonWeather(getWeatherJsonFile());
            }

            return this.weather;
        }
    }

    /**
     * Do we know what the weather is like?
     *
     * @return True if the weather is known. False otherwise (for a while during startup).
     */
    public boolean hasWeather() {
        return getWeather() != null;
    }

    /**
     * How are we doing on fetching the weather?
     *
     * @param status A status string.
     *
     * @param gpsaResolution A way to resolve Google Play Services API connection problems
     */
    public void setStatus(@NotNull String status, @Nullable PendingIntent gpsaResolution) {
        synchronized (weatherLock) {
            Calendar now = new GregorianCalendar();

            if (gpsaResolution == null) {
                this.status = Util.toHoursString(now, DateFormat.is24HourFormat(this)) + " " + status;
            } else {
                // Google Play Services API problem resolutions are timeless
                this.status = status;
            }
            Log.i(TAG, "Set user visible status: " + status);

            this.gpsaResolution = gpsaResolution;
            Log.i(TAG, "Google Play Services problem resolution is "
                    + (gpsaResolution == null ? "null" : "non-null"));

            // Show the new status to the user
            updateUi();
        }
    }

    /**
     * How are we doing on fetching the weather?
     *
     * @param status A status string.
     */
    public void setStatus(@NotNull String status) {
        synchronized (weatherLock) {
            if (gpsaResolution != null) {
                Log.w(TAG, "Implicitly nulling out GPSA resolution from: " + this.status);
            }
            setStatus(status, null);
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

    private PendingIntent getGpsaResolution() {
        synchronized (weatherLock) {
            return gpsaResolution;
        }
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
        DISPLAY_OR_TIMER,

        /**
         * We should try re-connecting to the Google Play Services API.
         */
        GPSA_RECONNECT
    }

    /**
     * Take a new weather measurement for the widget to display.
     *
     * @param why Why should the temperature be updated?
     */
    public void updateMeasurement(@NotNull UpdateReason why) {
        Log.d(TAG, "Weather observation fetch requested (" + why + ")...");

        Location currentLocation;
        synchronized (weatherLock) {
            if (updateListener == null) {
                Log.e(TAG, "Can't get location, update listener not available");
                return;
            }

            currentLocation = updateListener.getLocation();
        }
        if (currentLocation == null) {
            Log.d(TAG, "Don't know where we are, can't fetch any weather");
            return;
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
        assert manager != null;

        int[] appWidgetIds =
            manager.getAppWidgetIds(
                new ComponentName(
                    "net.launchpad.thermometer",
                    ThermometerWidget.class.getCanonicalName()));
        Log.d(TAG, "Got widget IDs: " + Arrays.toString(appWidgetIds));
        return appWidgetIds;
    }

    /**
     * Enqueue a widget display update.
     */
    public void updateUi() {
        if (!uiUpdated) {
            // The first time we do this in the foreground to get something on screen as soon as possible
            Log.d(TAG, "Doing UI update in foreground the first time");
            doUpdateUi();
            uiUpdated = true;
            return;
        }

        boolean posted = handler.post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Now performing enqueued UI update");
                doUpdateUi();
            }
        });

        if (posted) {
            Log.d(TAG, "Background UI update enqueued");
        } else {
            Log.w(TAG, "Enqueueing UI update failed, running in foreground");
            doUpdateUi();
        }
    }

    /**
     * Refresh the widget display.
     *
     * @see #updateUi()
     */
    private void doUpdateUi() {
        Log.d(TAG, "Updating widget display...");

        WeatherPresenter weatherPresenter = new WeatherPresenter(getWeather(), getStatus());
        weatherPresenter.setShowMetadata(getPreferences().getBoolean("showMetadataPref", false));
        weatherPresenter.setWithWindChill(getPreferences().getBoolean("windChillPref", false));
        weatherPresenter.setForceShowExcuse(getGpsaResolution() != null);
        weatherPresenter.setUseCelsius(!Util.isFahrenheit(getPreferences().getString("temperatureUnitPref", "Celsius")));
        weatherPresenter.setUse24HoursFormat(DateFormat.is24HourFormat(this));

        int textColor = getPreferences().getInt("textColorPref", Color.WHITE);
        RemoteViews remoteViews =
                weatherPresenter.createRemoteViews(this, textColor);

        PendingIntent pendingIntent;
        PendingIntent resolution = getGpsaResolution();
        if (resolution != null) {
            pendingIntent = resolution;
        } else {
            // All is well, tell widget to launch the preferences activity on click
            Intent intent = new Intent(this, ThermometerActions.class);
            pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        remoteViews.setOnClickPendingIntent(R.id.AllOfIt, pendingIntent);

        updateUi(remoteViews);

        Log.d(TAG, "UI updated");
    }

    private void updateUi(RemoteViews remoteViews) {
        AppWidgetManager appWidgetManager =
                AppWidgetManager.getInstance(this);
        assert appWidgetManager != null;

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

                // Fill in unset preferences from defaults
                PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
            } else {
                Log.d(TAG, "Not touching existing update listener");
            }

            setPeriodicUpdatesEnabled(true);
        }

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
    void scheduleTemperatureUpdate(@Nullable Intent intent) {
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

        if (why == UpdateReason.GPSA_RECONNECT) {
            if (updateListener != null) {
                updateListener.reconnectGpsa();
            } else {
                Log.i(TAG, "Ignoring Google Play Services API change");
            }
            return;
        }

        if (why == UpdateReason.DISPLAY_OR_TIMER) {
            // This can mean another widget was added; make sure it's fresh
            display_or_timer_count++;
            long dtHours = (System.currentTimeMillis() - serviceStartTimestamp) / (1000 * 60 * 60);
            if (dtHours == 0) {
                dtHours = 1;
            }
            Log.d(TAG, String.format("%d display updates in %dh at %f updates/hour",
                    display_or_timer_count,
                    dtHours,
                    display_or_timer_count / (double)dtHours));
            updateUi();
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

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (getWidgetIds().length == 0) {
            if (GENERATE_TRACEFILES) {
                Debug.stopMethodTracing();
            }
            // We have no widgets, shut down and drop out
            close();
        } else {
            onUpdateInternal(intent);
        }

        // All code expects us to be around continuously, return STICKY here
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (logcat != null) {
            logcat.destroy();
            logcat = null;

            Log.i(TAG, "Background logcat stopped");
        }
    }
}
