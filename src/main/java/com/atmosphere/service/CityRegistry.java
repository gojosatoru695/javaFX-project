
package com.atmosphere.service;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Collection;
import java.util.Locale;

/**
 * Shared observable city list.
 *
 * Newly searched cities should only be registered after a successful
 * OpenWeatherMap response.
 *
 * All mutations happen on the JavaFX Application Thread.
 */
public final class CityRegistry {

    private static final ObservableList<String> CITIES =
            FXCollections.observableArrayList(
                    "Bengaluru",
                    "Mumbai",
                    "Delhi"
            );

    private CityRegistry() {
    }

    public static ObservableList<String> getCities() {
        return CITIES;
    }

    public static void register(String city) {

        if (city == null || city.isBlank()) {
            return;
        }

        String normalized = normalize(city);

        boolean alreadyRegistered = CITIES.stream()
                .anyMatch(existing ->
                        normalize(existing).equals(normalized)
                );

        if (!alreadyRegistered) {
            CITIES.add(city.trim());
        }
    }

    public static boolean contains(String city) {

        if (city == null || city.isBlank()) {
            return false;
        }

        String normalized = normalize(city);

        return CITIES.stream()
                .anyMatch(existing ->
                        normalize(existing).equals(normalized)
                );
    }

    public static void unregister(String city) {

        if (city == null || city.isBlank()) {
            return;
        }

        String normalized = normalize(city);

        CITIES.removeIf(existing ->
                normalize(existing).equals(normalized)
        );
    }

    public static void registerAll(Collection<String> cities) {

        if (cities == null) {
            return;
        }

        cities.forEach(CityRegistry::register);
    }

    private static String normalize(String city) {
        return city.trim().toLowerCase(Locale.ROOT);
    }
}


