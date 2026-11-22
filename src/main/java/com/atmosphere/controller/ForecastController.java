
package com.atmosphere.controller;

import com.atmosphere.config.AppConfig;
import com.atmosphere.model.ForecastData;
import com.atmosphere.service.CityRegistry;
import com.atmosphere.service.WeatherService;
import com.atmosphere.util.CardHoverEffects;
import com.atmosphere.util.TemperatureStyler;
import com.atmosphere.util.ToastNotifications;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads five-day forecasts away from the JavaFX Application Thread and renders
 * the temperature trend and animated daily forecast cards.
 */
public class ForecastController {

    private static final Duration CHART_FADE_DURATION =
            Duration.millis(320);

    private static final Duration CARD_FADE_DURATION =
            Duration.millis(260);

    private static final Duration CARD_STAGGER =
            Duration.millis(80);

    private static final long CHART_POINT_INTERVAL_MS = 100;

    @FXML private ComboBox<String> citySelector;
    @FXML private AreaChart<String, Number> temperatureAreaChart;
    @FXML private CategoryAxis dayAxis;
    @FXML private NumberAxis temperatureAxis;
    @FXML private HBox dayCardsContainer;
    @FXML private Label forecastStatusLabel;

    private final WeatherService weatherService =
            new WeatherService();

