package com.atmosphere.service;

import com.atmosphere.model.ForecastData;
import com.atmosphere.model.PersonalComfortScore;
import com.atmosphere.model.WeatherData;
import com.atmosphere.model.WeatherPreferenceProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic weather comfort scoring engine (Personal Weather DNA).
 * Evaluates live weather conditions and forecast trends against user preferences.
 */
public final class PersonalComfortScorer {

    private static final double CALM_WIND_THRESHOLD_KMH = 8.0;

    private PersonalComfortScorer() {
    }

    /**
     * Calculates the deterministic Personal Comfort Score (0–100) for current weather data.
     */
    public static PersonalComfortScore score(
            WeatherPreferenceProfile profile,
            WeatherData weather
    ) {
        if (profile == null) {
            profile = WeatherPreferenceProfile.defaultProfile();
        }

        if (weather == null) {
            return new PersonalComfortScore(
                    0,
                    PersonalComfortScore.Category.HARSH,
                    0,
                    0,
                    0,
                    0,
                    "No weather data available to evaluate comfort.",
                    List.of("Missing weather metrics")
            );
        }

        int tempScore = calculateTemperatureScore(profile, weather.getTemperature());
        int windScore = calculateWindScore(profile, weather.getWindSpeed());
        int humidityScore = calculateHumidityScore(profile, weather.getHumidity());
        int aqiScore = calculateAirQualityScore(profile, weather.getAqi());

        int total = (int) Math.round(
                tempScore * 0.40
                        + windScore * 0.20
                        + humidityScore * 0.15
                        + aqiScore * 0.25
        );
        total = clamp(total, 0, 100);

        PersonalComfortScore.Category category = PersonalComfortScore.Category.fromScore(total);

        List<String> highlights = new ArrayList<>();
        String explanation = buildExplanation(
                profile,
                weather,
                total,
                category,
                tempScore,
                windScore,
                humidityScore,
                aqiScore,
                highlights
        );

        return new PersonalComfortScore(
                total,
                category,
                tempScore,
                windScore,
                humidityScore,
                aqiScore,
                explanation,
                highlights
        );
    }

    /**
     * Calculates forecast suitability (0–100) using available forecast temperature and rain probability.
     */
    public static ForecastSuitability scoreForecastDay(
            WeatherPreferenceProfile profile,
            ForecastData forecast
    ) {
        if (profile == null) {
            profile = WeatherPreferenceProfile.defaultProfile();
        }

        if (forecast == null) {
            return new ForecastSuitability(0, "Unavailable", "No forecast data.");
        }

        double avgTemp = (forecast.getTempHigh() + forecast.getTempLow()) / 2.0;
        int tempFit = calculateTemperatureScore(profile, avgTemp);

        int rainChance = Math.max(0, Math.min(100, forecast.getRainChance()));
        int rainFit = Math.max(0, 100 - (int) Math.round(rainChance * 1.1));

        int total = (int) Math.round(tempFit * 0.60 + rainFit * 0.40);
        total = clamp(total, 0, 100);

        String label = total >= 80 ? "Great" : total >= 60 ? "Good" : total >= 40 ? "Fair" : "Poor";
        String summary = String.format(
                Locale.ENGLISH,
                "%d%% suitability · Temp ~%.0f°C (%s), Rain risk %d%%",
                total,
                avgTemp,
                tempFit >= 90 ? "optimal" : (avgTemp < profile.getMinTemperatureCelsius() ? "cool" : "warm"),
                rainChance
        );

        return new ForecastSuitability(total, label, summary);
    }

    public record ForecastSuitability(int score, String label, String summary) {
    }

    // -------------------------------------------------------------------------
    // Individual Factor Calculations
    // -------------------------------------------------------------------------

    public static int calculateTemperatureScore(
            WeatherPreferenceProfile profile,
            double temperatureCelsius
    ) {
        double min = profile.getMinTemperatureCelsius();
        double max = profile.getMaxTemperatureCelsius();

        if (temperatureCelsius >= min && temperatureCelsius <= max) {
            return 100;
        }

        if (temperatureCelsius < min) {
            double delta = min - temperatureCelsius;
            return clamp((int) Math.round(100.0 - delta * 5.5), 0, 100);
        } else {
            double delta = temperatureCelsius - max;
            return clamp((int) Math.round(100.0 - delta * 5.5), 0, 100);
        }
    }

    public static int calculateWindScore(
            WeatherPreferenceProfile profile,
            double windSpeedKmh
    ) {
        double maxComfort = Math.max(10.0, profile.getMaxComfortableWindKmh());

        if (windSpeedKmh <= CALM_WIND_THRESHOLD_KMH) {
            return 100;
        }

        if (windSpeedKmh <= maxComfort) {
            double ratio = (windSpeedKmh - CALM_WIND_THRESHOLD_KMH) / (maxComfort - CALM_WIND_THRESHOLD_KMH);
            return clamp((int) Math.round(100.0 - ratio * 20.0), 80, 100);
        }

        double excess = windSpeedKmh - maxComfort;
        double penalty = (excess / maxComfort) * 90.0;
        return clamp((int) Math.round(80.0 - penalty), 0, 80);
    }

    public static int calculateHumidityScore(
            WeatherPreferenceProfile profile,
            double humidityPercent
    ) {
        double min = profile.getMinHumidityPercent();
        double max = profile.getMaxHumidityPercent();

        if (humidityPercent >= min && humidityPercent <= max) {
            return 100;
        }

        if (humidityPercent < min) {
            double delta = min - humidityPercent;
            return clamp((int) Math.round(100.0 - delta * 2.8), 0, 100);
        } else {
            double delta = humidityPercent - max;
            return clamp((int) Math.round(100.0 - delta * 2.6), 0, 100);
        }
    }

