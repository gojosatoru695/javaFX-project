package com.atmosphere.config;

import com.atmosphere.model.WeatherPreferenceProfile;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Properties;

/**
 * User-level AtmoSphere settings.
 *
 * Configuration is isolated in ~/.atmosphere so unrelated files in the
 * user's home directory are never overwritten.
 *
 * API values are masked in the UI. The properties file is NOT encrypted.
 */
public final class AppConfig {

    private static final Path CONFIG_DIRECTORY = Path.of(
            System.getProperty("user.home"),
            ".atmosphere"
    );

    private static final Path CONFIG_FILE =
            CONFIG_DIRECTORY.resolve("config.properties");

    private static final Properties PROPERTIES = new Properties();

    private static final BooleanProperty FAHRENHEIT =
            new SimpleBooleanProperty(false);

    private static final ObjectProperty<WeatherPreferenceProfile> PREFERENCE_PROFILE_PROPERTY =
            new SimpleObjectProperty<>(WeatherPreferenceProfile.defaultProfile());

    private static String openWeatherApiKey = "";
    private static String geminiApiKey = "";
    private static WeatherPreferenceProfile preferenceProfile =
            WeatherPreferenceProfile.defaultProfile();

    private static boolean loaded;

    private AppConfig() {
    }

    /**
     * Must run before FXML controllers are initialized or API requests start.
     */
    public static synchronized void load() throws IOException {

        PROPERTIES.clear();

        if (Files.exists(CONFIG_FILE)) {
            try (InputStream input = Files.newInputStream(CONFIG_FILE)) {
                PROPERTIES.load(input);
            }
        }

        openWeatherApiKey = PROPERTIES
                .getProperty("openweathermap.apiKey", "")
                .trim();

        geminiApiKey = PROPERTIES
                .getProperty("gemini.apiKey", "")
                .trim();

        FAHRENHEIT.set(
                "F".equalsIgnoreCase(
                        PROPERTIES.getProperty(
                                "temperature.unit",
                                "C"
                        ).trim()
                )
        );

        double minTemp = parseDouble(
                PROPERTIES.getProperty("weatherdna.temp.min"),
                WeatherPreferenceProfile.DEFAULT_MIN_TEMP
        );
        double maxTemp = parseDouble(
                PROPERTIES.getProperty("weatherdna.temp.max"),
                WeatherPreferenceProfile.DEFAULT_MAX_TEMP
        );
        double minHumidity = parseDouble(
                PROPERTIES.getProperty("weatherdna.humidity.min"),
                WeatherPreferenceProfile.DEFAULT_MIN_HUMIDITY
        );
        double maxHumidity = parseDouble(
                PROPERTIES.getProperty("weatherdna.humidity.max"),
                WeatherPreferenceProfile.DEFAULT_MAX_HUMIDITY
        );
        double maxWind = parseDouble(
                PROPERTIES.getProperty("weatherdna.wind.max"),
                WeatherPreferenceProfile.DEFAULT_MAX_WIND
        );

        String aqiSensStr = PROPERTIES.getProperty(
                "weatherdna.aqi.sensitivity",
                WeatherPreferenceProfile.DEFAULT_AQI_SENSITIVITY.name()
        );
        WeatherPreferenceProfile.AirQualitySensitivity aqiSensitivity =
                WeatherPreferenceProfile.DEFAULT_AQI_SENSITIVITY;
        try {
            if (aqiSensStr != null && !aqiSensStr.isBlank()) {
                aqiSensitivity = WeatherPreferenceProfile.AirQualitySensitivity.valueOf(
                        aqiSensStr.trim().toUpperCase(Locale.ROOT)
                );
            }
        } catch (IllegalArgumentException ignored) {
            // Keep default
        }

        preferenceProfile = new WeatherPreferenceProfile(
                minTemp,
                maxTemp,
                minHumidity,
                maxHumidity,
                maxWind,
                aqiSensitivity
        );

        PREFERENCE_PROFILE_PROPERTY.set(preferenceProfile.copy());

        loaded = true;
    }

    /**
     * Saves settings through a temporary file and replaces only the
     * AtmoSphere-specific configuration, preserving other properties.
     */
    public static synchronized void save(
            String weatherKey,
            String aiKey,
            boolean fahrenheit
    ) throws IOException {
        saveAll(weatherKey, aiKey, fahrenheit, preferenceProfile);
    }

    /**
     * Saves updated personal weather preferences, preserving API keys and temperature units.
     */
    public static synchronized void savePreferences(
            WeatherPreferenceProfile profile
    ) throws IOException {
        saveAll(openWeatherApiKey, geminiApiKey, FAHRENHEIT.get(), profile);
    }

