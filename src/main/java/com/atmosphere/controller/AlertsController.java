package com.atmosphere.controller;

import com.atmosphere.model.ForecastSlot;
import com.atmosphere.model.WeatherAlert;
import com.atmosphere.model.WeatherAlert.Severity;
import com.atmosphere.model.WeatherData;
import com.atmosphere.service.CityRegistry;
import com.atmosphere.service.WeatherAlertService;
import com.atmosphere.service.WeatherService;
import com.atmosphere.util.ToastNotifications;
import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads current and forecast weather and renders active city alerts. */
public class AlertsController {

    private static final String ALL_SEVERITIES = "All severities";

    private static final DateTimeFormatter UPDATED_FORMAT =
            DateTimeFormatter.ofPattern(
                    "HH:mm:ss",
                    Locale.ENGLISH
            );

    @FXML private ComboBox<String> severityFilter;
    @FXML private Label alertCountLabel;
    @FXML private Label refreshStatusLabel;
    @FXML private ProgressIndicator refreshProgress;
    @FXML private Button refreshButton;
    @FXML private Button markAllReadButton;
    @FXML private VBox alertCardsContainer;

    private final WeatherService weatherService = new WeatherService();
    private final WeatherAlertService alertService =
            new WeatherAlertService();
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "atmosphere-alert-worker"
                );
                thread.setDaemon(true);
                return thread;
            });
    private final PauseTransition refreshDebounce =
            new PauseTransition(Duration.millis(500));

    private final Set<String> readAlertIds = new HashSet<>();
    private List<WeatherAlert> activeAlerts = List.of();
    private Task<RefreshResult> activeTask;
    private boolean loading;
    private boolean disposed;

    @FXML
    private void initialize() {
        severityFilter.getItems().setAll(
                ALL_SEVERITIES,
                Severity.CRITICAL.label(),
                Severity.WARNING.label(),
                Severity.INFORMATION.label()
        );
        severityFilter.setValue(ALL_SEVERITIES);
        severityFilter.valueProperty().addListener(
                (observable, oldValue, newValue) -> renderAlerts()
        );

        refreshDebounce.setOnFinished(
                event -> refreshAlerts()
        );

        renderAlerts();
    }

    @FXML
    private void handleRefresh() {
        refreshAlerts();
    }

    @FXML
    private void markAllRead() {
        activeAlerts.forEach(alert -> readAlertIds.add(alert.id()));
        renderAlerts();
    }

    /** Refreshes after navigation or a manual request. */
    public void refreshAlerts() {
        if (disposed) {
            return;
        }

        refreshDebounce.stop();

        List<String> cities = List.copyOf(CityRegistry.getCities());
        if (cities.isEmpty()) {
            activeAlerts = List.of();
            readAlertIds.clear();
            refreshStatusLabel.setText(
                    "No registered cities are available to check."
            );
            setLoading(false);
            renderAlerts();
            return;
        }

        cancelActiveTask();
        setLoading(true);
        refreshStatusLabel.setText(
                "Checking current readings and upcoming forecasts for "
                        + cities.size() + " cities..."
        );
        renderAlerts();

        Task<RefreshResult> task = new Task<>() {
            @Override
            protected RefreshResult call() {
                List<WeatherAlert> alerts = new ArrayList<>();
                List<String> failures = new ArrayList<>();
                int checkedCities = 0;
                int successfulSources = 0;

                for (String city : cities) {
                    if (isCancelled()) {
                        break;
                    }

                    WeatherData current = null;
                    List<ForecastSlot> forecastSlots = List.of();

                    try {
                        current = weatherService.getCurrentWeather(city);
                        successfulSources++;
                    } catch (RuntimeException exception) {
                        failures.add(
                                city + " current weather: "
                                        + errorMessage(exception)
                        );
                    }

                    if (isCancelled()) {
                        break;
                    }

                    try {
                        forecastSlots = weatherService.getForecastSlots(city);
                        successfulSources++;
                    } catch (RuntimeException exception) {
                        failures.add(
                                city + " forecast: "
                                        + errorMessage(exception)
                        );
                    }

                    if (current != null || !forecastSlots.isEmpty()) {
                        checkedCities++;
                        alerts.addAll(
                                alertService.analyze(
                                        city,
                                        current,
                                        forecastSlots
                                )
                        );
                    }
                }

                return new RefreshResult(
                        deduplicate(alerts),
                        List.copyOf(failures),
                        checkedCities,
                        successfulSources
                );
            }
        };

        activeTask = task;

        task.setOnSucceeded(event -> {
            if (activeTask != task) {
                return;
            }

            activeTask = null;
            setLoading(false);

            RefreshResult result = task.getValue();

            if (!cities.isEmpty() && result.successfulSources() == 0) {
                refreshStatusLabel.setText(
                        "Could not load weather data. Existing alerts are retained."
                );
                ToastNotifications.show(
                        alertCardsContainer,
                        "Alert refresh failed: " + result.failures().get(0)
                );
                renderAlerts();
                return;
            }

            activeAlerts = result.alerts();
            Set<String> currentIds = new HashSet<>();
            activeAlerts.forEach(alert -> currentIds.add(alert.id()));
            readAlertIds.retainAll(currentIds);

            String updatedAt = UPDATED_FORMAT.format(LocalDateTime.now());
            if (result.failures().isEmpty()) {
                refreshStatusLabel.setText(
                        "Updated " + updatedAt + " · "
                                + result.checkedCities()
                                + " cities checked."
                );
            } else {
                refreshStatusLabel.setText(
                        "Updated " + updatedAt + " · "
                                + result.checkedCities()
                                + " cities checked; "
                                + result.failures().size()
                                + " reading(s) unavailable."
                );
                ToastNotifications.show(
                        alertCardsContainer,
                        result.failures().size()
                                + " weather reading(s) could not be loaded."
                );
            }

            renderAlerts();
        });

        task.setOnFailed(event -> {
            if (activeTask != task) {
                return;
            }

            activeTask = null;
            setLoading(false);

            Throwable error = task.getException();
            String message = error == null
                    ? "The alert analysis could not be completed."
                    : errorMessage(error);

            refreshStatusLabel.setText(
                    "Alert refresh failed. Existing alerts are retained."
            );
            ToastNotifications.show(alertCardsContainer, message);
            renderAlerts();
        });

        task.setOnCancelled(event -> {
            if (activeTask == task) {
                activeTask = null;
                setLoading(false);
            }
        });

        executor.execute(task);
    }

    /** Coalesces rapid Dashboard updates into one all-city refresh. */
    public void scheduleRefreshAfterWeatherUpdate() {
        if (!disposed) {
            refreshDebounce.playFromStart();
        }
    }

    private void renderAlerts() {
        if (alertCardsContainer == null) {
            return;
        }

        String selectedSeverity = severityFilter == null
                ? ALL_SEVERITIES
                : severityFilter.getValue();

        List<WeatherAlert> visibleAlerts = activeAlerts.stream()
                .filter(alert -> selectedSeverity == null
                        || ALL_SEVERITIES.equals(selectedSeverity)
                        || alert.severity().label().equals(selectedSeverity))
                .sorted(
                        Comparator
                                .comparingInt(
                                        (WeatherAlert alert) ->
                                                alert.severity().ordinal()
                                )
                                .thenComparing(
                                        WeatherAlert::cityName,
                                        String.CASE_INSENSITIVE_ORDER
                                )
                                .thenComparing(alert ->
                                        alert.condition().ordinal()
                                )
                )
                .toList();

        alertCardsContainer.getChildren().clear();

        if (visibleAlerts.isEmpty()) {
            Label emptyState = new Label(emptyStateMessage());
            emptyState.setWrapText(true);
            emptyState.getStyleClass().add("alert-empty-state");
            alertCardsContainer.getChildren().add(emptyState);
        } else {
            visibleAlerts.forEach(
                    alert -> alertCardsContainer.getChildren().add(
                            createAlertCard(alert)
                    )
            );
        }

        long unreadCount = activeAlerts.stream()
                .filter(alert -> !readAlertIds.contains(alert.id()))
                .count();
        alertCountLabel.setText(
                activeAlerts.size() + " active · "
                        + unreadCount + " unread"
        );
        markAllReadButton.setDisable(unreadCount == 0);
    }

    private String emptyStateMessage() {
        if (loading && activeAlerts.isEmpty()) {
            return "Checking registered cities for active weather alerts...";
        }
        if (!activeAlerts.isEmpty()) {
            return "No active alerts match the selected severity.";
        }
        return "You're all clear. No active weather alerts were found "
                + "in the latest available readings.";
    }

    private VBox createAlertCard(WeatherAlert alert) {
        boolean isRead = readAlertIds.contains(alert.id());

        VBox card = new VBox(10);
        card.getStyleClass().addAll("card", "alert-card");
        if (isRead) {
            card.getStyleClass().add("alert-card-read");
        }

        Label severityBadge = new Label(alert.severity().label());
        severityBadge.getStyleClass().addAll(
                "badge",
                "alert-severity-badge",
                "severity-" + alert.severity().name().toLowerCase(Locale.ROOT)
        );

        Label title = new Label(alert.title());
        title.getStyleClass().add("alert-card-title");

        Label timeframe = new Label(alert.timeframe());
        timeframe.getStyleClass().add("alert-timeframe");

        Button readButton = new Button(isRead ? "Read" : "Mark as read");
        readButton.getStyleClass().add("alert-read-button");
        readButton.setDisable(isRead);
        readButton.setOnAction(event -> {
            readAlertIds.add(alert.id());
            renderAlerts();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox heading = new HBox(
                10,
                severityBadge,
                title,
                spacer,
                timeframe,
                readButton
        );
        heading.setAlignment(Pos.CENTER_LEFT);

        Label city = new Label(alert.cityName());
        city.getStyleClass().add("alert-city");

        Label actualValue = new Label(alert.actualValue());
        actualValue.getStyleClass().add("alert-actual-value");

        HBox details = new HBox(14, city, actualValue);
        details.setAlignment(Pos.CENTER_LEFT);

        Label explanation = new Label(alert.explanation());
        explanation.setWrapText(true);
        explanation.getStyleClass().add("alert-explanation");

        Label precaution = new Label(
                "Recommended precaution: " + alert.precaution()
        );
        precaution.setWrapText(true);
        precaution.getStyleClass().add("alert-precaution");

        card.getChildren().setAll(
                heading,
                details,
                explanation,
                precaution
        );
        return card;
    }

    private void setLoading(boolean isLoading) {
        loading = isLoading;
        refreshButton.setDisable(isLoading);
        refreshProgress.setVisible(isLoading);
        refreshProgress.setManaged(isLoading);
    }

    private void cancelActiveTask() {
        if (activeTask != null) {
            activeTask.cancel(true);
            activeTask = null;
        }
    }

    private static List<WeatherAlert> deduplicate(
            List<WeatherAlert> alerts
    ) {
        Map<String, WeatherAlert> unique = new LinkedHashMap<>();
        alerts.forEach(alert -> unique.putIfAbsent(alert.id(), alert));
        return List.copyOf(unique.values());
    }

    private static String errorMessage(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? "No details provided."
                : error.getMessage();
    }

    public void dispose() {
        disposed = true;
        refreshDebounce.stop();
        cancelActiveTask();
        executor.shutdownNow();
    }

    private record RefreshResult(
            List<WeatherAlert> alerts,
            List<String> failures,
            int checkedCities,
            int successfulSources
    ) {
    }
}
