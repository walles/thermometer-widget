package net.launchpad.thermometer;

import static net.launchpad.thermometer.ThermometerWidget.TAG;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
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
            Activity activity = getActivity();
            assert activity != null;

            File logdir = getActivity().getDir("logs", Activity.MODE_PRIVATE);
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

            builder.append("\nGoogle Play Services version: ");
            builder.append(getVersion("com.google.android.gms"));
            builder.append("\nGoogle Play version: ");
            builder.append(getVersion("com.android.vending"));
            builder.append("\nNetwork positioning is ");
            Context context = getActivity();
            assert context != null;
            builder.append(Util.getNetworkPositioningStatus(context));
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

        Activity activity = getActivity();
        assert activity != null;

        // Dump log view contents into a file
        Writer out;
        try {
            //noinspection deprecation
            out = new OutputStreamWriter(
                    activity.openFileOutput(LOG_DUMP_FILENAME, Activity.MODE_WORLD_READABLE));
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

        File file = new File(activity.getFilesDir(), LOG_DUMP_FILENAME);
        return Uri.fromFile(file);
    }

    @NotNull
    private String getVersion(@NotNull String packageName) {
        try {
            Activity activity = getActivity();
            assert activity != null;

            final PackageManager packageManager = activity.getPackageManager();
            assert packageManager != null;

            final PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Couldn't find " + packageName + " version number", e);
            return "(unknown version)";
        }
    }

    @NotNull
    private String getEmailSubject() {
        Activity activity = getActivity();
        assert activity != null;

        String versionName = getVersion(activity.getPackageName());

        return "Thermometer Widget " + versionName;
    }

    private String getDeviceDescription() {
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
            textWriter.format("%s: %s\n", field.getName(), value);
        }

        return text.toString();
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
}
