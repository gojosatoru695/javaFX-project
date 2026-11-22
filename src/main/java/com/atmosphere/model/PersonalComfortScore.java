package com.atmosphere.model;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic personalized comfort score result and factor breakdown.
 */
public record PersonalComfortScore(
        int totalScore,
        Category category,
        int temperatureScore,
        int windScore,
        int humidityScore,
        int airQualityScore,
        String explanation,
        List<String> factorHighlights
) {

    public enum Category {
        OPTIMAL("Optimal", "badge-optimal", "#80D6C5"),
        FAVORABLE("Favorable", "badge-favorable", "#80D6C5"),
        MODERATE("Moderate", "badge-moderate", "#F2BB72"),
        SUBOPTIMAL("Suboptimal", "badge-suboptimal", "#F2BB72"),
        HARSH("Harsh", "badge-harsh", "#EF7D7D");

        private final String label;
        private final String badgeStyleClass;
        private final String hexColor;

        Category(String label, String badgeStyleClass, String hexColor) {
            this.label = label;
            this.badgeStyleClass = badgeStyleClass;
            this.hexColor = hexColor;
        }

        public String label() {
            return label;
        }

        public String badgeStyleClass() {
            return badgeStyleClass;
        }

        public String hexColor() {
            return hexColor;
        }

        public static Category fromScore(int score) {
            if (score >= 90) return OPTIMAL;
            if (score >= 75) return FAVORABLE;
            if (score >= 60) return MODERATE;
            if (score >= 45) return SUBOPTIMAL;
            return HARSH;
        }
    }

    public PersonalComfortScore {
        Objects.requireNonNull(category, "Category cannot be null");
        Objects.requireNonNull(explanation, "Explanation cannot be null");
        factorHighlights = factorHighlights == null ? List.of() : List.copyOf(factorHighlights);
    }

    /**
     * Weighted contribution of temperature to the total score (out of 40).
     */
    public double temperatureContribution() {
        return temperatureScore * 0.40;
    }

    /**
     * Weighted contribution of wind to the total score (out of 20).
     */
    public double windContribution() {
        return windScore * 0.20;
    }

    /**
     * Weighted contribution of humidity to the total score (out of 15).
     */
    public double humidityContribution() {
        return humidityScore * 0.15;
    }

    /**
     * Weighted contribution of air quality to the total score (out of 25).
     */
    public double airQualityContribution() {
        return airQualityScore * 0.25;
    }
}
