package net.launchpad.thermometer;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import android.util.Log;

import static org.junit.Assert.*;
import static org.powermock.api.mockito.PowerMockito.*;

/**
 * Validate {@link Weather}.
 *
 * @author johan.walles@gmail.com
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(Log.class)
public class WeatherTest {
    /**
     * Validate {@link Weather#getObservationTime()}.
     *
     * @throws Exception if testing goes exceptionally wrong
     */
    @Test
    public void testParseDateTime() throws Exception {
        mockStatic(Log.class);

        Calendar verifyMe =
            Weather.parseDateTime("2010-07-19 15:03:04");

        Calendar expected = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        expected.set(2010, 7, 19, 15, 3, 4);

        final SimpleDateFormat FORMATTER = new SimpleDateFormat("yyyy MMM dd hh:mm zz");
        assertEquals(
            FORMATTER.format(expected.getTime()),
            FORMATTER.format(verifyMe.getTime()));
    }

    /**
     * Validate {@link Weather#getCentigrades(boolean)}.
     *
     * @throws Exception when testing goes exceptionally bad
     */
    @Test
    public void testGetCentigrades() throws Exception {
        mockStatic(Log.class);

        JSONObject innerWeather = new JSONObject();

        JSONObject outerWeather = new JSONObject();
        outerWeather.put("weatherObservation", innerWeather);

        innerWeather.put("temperature", "5");
        assertEquals(5, new Weather(outerWeather).getCentigrades(false));

        innerWeather.put("temperature", "-5");
        assertEquals(-5, new Weather(outerWeather).getCentigrades(false));
    }

    /**
     * Validate {@link Weather#getFarenheit(boolean)}.
     *
     * @throws Exception when testing goes exceptionally bad
     */
    @Test
    public void testGetFarenheit() throws Exception {
        mockStatic(Log.class);

        JSONObject innerWeather = new JSONObject();

        JSONObject outerWeather = new JSONObject();
        outerWeather.put("weatherObservation", innerWeather);

        innerWeather.put("temperature", "5");
        assertEquals(41, new Weather(outerWeather).getFarenheit(false));

        innerWeather.put("temperature", "-5");
        assertEquals(23, new Weather(outerWeather).getFarenheit(false));
    }

    /**
     * What happens if no temperature is reported?
     *
     * @throws Exception when testing goes exceptionally bad
     */
    @SuppressWarnings("unused")
    @Test
    public void testNoTemperature() throws Exception {
        mockStatic(Log.class);

        JSONObject innerWeather = new JSONObject();

        JSONObject outerWeather = new JSONObject();
        outerWeather.put("weatherObservation", innerWeather);

        try {
            new Weather(outerWeather);
            fail("Expected exception on no temperature");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("No temperature"));
        }

        innerWeather.put("temperature", "");
        try {
            new Weather(outerWeather);
            fail("Expected exception on empty temperature");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(),
                e.getMessage().startsWith("Borken temperature received"));
        }

        innerWeather.put("temperature", "flaska");
        try {
            new Weather(outerWeather);
            fail("Expected exception on borken temperature");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(),
                e.getMessage().startsWith("Borken temperature received"));
        }
    }

    /**
     * Validate {@link Weather#getStationName()}.
     *
     * @throws Exception when testing goes exceptionally bad
     */
    @Test
    public void testGetStationName() throws Exception {
        mockStatic(Log.class);

        JSONObject innerWeather = new JSONObject();
        innerWeather.put("temperature", 0);

        JSONObject outerWeather = new JSONObject();
        outerWeather.put("weatherObservation", innerWeather);

        innerWeather.put("stationName", "gris");
        assertEquals("gris", new Weather(outerWeather).getStationName());
    }
}
