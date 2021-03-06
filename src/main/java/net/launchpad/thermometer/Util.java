package net.launchpad.thermometer;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.text.format.DateFormat;
import android.util.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Calendar;
import java.util.GregorianCalendar;

import static net.launchpad.thermometer.ThermometerWidget.TAG;

public final class Util {
    public static final String GOOGLE_PLAY_SERVICES_PACKAGE_NAME = "com.google.android.gms";
    public static final String GOOGLE_PLAY_STORE_PACKAGE_NAME = "com.android.vending";

    /**
     * Uncallable constructor to keep people from instantiating this class.
     */
    private Util() {
        // This block intentionally left blank
    }

    /**
     * Convert a number of minutes to a string like "3 days old".  Resolutions goes from minutes to years.
     *
     * @param ageMinutes The number of minutes.
     *
     * @return A string like "2 hours old".
     */
    @NotNull
    public static String minutesToTimeOldString(int ageMinutes) {
        if (ageMinutes < -1) {
            return -ageMinutes + " minutes future";
        }

        if (ageMinutes <= 1) {
            return "current";
        }

        return msToTimeString(ageMinutes * 60L * 1000) + " old";
    }

    /**
     * Convert a number of milliseconds to a string like "3s" or "12 days".
     *
     * @param ms A number of milliseconds
     *
     * @return A string like "9 years"
     */
    @NotNull
    public static String msToTimeString(long ms) {
        if (ms < 10000) {
            return ms + "ms";
        }

        if (ms < 120 * 1000) {
            return (ms / 1000) + "s";
        }

        long ageMinutes = ms / (60 * 1000);

        if (ageMinutes < 60 * 2) {
            return ageMinutes + " minutes";
        }

        if (ageMinutes < 60 * 24 * 2) {
            return ageMinutes / 60 + " hours";
        }

        if (ageMinutes < 60 * 24 * 7 * 2) {
            return ageMinutes / (60 * 24) + " days";
        }

        if (ageMinutes < 60 * 24 * (365.25 / 12) * 2) {
            return ageMinutes / (60 * 24 * 7) + " weeks";
        }

        if (ageMinutes < 60 * 24 * 365.25 * 2) {
            return ((int)(ageMinutes / (60 * 24 * (365.25 / 12)))) + " months";
        }

        return ((int)(ageMinutes / (60 * 24 * 365.25))) + " years";
    }

    /**
     * Return a String representing the given time of day (hours and minutes)
     * according to the user's system settings.
     *
     * @param time A time of day.
     *
     * @return Either "15:42" or "3:42PM".
     */
    @NotNull
    public static CharSequence toHoursString(@NotNull Calendar time, boolean is24HourFormat) {
        String format;

        if (is24HourFormat) {
            format = "kk:mm";
        } else {
            format = "h:mma";
        }

        return DateFormat.format(format, time);
    }

    /**
     * Find out if we're running on an emulator.
     *
     * @return true if we're running on an emulator, false otherwise
     */
    public static boolean isRunningOnEmulator() {
        // Inspired by
        // http://stackoverflow.com/questions/2799097/how-can-i-detect-when-an-android-application-is-running-in-the-emulator
        if ("sdk".equals(Build.PRODUCT)) {
            return true;
        }

        if ("google_sdk".equals(Build.PRODUCT)) {
            return true;
        }

        if ("google_sdk_x86".equals(Build.PRODUCT)) {
            return true;
        }

        return false;
    }

    public static boolean isFahrenheit(@NotNull String possiblyFahrenheit) {
        if ("Fahrenheit".equals(possiblyFahrenheit)) {
            return true;
        }

        // We used to have this mis-spelled
        if ("Farenheit".equals(possiblyFahrenheit)) {
            return true;
        }

        return false;
    }

