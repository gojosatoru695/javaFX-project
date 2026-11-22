package com.atmosphere.service;

import com.atmosphere.model.ForecastData;
import com.atmosphere.model.PersonalComfortScore;
import com.atmosphere.model.WeatherData;
import com.atmosphere.model.WeatherPreferenceProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PersonalComfortScorerTest {

    private WeatherPreferenceProfile defaultProfile;

    @BeforeEach
    void setUp() {
        defaultProfile = new WeatherPreferenceProfile(
                18.0,
                26.0,
                35.0,
                65.0,
                20.0,
                WeatherPreferenceProfile.AirQualitySensitivity.MODERATE
        );
    }

    @Test
    @DisplayName("Ideal weather conditions yield optimal score (>90)")
    void testIdealConditions() {
        WeatherData weather = new WeatherData();
        weather.setCityName("Bengaluru");
        weather.setTemperature(22.0); // Inside 18..26
        weather.setHumidity(50.0);    // Inside 35..65
        weather.setWindSpeed(8.0);    // Gentle wind <= 8 km/h
        weather.setAqi(1);            // Clean air
        weather.setAqiLabel("Good");

        PersonalComfortScore score = PersonalComfortScorer.score(defaultProfile, weather);

        assertEquals(100, score.totalScore());
        assertEquals(PersonalComfortScore.Category.OPTIMAL, score.category());
        assertEquals(100, score.temperatureScore());
        assertEquals(100, score.humidityScore());
        assertEquals(100, score.windScore());
        assertEquals(100, score.airQualityScore());
        assertTrue(score.explanation().contains("Optimal") || score.explanation().contains("optimal"));
    }

    @Test
    @DisplayName("Extreme harsh conditions clamp properly and produce low score (<40)")
    void testHarshConditions() {
        WeatherData weather = new WeatherData();
        weather.setCityName("ExtremeCity");
        weather.setTemperature(46.0);  // High heat (+20°C above max)
        weather.setHumidity(95.0);     // Very muggy
        weather.setWindSpeed(65.0);    // High gale
        weather.setAqi(5);             // Very Poor
        weather.setAqiLabel("Very Poor");

        PersonalComfortScore score = PersonalComfortScorer.score(defaultProfile, weather);

        assertTrue(score.totalScore() <= 20, "Harsh weather should score low: " + score.totalScore());
        assertTrue(score.totalScore() >= 0, "Score should never drop below 0: " + score.totalScore());
        assertEquals(PersonalComfortScore.Category.HARSH, score.category());
        assertTrue(score.explanation().contains("100"));
    }

    @Test
    @DisplayName("Air quality sensitivity adjusts AQI penalties deterministically")
    void testAirQualitySensitivity() {
        WeatherData weather = new WeatherData();
        weather.setCityName("Delhi");
        weather.setTemperature(22.0);
        weather.setHumidity(50.0);
        weather.setWindSpeed(5.0);
        weather.setAqi(4); // Poor AQI
        weather.setAqiLabel("Poor");

        WeatherPreferenceProfile lowSens = defaultProfile.copy();
        lowSens.setAirQualitySensitivity(WeatherPreferenceProfile.AirQualitySensitivity.LOW);

        WeatherPreferenceProfile highSens = defaultProfile.copy();
        highSens.setAirQualitySensitivity(WeatherPreferenceProfile.AirQualitySensitivity.HIGH);

        PersonalComfortScore scoreLow = PersonalComfortScorer.score(lowSens, weather);
        PersonalComfortScore scoreHigh = PersonalComfortScorer.score(highSens, weather);

        assertTrue(scoreLow.totalScore() > scoreHigh.totalScore(),
                "High sensitivity should penalize poor AQI more heavily than low sensitivity");
    }

    @Test
    @DisplayName("Boundary tests for temperature below and above preferred range")
    void testTemperatureBoundaries() {
        assertEquals(100, PersonalComfortScorer.calculateTemperatureScore(defaultProfile, 18.0));
        assertEquals(100, PersonalComfortScorer.calculateTemperatureScore(defaultProfile, 26.0));
        assertEquals(100, PersonalComfortScorer.calculateTemperatureScore(defaultProfile, 22.0));

        // 10°C below minimum (18 - 10 = 8°C)
        int scoreCold = PersonalComfortScorer.calculateTemperatureScore(defaultProfile, 8.0);
        assertTrue(scoreCold < 100 && scoreCold >= 40, "Cold penalty score: " + scoreCold);

        // 10°C above maximum (26 + 10 = 36°C)
        int scoreHot = PersonalComfortScorer.calculateTemperatureScore(defaultProfile, 36.0);
        assertTrue(scoreHot < 100 && scoreHot >= 40, "Hot penalty score: " + scoreHot);
    }

    @Test
    @DisplayName("Null weather data returns safe 0 score without throwing")
    void testNullWeather() {
        PersonalComfortScore score = PersonalComfortScorer.score(defaultProfile, null);
        assertNotNull(score);
        assertEquals(0, score.totalScore());
        assertEquals(PersonalComfortScore.Category.HARSH, score.category());
    }

    @Test
    @DisplayName("Forecast suitability score evaluates temperature and rain probability correctly")
    void testForecastSuitability() {
        ForecastData dryDay = new ForecastData();
        dryDay.setTempHigh(24.0);
        dryDay.setTempLow(18.0);
        dryDay.setRainChance(5);

        PersonalComfortScorer.ForecastSuitability drySuitability =
                PersonalComfortScorer.scoreForecastDay(defaultProfile, dryDay);

        assertTrue(drySuitability.score() >= 85, "Dry, comfortable day should score high");
        assertEquals("Great", drySuitability.label());

        ForecastData rainyDay = new ForecastData();
        rainyDay.setTempHigh(36.0);
        rainyDay.setTempLow(30.0);
        rainyDay.setRainChance(90);

        PersonalComfortScorer.ForecastSuitability rainySuitability =
                PersonalComfortScorer.scoreForecastDay(defaultProfile, rainyDay);

        assertTrue(rainySuitability.score() <= 50, "Hot and heavy rain day should score low");
    }
}
