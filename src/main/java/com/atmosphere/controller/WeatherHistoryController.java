package com.atmosphere.controller;

import com.atmosphere.model.WeatherSnapshot;
import com.atmosphere.service.CityRegistry;
import com.atmosphere.service.WeatherHistoryService;
import com.atmosphere.service.WeatherHistoryService.Anomaly;
import com.atmosphere.service.WeatherHistoryService.AnomalyType;
import com.atmosphere.util.TemperatureStyler;
import com.atmosphere.util.ToastNotifications;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Displays locally stored weather readings and deterministic anomalies. */
public class WeatherHistoryController {

    private static final int SNAPSHOT_LIMIT = 100;
    private static final int TABLE_ROW_LIMIT = 20;

    private static final DateTimeFormatter CHART_TIME_FORMAT =
            DateTimeFormatter.ofPattern(
                    "MMM d HH:mm:ss",
                    Locale.ENGLISH
            );
    private static final DateTimeFormatter TABLE_TIME_FORMAT =
            DateTimeFormatter.ofPattern(
                    "MMM d, yyyy HH:mm:ss",
                    Locale.ENGLISH
            );
    private static final DateTimeFormatter ANOMALY_TIME_FORMAT =
            DateTimeFormatter.ofPattern(
                    "MMM d, yyyy 'at' HH:mm:ss",
                    Locale.ENGLISH
            );

    @FXML private ComboBox<String> citySelector;
    @FXML private Label historyStatusLabel;
    @FXML private Label anomalyStatusLabel;
    @FXML private CategoryAxis temperatureTimeAxis;
    @FXML private NumberAxis temperatureAxis;
    @FXML private LineChart<String, Number> temperatureChart;
    @FXML private CategoryAxis aqiTimeAxis;
    @FXML private NumberAxis aqiAxis;
    @FXML private LineChart<String, Number> aqiChart;
    @FXML private VBox anomalyCardsContainer;
    @FXML private TableView<WeatherSnapshot> snapshotsTable;

