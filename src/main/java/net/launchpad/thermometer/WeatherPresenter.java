package net.launchpad.thermometer;

import android.util.Log;
import android.widget.RemoteViews;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Calendar;

import static net.launchpad.thermometer.ThermometerWidget.TAG;

public class WeatherPresenter {
    /**
     * We don't want to show weather observations older than this.
     */
    private static final int MAX_WEATHER_AGE_MINUTES = 150;

    private @NotNull String temperatureString;
    private @NotNull String subtextString;

    private @Nullable final Weather weather;
    private @NotNull final String excuse;
    private boolean showMetadata = false;
    private boolean use24HoursFormat = true;
    private boolean useCelsius = true;
    private boolean withWindChill = false;

    /**
     * @param weather The weather to present
     * @param excuse An excuse to use if we can't present any weather
     */
    public WeatherPresenter(@Nullable Weather weather, @NotNull String excuse) {
        this.weather = weather;
        this.excuse = excuse;

        updateStrings();
    }

    @NotNull
    public String getTemperatureString() {
        return temperatureString;
    }

    @NotNull
    public String getSubtextString() {
        return subtextString;
    }

    public void setShowMetadata(boolean showMetadata) {
        this.showMetadata = showMetadata;
        updateStrings();
    }

    public void setUse24HoursFormat(boolean use24HoursFormat) {
        this.use24HoursFormat = use24HoursFormat;
        updateStrings();
    }

    public void setUseCelsius(boolean useCelsius) {
        this.useCelsius = useCelsius;
        updateStrings();
    }

    public void setWithWindChill(boolean withWindChill) {
        this.withWindChill = withWindChill;
        updateStrings();
    }

    /**
     * Create a RemoteViews instance with the temperature string and the subtext string.
     *
     * @param color The text color to use for the RemoteViews
     *
     * @return A rendering of the temperature string and the subtext string.
     */
    @NotNull
    public RemoteViews createRemoteViews(int color) {
        RemoteViews remoteViews =
                new RemoteViews(ThermometerWidget.class.getPackage().getName(),
                        R.layout.main);

        Log.d(TAG, "Displaying temperature: <" + getTemperatureString() + ">");
        remoteViews.setTextViewText(R.id.TemperatureView, getTemperatureString());
        remoteViews.setTextColor(R.id.TemperatureView, color);

        Log.d(TAG, "Displaying subtext: <" + getSubtextString() + ">");
        remoteViews.setTextViewText(R.id.MetadataView, getSubtextString());
        remoteViews.setTextColor(R.id.MetadataView, color);

        return remoteViews;
    }

    private void updateStrings() {
        boolean windChillComputed = false;

        String degrees = "--";
        if (weather != null) {
            Log.d(TAG, "Weather data is " + weather);

            if (showMetadata) {
                Calendar observationTime = weather.getObservationTime();
                if (observationTime != null) {
                    subtextString =
                            Util.toHoursString(observationTime, use24HoursFormat).toString();
                } else {
                    subtextString = "";
                }

                if (weather.getStationName() != null) {
                    if (!subtextString.isEmpty()) {
                        subtextString += " ";
                    }
                    subtextString += weather.getStationName();
                }
            } else {
                subtextString = "";
            }

            if (weather.getAgeMinutes() > MAX_WEATHER_AGE_MINUTES) {
                // Present excuses for our old data
                subtextString = excuse;
            }

            int unchilledDegrees;
            int chilledDegrees;
            if (useCelsius) {
                chilledDegrees = weather.getCentigrades(withWindChill);
                unchilledDegrees = weather.getCentigrades(false);
            } else {
                // Liberian users and some others
                chilledDegrees = weather.getFahrenheit(withWindChill);
                unchilledDegrees = weather.getFahrenheit(false);
            }
            degrees = Integer.toString(chilledDegrees);
            windChillComputed = (chilledDegrees != unchilledDegrees);
        } else {
            subtextString = excuse;
        }

        if (windChillComputed) {
            temperatureString = degrees + "*";
        } else {
            temperatureString = degrees + "°";
        }
    }
}
