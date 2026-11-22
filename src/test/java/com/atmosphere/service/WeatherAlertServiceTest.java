package com.atmosphere.service;

import com.atmosphere.config.AppConfig;
import com.atmosphere.model.ForecastSlot;
import com.atmosphere.model.WeatherAlert;
import com.atmosphere.model.WeatherAlert.Condition;
import com.atmosphere.model.WeatherAlert.Severity;
import com.atmosphere.model.WeatherData;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherAlertServiceTest {

    @Test
    void triggersConfiguredConditionsAtTheirExactThresholds() {
        WeatherData current = current("Bengaluru", 35, 40, 4);
        ForecastSlot forecast = slot(
                LocalDateTime.now().plusHours(3),
                36,
                45,
                70
        );

        List<WeatherAlert> alerts =
                new WeatherAlertService().analyze(
                        "Bengaluru",
                        current,
                        List.of(forecast)
                );

        Map<Condition, WeatherAlert> byCondition = alerts.stream()
                .collect(Collectors.toMap(
                        WeatherAlert::condition,
                        Function.identity()
                ));

        assertEquals(4, alerts.size());
        assertEquals(
                Severity.WARNING,
                byCondition.get(Condition.AIR_QUALITY).severity()
        );
        assertEquals(
                Severity.WARNING,
                byCondition.get(Condition.HIGH_TEMPERATURE).severity()
        );
        assertEquals(
                "70% precipitation probability",
                byCondition.get(Condition.RAIN).actualValue()
        );
        assertEquals(
                "40.0 km/h",
                byCondition.get(Condition.WIND).actualValue()
        );
        assertEquals(
                "35.0°C",
                byCondition.get(Condition.HIGH_TEMPERATURE).actualValue()
        );
    }

    @Test
    void emitsOnlyOneAlertPerCityAndConditionUsingEarliestForecastSlot() {
        LocalDateTime now = LocalDateTime.now();
        List<WeatherAlert> alerts =
                new WeatherAlertService().analyze(
                        "Delhi",
                        current("Delhi", 20, 10, 2),
                        List.of(
                                slot(now.plusHours(6), 40, 55, 90),
                                slot(now.plusHours(3), 36, 42, 70)
                        )
                );

        assertEquals(3, alerts.size());
        assertEquals(
                3,
                alerts.stream()
                        .map(WeatherAlert::id)
                        .distinct()
                        .count()
        );
        assertEquals(
                Severity.WARNING,
                alerts.stream()
                        .filter(alert -> alert.condition() == Condition.RAIN)
                        .findFirst()
                        .orElseThrow()
                        .severity()
        );
        assertEquals(
                "42.0 km/h",
                alerts.stream()
                        .filter(alert -> alert.condition() == Condition.WIND)
                        .findFirst()
                        .orElseThrow()
                        .actualValue()
        );
        assertEquals(
                "Forecast",
                alerts.stream()
                        .filter(alert ->
                                alert.condition()
                                        == Condition.HIGH_TEMPERATURE
                        )
                        .findFirst()
                        .orElseThrow()
                        .timeframe()
                        .split(" · ")[0]
        );
    }

    @Test
    void acceptsCustomThresholdsAndDoesNotInventMissingCurrentReadings() {
        WeatherAlertService service = new WeatherAlertService(
                new WeatherAlertService.Thresholds(3, 50, 30, 30, 10)
        );

        List<WeatherAlert> alerts = service.analyze(
                "Mumbai",
                null,
                List.of(slot(
                        LocalDateTime.now().plusHours(3),
                        30,
                        30,
                        50
                ))
        );

        assertEquals(3, alerts.size());
        assertEquals(
                1,
                alerts.stream()
                        .filter(alert ->
                                alert.condition()
                                        == Condition.HIGH_TEMPERATURE
                        )
                        .count()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new WeatherAlertService.Thresholds(
                        0,
                        70,
                        40,
                        35,
                        5
                )
        );
    }

    @Test
    void alertsAtLowTemperatureThresholdAndEscalatesExtremeReadings() {
        WeatherAlertService service = new WeatherAlertService();

        WeatherAlert thresholdAlert = service.analyze(
                "Delhi",
                current("Delhi", 5, 0, 1),
                List.of()
        ).getFirst();

        WeatherAlert criticalAlert = service.analyze(
                "Delhi",
                current("Delhi", 0, 0, 1),
                List.of()
        ).getFirst();

        assertEquals(Condition.LOW_TEMPERATURE, thresholdAlert.condition());
        assertEquals("5.0°C", thresholdAlert.actualValue());
        assertEquals(Severity.WARNING, thresholdAlert.severity());
        assertEquals(Severity.CRITICAL, criticalAlert.severity());
    }

    @Test
    void formatsTemperatureAlertsInTheSelectedDisplayUnit() {
        boolean originalUnit = AppConfig.isFahrenheit();

        try {
            AppConfig.fahrenheitProperty().set(true);

            WeatherAlert alert = new WeatherAlertService().analyze(
                    "Delhi",
                    current("Delhi", 35, 0, 1),
                    List.of()
            ).getFirst();

            assertEquals("95.0°F", alert.actualValue());
            assertTrue(alert.explanation().contains("95.0°F"));
        } finally {
            AppConfig.fahrenheitProperty().set(originalUnit);
        }
    }

    private static WeatherData current(
            String city,
            double temperature,
            double windSpeed,
            int aqi
    ) {
        WeatherData data = new WeatherData();
        data.setCityName(city);
        data.setTemperature(temperature);
        data.setWindSpeed(windSpeed);
        data.setAqi(aqi);
        return data;
    }

    private static ForecastSlot slot(
            LocalDateTime dateTime,
            double temperature,
            double windSpeed,
            int rainProbability
    ) {
        ForecastSlot slot = new ForecastSlot();
        slot.setDateTime(dateTime);
        slot.setTemperatureCelsius(temperature);
        slot.setWindSpeedKmh(windSpeed);
        slot.setPrecipitationProbability(rainProbability);
        return slot;
    }
}
