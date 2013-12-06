package net.launchpad.thermometer;

import static net.launchpad.thermometer.ThermometerWidget.TAG;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
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
import java.io.Writer;

/**
 * Shows the Thermometer Widget logs.
 */
public class ThermometerLogViewer extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
    }

    private void emailDeveloper() {
        final String LOG_DUMP_FILENAME = "thermometer_widget_log.txt";

        // Dump log view contents into a file
        Writer out;
        try {
            out = new OutputStreamWriter(
                    openFileOutput(LOG_DUMP_FILENAME, MODE_WORLD_READABLE));
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Unable to open log dump file for writing", e);
            return;
        }
        try {
            TextView logView = (TextView)findViewById(R.id.logView);
            out.write(logView.getText().toString());
        } catch (IOException e) {
            Log.e(TAG, "Writing log dump failed", e);
            return;
        } finally {
            try {
                out.close();
            } catch (IOException e) {
                Log.w(TAG, "Closing log dump file failed", e);
            }
        }

        // Compose an e-mail with the log file attached
        Intent intent = new Intent(Intent.ACTION_SEND);

        intent.setType("message/rfc822");

        // Fill in the recipient
        intent.putExtra(Intent.EXTRA_EMAIL, new String[] { "johan.walles@gmail.com" });

        // Put the version number to the subject line
        String versionName;
        try {
            versionName = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Couldn't find my own version number", e);
            versionName = "(unknown version)";
        }
        intent.putExtra(Intent.EXTRA_SUBJECT, "Thermometer Widget " + versionName);

        intent.putExtra(Intent.EXTRA_TEXT,
                "Hi!\n"
                        + "\n"
                        + "I'm having problems with the Thermometer Widget. Here's a very thorough explanation of what's wrong:\n"
                        + "\n");

        // Attach the newly dumped log file
        File file = new File(getFilesDir(), LOG_DUMP_FILENAME);
        Uri uri = Uri.fromFile(file);
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
