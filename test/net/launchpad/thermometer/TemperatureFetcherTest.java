package net.launchpad.thermometer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TemperatureFetcherTest {
    @Test
    public void testCensorAppid() {
        assertEquals("whateverAPPID=XXXXXX",
                TemperatureFetcher.censorAppid("whateverAPPID=139qe9ghguoh82824908429r2"));
        assertEquals("whatever", TemperatureFetcher.censorAppid("whatever"));
    }
}
