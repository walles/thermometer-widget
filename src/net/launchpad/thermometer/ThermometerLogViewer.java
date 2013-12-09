package net.launchpad.thermometer;

import static net.launchpad.thermometer.ThermometerWidget.TAG;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;

/**
 * Shows the Thermometer Widget logs.
 */
public class ThermometerLogViewer extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        long t0 = System.currentTimeMillis();

        setContentView(R.layout.log_viewer);

        TextView logView = (TextView)findViewById(R.id.logView);
        logView.setHorizontallyScrolling(true);
        logView.setHorizontalScrollBarEnabled(true);

        addLogsToView(logView);

        // Scroll log view to bottom
        final ScrollView verticalScrollView = (ScrollView)findViewById(R.id.verticalScrollView);
        verticalScrollView.post(new Runnable() {
            @Override
            public void run() {
                verticalScrollView.smoothScrollTo(0, Integer.MAX_VALUE);
            }
        });

        Button reportProblemButton = (Button)findViewById(R.id.reportProblemButton);
        reportProblemButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                emailDeveloper();
            }
        });

        long t1 = System.currentTimeMillis();
        Log.d(TAG, String.format("Setting up the log viewer took %dms", t1 - t0));
    }

    private static final String LOG_DUMP_FILENAME = "thermometer_widget_log.txt";

    private Uri getEmailLogAttachmentUri() {
        // Dump log view contents into a file
        Writer out;
        try {
            out = new OutputStreamWriter(
                    openFileOutput(LOG_DUMP_FILENAME, MODE_WORLD_READABLE));
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Unable to open log dump file for writing", e);
            return null;
        }
        try {
            TextView logView = (TextView)findViewById(R.id.logView);
            out.write(logView.getText().toString());
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

        File file = new File(getFilesDir(), LOG_DUMP_FILENAME);
        return Uri.fromFile(file);
    }

    private String getEmailSubject() {
        String versionName;
        try {
            versionName = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Couldn't find my own version number", e);
            versionName = "(unknown version)";
        }

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

    private String getEmailText() {
        StringWriter text = new StringWriter();

        @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
        PrintWriter textWriter = new PrintWriter(text);

        textWriter.println("Hi!");
        textWriter.println();
        textWriter.println("Here's what I'm running on:");
        textWriter.print(getDeviceDescription());
        textWriter.println();
        textWriter.print("I'm having problems with the Thermometer Widget.");
        textWriter.println(" Here's a very detailed explanation of what's wrong:");
        textWriter.println();

        return text.toString();
    }

    private void emailDeveloper() {
        // Compose an e-mail with the log file attached
        Intent intent = new Intent(Intent.ACTION_SEND);

        intent.setType("message/rfc822");

        intent.putExtra(Intent.EXTRA_EMAIL,
                new String[] { "johan.walles@gmail.com" });

        intent.putExtra(Intent.EXTRA_SUBJECT, getEmailSubject());

        intent.putExtra(Intent.EXTRA_TEXT, getEmailText());

        // Attach the newly dumped log file
        Uri uri = getEmailLogAttachmentUri();
        if (uri == null) {
            Log.e(TAG, "No log file to attach");
            return;
        }
        intent.putExtra(Intent.EXTRA_STREAM, uri);

        startActivity(Intent.createChooser(intent, "Report Problem"));
    }

    private static void addLogsToView(TextView logView) {
        // Load the logs into the log_viewer's logView.
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("logcat -d -v time");
        } catch (IOException e) {
            Log.e(TAG, "exec(logcat) failed", e);
            logView.append("exec(logcat) failed");
            return;
        }

        BufferedReader bufferedReader =
                new BufferedReader(new InputStreamReader(process.getInputStream()));
        try {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                logView.append(line);
                logView.append("\n");
            }
        } catch (IOException e) {
            Log.e(TAG, "Reading log files failed", e);
            logView.append("Reading log files failed");
        } finally {
            try {
                bufferedReader.close();
            } catch (IOException e) {
                Log.w(TAG, "Closing logcat pipe failed", e);
            }
        }
    }
}
