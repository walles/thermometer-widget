package net.launchpad.thermometer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
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
    private boolean forceShowExcuse = false;

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

    public void setForceShowExcuse(boolean forceShowExcuse) {
        this.forceShowExcuse = forceShowExcuse;
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
    public RemoteViews createRemoteViews(Context context, int color) {
        // FIXME: Refactor this method to be readable
        RemoteViews remoteViews =
                new RemoteViews(ThermometerWidget.class.getPackage().getName(),
                        R.layout.main);

        final int screenWidth = context.getResources().getDisplayMetrics().widthPixels;

        // The WIDTH needs to be at least as wide as the widget, but I don't know how to get the widget width
        // (2014feb13). The current formula is based on the fact that you usually get 4x4 widgets on your home screen,
        // so a quarter of the screen size should be enough.
        final int WIDTH = screenWidth / 4;
        //noinspection SuspiciousNameCombination,UnnecessaryLocalVariable
        final int HEIGHT = WIDTH;
        final float SUBTEXT_LINE_HEIGHT = HEIGHT / 6f;
        StaticLayout subtextLayout = getSubtextLayout(WIDTH, SUBTEXT_LINE_HEIGHT, color);
        final int TEMPERATURE_HEIGHT = computeMaxTemperatureHeight(HEIGHT, subtextLayout.getHeight());
        Bitmap bitmap =
                Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.TRANSPARENT);

        // Draw the temperature
        float textSize = TEMPERATURE_HEIGHT;
        Paint temperaturePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        temperaturePaint.setTextSize(textSize);
        temperaturePaint.setTypeface(Typeface.DEFAULT_BOLD);
        Rect bounds = new Rect();
        temperaturePaint.getTextBounds(getTemperatureString(), 0, getTemperatureString().length(), bounds);
        float wFactor = WIDTH / (float)bounds.width();
        float hFactor = TEMPERATURE_HEIGHT / (float)bounds.height();
        float factor = Math.min(wFactor, hFactor);
        textSize *= factor;
        temperaturePaint.setTextSize(textSize);
        Log.d(TAG, String.format("HEIGHT=%d, TEMPERATURE_HEIGHT=%d, bounds=%dx%d, wFactor=%f, hFactor=%f, factor=%f, textSize=%f",
                HEIGHT, TEMPERATURE_HEIGHT, bounds.width(), bounds.height(), wFactor, hFactor, factor, textSize));

        temperaturePaint.setColor(color);
        temperaturePaint.setTextAlign(Paint.Align.CENTER);

        float xPos = canvas.getWidth() / 2f;
        // This yPos calculation top-aligns the temperature string
        float yPos = textSize - temperaturePaint.descent();
        canvas.drawText(getTemperatureString(), xPos, yPos, temperaturePaint);

        temperaturePaint.getTextBounds(getTemperatureString(), 0, getTemperatureString().length(), bounds);
        int temperatureBottom = bounds.height();

        // Draw the subtext
        float lineHeight = subtextLayout.getHeight() / (float)subtextLayout.getLineCount();
        float availablePixels = HEIGHT - temperatureBottom;
        int maxFullLines = (int)(availablePixels / lineHeight);
        int subtextStart = HEIGHT - (int)(maxFullLines * lineHeight);
        TextPaint subtextPaint = subtextLayout.getPaint();
        assert subtextPaint != null;
        float subtextFontSize = subtextPaint.getTextSize();
        subtextStart += lineHeight - subtextFontSize;
        canvas.translate(0, subtextStart);
        subtextLayout.draw(canvas);

        remoteViews.setImageViewBitmap(R.id.Bitmap, bitmap);

        Log.d(TAG, String.format("Display layout is %d-%d, %d-%d, subtext lines are %fpx, font is %fpx",
                0, temperatureBottom,
                subtextStart, HEIGHT - 1,
                lineHeight, subtextFontSize));
        Log.d(TAG, "Displaying temperature: <" + getTemperatureString() + ">");
        int subtextLinesShown = Math.min(maxFullLines, subtextLayout.getLineCount());
        Log.d(TAG, String.format("Displaying %d/%d lines of subtext: <%s>",
                subtextLinesShown, subtextLayout.getLineCount(),
                getSubtextString()));

        return remoteViews;
    }

    private int computeMaxTemperatureHeight(int canvasHeight, int subtextHeight) {
        // FIXME: Get rid of this 3 thing; it's needed not to get too few lines of subtext, but understanding the problem and making it go away would be better.
        final int GOODLUCK = 3;
        int returnMe;
        if (getSubtextString().isEmpty()) {
            // No subtext, make the temperature number as big as possible
            returnMe = canvasHeight;
        } else if (forceShowExcuse || weather == null) {
            // No weather or subtext is important for some other reason, give the subtext more room
            returnMe = canvasHeight / 3 - GOODLUCK;
        } else {
            // This is the default case
            returnMe = canvasHeight / 2 - GOODLUCK;
        }

        if (returnMe < (canvasHeight - subtextHeight - GOODLUCK)) {
            returnMe = (canvasHeight - subtextHeight - GOODLUCK);
        }
        return returnMe;
    }

    @NotNull
    private StaticLayout getSubtextLayout(int width, float lineHeight, int color) {
        TextPaint subtextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        subtextPaint.setTextSize(lineHeight);
        subtextPaint.setTextAlign(Paint.Align.LEFT);
        subtextPaint.setTypeface(Typeface.SERIF);
        subtextPaint.setColor(color);
        StaticLayout subtextLayout =
                new StaticLayout(getSubtextString(), subtextPaint, width, Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
        float measuredLineHeight = subtextLayout.getHeight() / (float)subtextLayout.getLineCount();
        float subtextFactor = lineHeight / measuredLineHeight;
        lineHeight *= subtextFactor;
        subtextPaint.setTextSize(lineHeight);
        subtextLayout =
                new StaticLayout(getSubtextString(), subtextPaint, width, Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
        return subtextLayout;
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

        if (forceShowExcuse) {
            subtextString = excuse;
        }
    }
}
