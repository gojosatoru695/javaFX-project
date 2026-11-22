package com.atmosphere.controller;

import com.atmosphere.model.ForecastSlot;
import com.atmosphere.model.WeatherData;
import com.atmosphere.service.ActivitySuitabilityScorer;
import com.atmosphere.service.ActivitySuitabilityScorer.Activity;
import com.atmosphere.service.ActivitySuitabilityScorer.Score;
import com.atmosphere.service.CityRegistry;
import com.atmosphere.service.WeatherService;
import com.atmosphere.util.CardHoverEffects;
import com.atmosphere.util.TemperatureStyler;
import com.atmosphere.util.ToastNotifications;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Recommends upcoming weather windows for common outdoor activities. */
public class ActivityPlannerController {

    private static final DateTimeFormatter SLOT_TIME_FORMAT =
            DateTimeFormatter.ofPattern(
                    "EEE, MMM d · h:mm a",
                    Locale.ENGLISH
            );

    private static final int DISPLAYED_UPCOMING_SLOTS = 5;

    @FXML private ComboBox<Activity> activitySelector;
    @FXML private ComboBox<String> citySelector;
    @FXML private Label activityWeightLabel;
    @FXML private Label plannerStatusLabel;
    @FXML private Label bestTimeLabel;
    @FXML private Label bestScoreBadge;
    @FXML private ProgressBar bestScoreProgress;
    @FXML private Label bestWeatherLabel;
    @FXML private Label bestExplanationLabel;
    @FXML private Label currentAqiValueLabel;
    @FXML private Label currentAqiStatusLabel;
    @FXML private VBox slotCardsContainer;

