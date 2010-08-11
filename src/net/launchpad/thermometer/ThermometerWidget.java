package net.launchpad.thermometer;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * A widget displaying the current outdoor temperature.
 *
 * @author johan.walles@gmail.com
 */
public class ThermometerWidget extends AppWidgetProvider {
    /**
     * Used for tagging log messages.
     */
    public static final String TAG = "ThermWidget";

    @Override
    public synchronized void onUpdate(
        Context context,
        AppWidgetManager appWidgetManager,
        int[] updatedAppWidgetIds)
    {
        WidgetManager.onUpdate(context, updatedAppWidgetIds);
    }

    @Override
    public synchronized void onReceive(Context context, Intent intent) {
        // v1.5 fix that doesn't call onDelete Action
        final String action = intent.getAction();
        if (AppWidgetManager.ACTION_APPWIDGET_DELETED.equals(action)) {
            Log.d(TAG, "onReceive() got DELETED event");
            final int appWidgetId = intent.getExtras().getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                Log.d(TAG, "onReceive() faking call to onDeleted()");
                this.onDeleted(context, new int[] { appWidgetId });
            }
        } else {
            super.onReceive(context, intent);
        }
    }

    @Override
    public synchronized void onDeleted(Context context, int[] deletedIds) {
        WidgetManager.onDeleted(context, deletedIds);
    }
}
