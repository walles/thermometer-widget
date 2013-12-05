package net.launchpad.thermometer;

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
}