package net.launchpad.thermometer;

import static net.launchpad.thermometer.ThermometerWidget.TAG;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.widget.TextView;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Launch this activity if you're having problems with the Google Play Services API.
 */
public class FixGpsaActivity extends Activity {
    /**
     * Create a pending intent to launch the FixGpsaActivity.
     */
    public static PendingIntent createPendingIntent(
            @NotNull Context context)
    {
        Intent intent = new Intent(context, FixGpsaActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent =
                PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        assert pendingIntent != null;
        return pendingIntent;
    }

    private void showNoGooglePlayStoreDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Google Play store missing");
        builder.setMessage(Html.fromHtml(
                "The store is needed for positioning, "
                        + "<a href=\"https://answers.launchpad.net/thermometer/+faq/2498\">read more here</a>."));
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setCancelable(false);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                finish();
            }
        });

        Dialog dialog = builder.create();
        dialog.show();

        // Make links clickable, from http://stackoverflow.com/questions/1997328#answer-5458225
        TextView messageView = (TextView)dialog.findViewById(android.R.id.message);
        messageView.setMovementMethod(LinkMovementMethod.getInstance());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "Launching GPSA fixer activity...");
        int errorCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        boolean userRecoverableError = GooglePlayServicesUtil.isUserRecoverableError(errorCode);
        if (errorCode == ConnectionResult.SERVICE_INVALID) {
            // SERVICE_INVALID == "Contact device manufacturer", but we want to know about that.
            userRecoverableError = false;
        }
        if (userRecoverableError) {
            Dialog resolutionDialog = GooglePlayServicesUtil.getErrorDialog(errorCode, this, 9999, new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialogInterface) {
                    // The user doesn't want our help
                    finish();
                }
            });

            Log.d(TAG, "Showing GPSA problem resolution dialog...");
            resolutionDialog.show();
            return;
        }

        if (errorCode == ConnectionResult.SUCCESS) {
            Log.i(TAG, "GPSA error seems gone, asking widget to try again");
            WidgetManager.onUpdate(this, WidgetManager.UpdateReason.GPSA_RECONNECT);
            finish();
            return;
        }

        if (Util.getPackageInfo(this, Util.GOOGLE_PLAY_STORE_PACKAGE_NAME) == null) {
            Log.d(TAG, "Showing no-Google-Play-store dialog...");
            showNoGooglePlayStoreDialog();
            return;
        }

        Log.e(TAG, "GPSA error not recoverable: " + GooglePlayServicesUtil.getErrorString(errorCode));

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Unrecoverable location error, please send in a problem report: "
                + GooglePlayServicesUtil.getErrorString(errorCode));
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setCancelable(false);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                // Launch the preferences activity with the Log fragment visible
                Intent intent = new Intent(FixGpsaActivity.this, ThermometerActions.class);
                intent.putExtra(ThermometerActions.SHOW_LOGS_EXTRA, true);
                startActivity(intent);

                finish();
            }
        });

        builder.create().show();
    }
}
