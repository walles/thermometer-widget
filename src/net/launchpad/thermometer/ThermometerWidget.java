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

import net.launchpad.thermometer.WidgetManager.UpdateReason;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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
        int[] appWidgetIds)
    {
        WidgetManager.onUpdate(context, UpdateReason.DISPLAY_OR_TIMER);
    }

    @Override
    public synchronized void onReceive(Context context, Intent intent) {
        // v1.5 fix that doesn't call onDelete Action
        final String action = intent.getAction();
        if (AppWidgetManager.ACTION_APPWIDGET_DELETED.equals(action)) {
            handleWidgetDelete(context, intent);
        } else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
            handleConnectivityChange(context);
        } else {
            super.onReceive(context, intent);
        }
    }

    private void handleWidgetDelete(Context context, Intent intent) {
        Log.d(TAG, "onReceive() got DELETED event");
        final int appWidgetId = intent.getExtras().getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID);
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.d(TAG, "onReceive() faking call to onDeleted()");
            this.onDeleted(context, new int[] { appWidgetId });
        }
    }

    private void handleConnectivityChange(Context context) {
        Log.d(TAG, "Network connectivity changed...");
        ConnectivityManager connectivityManager =
                (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo == null) {
            Log.d(TAG, "No active data network");
            return;
        }

        if (!networkInfo.isConnected()) {
            Log.d(TAG, "Active network not connected");
            return;
        }

        Log.d(TAG, "Network available, triggering update: " + networkInfo.getTypeName());
        WidgetManager.onUpdate(context, UpdateReason.NETWORK_AVAILABLE);
    }

    @Override
    public synchronized void onDeleted(Context context, int[] deletedIds) {
        WidgetManager.onUpdate(context, UpdateReason.DISPLAY_OR_TIMER);
    }
}