    private final WeatherHistoryService historyService =
            new WeatherHistoryService();
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "atmosphere-history-reader"
                );
                thread.setDaemon(true);
                return thread;
            });
    private final PauseTransition refreshDebounce =
            new PauseTransition(Duration.millis(500));

    private Task<HistoryResult> activeTask;
    private boolean disposed;

    @FXML
    private void initialize() {
        citySelector.setItems(CityRegistry.getCities());
        configureCharts();
        configureSnapshotTable();

        refreshDebounce.setOnFinished(
                event -> refreshSelectedCity()
        );

        if (citySelector.getItems().isEmpty()) {
            historyStatusLabel.setText(
                    "Add a city from the Dashboard to start its history."
            );
        } else {
            citySelector.getSelectionModel().selectFirst();
        }

        citySelector.valueProperty().addListener(
                (observable, oldCity, newCity) -> {
                    if (newCity != null && !newCity.isBlank()
                            && !newCity.equals(oldCity)) {
                        loadHistory(newCity);
                    }
                }
        );
    }

    private void configureCharts() {
        temperatureChart.setAnimated(false);
        temperatureChart.setCreateSymbols(true);
        temperatureChart.setLegendVisible(false);
        temperatureAxis.setForceZeroInRange(false);
        temperatureAxis.setLabel(
                "Temperature (" + TemperatureStyler.unitSuffix() + ")"
        );
        temperatureTimeAxis.setLabel("Snapshot time");

        aqiChart.setAnimated(false);
        aqiChart.setCreateSymbols(true);
        aqiChart.setLegendVisible(false);
        aqiAxis.setAutoRanging(false);
        aqiAxis.setLowerBound(1);
        aqiAxis.setUpperBound(5);
        aqiAxis.setTickUnit(1);
        aqiAxis.setLabel("AQI (1–5)");
        aqiTimeAxis.setLabel("Snapshot time");
    }

    private void configureSnapshotTable() {
        TableColumn<WeatherSnapshot, String> timeColumn =
                column("Timestamp", snapshot ->
                        snapshot.timestamp().format(TABLE_TIME_FORMAT));
        TableColumn<WeatherSnapshot, String> temperatureColumn =
                column("Temperature", snapshot ->
                        TemperatureStyler.formatOneDecimal(
                                snapshot.temperatureCelsius()
                        ));
        TableColumn<WeatherSnapshot, String> humidityColumn =
                column("Humidity", snapshot ->
                        format(snapshot.humidityPercent()) + "%");
        TableColumn<WeatherSnapshot, String> windColumn =
                column("Wind", snapshot ->
                        format(snapshot.windSpeedKmh()) + " km/h");
        TableColumn<WeatherSnapshot, String> pressureColumn =
                column("Pressure", snapshot ->
                        format(snapshot.pressureHpa()) + " hPa");
        TableColumn<WeatherSnapshot, String> aqiColumn =
                column("AQI", snapshot ->
                        Integer.toString(snapshot.aqi()));

        snapshotsTable.getColumns().setAll(List.of(
                timeColumn,
                temperatureColumn,
                humidityColumn,
                windColumn,
                pressureColumn,
                aqiColumn
        ));
        snapshotsTable.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
        );
        snapshotsTable.setPlaceholder(
                new Label("No saved snapshots for this city yet.")
        );
    }

    private static TableColumn<WeatherSnapshot, String> column(
            String title,
            java.util.function.Function<WeatherSnapshot, String> value
    ) {
        TableColumn<WeatherSnapshot, String> column =
                new TableColumn<>(title);
        column.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(value.apply(cell.getValue()))
        );
        return column;
    }

    @FXML
    public void refreshSelectedCity() {
        temperatureAxis.setLabel(
                "Temperature (" + TemperatureStyler.unitSuffix() + ")"
        );
        String city = citySelector.getValue();
        if (city != null && !city.isBlank()) {
            loadHistory(city);
        }
    }

    public void scheduleRefreshAfterWeatherUpdate() {
        if (!disposed && citySelector.getValue() != null) {
            refreshDebounce.playFromStart();
        }
    }

    private void loadHistory(String city) {
        if (disposed) {
            return;
        }

        cancelActiveTask();
        clearHistoryView();
        historyStatusLabel.setText(
                "Loading saved weather readings for " + city + "..."
        );
        anomalyStatusLabel.setText(
                "Comparing consecutive successful readings..."
        );

        Task<HistoryResult> task = new Task<>() {
            @Override
            protected HistoryResult call() throws Exception {
                List<WeatherSnapshot> snapshots =
                        historyService.getRecentSnapshots(
                                city,
                                SNAPSHOT_LIMIT
                        );

                return new HistoryResult(
                        snapshots,
                        WeatherHistoryService.detectAnomalies(snapshots)
                );
            }
        };

        activeTask = task;

        task.setOnSucceeded(event -> {
            if (activeTask != task) {
                return;
            }

            activeTask = null;
            HistoryResult result = task.getValue();
            List<WeatherSnapshot> snapshots = result.snapshots();

            snapshotsTable.getItems().setAll(
                    snapshots.stream()
                            .limit(TABLE_ROW_LIMIT)
                            .toList()
            );

            renderCharts(snapshots);
            renderAnomalies(snapshots, result.anomalies());

            if (snapshots.isEmpty()) {
                historyStatusLabel.setText(
                        "No saved readings for " + city
                                + ". A snapshot is stored after each "
                                + "successful Dashboard refresh."
                );
            } else {
                historyStatusLabel.setText(
                        "Showing " + snapshots.size()
                                + " recent saved reading(s) for "
                                + city + " · newest first in the table"
                );
            }
        });

        task.setOnFailed(event -> {
            if (activeTask != task) {
                return;
            }

            activeTask = null;
            clearHistoryView();

            Throwable error = task.getException();
            String message = error == null
                    || error.getMessage() == null
                    || error.getMessage().isBlank()
                    ? "The weather history database could not be read."
                    : error.getMessage();

            historyStatusLabel.setText(
                    "Could not load weather history: " + message
            );
            anomalyStatusLabel.setText(
                    "Anomalies are unavailable until the database can be read."
            );
            ToastNotifications.show(historyStatusLabel, message);
        });

        executor.execute(task);
    }

    private void renderCharts(List<WeatherSnapshot> snapshots) {
        temperatureChart.getData().clear();
        aqiChart.getData().clear();

        List<WeatherSnapshot> chronological = snapshots.stream()
                .sorted(Comparator.comparing(WeatherSnapshot::timestamp))
                .toList();

        XYChart.Series<String, Number> temperatureSeries =
                new XYChart.Series<>();
        temperatureSeries.setName("Temperature");

        XYChart.Series<String, Number> aqiSeries =
                new XYChart.Series<>();
        aqiSeries.setName("AQI");

        for (WeatherSnapshot snapshot : chronological) {
            String time = snapshot.timestamp().format(CHART_TIME_FORMAT);
            temperatureSeries.getData().add(
                    new XYChart.Data<>(
                            time,
                            TemperatureStyler.toDisplayValue(
                                    snapshot.temperatureCelsius()
                            )
                    )
            );
            aqiSeries.getData().add(
                    new XYChart.Data<>(time, snapshot.aqi())
            );
        }

        if (!chronological.isEmpty()) {
            temperatureChart.getData().setAll(
                    List.of(temperatureSeries)
            );
            aqiChart.getData().setAll(List.of(aqiSeries));
        }
    }

    private void renderAnomalies(
            List<WeatherSnapshot> snapshots,
            List<Anomaly> anomalies
    ) {
        anomalyCardsContainer.getChildren().clear();

        if (snapshots.size() < 2) {
            anomalyStatusLabel.setText(
                    "At least two successful snapshots are needed to "
                            + "compare consecutive readings."
            );
            return;
        }

        if (anomalies.isEmpty()) {
            anomalyStatusLabel.setText(
                    "No configured anomalies were detected in these readings."
            );
            return;
        }

        List<Anomaly> newestFirst = new ArrayList<>(anomalies);
        newestFirst.sort(
                Comparator.comparing(Anomaly::timestamp).reversed()
        );

        anomalyStatusLabel.setText(
                anomalies.size() + " anomaly change(s) detected."
        );

        for (Anomaly anomaly : newestFirst) {
            anomalyCardsContainer.getChildren().add(
                    createAnomalyCard(anomaly)
            );
        }
    }

    private VBox createAnomalyCard(Anomaly anomaly) {
        Label title = new Label(
                anomaly.type().label() + " · " + anomaly.cityName()
        );
        title.getStyleClass().add("history-anomaly-title");

        Label change = new Label(
                anomalyValue(anomaly.type(), anomaly.previousValue())
                        + "  →  "
                        + anomalyValue(
                                anomaly.type(),
                                anomaly.latestValue()
                        )
        );
        change.getStyleClass().add("history-anomaly-change");

        Label timestamp = new Label(
                "Latest snapshot · "
                        + anomaly.timestamp().format(ANOMALY_TIME_FORMAT)
        );
        timestamp.getStyleClass().add("history-anomaly-time");

        VBox card = new VBox(7, title, change, timestamp);
        card.getStyleClass().addAll("card", "history-anomaly-card");
        return card;
    }

    private void clearHistoryView() {
        temperatureChart.getData().clear();
        aqiChart.getData().clear();
        snapshotsTable.getItems().clear();
        anomalyCardsContainer.getChildren().clear();
    }

    private void cancelActiveTask() {
        if (activeTask != null) {
            activeTask.cancel(true);
            activeTask = null;
        }
    }

    private static String anomalyValue(
            AnomalyType type,
            double value
    ) {
        if (type == AnomalyType.AQI_CHANGE) {
            return "AQI " + (int) value;
        }

        if (type == AnomalyType.TEMPERATURE_CHANGE) {
            return TemperatureStyler.formatOneDecimal(value);
        }

        return format(value) + " " + type.unit();
    }

    private static String format(double value) {
        return String.format(Locale.ENGLISH, "%.1f", value);
    }

    public void dispose() {
        disposed = true;
        refreshDebounce.stop();
        cancelActiveTask();
        executor.shutdownNow();
        historyService.close();
    }

    private record HistoryResult(
            List<WeatherSnapshot> snapshots,
            List<Anomaly> anomalies
    ) {
    }
}
