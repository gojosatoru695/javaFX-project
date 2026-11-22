package com.atmosphere.service;

import com.atmosphere.model.ForecastSlot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivitySuitabilityScorerTest {

    @Test
    void givesFullScoreForIdealWeatherAndGoodCurrentAqi() {
        ForecastSlot slot = slot(20, 5, 0);

        ActivitySuitabilityScorer.Score score =
                ActivitySuitabilityScorer.score(
                        ActivitySuitabilityScorer.Activity.WALKING,
                        slot,
                        1
                );

        assertEquals(100, score.total());
        assertEquals(100, score.temperature());
        assertEquals(100, score.precipitation());
        assertEquals(100, score.wind());
        assertEquals(100, score.airQuality());
    }

    @Test
    void joggingWeightsCurrentAqiMoreThanWalking() {
        ForecastSlot slot = slot(18, 5, 0);

        int walkingAqiImpact = score(
                ActivitySuitabilityScorer.Activity.WALKING,
                slot,
                1
        ) - score(
                ActivitySuitabilityScorer.Activity.WALKING,
                slot,
                5
        );

        int joggingAqiImpact = score(
                ActivitySuitabilityScorer.Activity.JOGGING,
                slot,
                1
        ) - score(
                ActivitySuitabilityScorer.Activity.JOGGING,
                slot,
                5
        );

        assertEquals(25, walkingAqiImpact);
        assertEquals(35, joggingAqiImpact);
        assertTrue(joggingAqiImpact > walkingAqiImpact);
    }

    @Test
    void severeWeatherAndVeryPoorAqiCanReachZero() {
        ForecastSlot slot = slot(50, 40, 100);

        ActivitySuitabilityScorer.Score score =
                ActivitySuitabilityScorer.score(
                        ActivitySuitabilityScorer.Activity.JOGGING,
                        slot,
                        5
                );

        assertEquals(0, score.total());
        assertEquals(0, score.temperature());
        assertEquals(0, score.precipitation());
        assertEquals(0, score.wind());
        assertEquals(0, score.airQuality());
    }

    @Test
    void rejectsAqiOutsideTheOpenWeatherScale() {
        assertThrows(
                IllegalArgumentException.class,
                () -> score(
                        ActivitySuitabilityScorer.Activity.WALKING,
                        slot(20, 5, 0),
                        0
                )
        );
    }

    private static int score(
            ActivitySuitabilityScorer.Activity activity,
            ForecastSlot slot,
            int currentAqi
    ) {
        return ActivitySuitabilityScorer.score(
                activity,
                slot,
                currentAqi
        ).total();
    }

    private static ForecastSlot slot(
            double temperatureCelsius,
            double windSpeedKmh,
            int precipitationProbability
    ) {
        ForecastSlot slot = new ForecastSlot();
        slot.setTemperatureCelsius(temperatureCelsius);
        slot.setWindSpeedKmh(windSpeedKmh);
        slot.setPrecipitationProbability(
                precipitationProbability
        );
        return slot;
    }
}
