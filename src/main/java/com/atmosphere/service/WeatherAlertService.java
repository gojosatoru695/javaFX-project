package com.atmosphere.service;

import com.atmosphere.model.ForecastSlot;
import com.atmosphere.model.WeatherAlert;
import com.atmosphere.model.WeatherAlert.Condition;
import com.atmosphere.model.WeatherAlert.Severity;
import com.atmosphere.model.WeatherData;
import com.atmosphere.util.TemperatureStyler;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Creates at most one alert for each city and condition from current readings
 * and upcoming forecast slots.
 */
public class WeatherAlertService {

    private static final DateTimeFormatter FORECAST_TIME_FORMAT =
            DateTimeFormatter.ofPattern(
                    "EEE, MMM d 'at' h:mm a",
                    Locale.ENGLISH
            );

    private final Thresholds thresholds;

    public WeatherAlertService() {
        this(Thresholds.defaults());
    }

    public WeatherAlertService(Thresholds thresholds) {
        if (thresholds == null) {
            throw new IllegalArgumentException(
                    "Alert thresholds must not be null."
            );
        }

        this.thresholds = thresholds;
    }

    /**
     * Current data may be null when only the forecast endpoint succeeded.
     * Forecast slots are examined from earliest to latest, so duplicate
     * conditions resolve to the first upcoming slot that crosses a threshold.
     */
    public List<WeatherAlert> analyze(
            String requestedCity,
            WeatherData current,
            List<ForecastSlot> forecastSlots
    ) {
        if (requestedCity == null || requestedCity.isBlank()) {
            throw new IllegalArgumentException(
                    "A city name is required to analyze weather alerts."
            );
        }
        if (forecastSlots == null) {
            throw new IllegalArgumentException(
                    "Forecast slots must not be null."
            );
        }

        String city = current == null
                || current.getCityName() == null
                || current.getCityName().isBlank()
                ? requestedCity.trim()
                : current.getCityName().trim();

        Map<Condition, WeatherAlert> alerts =
                new EnumMap<>(Condition.class);

        if (current != null) {
            addCurrentAlerts(city, current, alerts);
        }

        List<ForecastSlot> orderedSlots = forecastSlots.stream()
                .filter(slot -> slot != null && slot.getDateTime() != null)
                .sorted(Comparator.comparing(ForecastSlot::getDateTime))
                .toList();

        for (ForecastSlot slot : orderedSlots) {
            addForecastAlerts(city, slot, alerts);
        }

        return new ArrayList<>(alerts.values());
    }

    private void addCurrentAlerts(
            String city,
            WeatherData current,
            Map<Condition, WeatherAlert> alerts
    ) {
        int aqi = current.getAqi();
        if (aqi >= thresholds.poorAqi()) {
            addIfAbsent(
                    alerts,
                    alert(
                            city,
                            Condition.AIR_QUALITY,
                            "Poor air quality",
                            aqi >= 5 ? Severity.CRITICAL : Severity.WARNING,
                            "AQI " + aqi + " / 5",
                            "The current air-quality index is " + aqi
                                    + " on the 1–5 scale; the configured "
                                    + "alert threshold is "
                                    + thresholds.poorAqi() + ".",
                            "Limit strenuous outdoor activity. Sensitive "
                                    + "people should reduce prolonged "
                                    + "exposure.",
                            "Current reading"
                    )
            );
        }

        double temperature = current.getTemperature();
        if (Double.isFinite(temperature)) {
            addTemperatureAlerts(
                    city,
                    temperature,
                    "Current reading",
                    alerts
            );
        }

        double wind = current.getWindSpeed();
        if (Double.isFinite(wind) && wind >= thresholds.strongWindKmh()) {
            addIfAbsent(
                    alerts,
                    Condition.WIND,
                    alert(
                            city,
                            Condition.WIND,
                            "Strong winds",
                            windSeverity(wind),
                            format(wind) + " km/h",
                            "Current wind speed is " + format(wind)
                                    + " km/h; the configured threshold is "
                                    + format(thresholds.strongWindKmh())
                                    + " km/h.",
                            "Avoid exposed routes and secure loose items "
                                    + "outdoors.",
                            "Current reading"
                    )
            );
        }
    }

