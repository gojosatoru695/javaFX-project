package com.atmosphere.model;

import java.time.LocalDateTime;
import java.util.Objects;

/** Persistable measurements captured from one successful current-weather read. */
public record WeatherSnapshot(
        String cityName,
        double temperatureCelsius,
        double humidityPercent,
        double windSpeedKmh,
        double pressureHpa,
        int aqi,
        LocalDateTime timestamp
) {

    public WeatherSnapshot {
        if (cityName == null || cityName.isBlank()) {
            throw new IllegalArgumentException(
                    "A city name is required for a weather snapshot."
            );
        }
        if (!Double.isFinite(temperatureCelsius)
                || !Double.isFinite(humidityPercent)
                || humidityPercent < 0
                || humidityPercent > 100
                || !Double.isFinite(windSpeedKmh)
                || windSpeedKmh < 0
                || !Double.isFinite(pressureHpa)
                || pressureHpa <= 0
                || aqi < 1
                || aqi > 5) {
            throw new IllegalArgumentException(
                    "Weather snapshot contains an invalid measurement."
            );
        }
        Objects.requireNonNull(timestamp, "timestamp");
        cityName = cityName.trim();
    }

    public static WeatherSnapshot from(
            String cityName,
            WeatherData weather
    ) {
        Objects.requireNonNull(weather, "weather");

        if (weather.getFetchedAt() == null) {
            throw new IllegalArgumentException(
                    "A successful weather reading must include its timestamp."
            );
        }

        return new WeatherSnapshot(
                cityName,
                weather.getTemperature(),
                weather.getHumidity(),
                weather.getWindSpeed(),
                weather.getPressure(),
                weather.getAqi(),
                weather.getFetchedAt()
        );
    }
}
