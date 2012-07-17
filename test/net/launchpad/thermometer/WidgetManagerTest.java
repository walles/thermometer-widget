package net.launchpad.thermometer;

import static org.powermock.api.support.membermodification.MemberMatcher.constructor;
import static org.powermock.api.support.membermodification.MemberModifier.suppress;
import net.launchpad.thermometer.WidgetManager.UpdateReason;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import android.app.Service;
import android.content.Intent;
import android.util.Log;

/**
 * Validate {@link WidgetManager}.
 *
 * @author johan.walles@gmail.com
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({WidgetManager.class, Log.class})
public class WidgetManagerTest {
    @Test
    public void testScheduleTemperatureUpdate() throws Exception {
        // Avoid running the Service constructor
        suppress(constructor(Service.class));

        // Avoid all logging
        PowerMockito.mockStatic(Log.class);

        WidgetManager widgetManager = PowerMockito.spy(new WidgetManager());

        // We don't actually want to start any update
        PowerMockito.doNothing().when(widgetManager, "updateMeasurement", Matchers.any(UpdateReason.class));

        // This shouldn't crash with an NPE
        widgetManager.scheduleTemperatureUpdate((Intent)null);
    }
}
