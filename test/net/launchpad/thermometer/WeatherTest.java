package net.launchpad.thermometer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

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

/**
 * Validate {@link Weather}.
 *
 * @author johan.walles@gmail.com
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(Log.class)
public class WeatherTest {
    @Test
    public void testParseJsonWeather() throws Exception {
        mockStatic(Log.class);

        // From: http://api.openweathermap.org/data/2.5/weather?lat=35&lon=139
        JSONObject parseMe = new JSONObject("{\"coord\":{\"lon\":139,\"lat\":35},\"sys\":{\"country\":\"JP\",\"sunrise\":1381005770,\"sunset\":1381047672},\"weather\":[{\"id\":500,\"main\":\"Rain\",\"description\":\"light rain\",\"icon\":\"10n\"}],\"base\":\"gdps stations\",\"main\":{\"temp\":293.717,\"temp_min\":293.717,\"temp_max\":293.717,\"pressure\":1010.22,\"sea_level\":1035.21,\"grnd_level\":1010.22,\"humidity\":100},\"wind\":{\"speed\":3.04,\"deg\":49.0001},\"rain\":{\"3h\":2},\"clouds\":{\"all\":92},\"dt\":1381081014,\"id\":1848899,\"name\":\"Warabo\",\"cod\":200}");
        Weather verifyMe = new Weather(parseMe);
        assertEquals(21, verifyMe.getCentigrades(false));
        assertEquals(69, verifyMe.getFarenheit(false));
        assertEquals("Warabo", verifyMe.getStationName());

        Calendar expectedCal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        expectedCal.set(2013, Calendar.OCTOBER, 6, 17, 36, 54);
        final SimpleDateFormat FORMATTER = new SimpleDateFormat("yyyy MMM dd hh:mm zz");
        String expectedDateString = FORMATTER.format(expectedCal.getTime());
        String actualDateString = FORMATTER.format(verifyMe.getObservationTime().getTime());
        assertEquals(expectedDateString, actualDateString);

        assertEquals(5.90, verifyMe.getWindKnots(), 0.02);
    }

    @Test
    public void testParseJsonError() throws Exception {
        mockStatic(Log.class);

        // From: http://api.openweathermap.org/data/2.5/weather?hej=78
        JSONObject parseMe = new JSONObject("{\"message\":\"Error: Not found city\",\"cod\":\"404\"}");
        try {
            new Weather(parseMe);
            fail("IllegalArgumentException Expected");
        } catch (IllegalArgumentException e) {
            assertEquals("Weather service error: Not found city", e.getMessage());
        }
    }

    /**
     * Validate {@link Weather#getCentigrades(boolean)}.
     *
     * @throws Exception when testing goes exceptionally bad
     */
    @Test
    public void testGetCentigrades() throws Exception {
        mockStatic(Log.class);

        assertEquals(5, createWeather("Foo", 5, 0).getCentigrades(false));
        assertEquals(-5, createWeather("Foo", -5, 0).getCentigrades(false));
    }

    /**
     * Validate {@link Weather#getFarenheit(boolean)}.
     *
     * @throws Exception when testing goes exceptionally bad
     */
    @Test
    public void testGetFarenheit() throws Exception {
        mockStatic(Log.class);

        assertEquals(41, createWeather("Foo", 5, 0).getFarenheit(false));
        assertEquals(23, createWeather("Foo", -5, 0).getFarenheit(false));
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

        try {
            createWeatherWithTemperatureString(null);
            fail("Expected exception on no temperature");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(),
                    e.getMessage().startsWith("No temperature"));
        }

        try {
            createWeatherWithTemperatureString("");
            fail("Expected exception on empty temperature");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(),
                e.getMessage().startsWith("Borken temperature <>"));
        }

        try {
            createWeatherWithTemperatureString("flaska");
            fail("Expected exception on borken temperature");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(),
                e.getMessage().startsWith("Borken temperature <flaska>"));
        }
    }

    private Weather createWeather(String name, int centigrades, int windMps) throws Exception {
        JSONObject main = new JSONObject();
        main.put("temp", centigrades + 273.15);

        JSONObject wind = new JSONObject();
        wind.put("speed", windMps);

        JSONObject weather = new JSONObject();
        weather.put("main", main);
        weather.put("wind", wind);
        weather.put("name", name);

        return new Weather(weather);
    }

    private Weather createWeatherWithTemperatureString(String temperatureString) throws Exception {
        JSONObject main = new JSONObject();
        if (temperatureString != null) {
            main.put("temp", temperatureString);
        }

        JSONObject wind = new JSONObject();
        wind.put("speed", 0);

        JSONObject weather = new JSONObject();
        weather.put("main", main);
        weather.put("wind", wind);
        weather.put("name", "Monkey");

        return new Weather(weather);
    }

    /**
     * Validate {@link Weather#getStationName()}.
     *
     * @throws Exception when testing goes exceptionally bad
     */
    @Test
    public void testGetStationName() throws Exception {
        mockStatic(Log.class);

        assertEquals("Gris flaska",
                createWeather("Gris flaska", 0, 0).getStationName());

        assertEquals("Gris Flaska",
                createWeather("GRIS FLASKA", 0, 0).getStationName());

        assertEquals("Gris Flaska",
                createWeather("gris flaska", 0, 0).getStationName());

        assertEquals("Coeur d'Alene Air Terminal",
                createWeather("Coeur d'Alene, Coeur d'Alene Air Terminal", 0, 0).getStationName());

        assertEquals("Angelholm",
                createWeather("ANGELHOLM (SWE-A", 0, 0).getStationName());
    }
}