    private final WeatherService weatherService =
            new WeatherService();

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "atmosphere-activity-planner-worker"
                );
                thread.setDaemon(true);
                return thread;
            });

    private Task<PlannerData> activeTask;
    private List<ForecastSlot> forecastSlots = List.of();
    private WeatherData currentWeather;

    @FXML
    private void initialize() {
        citySelector.setItems(CityRegistry.getCities());
        activitySelector.setItems(
                FXCollections.observableArrayList(Activity.values())
        );

        activitySelector.valueProperty().addListener(
                (observable, oldActivity, newActivity) -> {
                    updateActivityWeights(newActivity);
                    renderPlan();
                }
        );

        citySelector.valueProperty().addListener(
                (observable, oldCity, newCity) -> {
                    if (newCity != null && !newCity.isBlank()
                            && !newCity.equals(oldCity)) {
                        loadPlannerData(newCity);
                    }
                }
        );

        if (!activitySelector.getItems().isEmpty()) {
            activitySelector.getSelectionModel().selectFirst();
        }

        if (!citySelector.getItems().isEmpty()) {
            citySelector.getSelectionModel().selectFirst();
        } else {
            plannerStatusLabel.setText(
                    "Add a city from the Dashboard to build an activity plan."
            );
        }
    }

    private void updateActivityWeights(Activity activity) {
        activityWeightLabel.setText(
                activity == null ? "" : activity.weightSummary()
        );
    }

    private void loadPlannerData(String city) {
        cancelActiveTask();

        forecastSlots = List.of();
        currentWeather = null;
        slotCardsContainer.getChildren().clear();

        plannerStatusLabel.setText(
                "Loading the forecast and current air quality for "
                        + city + "..."
        );
        bestTimeLabel.setText("Finding a suitable time...");
        bestScoreBadge.setText("—");
        bestScoreProgress.setProgress(0);
        bestWeatherLabel.setText("");
        bestExplanationLabel.setText("");
        currentAqiValueLabel.setText("Loading...");
        currentAqiStatusLabel.setText(
                "Current reading; not a forecast."
        );

        Task<PlannerData> task = new Task<>() {
            @Override
            protected PlannerData call() {
                List<ForecastSlot> slots =
                        weatherService.getForecastSlots(city);

                WeatherData current =
                        weatherService.getCurrentWeather(city);

                if (current.getAqi() < 1 || current.getAqi() > 5) {
                    throw new IllegalStateException(
                            "Current AQI is outside the expected 1–5 scale."
                    );
                }

                return new PlannerData(
                        city,
                        List.copyOf(slots),
                        current
                );
            }
        };

        activeTask = task;

        task.setOnSucceeded(event -> {
            if (activeTask != task) {
                return;
            }

            activeTask = null;

            PlannerData data = task.getValue();
            forecastSlots = data.forecastSlots();
            currentWeather = data.currentWeather();

            renderPlan();

            plannerStatusLabel.setText(
                    forecastSlots.size()
                            + " upcoming forecast slots loaded for "
                            + data.cityName()
                            + "."
            );
        });

        task.setOnFailed(event -> {
            if (activeTask != task) {
                return;
            }

            activeTask = null;

            Throwable error = task.getException();
            String message = error == null
                    || error.getMessage() == null
                    || error.getMessage().isBlank()
                    ? "Could not load forecast and current air-quality data."
                    : error.getMessage();

            plannerStatusLabel.setText(
                    "Could not build an activity plan: " + message
            );
            bestTimeLabel.setText("No recommendation available");
            currentAqiValueLabel.setText("Unavailable");
            currentAqiStatusLabel.setText(
                    "Check the connection and API settings, then try again."
            );

            ToastNotifications.show(
                    plannerStatusLabel,
                    message
            );
        });

        executor.execute(task);
    }

    private void renderPlan() {
        Activity activity = activitySelector.getValue();

        if (activity == null || currentWeather == null
                || forecastSlots.isEmpty()) {
            return;
        }

        int currentAqi = currentWeather.getAqi();

        List<ScoredSlot> rankedSlots = forecastSlots.stream()
                .map(slot -> new ScoredSlot(
                        slot,
                        ActivitySuitabilityScorer.score(
                                activity,
                                slot,
                                currentAqi
                        )
                ))
                .sorted(
                        Comparator
                                .comparingInt(
                                        (ScoredSlot option) ->
                                                option.score().total()
                                )
                                .reversed()
                                .thenComparing(
                                        option -> option.slot().getDateTime()
                                )
                )
                .toList();

        ScoredSlot best = rankedSlots.get(0);
        updateBestRecommendation(best, currentAqi);
        updateCurrentAqi(currentAqi);

        List<ScoredSlot> nextSlots = forecastSlots.stream()
                .sorted(Comparator.comparing(ForecastSlot::getDateTime))
                .limit(DISPLAYED_UPCOMING_SLOTS)
                .map(slot -> new ScoredSlot(
                        slot,
                        ActivitySuitabilityScorer.score(
                                activity,
                                slot,
                                currentAqi
                        )
                ))
                .toList();

        slotCardsContainer.getChildren().setAll(
                nextSlots.stream()
                        .map(option -> createSlotCard(
                                option,
                                option.slot() == best.slot()
                        ))
                        .toList()
        );
    }

    private void updateBestRecommendation(
            ScoredSlot best,
            int currentAqi
    ) {
        ForecastSlot slot = best.slot();
        Score score = best.score();

        bestTimeLabel.setText(
                SLOT_TIME_FORMAT.format(slot.getDateTime())
        );
        setScoreBadge(bestScoreBadge, score.total());
        bestScoreProgress.setProgress(score.total() / 100.0);

        bestWeatherLabel.setText(
                capitalize(slot.getDescription())
                        + " · "
                        + TemperatureStyler.formatOneDecimal(
                                slot.getTemperatureCelsius()
                        )
                        + " · Wind "
                        + formatOneDecimal(slot.getWindSpeedKmh())
                        + " km/h · Precipitation "
                        + slot.getPrecipitationProbability()
                        + "%"
        );

        bestExplanationLabel.setText(
                "Highest score in the available forecast: temperature "
                        + score.temperature()
                        + "/100, precipitation "
                        + score.precipitation()
                        + "/100, wind "
                        + score.wind()
                        + "/100, current AQI "
                        + score.airQuality()
                        + "/100. AQI "
                        + currentAqi
                        + "/5 is the latest reading, not a future measurement."
        );
    }

    private void updateCurrentAqi(int aqi) {
        currentAqiValueLabel.setText("AQI " + aqi + " / 5");
        currentAqiStatusLabel.setText(
                aqiLabel(aqi)
                        + " · Current conditions only; future AQI is unavailable."
        );
    }

    private HBox createSlotCard(
            ScoredSlot option,
            boolean isBest
    ) {
        ForecastSlot slot = option.slot();
        Score score = option.score();

        VBox details = new VBox(5);
        details.setMaxWidth(Double.MAX_VALUE);

        Label time = new Label(
                SLOT_TIME_FORMAT.format(slot.getDateTime())
        );
        time.getStyleClass().add("activity-slot-time");

        Label description = new Label(
                capitalize(slot.getDescription())
        );
        description.getStyleClass().add("metric-value");

        Label weatherDetails = new Label(
                TemperatureStyler.formatOneDecimal(
                        slot.getTemperatureCelsius()
                )
                        + " · Wind "
                        + formatOneDecimal(slot.getWindSpeedKmh())
                        + " km/h · Precipitation "
                        + slot.getPrecipitationProbability()
                        + "%"
        );
        weatherDetails.getStyleClass().add("metric-hint");

        Label scoreDetails = new Label(
                "Suitability factors — temperature "
                        + score.temperature()
                        + ", precipitation "
                        + score.precipitation()
                        + ", wind "
                        + score.wind()
                        + ", current AQI "
                        + score.airQuality()
        );
        scoreDetails.getStyleClass().add("activity-factor-details");

        details.getChildren().setAll(
                time,
                description,
                weatherDetails,
                scoreDetails
        );

        Region spacer = new Region();
        HBox.setHgrow(details, Priority.ALWAYS);
        HBox.setHgrow(spacer, Priority.ALWAYS);

        VBox scoreColumn = new VBox(8);
        scoreColumn.setMinWidth(155);
        scoreColumn.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        Label scoreBadge = new Label();
        scoreBadge.getStyleClass().add("badge");
        setScoreBadge(scoreBadge, score.total());

        ProgressBar scoreProgress =
                new ProgressBar(score.total() / 100.0);
        scoreProgress.setMaxWidth(Double.MAX_VALUE);
        scoreProgress.getStyleClass().add("activity-score-progress");

        Label bestTag = new Label("TOP PICK");
        bestTag.getStyleClass().addAll("badge", "badge-blue");
        bestTag.setVisible(isBest);
        bestTag.setManaged(isBest);

        scoreColumn.getChildren().setAll(
                bestTag,
                scoreBadge,
                scoreProgress
        );

        HBox card = new HBox(18, details, spacer, scoreColumn);
        card.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().addAll("card", "activity-slot-card");
        CardHoverEffects.install(card);

        return card;
    }

    private static void setScoreBadge(Label badge, int score) {
        badge.getStyleClass().removeAll(
                "badge-green",
                "badge-yellow",
                "badge-red",
                "badge-blue"
        );

        badge.getStyleClass().add(
                score >= 75
                        ? "badge-green"
                        : score >= 50
                        ? "badge-yellow"
                        : "badge-red"
        );

        badge.setText(score + " / 100");
    }

    private static String aqiLabel(int aqi) {
        return switch (aqi) {
            case 1 -> "Good";
            case 2 -> "Fair";
            case 3 -> "Moderate";
            case 4 -> "Poor";
            case 5 -> "Very Poor";
            default -> "Unknown";
        };
    }

    private static String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "Conditions unavailable";
        }

        return value.substring(0, 1).toUpperCase(Locale.ENGLISH)
                + value.substring(1);
    }

    private static String formatOneDecimal(double value) {
        return String.format(Locale.ENGLISH, "%.1f", value);
    }

    private void cancelActiveTask() {
        if (activeTask != null && !activeTask.isDone()) {
            activeTask.cancel(true);
        }

        activeTask = null;
    }

    /** Reload the selected city after provider settings change. */
    public void refreshSelectedCity() {
        String city = citySelector.getValue();

        if (city != null && !city.isBlank()) {
            loadPlannerData(city);
        }
    }

    public void dispose() {
        cancelActiveTask();
        executor.shutdownNow();
    }

    private record PlannerData(
            String cityName,
            List<ForecastSlot> forecastSlots,
            WeatherData currentWeather
    ) {
    }

    private record ScoredSlot(
            ForecastSlot slot,
            Score score
    ) {
    }
}
