package com.atmosphere.service;

import com.atmosphere.model.ForecastSlot;

import java.util.Locale;

/**
 * Deterministic, explainable suitability scoring for outdoor activities.
 */
public final class ActivitySuitabilityScorer {

    private static final double COMFORTABLE_WIND_KMH = 5.0;

    private ActivitySuitabilityScorer() {
    }

    public static Score score(
            Activity activity,
            ForecastSlot slot,
            int currentAqi
    ) {
        if (activity == null || slot == null) {
            throw new IllegalArgumentException(
                    "Activity and forecast slot are required."
            );
        }

        if (currentAqi < 1 || currentAqi > 5) {
            throw new IllegalArgumentException(
                    "Current AQI must be between 1 and 5."
            );
        }

        if (!Double.isFinite(slot.getTemperatureCelsius())
                || !Double.isFinite(slot.getWindSpeedKmh())
                || slot.getWindSpeedKmh() < 0
                || slot.getPrecipitationProbability() < 0
                || slot.getPrecipitationProbability() > 100) {
            throw new IllegalArgumentException(
                    "Forecast slot contains invalid weather values."
            );
        }

        int temperatureScore = temperatureScore(activity, slot);
        int precipitationScore =
                100 - slot.getPrecipitationProbability();
        int windScore = windScore(activity, slot.getWindSpeedKmh());
        int airQualityScore = airQualityScore(currentAqi);

        int total = (int) Math.round(
                (temperatureScore * activity.temperatureWeight()
                        + precipitationScore
                        * activity.precipitationWeight()
                        + windScore * activity.windWeight()
                        + airQualityScore * activity.airQualityWeight())
                        / 100.0
        );

        return new Score(
                total,
                temperatureScore,
                precipitationScore,
                windScore,
                airQualityScore
        );
    }

    private static int temperatureScore(
            Activity activity,
            ForecastSlot slot
    ) {
        double temperature = slot.getTemperatureCelsius();

        if (temperature < activity.comfortableMinimumCelsius()) {
            return clampScore(
                    100 - (activity.comfortableMinimumCelsius()
                            - temperature) * 6
            );
        }

        if (temperature > activity.comfortableMaximumCelsius()) {
            return clampScore(
                    100 - (temperature
                            - activity.comfortableMaximumCelsius()) * 6
            );
        }

        return 100;
    }

    private static int windScore(
            Activity activity,
            double windSpeedKmh
    ) {
        return clampScore(
                100 - Math.max(
                        0,
                        windSpeedKmh - COMFORTABLE_WIND_KMH
                ) * activity.windPenaltyPerKmh()
        );
    }

    private static int airQualityScore(int aqi) {
        return switch (aqi) {
            case 1 -> 100;
            case 2 -> 80;
            case 3 -> 55;
            case 4 -> 25;
            case 5 -> 0;
            default -> throw new IllegalArgumentException(
                    "Current AQI must be between 1 and 5."
            );
        };
    }

    private static int clampScore(double score) {
        return (int) Math.round(Math.max(0, Math.min(100, score)));
    }

    public enum Activity {
        WALKING(
                "Walking",
                35,
                25,
                15,
                25,
                16,
                26,
                4.0
        ),
        JOGGING(
                "Jogging",
                25,
                15,
                25,
                35,
                12,
                22,
                5.0
        ),
        CYCLING(
                "Cycling",
                25,
                20,
                30,
                25,
                14,
                24,
                4.5
        );

        private final String label;
        private final int temperatureWeight;
        private final int precipitationWeight;
        private final int windWeight;
        private final int airQualityWeight;
        private final double comfortableMinimumCelsius;
        private final double comfortableMaximumCelsius;
        private final double windPenaltyPerKmh;

        Activity(
                String label,
                int temperatureWeight,
                int precipitationWeight,
                int windWeight,
                int airQualityWeight,
                double comfortableMinimumCelsius,
                double comfortableMaximumCelsius,
                double windPenaltyPerKmh
        ) {
            this.label = label;
            this.temperatureWeight = temperatureWeight;
            this.precipitationWeight = precipitationWeight;
            this.windWeight = windWeight;
            this.airQualityWeight = airQualityWeight;
            this.comfortableMinimumCelsius =
                    comfortableMinimumCelsius;
            this.comfortableMaximumCelsius =
                    comfortableMaximumCelsius;
            this.windPenaltyPerKmh = windPenaltyPerKmh;

            if (temperatureWeight + precipitationWeight
                    + windWeight + airQualityWeight != 100) {
                throw new IllegalArgumentException(
                        "Activity score weights must total 100."
                );
            }
        }

        public String label() {
            return label;
        }

        public String weightSummary() {
            return String.format(
                    Locale.ENGLISH,
                    "Weights: temperature %d%%, precipitation %d%%, wind %d%%, "
                            + "current AQI %d%%.",
                    temperatureWeight,
                    precipitationWeight,
                    windWeight,
                    airQualityWeight
            );
        }

        private int temperatureWeight() {
            return temperatureWeight;
        }

        private int precipitationWeight() {
            return precipitationWeight;
        }

        private int windWeight() {
            return windWeight;
        }

        private int airQualityWeight() {
            return airQualityWeight;
        }

        private double comfortableMinimumCelsius() {
            return comfortableMinimumCelsius;
        }

        private double comfortableMaximumCelsius() {
            return comfortableMaximumCelsius;
        }

        private double windPenaltyPerKmh() {
            return windPenaltyPerKmh;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record Score(
            int total,
            int temperature,
            int precipitation,
            int wind,
            int airQuality
    ) {
    }
}
