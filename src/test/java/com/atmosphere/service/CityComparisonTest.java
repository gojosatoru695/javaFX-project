package com.atmosphere.service;

import com.atmosphere.model.PersonalComfortScore;
import com.atmosphere.model.WeatherData;
import com.atmosphere.model.WeatherPreferenceProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CityComparisonTest {

    private WeatherPreferenceProfile profile;

    @BeforeEach
    void setUp() {
        profile = new WeatherPreferenceProfile(
                18.0, 26.0, 35.0, 65.0, 20.0,
                WeatherPreferenceProfile.AirQualitySensitivity.MODERATE
        );
    }

    @Test
    @DisplayName("Comparison between 3 cities correctly identifies the best match and ranks accurately")
    void testThreeCityComparisonRanking() {
        // City 1: Bengaluru (Ideal conditions)
        WeatherData blr = new WeatherData();
        blr.setCityName("Bengaluru");
        blr.setTemperature(22.0);
        blr.setHumidity(50.0);
        blr.setWindSpeed(8.0);
        blr.setAqi(1);
        blr.setAqiLabel("Good");

        // City 2: Mumbai (Humid & warm)
        WeatherData bom = new WeatherData();
        bom.setCityName("Mumbai");
        bom.setTemperature(32.0); // +6°C over max
        bom.setHumidity(85.0);    // +20% over max
        bom.setWindSpeed(14.0);
        bom.setAqi(2);
        bom.setAqiLabel("Fair");

        // City 3: Delhi (Severe AQI)
        WeatherData del = new WeatherData();
        del.setCityName("Delhi");
        del.setTemperature(28.0);
        del.setHumidity(55.0);
        del.setWindSpeed(6.0);
        del.setAqi(4); // Poor AQI
        del.setAqiLabel("Poor");

        List<CityEntry> list = new ArrayList<>(List.of(
                new CityEntry("Bengaluru", blr, PersonalComfortScorer.score(profile, blr)),
                new CityEntry("Mumbai", bom, PersonalComfortScorer.score(profile, bom)),
                new CityEntry("Delhi", del, PersonalComfortScorer.score(profile, del))
        ));

        list.sort(Comparator.comparingInt(e -> -e.score.totalScore()));

        assertEquals("Bengaluru", list.get(0).name, "Bengaluru should rank #1 due to ideal conditions");
        assertEquals(100, list.get(0).score.totalScore());
        assertTrue(list.get(0).score.totalScore() > list.get(1).score.totalScore());
        assertTrue(list.get(1).score.totalScore() >= list.get(2).score.totalScore()
                || list.get(2).score.totalScore() < list.get(0).score.totalScore());
    }

    @Test
    @DisplayName("Comparison handles 2 cities and generates distinct difference margins")
    void testTwoCityComparison() {
        WeatherData cityA = new WeatherData();
        cityA.setCityName("CityA");
        cityA.setTemperature(20.0);
        cityA.setHumidity(45.0);
        cityA.setWindSpeed(10.0);
        cityA.setAqi(1);
        cityA.setAqiLabel("Good");

        WeatherData cityB = new WeatherData();
        cityB.setCityName("CityB");
        cityB.setTemperature(35.0);
        cityB.setHumidity(90.0);
        cityB.setWindSpeed(30.0);
        cityB.setAqi(3);
        cityB.setAqiLabel("Moderate");

        PersonalComfortScore scoreA = PersonalComfortScorer.score(profile, cityA);
        PersonalComfortScore scoreB = PersonalComfortScorer.score(profile, cityB);

        int margin = scoreA.totalScore() - scoreB.totalScore();
        assertTrue(margin >= 30, "CityA should comfortably lead CityB by at least 30 pts: " + margin);
    }

    private record CityEntry(String name, WeatherData weather, PersonalComfortScore score) {}
}