    private void addForecastAlerts(
            String city,
            ForecastSlot slot,
            Map<Condition, WeatherAlert> alerts
    ) {
        String timeframe = "Forecast · "
                + slot.getDateTime().format(FORECAST_TIME_FORMAT);

        int rainProbability = slot.getPrecipitationProbability();
        if (rainProbability >= thresholds.rainProbabilityPercent()) {
            addIfAbsent(
                    alerts,
                    Condition.RAIN,
                    alert(
                            city,
                            Condition.RAIN,
                            "High chance of rain",
                            rainProbability >= 90
                                    ? Severity.CRITICAL
                                    : Severity.WARNING,
                            rainProbability + "% precipitation probability",
                            "The forecast precipitation probability is "
                                    + rainProbability + "%; the configured "
                                    + "threshold is "
                                    + thresholds.rainProbabilityPercent()
                                    + "%.",
                            "Carry rain protection and consider an indoor "
                                    + "alternative.",
                            timeframe
                    )
            );
        }

        double wind = slot.getWindSpeedKmh();
        if (Double.isFinite(wind)
                && wind >= thresholds.strongWindKmh()) {
            addIfAbsent(
                    alerts,
                    Condition.WIND,
                    alert(
                            city,
                            Condition.WIND,
                            "Strong winds",
                            windSeverity(wind),
                            format(wind) + " km/h",
                            "Forecast wind speed is " + format(wind)
                                    + " km/h; the configured threshold is "
                                    + format(thresholds.strongWindKmh())
                                    + " km/h.",
                            "Avoid exposed routes and secure loose items "
                                    + "outdoors.",
                            timeframe
                    )
            );
        }

        double temperature = slot.getTemperatureCelsius();
        if (Double.isFinite(temperature)) {
            addTemperatureAlerts(city, temperature, timeframe, alerts);
        }
    }

    private void addTemperatureAlerts(
            String city,
            double temperature,
            String timeframe,
            Map<Condition, WeatherAlert> alerts
    ) {
        String displayTemperature =
                TemperatureStyler.formatOneDecimal(temperature);

        if (temperature >= thresholds.highTemperatureCelsius()) {
            addIfAbsent(
                    alerts,
                    Condition.HIGH_TEMPERATURE,
                    alert(
                            city,
                            Condition.HIGH_TEMPERATURE,
                            "High temperature",
                            temperature >= thresholds.highTemperatureCelsius()
                                    + 5
                                    ? Severity.CRITICAL
                                    : Severity.WARNING,
                            displayTemperature,
                            "Temperature is " + displayTemperature
                                    + "; the configured high-temperature "
                                    + "threshold is "
                                    + TemperatureStyler.formatOneDecimal(
                                            thresholds.highTemperatureCelsius()
                                    ) + ".",
                            "Drink water, seek shade, and avoid strenuous "
                                    + "activity during the hottest period.",
                            timeframe
                    )
            );
        }

        if (temperature <= thresholds.lowTemperatureCelsius()) {
            addIfAbsent(
                    alerts,
                    Condition.LOW_TEMPERATURE,
                    alert(
                            city,
                            Condition.LOW_TEMPERATURE,
                            "Low temperature",
                            temperature <= thresholds.lowTemperatureCelsius()
                                    - 5
                                    ? Severity.CRITICAL
                                    : Severity.WARNING,
                            displayTemperature,
                            "Temperature is " + displayTemperature
                                    + "; the configured low-temperature "
                                    + "threshold is "
                                    + TemperatureStyler.formatOneDecimal(
                                            thresholds.lowTemperatureCelsius()
                                    ) + ".",
                            "Dress in warm layers and limit prolonged "
                                    + "exposure to the cold.",
                            timeframe
                    )
            );
        }
    }

    private Severity windSeverity(double windSpeedKmh) {
        return windSpeedKmh >= thresholds.strongWindKmh() * 1.5
                ? Severity.CRITICAL
                : Severity.WARNING;
    }

    private static WeatherAlert alert(
            String city,
            Condition condition,
            String title,
            Severity severity,
            String actualValue,
            String explanation,
            String precaution,
            String timeframe
    ) {
        return new WeatherAlert(
                city,
                condition,
                title,
                severity,
                actualValue,
                explanation,
                precaution,
                timeframe
        );
    }

    private static void addIfAbsent(
            Map<Condition, WeatherAlert> alerts,
            WeatherAlert alert
    ) {
        alerts.putIfAbsent(alert.condition(), alert);
    }

    private static void addIfAbsent(
            Map<Condition, WeatherAlert> alerts,
            Condition condition,
            WeatherAlert alert
    ) {
        alerts.putIfAbsent(condition, alert);
    }

    private static String format(double value) {
        return String.format(Locale.ENGLISH, "%.1f", value);
    }

    public record Thresholds(
            int poorAqi,
            int rainProbabilityPercent,
            double strongWindKmh,
            double highTemperatureCelsius,
            double lowTemperatureCelsius
    ) {

        public Thresholds {
            if (poorAqi < 1 || poorAqi > 5) {
                throw new IllegalArgumentException(
                        "Poor-air AQI threshold must be between 1 and 5."
                );
            }
            if (rainProbabilityPercent < 0
                    || rainProbabilityPercent > 100) {
                throw new IllegalArgumentException(
                        "Rain threshold must be between 0 and 100 percent."
                );
            }
            if (!Double.isFinite(strongWindKmh) || strongWindKmh < 0
                    || !Double.isFinite(highTemperatureCelsius)
                    || !Double.isFinite(lowTemperatureCelsius)
                    || lowTemperatureCelsius >= highTemperatureCelsius) {
                throw new IllegalArgumentException(
                        "Wind and temperature thresholds are invalid."
                );
            }
        }

        public static Thresholds defaults() {
            return new Thresholds(4, 70, 40, 35, 5);
        }
    }
}
