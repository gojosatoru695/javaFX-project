package com.atmosphere.service;

import com.atmosphere.model.WeatherSnapshot;
import com.atmosphere.service.WeatherHistoryService.Anomaly;
import com.atmosphere.service.WeatherHistoryService.AnomalyType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherHistoryServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsAndOrdersSnapshotsWithoutDuplicateCityTimestamps()
            throws SQLException {
        WeatherHistoryService service = new WeatherHistoryService(
                temporaryDirectory.resolve("history.db")
        );

        try {
            LocalDateTime olderTime =
                    LocalDateTime.of(2026, 11, 16, 8, 0, 0, 100_000_000);
            LocalDateTime latestTime =
                    LocalDateTime.of(2026, 11, 16, 9, 0, 0, 200_000_000);

            WeatherSnapshot older = snapshot(
                    "Bengaluru",
                    olderTime,
                    20,
                    55,
                    10,
                    1012,
                    2
            );
            WeatherSnapshot latest = snapshot(
                    "Bengaluru",
                    latestTime,
                    25,
                    60,
                    12,
                    1010,
                    3
            );

            service.saveSnapshot(older);
            service.saveSnapshot(older);
            service.saveSnapshot(latest);

            List<WeatherSnapshot> saved =
                    service.getRecentSnapshots("bengaluru", 10);

            assertEquals(2, saved.size());
            assertEquals(latest.timestamp(), saved.get(0).timestamp());
            assertEquals(older.timestamp(), saved.get(1).timestamp());
            assertEquals(25, saved.get(0).temperatureCelsius());
            assertEquals(3, saved.get(0).aqi());
        } finally {
            service.close();
        }
    }

    @Test
    void detectsInclusiveTemperatureAqiAndWindThresholds() {
        LocalDateTime firstTime = LocalDateTime.of(2026, 11, 16, 8, 0);
        LocalDateTime secondTime = firstTime.plusMinutes(10);
        LocalDateTime thirdTime = secondTime.plusMinutes(10);

        List<WeatherSnapshot> snapshots = List.of(
                snapshot("Delhi", thirdTime, 20, 50, 49, 1000, 1),
                snapshot("Delhi", firstTime, 20, 50, 10, 1000, 1),
                snapshot("Delhi", secondTime, 25, 50, 30, 1000, 3)
        );

        List<Anomaly> anomalies =
                WeatherHistoryService.detectAnomalies(snapshots);

        assertEquals(5, anomalies.size());
        assertTrue(anomalies.stream().anyMatch(anomaly ->
                anomaly.type() == AnomalyType.TEMPERATURE_CHANGE
                        && anomaly.previousValue() == 20
                        && anomaly.latestValue() == 25
                        && anomaly.timestamp().equals(secondTime)
        ));
        assertTrue(anomalies.stream().anyMatch(anomaly ->
                anomaly.type() == AnomalyType.AQI_CHANGE
                        && anomaly.previousValue() == 1
                        && anomaly.latestValue() == 3
                        && anomaly.timestamp().equals(secondTime)
        ));
        assertTrue(anomalies.stream().anyMatch(anomaly ->
                anomaly.type() == AnomalyType.WIND_INCREASE
                        && anomaly.previousValue() == 10
                        && anomaly.latestValue() == 30
                        && anomaly.timestamp().equals(secondTime)
        ));
        assertTrue(anomalies.stream().noneMatch(anomaly ->
                anomaly.type() == AnomalyType.WIND_INCREASE
                        && anomaly.timestamp().equals(thirdTime)
        ));
    }

    private static WeatherSnapshot snapshot(
            String city,
            LocalDateTime timestamp,
            double temperature,
            double humidity,
            double wind,
            double pressure,
            int aqi
    ) {
        return new WeatherSnapshot(
                city,
                temperature,
                humidity,
                wind,
                pressure,
                aqi,
                timestamp
        );
    }
}
