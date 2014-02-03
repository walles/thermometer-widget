package net.launchpad.thermometer;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

/**
 * Validate {@link Weather}.
 *
 * @author johan.walles@gmail.com
 */
@SuppressWarnings("ResultOfObjectAllocationIgnored")
public class WeatherTest extends TestCase {
    public void testParseJsonWeather() throws Exception {
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
        Calendar observationTime = verifyMe.getObservationTime();
        assertNotNull(observationTime);
        String actualDateString = FORMATTER.format(observationTime.getTime());
        assertEquals(expectedDateString, actualDateString);

        assertEquals(5.90, verifyMe.getWindKnots(), 0.02);
    }

    public void testParseJsonError() throws Exception {
        // From: http://api.openweathermap.org/data/2.5/weather?hej=78
        JSONObject parseMe = new JSONObject("{\"message\":\"Error: Not found city\",\"cod\":\"404\"}");
        try {
            new Weather(parseMe);
            fail("IllegalArgumentException Expected");
        } catch (IllegalArgumentException e) {
            assertEquals("No weather stations nearby", e.getMessage());
        }
    }

    /**
     * Validate {@link Weather#getCentigrades(boolean)}.
     *
     * @throws Exception when testing goes exceptionally bad
     */
    public void testGetCentigrades() throws Exception {
        assertEquals(5, createWeather("Foo", 5, 0).getCentigrades(false));
        assertEquals(-5, createWeather("Foo", -5, 0).getCentigrades(false));
    }

    /**
     * Validate {@link Weather#getFarenheit(boolean)}.
     *
     * @throws Exception when testing goes exceptionally bad
     */
    public void testGetFarenheit() throws Exception {
        assertEquals(41, createWeather("Foo", 5, 0).getFarenheit(false));
        assertEquals(23, createWeather("Foo", -5, 0).getFarenheit(false));
    }

    /**
     * What happens if no temperature is reported?
     *
     * @throws Exception when testing goes exceptionally bad
     */
    @SuppressWarnings("unused")
    public void testNoTemperature() throws Exception {
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

    @NotNull
    static Weather createWeather(String name, int centigrades, int windMps) throws Exception {
        return createWeather(name, centigrades, windMps, System.currentTimeMillis());
    }

    @NotNull
    static Weather createWeather(String name, int centigrades, int windMps, long timestamp) throws Exception {
        JSONObject main = new JSONObject();
        main.put("temp", centigrades + 273.15);

        JSONObject wind = new JSONObject();
        wind.put("speed", windMps);

        JSONObject weather = new JSONObject();
        weather.put("main", main);
        weather.put("wind", wind);
        weather.put("name", name);
        weather.put("dt", timestamp / 1000L);

        return new Weather(weather);
    }

    @NotNull
    private Weather createWeatherWithTemperatureString(@Nullable String temperatureString) throws Exception {
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
    public void testGetStationName() throws Exception {
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