    private final ExecutorService forecastExecutor =
            Executors.newFixedThreadPool(2, runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "atmosphere-forecast-worker"
                );
                thread.setDaemon(true);
                return thread;
            });

    private Task<List<ForecastData>> activeForecastTask;
    private List<ForecastData> lastForecast = List.of();

    private Timeline chartDrawTimeline;
    private FadeTransition chartFadeTransition;

    @FXML
    private void initialize() {
        configureChart();
        configureCitySelector();

        AppConfig.fahrenheitProperty().addListener(
                (observable, oldValue, newValue) -> {
                    temperatureAxis.setLabel(
                            "Temperature ("
                                    + TemperatureStyler.unitSuffix()
                                    + ")"
                    );

                    if (!lastForecast.isEmpty()) {
                        renderTemperatureChart(lastForecast);
                        renderDayCards(lastForecast);
                    }
                }
        );

        if (citySelector.getItems().isEmpty()) {
            forecastStatusLabel.setText(
                    "Add a city from the Dashboard to view its forecast."
            );
        } else {
            citySelector.getSelectionModel().selectFirst();
        }
    }

    private void configureChart() {
        temperatureAreaChart.setAnimated(false);
        temperatureAreaChart.setLegendVisible(true);
        temperatureAreaChart.setCreateSymbols(false);
        temperatureAreaChart.setAlternativeColumnFillVisible(false);
        temperatureAreaChart.setAlternativeRowFillVisible(false);
        temperatureAreaChart.setHorizontalGridLinesVisible(true);
        temperatureAreaChart.setVerticalGridLinesVisible(false);
        temperatureAreaChart.setOpacity(0.0);

        dayAxis.setLabel("Day");
        dayAxis.setGapStartAndEnd(false);

        temperatureAxis.setLabel(
                "Temperature (" + TemperatureStyler.unitSuffix() + ")"
        );

        temperatureAxis.setForceZeroInRange(false);
        temperatureAxis.setMinorTickVisible(false);
    }

    private void configureCitySelector() {
        // This is the same ObservableList updated by DashboardController.
        citySelector.setItems(CityRegistry.getCities());

        citySelector.valueProperty().addListener(
                (observable, previousCity, selectedCity) -> {
                    if (selectedCity != null
                            && !selectedCity.isBlank()
                            && !selectedCity.equals(previousCity)) {
                        loadForecast(selectedCity);
                    }
                }
        );
    }

    private void loadForecast(String city) {
        cancelActiveLoadAndAnimations();

        forecastStatusLabel.setText(
                "Loading the five-day forecast for " + city + "…"
        );

        forecastStatusLabel.setVisible(true);
        forecastStatusLabel.setManaged(true);

        dayCardsContainer.getChildren().clear();
        temperatureAreaChart.getData().clear();
        dayAxis.getCategories().clear();
        temperatureAreaChart.setOpacity(0.0);

        Task<List<ForecastData>> task = new Task<>() {
            @Override
            protected List<ForecastData> call() {
                return weatherService.getForecast(city);
            }
        };

        activeForecastTask = task;

        task.setOnSucceeded(event -> {
            if (activeForecastTask != task) {
                return; // Ignore stale result after a city change.
            }

            List<ForecastData> forecast = task.getValue();

            lastForecast = forecast == null
                    ? List.of()
                    : List.copyOf(forecast);

            if (forecast == null || forecast.isEmpty()) {
                showForecastError(
                        "No forecast data was returned for " + city + "."
                );
                return;
            }

            forecastStatusLabel.setText(
                    "Five-day forecast for " + city
                            + " · Updated just now"
            );

            renderTemperatureChart(forecast);
            renderDayCards(forecast);
        });

        task.setOnFailed(event -> {
            if (activeForecastTask != task) {
                return;
            }

            Throwable error = task.getException();

            String message = error == null || error.getMessage() == null
                    ? "Forecast data could not be loaded."
                    : error.getMessage();

            showForecastError(message);
        });

        task.setOnCancelled(event -> {
            if (activeForecastTask == task) {
                forecastStatusLabel.setText(
                        "Forecast loading cancelled."
                );
            }
        });

        forecastExecutor.execute(task);
    }

    private void renderTemperatureChart(
            List<ForecastData> forecast
    ) {
        XYChart.Series<String, Number> highSeries =
                new XYChart.Series<>();

        highSeries.setName("High");

        XYChart.Series<String, Number> lowSeries =
                new XYChart.Series<>();

        lowSeries.setName("Low");

        dayAxis.getCategories().setAll(
                forecast.stream()
                        .map(ForecastData::getDayName)
                        .toList()
        );

        // Add the empty series first. The Timeline fills each point
        // in sequence to create the requested 100ms draw-on effect.
        temperatureAreaChart.getData().setAll(
                List.of(highSeries, lowSeries)
        );

        chartFadeTransition = new FadeTransition(
                CHART_FADE_DURATION,
                temperatureAreaChart
        );

        chartFadeTransition.setFromValue(0.0);
        chartFadeTransition.setToValue(1.0);
        chartFadeTransition.play();

        chartDrawTimeline = new Timeline();

        for (int index = 0; index < forecast.size(); index++) {
            ForecastData day = forecast.get(index);
            int pointIndex = index;

            chartDrawTimeline.getKeyFrames().add(
                    new javafx.animation.KeyFrame(
                            Duration.millis(
                                    CHART_POINT_INTERVAL_MS * (pointIndex + 1)
                            ),
                            event -> {
                                highSeries.getData().add(
                                        new XYChart.Data<>(
                                                day.getDayName(),
                                                TemperatureStyler.toDisplayValue(
                                                        day.getTempHigh()
                                                )
                                        )
                                );

                                lowSeries.getData().add(
                                        new XYChart.Data<>(
                                                day.getDayName(),
                                                TemperatureStyler.toDisplayValue(
                                                        day.getTempLow()
                                                )
                                        )
                                );
                            }
                    )
            );
        }

        chartDrawTimeline.playFromStart();
    }

    private void renderDayCards(List<ForecastData> forecast) {
        dayCardsContainer.getChildren().clear();

        for (int index = 0; index < forecast.size(); index++) {
            ForecastData data = forecast.get(index);

            VBox card = createDayCard(data);

            dayCardsContainer.getChildren().add(card);
            playDayCardEntrance(card, index);
        }
    }

    private VBox createDayCard(ForecastData forecast) {
        Label dayName = new Label(
                safe(forecast.getDayName(), "Day")
        );
        dayName.getStyleClass().add("forecast-day-name");

        Label weatherIcon = new Label(
                weatherEmoji(forecast.getIcon())
        );
        weatherIcon.getStyleClass().add("forecast-weather-emoji");

        Label highTemperature = new Label();
        highTemperature.getStyleClass().add(
                "forecast-high-temperature"
        );

        TemperatureStyler.apply(
                highTemperature,
                forecast.getTempHigh()
        );

        Label lowTemperature = new Label();
        lowTemperature.getStyleClass().add(
                "forecast-low-temperature"
        );

        TemperatureStyler.apply(
                lowTemperature,
                forecast.getTempLow()
        );

        Label description = new Label(
                capitalize(
                        safe(
                                forecast.getDescription(),
                                "Conditions unavailable"
                        )
                )
        );

        description.getStyleClass().add("forecast-description");
        description.setWrapText(true);
        description.setAlignment(Pos.CENTER);
        description.setMaxWidth(Double.MAX_VALUE);

        Label rainChance = new Label(
                "☂ "
                        + Math.max(
                                0,
                                Math.min(100, forecast.getRainChance())
                        )
                        + "%"
        );

        rainChance.getStyleClass().addAll(
                "badge",
                "badge-blue",
                "forecast-rain-badge"
        );

        VBox card = new VBox(
                11,
                dayName,
                weatherIcon,
                highTemperature,
                lowTemperature,
                description,
                rainChance
        );

        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(160);
        card.setMinWidth(145);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMinHeight(230);

        card.getStyleClass().addAll(
                "card",
                "forecast-day-card"
        );

        CardHoverEffects.install(card);
        HBox.setHgrow(card, Priority.ALWAYS);

        return card;
    }

    private void playDayCardEntrance(
            VBox card,
            int index
    ) {
        Duration delay = CARD_STAGGER.multiply(index);

        // Set initial values before applying the stagger so cards do not
        // flash fully visible while waiting for their individual animation.
        card.setOpacity(0.0);
        card.setTranslateY(20.0);

        FadeTransition fade = new FadeTransition(
                CARD_FADE_DURATION,
                card
        );

        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.setDelay(delay);

        TranslateTransition translate = new TranslateTransition(
                CARD_FADE_DURATION,
                card
        );

        translate.setFromY(20.0);
        translate.setToY(0.0);
        translate.setDelay(delay);

        new ParallelTransition(fade, translate).play();
    }

    private void showForecastError(String message) {
        forecastStatusLabel.setText(message);
        forecastStatusLabel.setVisible(true);
        forecastStatusLabel.setManaged(true);

        temperatureAreaChart.getData().clear();
        dayCardsContainer.getChildren().clear();

        ToastNotifications.show(
                forecastStatusLabel,
                message
        );
    }

    /** Reload the currently selected city after settings are saved. */
    public void refreshSelectedCity() {
        String city = citySelector.getValue();

        if (city != null && !city.isBlank()) {
            loadForecast(city);
        }
    }

    private void cancelActiveLoadAndAnimations() {
        if (activeForecastTask != null
                && !activeForecastTask.isDone()) {
            activeForecastTask.cancel(true);
        }

        if (chartDrawTimeline != null) {
            chartDrawTimeline.stop();
        }

        if (chartFadeTransition != null) {
            chartFadeTransition.stop();
        }
    }

    private static String weatherEmoji(String iconCode) {
        return WeatherService.getConditionEmoji(iconCode);
    }

    private static String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "Conditions unavailable";
        }

        return value.substring(0, 1).toUpperCase(Locale.ENGLISH)
                + value.substring(1);
    }

    private static String safe(
            String value,
            String fallback
    ) {
        return value == null || value.isBlank()
                ? fallback
                : value;
    }

    /** Stop the background worker and JavaFX animations when the screen is disposed. */
    public void dispose() {
        cancelActiveLoadAndAnimations();
        forecastExecutor.shutdownNow();
    }
}

