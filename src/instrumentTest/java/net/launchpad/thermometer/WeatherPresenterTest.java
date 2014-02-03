package net.launchpad.thermometer;

import junit.framework.TestCase;

import java.util.regex.Pattern;

public class WeatherPresenterTest extends TestCase {
    public void testSetShowMetadata() throws Exception {
        Weather weather = WeatherTest.createWeather("Hjo", 10, 0);
        WeatherPresenter testMe = new WeatherPresenter(weather, "status");

        testMe.setShowMetadata(true);
        assertEquals("10°", testMe.getTemperatureString());
        assertTrue(testMe.getSubtextString(),
                Pattern.matches("[0-2][0-9]:[0-5][0-9] Hjo", testMe.getSubtextString()));
    }

    public void testSetUse24HoursFormat() throws Exception {
        Weather weather = WeatherTest.createWeather("Hjo", 10, 0);
        WeatherPresenter testMe = new WeatherPresenter(weather, "status");
        testMe.setShowMetadata(true);

        testMe.setUse24HoursFormat(false);
        assertEquals("10°", testMe.getTemperatureString());
        assertTrue(testMe.getSubtextString(),
                Pattern.matches("[0-1]?[0-9]:[0-5][0-9][AP]M Hjo", testMe.getSubtextString()));
    }

    public void testSetUseCelsius() throws Exception {
        Weather weather = WeatherTest.createWeather("Hjo", 10, 0);
        WeatherPresenter testMe = new WeatherPresenter(weather, "status");

        testMe.setUseCelsius(false);
        // 10C = 50F
        assertEquals("50°", testMe.getTemperatureString());
        assertEquals("", testMe.getSubtextString());
    }

    public void testSetWithWindChill() throws Exception {
        Weather weather = WeatherTest.createWeather("Hjo", 10, 10);
        WeatherPresenter testMe = new WeatherPresenter(weather, "status");

        testMe.setWithWindChill(true);
        assertEquals("6*", testMe.getTemperatureString());
        assertEquals("", testMe.getSubtextString());
    }

    public void testPresentNullWeather() throws Exception {
        WeatherPresenter testMe = new WeatherPresenter(null, "status");
        assertEquals("--°", testMe.getTemperatureString());
        assertEquals("status", testMe.getSubtextString());
    }

    public void testPresentBasicWeather() throws Exception {
        Weather weather = WeatherTest.createWeather("Hjo", 10, 0);
        WeatherPresenter testMe = new WeatherPresenter(weather, "status");
        assertEquals("10°", testMe.getTemperatureString());
        assertEquals("", testMe.getSubtextString());
    }

    public void testPresentOldWeather() throws Exception {
        final long TWO_HUNDRED_MINUTES_IN_MS = 200L * 60L * 1000L;
        Weather weather =
                WeatherTest.createWeather("Hjo", 10, 0,
                        System.currentTimeMillis() - TWO_HUNDRED_MINUTES_IN_MS);
        WeatherPresenter testMe = new WeatherPresenter(weather, "status");
        assertEquals("10°", testMe.getTemperatureString());
        assertEquals("Excuse should be visible if weather is old",
                "status", testMe.getSubtextString());
    }
}
