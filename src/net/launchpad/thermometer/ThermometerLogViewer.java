package net.launchpad.thermometer;

import static net.launchpad.thermometer.ThermometerWidget.TAG;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

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

        // Scroll view to bottom
        final ScrollView verticalScrollView = (ScrollView)findViewById(R.id.verticalScrollView);
        verticalScrollView.post(new Runnable() {
            @Override
            public void run() {
                verticalScrollView.smoothScrollTo(0, Integer.MAX_VALUE);
            }
        });
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
