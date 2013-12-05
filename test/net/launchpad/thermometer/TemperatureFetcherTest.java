package net.launchpad.thermometer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TemperatureFetcherTest {

    @Test
    public void testMinutesToTimeOldString() {
        assertEquals("current", TemperatureFetcher.minutesToTimeOldString(0));
        assertEquals("current", TemperatureFetcher.minutesToTimeOldString(1));

        assertEquals("2 minutes old", TemperatureFetcher.minutesToTimeOldString(2));
        assertEquals("119 minutes old", TemperatureFetcher.minutesToTimeOldString(60 * 2 - 1));

        assertEquals("2 hours old", TemperatureFetcher.minutesToTimeOldString(60 * 2));
        assertEquals("47 hours old", TemperatureFetcher.minutesToTimeOldString(60 * 24 * 2 - 1));

        assertEquals("2 days old", TemperatureFetcher.minutesToTimeOldString(60 * 24 * 2));
        assertEquals("13 days old", TemperatureFetcher.minutesToTimeOldString(60 * 24 * 7 * 2 - 1));

        assertEquals("2 weeks old", TemperatureFetcher.minutesToTimeOldString(60 * 24 * 7 * 2));
        assertEquals("8 weeks old", TemperatureFetcher.minutesToTimeOldString(60 * 24 * 7 * 8));

        assertEquals("2 months old", TemperatureFetcher.minutesToTimeOldString((int)(60 * 24 * 2 * (365.25 / 12))));
        assertEquals("23 months old", TemperatureFetcher.minutesToTimeOldString((int)(60 * 24 * 365.25 * 2 - 1)));

        assertEquals("2 years old", TemperatureFetcher.minutesToTimeOldString((int)(60 * 24 * 365.25 * 2)));
    }

    @Test
    public void testCensorAppid() {
        assertEquals("whateverAPPID=XXXXXX",
                TemperatureFetcher.censorAppid("whateverAPPID=139qe9ghguoh82824908429r2"));
        assertEquals("whatever", TemperatureFetcher.censorAppid("whatever"));
    }
}