    /**
     * Converts an UTC calendar to a calendar for the local time zone.
     *
     * @param utc An UTC calendar.
     *
     * @return A calendar representing the same time, but in the local time zone.
     */
    @NotNull
    static Calendar toLocal(@NotNull Calendar utc) {
        Calendar local = new GregorianCalendar();
        local.setTimeInMillis(utc.getTimeInMillis());
        return local;
    }

    /**
     * Capitalize A String Like This.
     *
     * @param capitalizeMe A string to capitalize.
     *
     * @return A capitalized version of capitalizeMe.
     */
    @NotNull
    static String capitalize(@NotNull CharSequence capitalizeMe) {
        StringBuilder builder = new StringBuilder(capitalizeMe.length());
        boolean shouldCapitalize = true;
        for (int i = 0; i < capitalizeMe.length(); i++) {
            char current = capitalizeMe.charAt(i);

            if (Character.isLetter(current)) {
                if (shouldCapitalize) {
                    builder.append(Character.toUpperCase(current));
                } else {
                    builder.append(Character.toLowerCase(current));
                }
                shouldCapitalize = false;
            } else if (Character.isWhitespace(current)) {
                builder.append(current);
                shouldCapitalize = true;
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    /**
     * Prettify a station name that we got from the Internet.
     *
     * @param ugly An ugly station name.
     *
     * @return A pretty station name.
     */
    @Nullable
    static String prettifyStationName(@Nullable String ugly) {
        if (ugly == null) {
            return null;
        }

        ugly = ugly.trim();
        if (ugly.length() == 0) {
            return null;
        }

        boolean hasUpper = false;
        boolean hasLower = false;
        for (int i = 0; i < ugly.length(); i++) {
            char current = ugly.charAt(i);
            if (Character.isUpperCase(current)) {
                hasUpper = true;
            } else if (Character.isLowerCase(current)) {
                hasLower = true;
            }
        }
        if (hasUpper && !hasLower) {
            ugly = capitalize(ugly);
        } else if (hasLower && !hasUpper) {
            ugly = capitalize(ugly);
        }

        // Chop off any unfinished parenthesis
        int leftParen = ugly.indexOf('(');
        if (leftParen > 0 && ugly.indexOf(')') < 0) {
            ugly = ugly.substring(0, leftParen);
            ugly = ugly.trim();
        }

        // Convert "Coeur d'Alene, Coeur d'Alene Air Terminal"
        //    into                "Coeur d'Alene Air Terminal"
        int separatorIndex = ugly.indexOf(", ");
        if (separatorIndex >= 0) {
            String left = ugly.substring(0, separatorIndex);
            String right = ugly.substring(separatorIndex + 2);
            if (right.startsWith(left)) {
                ugly = right;
            }
        }

        // Ugly is now pretty
        return ugly;
    }

    /**
     * Can network positioning be used on this device?
     */
    @NotNull
    public static ProviderStatus getNetworkPositioningStatus(@NotNull Context context) {
        LocationManager locationManager =
                (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);

        if (locationManager.getProvider(LocationManager.NETWORK_PROVIDER) == null) {
            return ProviderStatus.UNAVAILABLE;
        }

        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            return ProviderStatus.ENABLED;
        }

        return ProviderStatus.DISABLED;
    }

    /**
     * @see #getNetworkPositioningStatus(Context)
     */
    public enum ProviderStatus {
        /**
         * Provider is not available on this device, don't confuse with {@link #DISABLED}.
         */
        UNAVAILABLE,

        /**
         * Provider can be enabled but is currently disabled.
         */
        DISABLED,

        /**
         * Provider is enabled.
         */
        ENABLED
    }

    /**
     * Get info about an installed package.
     * @return Null if the package wasn't found.
     */
    @Nullable
    public static PackageInfo getPackageInfo(@NotNull Context context, @NotNull String packageName) {
        try {
            final PackageManager packageManager = context.getPackageManager();
            assert packageManager != null;

            return packageManager.getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.i(TAG, "Couldn't find " + packageName);
            return null;
        }
    }

}
