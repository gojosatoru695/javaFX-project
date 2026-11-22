package com.atmosphere.model;

import java.util.Locale;
import java.util.Objects;

/** A deterministic weather alert for a city and one weather condition. */
public record WeatherAlert(
        String cityName,
        Condition condition,
        String title,
        Severity severity,
        String actualValue,
        String explanation,
        String precaution,
        String timeframe
) {

    public WeatherAlert {
        Objects.requireNonNull(cityName, "cityName");
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(actualValue, "actualValue");
        Objects.requireNonNull(explanation, "explanation");
        Objects.requireNonNull(precaution, "precaution");
        Objects.requireNonNull(timeframe, "timeframe");
    }

    /** Stable key so read state survives refreshed values for the same alert. */
    public String id() {
        return cityName.trim().toLowerCase(Locale.ROOT)
                + "|" + condition.name();
    }

    public enum Condition {
        AIR_QUALITY,
        RAIN,
        WIND,
        HIGH_TEMPERATURE,
        LOW_TEMPERATURE
    }

    public enum Severity {
        CRITICAL("Critical"),
        WARNING("Warning"),
        INFORMATION("Information");

        private final String label;

        Severity(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
