package com.atmosphere.config;

import com.atmosphere.model.WeatherPreferenceProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    @Test
    @DisplayName("AppConfig loads and saves WeatherPreferenceProfile without destroying existing keys")
    void testPreferencePersistence() throws IOException {
        AppConfig.load();

        String originalWeatherKey = AppConfig.getOpenWeatherApiKey();
        String originalAiKey = AppConfig.getGeminiApiKey();
        boolean originalFahrenheit = AppConfig.isFahrenheit();

        WeatherPreferenceProfile customProfile = new WeatherPreferenceProfile(
                19.5,
                27.5,
                40.0,
                70.0,
                18.0,
                WeatherPreferenceProfile.AirQualitySensitivity.HIGH
        );

        AppConfig.savePreferences(customProfile);

        // Reload to verify disk round-trip
        AppConfig.load();

        WeatherPreferenceProfile reloadedProfile = AppConfig.getWeatherPreferenceProfile();
        assertEquals(19.5, reloadedProfile.getMinTemperatureCelsius(), 0.1);
        assertEquals(27.5, reloadedProfile.getMaxTemperatureCelsius(), 0.1);
        assertEquals(40.0, reloadedProfile.getMinHumidityPercent(), 0.1);
        assertEquals(70.0, reloadedProfile.getMaxHumidityPercent(), 0.1);
        assertEquals(18.0, reloadedProfile.getMaxComfortableWindKmh(), 0.1);
        assertEquals(WeatherPreferenceProfile.AirQualitySensitivity.HIGH, reloadedProfile.getAirQualitySensitivity());

        // Verify API keys were preserved
        assertEquals(originalWeatherKey, AppConfig.getOpenWeatherApiKey());
        assertEquals(originalAiKey, AppConfig.getGeminiApiKey());
        assertEquals(originalFahrenheit, AppConfig.isFahrenheit());
    }
}
