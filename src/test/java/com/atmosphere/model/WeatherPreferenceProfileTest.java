package com.atmosphere.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeatherPreferenceProfileTest {

    @Test
    @DisplayName("Default profile provides sensible default values")
    void testDefaults() {
        WeatherPreferenceProfile profile = WeatherPreferenceProfile.defaultProfile();

        assertEquals(18.0, profile.getMinTemperatureCelsius());
        assertEquals(26.0, profile.getMaxTemperatureCelsius());
        assertEquals(35.0, profile.getMinHumidityPercent());
        assertEquals(65.0, profile.getMaxHumidityPercent());
        assertEquals(20.0, profile.getMaxComfortableWindKmh());
        assertEquals(WeatherPreferenceProfile.AirQualitySensitivity.MODERATE, profile.getAirQualitySensitivity());
    }

    @Test
    @DisplayName("Temperature inverted range auto-corrects safely")
    void testTemperatureInvertedRange() {
        WeatherPreferenceProfile profile = new WeatherPreferenceProfile();
        profile.setTemperatureRange(30.0, 15.0);

        assertEquals(15.0, profile.getMinTemperatureCelsius());
        assertEquals(30.0, profile.getMaxTemperatureCelsius());
    }

    @Test
    @DisplayName("Humidity clamp prevents values outside 0..100")
    void testHumidityClamping() {
        WeatherPreferenceProfile profile = new WeatherPreferenceProfile();
        profile.setHumidityRange(-10.0, 120.0);

        assertEquals(0.0, profile.getMinHumidityPercent());
        assertEquals(100.0, profile.getMaxHumidityPercent());
    }

    @Test
    @DisplayName("Copy produces identical independent instance")
    void testCopy() {
        WeatherPreferenceProfile original = new WeatherPreferenceProfile(
                20.0, 28.0, 40.0, 70.0, 15.0,
                WeatherPreferenceProfile.AirQualitySensitivity.HIGH
        );

        WeatherPreferenceProfile copy = original.copy();

        assertEquals(original, copy);
        copy.setMinTemperatureCelsius(22.0);
        assertNotEquals(original.getMinTemperatureCelsius(), copy.getMinTemperatureCelsius());
    }
}