    /**
     * Saves all application settings atomically.
     */
    public static synchronized void saveAll(
            String weatherKey,
            String aiKey,
            boolean fahrenheit,
            WeatherPreferenceProfile profile
    ) throws IOException {

        ensureLoaded();

        Files.createDirectories(CONFIG_DIRECTORY);

        Properties updated = new Properties();
        // Preserve all previously saved properties
        updated.putAll(PROPERTIES);

        updated.setProperty(
                "openweathermap.apiKey",
                safe(weatherKey)
        );

        updated.setProperty(
                "gemini.apiKey",
                safe(aiKey)
        );

        updated.setProperty(
                "temperature.unit",
                fahrenheit ? "F" : "C"
        );

        WeatherPreferenceProfile resolvedProfile =
                profile == null ? WeatherPreferenceProfile.defaultProfile() : profile;

        updated.setProperty(
                "weatherdna.temp.min",
                String.format(Locale.ENGLISH, "%.1f", resolvedProfile.getMinTemperatureCelsius())
        );
        updated.setProperty(
                "weatherdna.temp.max",
                String.format(Locale.ENGLISH, "%.1f", resolvedProfile.getMaxTemperatureCelsius())
        );
        updated.setProperty(
                "weatherdna.humidity.min",
                String.format(Locale.ENGLISH, "%.1f", resolvedProfile.getMinHumidityPercent())
        );
        updated.setProperty(
                "weatherdna.humidity.max",
                String.format(Locale.ENGLISH, "%.1f", resolvedProfile.getMaxHumidityPercent())
        );
        updated.setProperty(
                "weatherdna.wind.max",
                String.format(Locale.ENGLISH, "%.1f", resolvedProfile.getMaxComfortableWindKmh())
        );
        updated.setProperty(
                "weatherdna.aqi.sensitivity",
                resolvedProfile.getAirQualitySensitivity().name()
        );

        Path temporary = Files.createTempFile(
                CONFIG_DIRECTORY,
                "config-",
                ".tmp"
        );

        boolean moved = false;

        try {

            try (OutputStream output =
                         Files.newOutputStream(temporary)) {

                updated.store(
                        output,
                        "AtmoSphere user settings"
                );
            }

            restrictToCurrentUser(temporary);

            try {

                Files.move(
                        temporary,
                        CONFIG_FILE,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );

            } catch (AtomicMoveNotSupportedException exception) {

                Files.move(
                        temporary,
                        CONFIG_FILE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            moved = true;

            restrictToCurrentUser(CONFIG_FILE);

        } finally {

            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }

        PROPERTIES.clear();
        PROPERTIES.putAll(updated);

        openWeatherApiKey = safe(weatherKey);
        geminiApiKey = safe(aiKey);

        FAHRENHEIT.set(fahrenheit);
        preferenceProfile = resolvedProfile.copy();
        PREFERENCE_PROFILE_PROPERTY.set(preferenceProfile.copy());
    }

    /**
     * Restricts permissions to the current OS user where POSIX permissions
     * are supported. Windows uses the user's profile-directory ACL.
     */
    private static void restrictToCurrentUser(Path file) {

        try {

            Files.setPosixFilePermissions(
                    file,
                    EnumSet.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE
                    )
            );

        } catch (
                UnsupportedOperationException |
                IOException ignored
        ) {
            // Windows ACLs are managed by the OS.
        }
    }

    public static synchronized String getOpenWeatherApiKey() {
        ensureLoaded();
        return openWeatherApiKey;
    }

    public static synchronized String getGeminiApiKey() {
        ensureLoaded();
        return geminiApiKey;
    }

    public static synchronized boolean isFahrenheit() {
        ensureLoaded();
        return FAHRENHEIT.get();
    }

    public static BooleanProperty fahrenheitProperty() {
        return FAHRENHEIT;
    }

    public static synchronized WeatherPreferenceProfile getWeatherPreferenceProfile() {
        ensureLoaded();
        return preferenceProfile.copy();
    }

    public static ObjectProperty<WeatherPreferenceProfile> weatherPreferenceProfileProperty() {
        return PREFERENCE_PROFILE_PROPERTY;
    }

    public static Path getConfigFile() {
        return CONFIG_FILE;
    }

    private static void ensureLoaded() {

        if (!loaded) {

            try {
                load();

            } catch (IOException exception) {

                throw new IllegalStateException(
                        "Unable to load AtmoSphere settings from "
                                + CONFIG_FILE,
                        exception
                );
            }
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static double parseDouble(String value, double defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
