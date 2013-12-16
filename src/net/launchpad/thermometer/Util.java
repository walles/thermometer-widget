package net.launchpad.thermometer;

import android.os.Build;

public final class Util {
    /**
     * Uncallable constructor to keep people from instantiating this class.
     */
    private Util() {
        // This block intentionally left blank
    }

    /**
     * Convert a number of minutes to a string like "3 days old".  Resolutions goes from minutes to years.
     * <p>
     * Has default protection for testing purposes.
     *
     * @param ageMinutes The number of minutes.
     *
     * @return A string like "2 hours old".
     */
    public static String minutesToTimeOldString(int ageMinutes) {
        if (ageMinutes <= 1) {
            return "current";
        }

        if (ageMinutes < 60 * 2) {
            return ageMinutes + " minutes old";
        }

        if (ageMinutes < 60 * 24 * 2) {
            return ageMinutes / 60 + " hours old";
        }

        if (ageMinutes < 60 * 24 * 7 * 2) {
            return ageMinutes / (60 * 24) + " days old";
        }

        if (ageMinutes < 60 * 24 * (365.25 / 12) * 2) {
            return ageMinutes / (60 * 24 * 7) + " weeks old";
        }

        if (ageMinutes < 60 * 24 * 365.25 * 2) {
            return ((int)(ageMinutes / (60 * 24 * (365.25 / 12)))) + " months old";
        }

        return ((int)(ageMinutes / (60 * 24 * 365.25))) + " years old";
    }

    /**
     * Find out if we're running on an emulator.
     *
     * @return true if we're running on an emulator, false otherwise
     */
    public static boolean isRunningOnEmulator() {
        // Inspired by
        // http://stackoverflow.com/questions/2799097/how-can-i-detect-when-an-android-application-is-running-in-the-emulator
        return "sdk".equals(Build.PRODUCT);
    }
}
