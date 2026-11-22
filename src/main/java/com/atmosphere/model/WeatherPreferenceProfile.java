package com.atmosphere.model;

import java.util.Objects;

/**
 * User's personal weather comfort preferences (Personal Weather DNA).
 */
public class WeatherPreferenceProfile {

    public enum AirQualitySensitivity {
        LOW("Low sensitivity", 0.75),
        MODERATE("Moderate sensitivity", 1.0),
        HIGH("High sensitivity", 1.35);

        private final String label;
        private final double weightMultiplier;

        AirQualitySensitivity(String label, double weightMultiplier) {
            this.label = label;
            this.weightMultiplier = weightMultiplier;
        }

        public String label() {
            return label;
        }

        public double weightMultiplier() {
            return weightMultiplier;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static final double DEFAULT_MIN_TEMP = 18.0;
    public static final double DEFAULT_MAX_TEMP = 26.0;
    public static final double DEFAULT_MIN_HUMIDITY = 35.0;
    public static final double DEFAULT_MAX_HUMIDITY = 65.0;
    public static final double DEFAULT_MAX_WIND = 20.0;
    public static final AirQualitySensitivity DEFAULT_AQI_SENSITIVITY = AirQualitySensitivity.MODERATE;

    private double minTemperatureCelsius;
    private double maxTemperatureCelsius;
    private double minHumidityPercent;
    private double maxHumidityPercent;
    private double maxComfortableWindKmh;
    private AirQualitySensitivity airQualitySensitivity;

    public WeatherPreferenceProfile() {
        this.minTemperatureCelsius = DEFAULT_MIN_TEMP;
        this.maxTemperatureCelsius = DEFAULT_MAX_TEMP;
        this.minHumidityPercent = DEFAULT_MIN_HUMIDITY;
        this.maxHumidityPercent = DEFAULT_MAX_HUMIDITY;
        this.maxComfortableWindKmh = DEFAULT_MAX_WIND;
        this.airQualitySensitivity = DEFAULT_AQI_SENSITIVITY;
    }

    public WeatherPreferenceProfile(
            double minTemperatureCelsius,
            double maxTemperatureCelsius,
            double minHumidityPercent,
            double maxHumidityPercent,
            double maxComfortableWindKmh,
            AirQualitySensitivity airQualitySensitivity
    ) {
        setTemperatureRange(minTemperatureCelsius, maxTemperatureCelsius);
        setHumidityRange(minHumidityPercent, maxHumidityPercent);
        setMaxComfortableWindKmh(maxComfortableWindKmh);
        setAirQualitySensitivity(airQualitySensitivity);
    }

    public static WeatherPreferenceProfile defaultProfile() {
        return new WeatherPreferenceProfile();
    }

    public double getMinTemperatureCelsius() {
        return minTemperatureCelsius;
    }

    public double getMaxTemperatureCelsius() {
        return maxTemperatureCelsius;
    }

    public void setTemperatureRange(double min, double max) {
        if (min > max) {
            this.minTemperatureCelsius = max;
            this.maxTemperatureCelsius = min;
        } else {
            this.minTemperatureCelsius = min;
            this.maxTemperatureCelsius = max;
        }
    }

    public void setMinTemperatureCelsius(double min) {
        setTemperatureRange(min, Math.max(min, this.maxTemperatureCelsius));
    }

    public void setMaxTemperatureCelsius(double max) {
        setTemperatureRange(Math.min(max, this.minTemperatureCelsius), max);
    }

    public double getMinHumidityPercent() {
        return minHumidityPercent;
    }

    public double getMaxHumidityPercent() {
        return maxHumidityPercent;
    }

    public void setHumidityRange(double min, double max) {
        double clampedMin = Math.max(0.0, Math.min(100.0, min));
        double clampedMax = Math.max(0.0, Math.min(100.0, max));
        if (clampedMin > clampedMax) {
            this.minHumidityPercent = clampedMax;
            this.maxHumidityPercent = clampedMin;
        } else {
            this.minHumidityPercent = clampedMin;
            this.maxHumidityPercent = clampedMax;
        }
    }

    public void setMinHumidityPercent(double min) {
        setHumidityRange(min, Math.max(min, this.maxHumidityPercent));
    }

    public void setMaxHumidityPercent(double max) {
        setHumidityRange(Math.min(max, this.minHumidityPercent), max);
    }

    public double getMaxComfortableWindKmh() {
        return maxComfortableWindKmh;
    }

    public void setMaxComfortableWindKmh(double maxComfortableWindKmh) {
        this.maxComfortableWindKmh = Math.max(0.0, maxComfortableWindKmh);
    }

    public AirQualitySensitivity getAirQualitySensitivity() {
        return airQualitySensitivity == null ? DEFAULT_AQI_SENSITIVITY : airQualitySensitivity;
    }

    public void setAirQualitySensitivity(AirQualitySensitivity airQualitySensitivity) {
        this.airQualitySensitivity = airQualitySensitivity == null ? DEFAULT_AQI_SENSITIVITY : airQualitySensitivity;
    }

    public WeatherPreferenceProfile copy() {
        return new WeatherPreferenceProfile(
                this.minTemperatureCelsius,
                this.maxTemperatureCelsius,
                this.minHumidityPercent,
                this.maxHumidityPercent,
                this.maxComfortableWindKmh,
                this.airQualitySensitivity
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WeatherPreferenceProfile that)) return false;
        return Double.compare(that.minTemperatureCelsius, minTemperatureCelsius) == 0 &&
                Double.compare(that.maxTemperatureCelsius, maxTemperatureCelsius) == 0 &&
                Double.compare(that.minHumidityPercent, minHumidityPercent) == 0 &&
                Double.compare(that.maxHumidityPercent, maxHumidityPercent) == 0 &&
                Double.compare(that.maxComfortableWindKmh, maxComfortableWindKmh) == 0 &&
                airQualitySensitivity == that.airQualitySensitivity;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                minTemperatureCelsius,
                maxTemperatureCelsius,
                minHumidityPercent,
                maxHumidityPercent,
                maxComfortableWindKmh,
                airQualitySensitivity
        );
    }

    @Override
    public String toString() {
        return "WeatherPreferenceProfile{" +
                "temp=[" + minTemperatureCelsius + ".." + maxTemperatureCelsius + "°C]" +
                ", humidity=[" + minHumidityPercent + ".." + maxHumidityPercent + "%]" +
                ", maxWind=" + maxComfortableWindKmh + "km/h" +
                ", aqiSensitivity=" + airQualitySensitivity +
                '}';
    }
}
