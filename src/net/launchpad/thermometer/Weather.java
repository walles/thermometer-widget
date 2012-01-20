package net.launchpad.thermometer;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * Tag used for logging.
     */
    private final static String TAG = "Weather";

    /**
     * Parser for timestamp strings in the following format: "2010-07-29 10:20:00"
     */
    private final static Pattern DATE_PARSE =
        Pattern.compile("([0-9]+).([0-9]+).([0-9]+).([0-9]+).([0-9]+).([0-9]+)");

    /**
     * The temperature in Celsius.
     */
    private final int centigrades;

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
    private Calendar observationTime;

    /**
     * When was this weather observed?
     *
     * @return When this weather observation is from.
     */
    public Calendar getObservationTime() {
        return observationTime;
    }

    /**
     * Convert a string to an UTC Calendar object.
     *
     * @param timeString An UTC time stamp in the following format: "2010-07-29 10:20:00"
     *
     * @return A calendar object representing the same time as the timeString,
     * or null if the time string couldn't be parsed.
     */
    static Calendar parseDateTime(String timeString) {
        Matcher match = DATE_PARSE.matcher(timeString);
        if (!match.matches()) {
            Log.w(TAG, "Can't parse time string: " + timeString);
            return null;
        }

        int year = Integer.valueOf(match.group(1));
        int month = Integer.valueOf(match.group(2));
        int day = Integer.valueOf(match.group(3));

        int hour = Integer.valueOf(match.group(4));
        int minute = Integer.valueOf(match.group(5));
        int second = Integer.valueOf(match.group(6));

        Calendar utcCalendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        utcCalendar.set(year, month, day, hour, minute, second);

        return utcCalendar;
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
     * Parse the weather from a JSON object.
     *
     * @param weatherObservation The (non-null) weather data.
     *
     * @throws IllegalArgumentException with an explanatory message on trouble
     */
    public Weather(JSONObject weatherObservation) {
        if (weatherObservation == null) {
            throw new NullPointerException("Want a non-null JSON object to parse");
        }

        try {
            if (weatherObservation.has("weatherObservation")) {
                weatherObservation =
                    weatherObservation.getJSONObject("weatherObservation");

                Log.d(TAG, "New weather observation received:\n" + weatherObservation);

                if (weatherObservation.has("datetime")) {
                    observationTime = toLocal(parseDateTime(weatherObservation.getString("datetime")));
                }

                if (weatherObservation.has("stationName")) {
                    String extractedStationName = weatherObservation.getString("stationName");
                    if (extractedStationName.length() == 0) {
                        extractedStationName = null;
                    }
                    stationName = extractedStationName;
                } else {
                    stationName = null;
                }

                if (!weatherObservation.has("temperature")) {
                    String fromStation = "";
                    if (stationName != null) {
                        fromStation = " from " + stationName;
                    }
                    throw new IllegalArgumentException("No temperature received"
                        + fromStation);
                }
                centigrades = weatherObservation.getInt("temperature");

                if (weatherObservation.has("windSpeed")) {
                    windKnots = weatherObservation.getDouble("windSpeed");
                } else {
                    // No wind observation received, let's pretend it's calm
                    windKnots = 0;
                }

                return;
            }

            // Assume an error message:
            // {"status":{"message":"error parsing parameters for lat/lng","value":14}}
            weatherObservation =
                weatherObservation.getJSONObject("status");
            int statusCode = weatherObservation.getInt("value");

            String statusMessage = weatherObservation.getString("message");
            Log.w(TAG, "Web service trouble: ["
                + statusCode
                + "] "
                + statusMessage);

            switch (statusCode) {
            case 22:
                // "The free servers are busy, go away"
                throw new IllegalArgumentException("Weather server busy");

            case 12:
                // Probably database trouble at the server side:
                // "Connection refused. Check that the hostname and port are
                // correct and that the postmaster is accepting TCP/IP
                // connections."
                throw new IllegalArgumentException("Weather server temporarily unavailable [12]");

            case 15:
                // "No observation found"
                throw new IllegalArgumentException("Temperature unavailable at current location");

            default:
                throw new IllegalArgumentException(statusMessage);
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
            return centigrades;
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
        return String.format("%dC, %.1fkts at %s on %s",
            centigrades, windKnots, stationName, observationTime);
    }
}
