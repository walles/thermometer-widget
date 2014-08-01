package net.launchpad.thermometer;

import junit.framework.TestCase;

public class TemperatureFetcherTest extends TestCase {
    public void testCensorAppid() {
        assertEquals("whateverAPPID=XXXXXX",
                TemperatureFetcher.censorAppid("whateverAPPID=139qe9ghguoh82824908429r2"));
        assertEquals("whatever", TemperatureFetcher.censorAppid("whatever"));
    }
}
