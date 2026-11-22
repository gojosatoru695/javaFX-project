
package com.atmosphere.config;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
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

    private static String openWeatherApiKey = "";
    private static String geminiApiKey = "";

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

        loaded = true;
    }

    /**
     * Saves settings through a temporary file and replaces only the
     * AtmoSphere-specific configuration.
     */
    public static synchronized void save(
            String weatherKey,
            String aiKey,
            boolean fahrenheit
    ) throws IOException {

        ensureLoaded();

        Files.createDirectories(CONFIG_DIRECTORY);

        Properties updated = new Properties();

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
}