    public static int calculateAirQualityScore(
            WeatherPreferenceProfile profile,
            int aqi
    ) {
        if (aqi < 1) {
            return 50; // Neutral fallback when missing
        }

        int baseScore = switch (aqi) {
            case 1 -> 100; // Good
            case 2 -> 80;  // Fair
            case 3 -> 55;  // Moderate
            case 4 -> 25;  // Poor
            default -> 0;  // Very Poor (5+)
        };

        WeatherPreferenceProfile.AirQualitySensitivity sensitivity =
                profile.getAirQualitySensitivity();

        if (aqi == 1) {
            return 100;
        }

        int penalty = 100 - baseScore;
        int adjustedPenalty = (int) Math.round(penalty * sensitivity.weightMultiplier());
        return clamp(100 - adjustedPenalty, 0, 100);
    }

    // -------------------------------------------------------------------------
    // Explanation Generator
    // -------------------------------------------------------------------------

    private static String buildExplanation(
            WeatherPreferenceProfile profile,
            WeatherData weather,
            int totalScore,
            PersonalComfortScore.Category category,
            int tempScore,
            int windScore,
            int humidityScore,
            int aqiScore,
            List<String> highlights
    ) {
        List<String> positives = new ArrayList<>();
        List<String> negatives = new ArrayList<>();

        // Temperature evaluation
        if (tempScore >= 95) {
            positives.add(String.format(Locale.ENGLISH, "temperature is optimal at %.1f°C", weather.getTemperature()));
            highlights.add(String.format(Locale.ENGLISH, "Ideal temp (%.0f°C)", weather.getTemperature()));
        } else if (weather.getTemperature() < profile.getMinTemperatureCelsius()) {
            double diff = profile.getMinTemperatureCelsius() - weather.getTemperature();
            negatives.add(String.format(Locale.ENGLISH, "%.1f°C below your preferred minimum", diff));
            highlights.add(String.format(Locale.ENGLISH, "Cool (%.0f°C)", weather.getTemperature()));
        } else {
            double diff = weather.getTemperature() - profile.getMaxTemperatureCelsius();
            negatives.add(String.format(Locale.ENGLISH, "%.1f°C warmer than your preferred maximum", diff));
            highlights.add(String.format(Locale.ENGLISH, "Warm (%.0f°C)", weather.getTemperature()));
        }

        // Humidity evaluation
        if (humidityScore >= 95) {
            positives.add(String.format(Locale.ENGLISH, "humidity is comfortable (%.0f%%)", weather.getHumidity()));
            highlights.add(String.format(Locale.ENGLISH, "Comfortable humidity (%.0f%%)", weather.getHumidity()));
        } else if (weather.getHumidity() < profile.getMinHumidityPercent()) {
            negatives.add(String.format(Locale.ENGLISH, "dry air (%.0f%% humidity)", weather.getHumidity()));
            highlights.add("Dry air");
        } else {
            negatives.add(String.format(Locale.ENGLISH, "muggy air (%.0f%% humidity)", weather.getHumidity()));
            highlights.add("High humidity");
        }

        // Wind evaluation
        if (windScore >= 90) {
            positives.add(String.format(Locale.ENGLISH, "wind conditions are favourable (%.1f km/h)", weather.getWindSpeed()));
            highlights.add(String.format(Locale.ENGLISH, "Gentle wind (%.0f km/h)", weather.getWindSpeed()));
        } else {
            negatives.add(String.format(Locale.ENGLISH, "gusty wind (%.1f km/h)", weather.getWindSpeed()));
            highlights.add(String.format(Locale.ENGLISH, "Breezy (%.0f km/h)", weather.getWindSpeed()));
        }

        // Air Quality evaluation
        if (aqiScore >= 80) {
            positives.add("air quality is clean (" + weather.getAqiLabel() + ")");
            highlights.add("Clean air (AQI " + weather.getAqi() + ")");
        } else {
            negatives.add("air quality is degraded (" + weather.getAqiLabel() + ", AQI " + weather.getAqi() + ")");
            highlights.add("AQI " + weather.getAqi() + " (" + weather.getAqiLabel() + ")");
        }

        StringBuilder narrative = new StringBuilder();
        narrative.append(String.format(Locale.ENGLISH, "Your current conditions score %d/100 (%s). ", totalScore, category.label()));

        if (!positives.isEmpty() && negatives.isEmpty()) {
            narrative.append(capitalize(joinWithAnd(positives))).append(".");
        } else if (!positives.isEmpty()) {
            narrative.append(capitalize(joinWithAnd(positives))).append(", but ");
            narrative.append(joinWithAnd(negatives)).append(".");
        } else {
            narrative.append("Noticeable factors reducing comfort: ").append(joinWithAnd(negatives)).append(".");
        }

        return narrative.toString();
    }

    private static String joinWithAnd(List<String> items) {
        if (items.isEmpty()) return "";
        if (items.size() == 1) return items.get(0);
        if (items.size() == 2) return items.get(0) + " and " + items.get(1);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(i == items.size() - 1 ? " and " : ", ");
            }
            sb.append(items.get(i));
        }
        return sb.toString();
    }

    private static String capitalize(String text) {
        if (text == null || text.isBlank()) return "";
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }
}
