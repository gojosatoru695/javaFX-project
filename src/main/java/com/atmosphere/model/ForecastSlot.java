package com.atmosphere.model;

import java.time.LocalDateTime;

/**
 * One three-hour OpenWeatherMap forecast slot.
 *
 * Temperature is in Celsius, wind speed is in km/h, and precipitation
 * probability is a percentage. The date-time is local to the forecast city.
 */
public class ForecastSlot {

    private LocalDateTime dateTime;
    private double temperatureCelsius;
    private double windSpeedKmh;
    private String description;
    private int precipitationProbability;

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }

    public double getTemperatureCelsius() {
        return temperatureCelsius;
    }

    public void setTemperatureCelsius(double temperatureCelsius) {
        this.temperatureCelsius = temperatureCelsius;
    }

    public double getWindSpeedKmh() {
        return windSpeedKmh;
    }

    public void setWindSpeedKmh(double windSpeedKmh) {
        this.windSpeedKmh = windSpeedKmh;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getPrecipitationProbability() {
        return precipitationProbability;
    }

    public void setPrecipitationProbability(
            int precipitationProbability
    ) {
        this.precipitationProbability = precipitationProbability;
    }
}
