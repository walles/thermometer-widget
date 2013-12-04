package net.launchpad.thermometer;

import static net.launchpad.thermometer.ThermometerWidget.TAG;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

/**
 * A weather observation.
 *
 * @author johan.walles@gmail.com
 */
public class Weather {
    /**
     * Used by {@link #toString()}.
     */
    private static final SimpleDateFormat FORMATTER =
            new SimpleDateFormat("yyyy MMM dd hh:mm zz");

    /**
     * The temperature in Celsius.
     */
    private final double centigrades;

    /**
     * The wind speed in knots.
     */
    private final double windKnots;

    /**
     * The name of the weather station.
     */
    private final String stationName;

    /**
     * When this weather was observed.
     */
    private final Calendar observationTime;

    /**
     * When was this weather observed?
     *
     * @return When this weather observation is from.
     */
    public Calendar getObservationTime() {
        return observationTime;
    }

    /**
     * Converts an UTC calendar to a calendar for the local time zone.
     *
     * @param utc An UTC calendar.
     *
     * @return A calendar representing the same time, but in the local time zone.
     */
    static Calendar toLocal(Calendar utc) {
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
    private static String capitalize(CharSequence capitalizeMe) {
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
    private static String prettifyStationName(String ugly) {
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
     * Parse the weather from a JSON object.
     *
     * @param weatherObservation The (non-null) weather data.
     *
     * @throws IllegalArgumentException with an explanatory message on trouble
     */
    @SuppressWarnings("StringConcatenationMissingWhitespace")
    public Weather(JSONObject weatherObservation) {
        if (weatherObservation == null) {
            throw new NullPointerException("Want a non-null JSON object to parse");
        }

        try {
            if (weatherObservation.has("message")) {
                String message = weatherObservation.getString("message");
                if ("Error: Not found city".equals(message)) {
                    message = "No weather stations nearby";
                } else {
                    message = message.replace("Error: ", "Weather service error: ");
                }
                throw new IllegalArgumentException(message);
            }

            Log.d(TAG, "New weather observation received:\n" + weatherObservation);

            if (weatherObservation.has("dt")) {
                Calendar utc = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
                utc.setTimeInMillis(weatherObservation.getLong("dt") * 1000);
                observationTime = toLocal(utc);
                Log.d(TAG, "New observation is " + getAgeMinutes() + " minutes old");
            } else {
                observationTime = null;
            }

            if (weatherObservation.has("name")) {
                String extractedStationName = weatherObservation.getString("name");
                stationName = prettifyStationName(extractedStationName);
            } else {
                stationName = null;
            }

            String fromStation = "";
            if (stationName != null) {
                fromStation = " from " + stationName;
            }

            if (!weatherObservation.has("main")) {
                throw new IllegalArgumentException("No temperature (1)" + fromStation);
            }
            JSONObject observationMain = weatherObservation.getJSONObject("main");

            if (!observationMain.has("temp")) {
                throw new IllegalArgumentException("No temperature (2)" + fromStation);
            }
            try {
                double kelvin = observationMain.getDouble("temp");
                centigrades = kelvin - 273.15;
            } catch (JSONException e) {
                throw new IllegalArgumentException(String.format("Borken temperature <%s>%s",
                        observationMain.getString("temp"),
                        fromStation), e);
            }

            if (weatherObservation.has("wind")) {
                JSONObject windObservation = weatherObservation.getJSONObject("wind");
                double windSpeedMps = windObservation.getDouble("speed");
                windKnots = windSpeedMps * 1.942615;
            } else {
                Log.d(TAG, "Got no wind info" + fromStation);

                // Pretend it's calm
                windKnots = 0.0;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Parsing weather data failed:\n" + weatherObservation, e);
            throw new IllegalArgumentException("Error parsing weather data", e);
        }
    }

    /**
     * Compute a wind chilled number of centigrades.
     *
     * @return The wind chilled number of centigrades.
     *
     * @see "http://en.wikipedia.org/wiki/Wind_chill#North_American_wind_chill_index"
     */
    @SuppressWarnings("StringConcatenationMissingWhitespace")
    private double getWindChilledCentigrades() {
        double windKmh = 1.85 * windKnots;

        if (centigrades > 10.0) {
            Log.d(TAG, "Not computing wind chill over 10C: "
                + Math.round(centigrades) + "C");
            return centigrades;
        }

        if (windKmh < 4.8) {
            Log.d(TAG, "Not computing wind chill under 4.8km/h: "
                + Math.round(windKmh) + "km/h");
            return centigrades;
        }

        double windKmhTo0_16 = Math.pow(windKmh, 0.16);
        double adjustedCentigrades =
            13.12
            + 0.6215 * centigrades
            - 11.37 *  windKmhTo0_16
            + 0.3965 * centigrades * windKmhTo0_16;

        return adjustedCentigrades;
    }

    /**
     * Get the temperature in Celsius.
     *
     * @param correctForWindChill True to get a value corrected for wind chill.
     * False otherwise.
     *
     * @return The temperature in celsius.
     */
    public int getCentigrades(boolean correctForWindChill) {
        if (!correctForWindChill) {
            return (int)Math.round(centigrades);
        }

        return (int)Math.round(getWindChilledCentigrades());
    }

    /**
     * Get the temperature in Farenheit.
     *
     * @param correctForWindChill True to get a value corrected for wind chill.
     * False otherwise.
     *
     * @return The temperature in farenheit.
     */
    public int getFarenheit(boolean correctForWindChill) {
        double convertMe = centigrades;

        if (correctForWindChill) {
            convertMe = getWindChilledCentigrades();
        }

        double farenheit = convertMe * 9.0 / 5.0 + 32.0;
        return (int)Math.round(farenheit);
    }

    /**
     * Get the weather station name.
     *
     * @return The weather station name, or null if the name of the weather
     * station is unknown.
     */
    public String getStationName() {
        return stationName;
    }

    @Override
    public String toString() {
        String timeString;
        if (observationTime != null) {
            timeString = FORMATTER.format(observationTime.getTime());
        } else {
            timeString = "<null>";
        }
        return String.format("%.1fC, %.1fkts at %s on %s",
            centigrades, windKnots, stationName, timeString);
    }

    /**
     * How many minutes old is this observation?
     *
     * @return The age of this observation in minutes.
     */
    public final int getAgeMinutes() {
        if (observationTime == null) {
            return Integer.MAX_VALUE;
        }

        long ageMs =
            System.currentTimeMillis() - observationTime.getTimeInMillis();

        return (int)(ageMs / (60 * 1000));
    }

    /**
     * This method has default protection for testing purposes
     *
     * @return The wind speed in knots
     */
    double getWindKnots() {
        return windKnots;
    }
}
