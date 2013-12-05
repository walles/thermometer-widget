package net.launchpad.thermometer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UtilTest {
    @Test
    public void testMinutesToTimeOldString() {
        assertEquals("current", Util.minutesToTimeOldString(0));
        assertEquals("current", Util.minutesToTimeOldString(1));

        assertEquals("2 minutes old", Util.minutesToTimeOldString(2));
        assertEquals("119 minutes old", Util.minutesToTimeOldString(60 * 2 - 1));

        assertEquals("2 hours old", Util.minutesToTimeOldString(60 * 2));
        assertEquals("47 hours old", Util.minutesToTimeOldString(60 * 24 * 2 - 1));

        assertEquals("2 days old", Util.minutesToTimeOldString(60 * 24 * 2));
        assertEquals("13 days old", Util.minutesToTimeOldString(60 * 24 * 7 * 2 - 1));

        assertEquals("2 weeks old", Util.minutesToTimeOldString(60 * 24 * 7 * 2));
        assertEquals("8 weeks old", Util.minutesToTimeOldString(60 * 24 * 7 * 8));

        assertEquals("2 months old", Util.minutesToTimeOldString((int) (60 * 24 * 2 * (365.25 / 12))));
        assertEquals("23 months old", Util.minutesToTimeOldString((int) (60 * 24 * 365.25 * 2 - 1)));

        assertEquals("2 years old", Util.minutesToTimeOldString((int) (60 * 24 * 365.25 * 2)));
    }
}
