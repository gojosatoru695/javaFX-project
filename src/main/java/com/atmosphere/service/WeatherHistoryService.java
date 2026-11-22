package com.atmosphere.service;

import com.atmosphere.config.AppConfig;
import com.atmosphere.model.WeatherSnapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Stores and reads real weather snapshots in the user's AtmoSphere folder. */
public class WeatherHistoryService implements AutoCloseable {

    private static final DateTimeFormatter DATABASE_TIME_FORMAT =
            DateTimeFormatter.ofPattern(
                    "uuuu-MM-dd HH:mm:ss.SSSSSSSSS",
                    Locale.ROOT
            );

    private final Path databasePath;
    private final ExecutorService writerExecutor =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "atmosphere-history-writer"
                );
                thread.setDaemon(true);
                return thread;
            });

    public WeatherHistoryService() {
        this(
                AppConfig.getConfigFile()
                        .getParent()
                        .resolve("weather-history.db")
        );
    }

    public WeatherHistoryService(Path databasePath) {
        this.databasePath = Objects.requireNonNull(
                databasePath,
                "databasePath"
        ).toAbsolutePath().normalize();
    }

    public Path getDatabasePath() {
        return databasePath;
    }

    /**
     * Queues persistence away from the calling thread. Dashboard success
     * handlers can use this without blocking the JavaFX Application Thread.
     */
    public CompletableFuture<Void> saveSnapshotAsync(
            WeatherSnapshot snapshot
    ) {
        Objects.requireNonNull(snapshot, "snapshot");

        try {
            return CompletableFuture.runAsync(
                    () -> {
                        try {
                            saveSnapshot(snapshot);
                        } catch (SQLException exception) {
                            throw new CompletionException(exception);
                        }
                    },
                    writerExecutor
            );
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    /** Synchronous database write; call this only from a worker thread. */
    public void saveSnapshot(WeatherSnapshot snapshot)
            throws SQLException {
        Objects.requireNonNull(snapshot, "snapshot");

        String sql = """
                INSERT INTO weather_snapshots (
                    city, temperature_celsius, humidity_percent,
                    wind_speed_kmh, pressure_hpa, aqi, recorded_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(city, recorded_at) DO NOTHING
                """;

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {
            statement.setString(1, snapshot.cityName());
            statement.setDouble(2, snapshot.temperatureCelsius());
            statement.setDouble(3, snapshot.humidityPercent());
            statement.setDouble(4, snapshot.windSpeedKmh());
            statement.setDouble(5, snapshot.pressureHpa());
            statement.setInt(6, snapshot.aqi());
            statement.setString(
                    7,
                    snapshot.timestamp().format(DATABASE_TIME_FORMAT)
            );
            statement.executeUpdate();
        }
    }

    /** Synchronous query; callers should run it in a JavaFX Task. */
    public List<WeatherSnapshot> getRecentSnapshots(
            String city,
            int limit
    ) throws SQLException {
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException(
                    "A city name is required to read weather history."
            );
        }
        if (limit < 1) {
            throw new IllegalArgumentException(
                    "Snapshot limit must be at least one."
            );
        }

        String sql = """
                SELECT city, temperature_celsius, humidity_percent,
                       wind_speed_kmh, pressure_hpa, aqi, recorded_at
                FROM weather_snapshots
                WHERE city = ? COLLATE NOCASE
                ORDER BY recorded_at DESC, id DESC
                LIMIT ?
                """;

        List<WeatherSnapshot> snapshots = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {
            statement.setString(1, city.trim());
            statement.setInt(2, limit);

            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    snapshots.add(new WeatherSnapshot(
                            results.getString("city"),
                            results.getDouble("temperature_celsius"),
                            results.getDouble("humidity_percent"),
                            results.getDouble("wind_speed_kmh"),
                            results.getDouble("pressure_hpa"),
                            results.getInt("aqi"),
                            LocalDateTime.parse(
                                    results.getString("recorded_at"),
                                    DATABASE_TIME_FORMAT
                            )
                    ));
                }
            }
        }

        return List.copyOf(snapshots);
    }

    /** Finds the requested changes between consecutive readings by city. */
    public static List<Anomaly> detectAnomalies(
            List<WeatherSnapshot> snapshots
    ) {
        Objects.requireNonNull(snapshots, "snapshots");

        Map<String, List<WeatherSnapshot>> byCity =
                new LinkedHashMap<>();
        for (WeatherSnapshot snapshot : snapshots) {
            Objects.requireNonNull(snapshot, "snapshot");
            byCity.computeIfAbsent(
                    snapshot.cityName().toLowerCase(Locale.ROOT),
                    ignored -> new ArrayList<>()
            ).add(snapshot);
        }

        List<Anomaly> anomalies = new ArrayList<>();

        for (List<WeatherSnapshot> citySnapshots : byCity.values()) {
            List<WeatherSnapshot> ordered = citySnapshots.stream()
                    .sorted(Comparator.comparing(
                            WeatherSnapshot::timestamp
                    ))
                    .toList();

            for (int index = 1; index < ordered.size(); index++) {
                WeatherSnapshot previous = ordered.get(index - 1);
                WeatherSnapshot latest = ordered.get(index);

                if (Math.abs(
                        latest.temperatureCelsius()
                                - previous.temperatureCelsius()
                ) >= 5) {
                    anomalies.add(new Anomaly(
                            latest.cityName(),
                            AnomalyType.TEMPERATURE_CHANGE,
                            previous.temperatureCelsius(),
                            latest.temperatureCelsius(),
                            latest.timestamp()
                    ));
                }

                if (Math.abs(latest.aqi() - previous.aqi()) >= 2) {
                    anomalies.add(new Anomaly(
                            latest.cityName(),
                            AnomalyType.AQI_CHANGE,
                            previous.aqi(),
                            latest.aqi(),
                            latest.timestamp()
                    ));
                }

                if (latest.windSpeedKmh() - previous.windSpeedKmh() >= 20) {
                    anomalies.add(new Anomaly(
                            latest.cityName(),
                            AnomalyType.WIND_INCREASE,
                            previous.windSpeedKmh(),
                            latest.windSpeedKmh(),
                            latest.timestamp()
                    ));
                }
            }
        }

        return anomalies.stream()
                .sorted(
                        Comparator.comparing(Anomaly::timestamp)
                                .thenComparing(anomaly ->
                                        anomaly.type().ordinal()
                                )
                                .thenComparing(
                                        Anomaly::cityName,
                                        String.CASE_INSENSITIVE_ORDER
                                )
                )
                .toList();
    }

    private Connection openConnection() throws SQLException {
        try {
            Files.createDirectories(databasePath.getParent());
        } catch (IOException exception) {
            throw new SQLException(
                    "Could not create the AtmoSphere data directory: "
                            + databasePath.getParent(),
                    exception
            );
        }

        Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath
        );

        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS weather_snapshots (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        city TEXT NOT NULL COLLATE NOCASE,
                        temperature_celsius REAL NOT NULL,
                        humidity_percent REAL NOT NULL,
                        wind_speed_kmh REAL NOT NULL,
                        pressure_hpa REAL NOT NULL,
                        aqi INTEGER NOT NULL,
                        recorded_at TEXT NOT NULL,
                        UNIQUE (city, recorded_at)
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_weather_history_city_time
                    ON weather_snapshots(city, recorded_at DESC)
                    """);
        } catch (SQLException exception) {
            try {
                connection.close();
            } catch (SQLException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }

        return connection;
    }

    @Override
    public void close() {
        writerExecutor.shutdown();
    }

    public enum AnomalyType {
        TEMPERATURE_CHANGE("Temperature change", "°C"),
        AQI_CHANGE("AQI change", "AQI levels"),
        WIND_INCREASE("Wind increase", "km/h");

        private final String label;
        private final String unit;

        AnomalyType(String label, String unit) {
            this.label = label;
            this.unit = unit;
        }

        public String label() {
            return label;
        }

        public String unit() {
            return unit;
        }
    }

    public record Anomaly(
            String cityName,
            AnomalyType type,
            double previousValue,
            double latestValue,
            LocalDateTime timestamp
    ) {
    }
}
