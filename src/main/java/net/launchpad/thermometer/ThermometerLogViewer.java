package net.launchpad.thermometer;

import static net.launchpad.thermometer.ThermometerWidget.TAG;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Shows the Thermometer Widget logs.
 */
public class ThermometerLogViewer extends Fragment {
    private TextView logView;
    private Button reportProblemButton;
    private ScrollView verticalScrollView;

    private class ReadLogsTask extends AsyncTask<Void, Void, CharSequence> {
        private @NotNull CharSequence getCurrentLogs() {
            StringBuilder builder = new StringBuilder();

            Process process;
            try {
                process = Runtime.getRuntime().exec("logcat -d -v time");
            } catch (IOException e) {
                Log.e(TAG, "exec(logcat) failed", e);

                builder.append("exec(logcat) failed");
                return builder;
            }

            BufferedReader bufferedReader =
                    new BufferedReader(new InputStreamReader(process.getInputStream()));
            try {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    builder.append(line);
                    builder.append("\n");
                }
            } catch (IOException e) {
                Log.e(TAG, "Reading log files failed", e);
                builder.append("Reading log files failed");
            } finally {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    Log.w(TAG, "Closing logcat pipe failed", e);
                }
            }

            return builder;
        }

        /**
         * Read logs stored on disk.
         * <p>
         * Note that this method needs to read the logs produced by logcat started at {@link WidgetManager#onCreate()},
         * so if that method changes, this one needs to change as well.
         *
         * @return The log file contents
         */
        private @NotNull CharSequence getStoredLogs() {
            File logdir = getNonNullActivity().getDir("logs", Activity.MODE_PRIVATE);
            File[] files = logdir.listFiles();
            assert files != null;

            List<File> logfiles = new ArrayList<File>();
            for (File file : files) {
                if (!file.isFile()) {
                    continue;
                }

                if (!file.getName().startsWith("log")) {
                    continue;
                }

                logfiles.add(file);
            }

            Collections.sort(logfiles);
            Collections.reverse(logfiles);

            StringBuilder builder = new StringBuilder();
            for (File logfile : logfiles) {
                builder.append("\n");
                builder.append(logfile.getAbsolutePath());
                builder.append(":\n");

                BufferedReader reader = null;
                try {
                    reader = new BufferedReader(new FileReader(logfile));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                        builder.append("\n");
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to read " + logfile.getAbsolutePath());
                    builder.append("Reading file failed: ");
                    builder.append(e.getMessage());
                    builder.append("\n");
                } finally {
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (IOException e) {
                            Log.w(TAG, "Closing log file reader failed: " + logfile.getAbsolutePath(), e);
                        }
                    }
                }
            }

            return builder;
        }

        @NotNull
        @Override
        protected CharSequence doInBackground(Void... voids) {
            long t0 = System.currentTimeMillis();

            StringBuilder builder = new StringBuilder();
            builder.append("Device description:\n");
            builder.append(getDeviceDescription());

            builder.append("\n");
            builder.append("Installed:\n");
            builder.append(listInstalledPackages());

            builder.append("\nGoogle Play Services version: ");
            builder.append(getVersion("com.google.android.gms"));
            builder.append("\nGoogle Play version: ");
            builder.append(getVersion("com.android.vending"));
            builder.append("\nNetwork positioning is ");
            builder.append(Util.getNetworkPositioningStatus(getNonNullActivity()));
            builder.append("\n");

            builder.append("\n");
            builder.append(getServiceCpuStats());

            builder.append("\n");
            builder.append(getStoredLogs());

            builder.append("\n");
            builder.append("Current logs:\n");
            builder.append(getCurrentLogs());

            long t1 = System.currentTimeMillis();
            Log.d(TAG, String.format("Reading all logs took %dms", t1 - t0));

            return builder;
        }

        @Override
        protected void onPostExecute(CharSequence text) {
            logView.setText(text);

            // Scroll log view to bottom
            verticalScrollView.post(new Runnable() {
                @Override
                public void run() {
                    verticalScrollView.smoothScrollTo(0, Integer.MAX_VALUE);
                }
            });

            reportProblemButton.setEnabled(true);
        }
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        long t0 = System.currentTimeMillis();

        View view = inflater.inflate(R.layout.log_viewer, container, false);
        assert view != null;

        logView = (TextView)view.findViewById(R.id.logView);
        logView.setHorizontallyScrolling(true);
        logView.setHorizontalScrollBarEnabled(true);
        logView.setText("Reading logs, stand by...");

        verticalScrollView = (ScrollView)view.findViewById(R.id.verticalScrollView);

        reportProblemButton = (Button)view.findViewById(R.id.reportProblemButton);
        reportProblemButton.setEnabled(false);  // Disable button until the logs have been loaded
        reportProblemButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                emailDeveloper();
            }
        });

        // Read the logs asynchronously and put them in the UI when done
        new ReadLogsTask().execute();

        long t1 = System.currentTimeMillis();
        Log.d(TAG, String.format("Setting up the log viewer took %dms", t1 - t0));

        return view;
    }

    private static final String LOG_DUMP_FILENAME = "thermometer_widget_log.txt";

    @Nullable
    @SuppressLint("WorldReadableFiles")
    private Uri getEmailLogAttachmentUri() {
        if (logView == null) {
            Log.e(TAG, "Log view not initialized before sending e-mail");
            return null;
        }

        // Dump log view contents into a file
        Writer out;
        try {
            //noinspection deprecation
            out = new OutputStreamWriter(
                    getNonNullActivity().openFileOutput(LOG_DUMP_FILENAME, Activity.MODE_WORLD_READABLE));
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Unable to open log dump file for writing", e);
            return null;
        }
        try {
            CharSequence text = logView.getText();
            out.write(text != null ? text.toString(): "<null>");
        } catch (IOException e) {
            Log.e(TAG, "Writing log dump failed", e);
            return null;
        } finally {
            try {
                out.close();
            } catch (IOException e) {
                Log.w(TAG, "Closing log dump file failed", e);
            }
        }

        File file = new File(getNonNullActivity().getFilesDir(), LOG_DUMP_FILENAME);
        return Uri.fromFile(file);
    }

    @NotNull
    private String getVersion(@NotNull String packageName) {
        PackageInfo packageInfo = getPackageInfo(packageName);
        if (packageInfo == null) {
            return "(unknown version)";
        }
        return packageInfo.versionName;
    }

    @Nullable
    private PackageInfo getPackageInfo(@NotNull String packageName) {
        try {
            final PackageManager packageManager = getNonNullActivity().getPackageManager();
            assert packageManager != null;

            return packageManager.getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Couldn't find " + packageName, e);
            return null;
        }
    }

    @NotNull
    private String getEmailSubject() {
        String versionName = getVersion(getNonNullActivity().getPackageName());

        return "Thermometer Widget " + versionName;
    }

    private CharSequence getDeviceDescription() {
        StringWriter text = new StringWriter();

        @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
        PrintWriter textWriter = new PrintWriter(text);

        for (Field field : Build.class.getFields()) {
            if (!String.class.equals(field.getType())) {
                continue;
            }

            String value;
            try {
                value = (String)field.get(null);
            } catch (IllegalAccessException ignored) {
                continue;
            }
            textWriter.format("  %s: %s\n", field.getName(), value);
        }

        return text.getBuffer();
    }

    /**
     * Get info about our widget service process.
     *
     * @return Null if no service info is found.
     */
    @Nullable
    private ActivityManager.RunningServiceInfo getServiceInfo() {
        ActivityManager activityManager =
                (ActivityManager)getNonNullActivity().getSystemService(Context.ACTIVITY_SERVICE);
        assert activityManager != null;

        List<ActivityManager.RunningServiceInfo> runningServices = activityManager.getRunningServices(999);
        assert runningServices != null;

        String myPackageName = getNonNullActivity().getComponentName().getPackageName();
        assert myPackageName != null;

        for (ActivityManager.RunningServiceInfo serviceInfo : runningServices) {
            ComponentName componentName = serviceInfo.service;
            if (componentName == null) {
                continue;
            }
            if (myPackageName.equals(componentName.getPackageName())) {
                return serviceInfo;
            }
        }

        // Not found
        return null;
    }

    /**
     * Get information about a given PID. The information is retrieved from
     * <a href="http://linux.die.net/man/5/proc">/proc/[pid]/stat</a>.
     * 
     * @return null if stats couldn't be retrieved.
     */
    @Nullable
    private String[] getPidStats(int pid) {
        String statFileName = String.format("/proc/%d/stat", pid);
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(statFileName));
            String line = reader.readLine();
            if (line == null) {
                Log.w(TAG, String.format("%s was empty", statFileName));
                return null;
            }
            return line.split("\\s");
        } catch (IOException e) {
            Log.w(TAG, String.format("Reading %s failed", statFileName), e);
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.w(TAG, String.format("Closing %s failed", statFileName), e);
                }
            }
        }
    }

    private CharSequence getServiceCpuStats() {
        ActivityManager.RunningServiceInfo serviceInfo = getServiceInfo();
        if (serviceInfo == null) {
            return "Service not running (no RunningServiceInfo)";
        }

        int servicePid = serviceInfo.pid;
        if (servicePid == 0) {
            return "Service not running (no PID)";
        }

        float uptime = SystemClock.elapsedRealtime() - serviceInfo.activeSince;

        String[] procPidStat = getPidStats(servicePid);
        if (procPidStat == null) {
            return "Stats unavailable for PID " + servicePid;
        }
        // Magic constants are from the Linux proc man page:
        // http://linux.die.net/man/5/proc look for "/proc/[pid]/stat"
        // The -1s are because the man page is one-based and the array indices are zero-based.
        long userTicks = Long.valueOf(procPidStat[14 - 1]);
        long kernelTicks = Long.valueOf(procPidStat[15 - 1]);

        // FIXME: How can we find out the real value? 100 seems to be most common.
        final float _SC_CLK_TCK = 100;
        float userMs = (1000 * userTicks) / _SC_CLK_TCK;
        float kernelMs = (1000 * kernelTicks) / _SC_CLK_TCK;

        StringBuilder builder = new StringBuilder();
        builder.append(String.format("Widget uptime: %s", Util.msToTimeString((long)uptime)));
        builder.append(String.format("\nCPU seconds per hour uptime: %.1f (total=%s)",
                3600 * (kernelMs + userMs) / uptime,
                Util.msToTimeString((long)(kernelMs + userMs))));
        builder.append(String.format("\nCPU seconds per hour uptime (usr): %.1f (total=%s)",
                3600 * userMs / uptime,
                Util.msToTimeString((long)userMs)));
        builder.append(String.format("\nCPU seconds per hour uptime (sys): %.1f (total=%s)",
                3600 * kernelMs / uptime,
                Util.msToTimeString((long)kernelMs)));
        return builder;
    }

    private CharSequence listInstalledPackages() {
        final PackageManager packageManager = getNonNullActivity().getPackageManager();
        assert packageManager != null;

        SortedSet<String> packages = new TreeSet<String>();
        for (PackageInfo packageInfo : packageManager.getInstalledPackages(0)) {
            ApplicationInfo applicationInfo = packageInfo.applicationInfo;
            CharSequence applicationName = null;
            if (applicationInfo != null) {
                applicationName = packageInfo.applicationInfo.loadLabel(packageManager);
            }

            StringBuilder builder = new StringBuilder();
            if (applicationName != null && applicationName.length() > 0) {
                builder.append(applicationName);
            } else {
                builder.append(packageInfo.packageName);
            }

            builder.append(" ");
            builder.append(packageInfo.versionName);

            packages.add(builder.toString());
        }

        StringBuilder builder = new StringBuilder();
        for (String packageInfo : packages) {
            builder.append("  ");
            builder.append(packageInfo);
            builder.append("\n");
        }

        return builder.toString();
    }

    private void emailDeveloper() {
        // Compose an e-mail with the log file attached
        Intent intent = new Intent(Intent.ACTION_SEND);

        intent.setType("message/rfc822");

        intent.putExtra(Intent.EXTRA_EMAIL,
                new String[] { "johan.walles@gmail.com" });

        intent.putExtra(Intent.EXTRA_SUBJECT, getEmailSubject());

        // Attach the newly dumped log file
        Uri uri = getEmailLogAttachmentUri();
        if (uri == null) {
            Log.e(TAG, "No log file to attach");
            return;
        }
        intent.putExtra(Intent.EXTRA_STREAM, uri);

        startActivity(Intent.createChooser(intent, "Report Problem"));
    }

    @NotNull
    public Activity getNonNullActivity() {
        Activity activity = getActivity();
        assert activity != null;
        return activity;
    }
}
