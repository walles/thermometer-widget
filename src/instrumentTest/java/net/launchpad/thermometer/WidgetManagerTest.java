package net.launchpad.thermometer;

import android.content.Intent;
import junit.framework.TestCase;

/**
 * Validate {@link WidgetManager}.
 *
 * @author johan.walles@gmail.com
 */
public class WidgetManagerTest extends TestCase {
    private static class TestableWidgetManager extends WidgetManager {
        @Override
        public void updateMeasurement(UpdateReason reason) {
            // This method intentionally left blank
        }
    }

    /**
     * Verify that {@link WidgetManager#scheduleTemperatureUpdate(Intent)}
     * can handle a null intent.
     *
     * @throws Exception if testing goes exceptionally bad
     */
    public void testScheduleTemperatureUpdate() throws Exception {
        WidgetManager widgetManager = new TestableWidgetManager();

        // This shouldn't crash with an NPE
        widgetManager.scheduleTemperatureUpdate(null);
    }
}
