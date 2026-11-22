package com.atmosphere.controller;

import com.atmosphere.config.AppConfig;
import com.atmosphere.model.PersonalComfortScore;
import com.atmosphere.model.WeatherData;
import com.atmosphere.model.WeatherPreferenceProfile;
import com.atmosphere.service.CityRegistry;
import com.atmosphere.service.PersonalComfortScorer;
import com.atmosphere.service.WeatherService;
import com.atmosphere.util.CardHoverEffects;
import com.atmosphere.util.TemperatureStyler;
import com.atmosphere.util.ToastNotifications;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller for the City Comparison Studio.
 * Evaluates 2 to 4 cities side-by-side using Personal Weather DNA comfort scoring.
 */
public class ComparisonController {

    private static final int MIN_CITIES = 2;
    private static final int MAX_CITIES = 4;

    @FXML private TextField addCitySearchField;
    @FXML private Button addCityButton;
    @FXML private Label comparisonStatusLabel;

    @FXML private VBox recommendationBox;
    @FXML private Label winnerCityTitle;
    @FXML private Label winnerScoreBadge;
    @FXML private Label winnerExplanationLabel;
    @FXML private HBox winnerHighlightsBox;

    @FXML private HBox cityCardsContainer;
    @FXML private VBox metricMatrixContainer;

    private final WeatherService weatherService = new WeatherService();
    private final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "atmosphere-comparison-worker");
        t.setDaemon(true);
        return t;
    });

    private final List<String> comparedCities = new ArrayList<>(List.of("Bengaluru", "Mumbai", "Delhi"));
    private final Map<String, WeatherData> weatherCache = new LinkedHashMap<>();
    private final Map<String, String> cityErrors = new LinkedHashMap<>();
    private final Map<String, Boolean> loadingStates = new LinkedHashMap<>();
    private volatile boolean disposed;

    @FXML
    private void initialize() {
        addCitySearchField.setOnAction(event -> handleAddCityFromSearch());

        AppConfig.fahrenheitProperty().addListener((obs, oldVal, newVal) -> renderAll());
        AppConfig.weatherPreferenceProfileProperty().addListener((obs, oldVal, newVal) -> renderAll());

        // Initial load for default cities
        for (String city : comparedCities) {
            loadCityWeather(city);
        }

        renderAll();
    }

    @FXML
    private void handleAddCityFromSearch() {
        String city = addCitySearchField.getText() == null ? "" : addCitySearchField.getText().trim();
        if (city.isBlank()) {
            ToastNotifications.show(addCitySearchField, "Enter a city name to compare.");
            return;
        }

        if (comparedCities.size() >= MAX_CITIES) {
            ToastNotifications.show(addCitySearchField, "Maximum of 4 cities can be compared simultaneously.");
            return;
        }

        if (containsCityIgnoreCase(comparedCities, city)) {
            ToastNotifications.show(addCitySearchField, city + " is already in the comparison.");
            addCitySearchField.clear();
            return;
        }

        comparedCities.add(city);
        addCitySearchField.clear();
        loadCityWeather(city);
        renderAll();
    }

    private void loadCityWeather(String city) {
        loadingStates.put(city, true);
        cityErrors.remove(city);

        Task<WeatherData> task = new Task<>() {
            @Override
            protected WeatherData call() {
                return weatherService.getCurrentWeather(city);
            }
        };

        task.setOnSucceeded(e -> {
            WeatherData data = task.getValue();
            weatherCache.put(city, data);
            loadingStates.put(city, false);
            CityRegistry.register(city);
            renderAll();
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            String msg = ex != null && ex.getMessage() != null ? ex.getMessage() : "Failed to load weather data.";
            cityErrors.put(city, msg);
            loadingStates.put(city, false);
            renderAll();
        });

        executor.execute(task);
    }

    private void removeCity(String city) {
        if (comparedCities.size() <= MIN_CITIES) {
            ToastNotifications.show(cityCardsContainer, "A minimum of 2 cities is required for comparison.");
            return;
        }
        comparedCities.remove(city);
        cityErrors.remove(city);
        loadingStates.remove(city);
        renderAll();
    }

    /**
     * Re-renders the recommendation banner, hero cards, and comparative matrix.
     */
    public void renderAll() {
        renderHeroCards();
        renderRecommendationAndMatrix();
    }

    private void renderHeroCards() {
        cityCardsContainer.getChildren().clear();

        // Calculate rankings among loaded cities
        WeatherPreferenceProfile profile = AppConfig.getWeatherPreferenceProfile();
        String winningCity = findWinningCity(profile);

        for (String city : comparedCities) {
            VBox card = createCitySlotCard(city, winningCity, profile);
            HBox.setHgrow(card, Priority.ALWAYS);
            cityCardsContainer.getChildren().add(card);
        }
    }

    private VBox createCitySlotCard(String city, String winningCity, WeatherPreferenceProfile profile) {
        VBox card = new VBox(12);
        card.getStyleClass().addAll("card", "compare-card");
        card.setMinWidth(220);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setPrefWidth(260);

        boolean isWinner = city.equalsIgnoreCase(winningCity);
        if (isWinner) {
            card.getStyleClass().add("winner");
        }

        // Header: City Selector + Remove button
        HBox headerRow = new HBox(8);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        ComboBox<String> selector = new ComboBox<>(CityRegistry.getCities());
        selector.setValue(city);
        selector.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(selector, Priority.ALWAYS);
        selector.getStyleClass().add("search-field");
        selector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(city) && !containsCityIgnoreCase(comparedCities, newVal)) {
                int index = comparedCities.indexOf(city);
                if (index >= 0) {
                    comparedCities.set(index, newVal);
                    loadCityWeather(newVal);
                    renderAll();
                }
            }
        });

        headerRow.getChildren().add(selector);

        if (comparedCities.size() > MIN_CITIES) {
            Button removeBtn = new Button("✕");
            removeBtn.getStyleClass().add("secondary-button");
            removeBtn.setStyle("-fx-padding: 4px 8px; -fx-font-size: 11px;");
            removeBtn.setOnAction(e -> removeCity(city));
            headerRow.getChildren().add(removeBtn);
        }

        card.getChildren().add(headerRow);

        // State: Loading, Error, or Weather Content
        boolean isLoading = Boolean.TRUE.equals(loadingStates.get(city));
        String errorMsg = cityErrors.get(city);

        if (isLoading) {
            Label loadingLbl = new Label("Observatory fetching data for " + city + "…");
            loadingLbl.getStyleClass().add("status-text");
            loadingLbl.setMinHeight(160);
            loadingLbl.setAlignment(Pos.CENTER);
            card.getChildren().add(loadingLbl);
            return card;
        }

        if (errorMsg != null) {
            VBox errorBox = new VBox(8);
            errorBox.setAlignment(Pos.CENTER);
            errorBox.setMinHeight(160);
            Label errTitle = new Label("Could not load " + city);
            errTitle.getStyleClass().add("stat-label");
            Label errDetails = new Label(errorMsg);
            errDetails.getStyleClass().add("metric-hint");
            errDetails.setWrapText(true);
            Button retryBtn = new Button("Retry");
            retryBtn.getStyleClass().add("secondary-button");
            retryBtn.setOnAction(e -> loadCityWeather(city));
            errorBox.getChildren().addAll(errTitle, errDetails, retryBtn);
            card.getChildren().add(errorBox);
            return card;
        }

        WeatherData weather = weatherCache.get(city);
        if (weather == null) {
            Label waiting = new Label("Awaiting data…");
            waiting.getStyleClass().add("status-text");
            card.getChildren().add(waiting);
            return card;
        }

        // Winner badge if ranked #1
        if (isWinner) {
            Label bestMatchBadge = new Label("★ BEST MATCH");
            bestMatchBadge.getStyleClass().addAll("badge", "badge-optimal");
            card.getChildren().add(bestMatchBadge);
        }

        // Temperature & Emoji Row
        HBox tempRow = new HBox(10);
        tempRow.setAlignment(Pos.CENTER_LEFT);

        Label emoji = new Label(WeatherService.getConditionEmoji(weather.getIcon()));
        emoji.setStyle("-fx-font-size: 28px;");

        Label tempLabel = new Label();
        tempLabel.getStyleClass().add("temperature-large");
        tempLabel.setStyle("-fx-font-size: 34px;");
        TemperatureStyler.apply(tempLabel, weather.getTemperature());

        VBox tempDetails = new VBox(2);
        Label feels = new Label();
        feels.getStyleClass().add("feels-like");
        TemperatureStyler.apply(feels, weather.getFeelsLike(), "Feels ");

        Label cond = new Label(weather.getDescription());
        cond.getStyleClass().add("metric-hint");
        tempDetails.getChildren().addAll(feels, cond);

        tempRow.getChildren().addAll(emoji, tempLabel, tempDetails);
        card.getChildren().add(tempRow);

        // Personal Comfort Score Badge
        PersonalComfortScore comfortScore = PersonalComfortScorer.score(profile, weather);
        HBox scoreRow = new HBox(8);
        scoreRow.setAlignment(Pos.CENTER_LEFT);

        Label scoreBadge = new Label("DNA Score: " + comfortScore.totalScore() + "/100 · " + comfortScore.category().label());
        scoreBadge.getStyleClass().addAll("badge", comfortScore.category().badgeStyleClass());
        scoreRow.getChildren().add(scoreBadge);
        card.getChildren().add(scoreRow);

        // Key Metrics Summary
        VBox metricsBox = new VBox(6);
        metricsBox.getChildren().addAll(
                createMiniMetricRow("HUMIDITY", String.format(Locale.ENGLISH, "%.0f%%", weather.getHumidity())),
                createMiniMetricRow("WIND", String.format(Locale.ENGLISH, "%.1f km/h", weather.getWindSpeed())),
                createMiniMetricRow("AIR QUALITY", "AQI " + weather.getAqi() + " (" + weather.getAqiLabel() + ")")
        );
        card.getChildren().add(metricsBox);

        CardHoverEffects.install(card);
        return card;
    }

    private HBox createMiniMetricRow(String label, String value) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        Label l = new Label(label);
        l.getStyleClass().add("stat-label");
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        Label v = new Label(value);
        v.getStyleClass().add("stat-value");
        row.getChildren().addAll(l, r, v);
        return row;
    }

    private void renderRecommendationAndMatrix() {
        WeatherPreferenceProfile profile = AppConfig.getWeatherPreferenceProfile();

        List<CityScoreEntry> entries = new ArrayList<>();
        for (String city : comparedCities) {
            WeatherData weather = weatherCache.get(city);
            if (weather != null) {
                PersonalComfortScore score = PersonalComfortScorer.score(profile, weather);
                entries.add(new CityScoreEntry(city, weather, score));
            }
        }

        if (entries.isEmpty()) {
            recommendationBox.setVisible(false);
            recommendationBox.setManaged(false);
            metricMatrixContainer.getChildren().clear();
            return;
        }

        recommendationBox.setVisible(true);
        recommendationBox.setManaged(true);

        entries.sort(Comparator.comparingInt(e -> -e.score.totalScore()));
        CityScoreEntry winner = entries.get(0);

        winnerCityTitle.setText("Optimal Match: " + winner.city);
        winnerScoreBadge.setText(winner.score.totalScore() + "/100 · " + winner.score.category().label());
        winnerScoreBadge.getStyleClass().removeAll("badge-optimal", "badge-favorable", "badge-moderate", "badge-suboptimal", "badge-harsh");
        winnerScoreBadge.getStyleClass().add(winner.score.category().badgeStyleClass());

        // Transparent narrative explaining why #1 won
        StringBuilder narrative = new StringBuilder();
        narrative.append(winner.city).append(" ranks #1 with a comfort score of ")
                .append(winner.score.totalScore()).append("/100. ")
                .append(winner.score.explanation());

        if (entries.size() > 1) {
            CityScoreEntry runnerUp = entries.get(1);
            int margin = winner.score.totalScore() - runnerUp.score.totalScore();
            narrative.append(String.format(
                    Locale.ENGLISH,
                    " It outperforms %s by +%d points, where %s.",
                    runnerUp.city,
                    margin,
                    explainRunnerUpDeficit(winner, runnerUp)
            ));
        }

        winnerExplanationLabel.setText(narrative.toString());

        // Highlights
        winnerHighlightsBox.getChildren().clear();
        for (String hl : winner.score.factorHighlights()) {
            Label pill = new Label(hl);
            pill.getStyleClass().addAll("badge", "badge-optimal");
            winnerHighlightsBox.getChildren().add(pill);
        }

        renderMatrixRows(entries);
    }

    private String explainRunnerUpDeficit(CityScoreEntry winner, CityScoreEntry runnerUp) {
        if (runnerUp.weather.getAqi() > winner.weather.getAqi()) {
            return runnerUp.city + " suffers from degraded air quality (AQI " + runnerUp.weather.getAqi() + " vs " + winner.weather.getAqi() + ")";
        }
        if (Math.abs(runnerUp.weather.getHumidity() - 50.0) > Math.abs(winner.weather.getHumidity() - 50.0)) {
            return runnerUp.city + " has more extreme humidity (" + String.format(Locale.ENGLISH, "%.0f%%", runnerUp.weather.getHumidity()) + ")";
        }
        if (runnerUp.weather.getWindSpeed() > winner.weather.getWindSpeed()) {
            return runnerUp.city + " experiences higher wind speeds (" + String.format(Locale.ENGLISH, "%.1f km/h", runnerUp.weather.getWindSpeed()) + ")";
        }
        return runnerUp.city + " has temperature conditions further from your personal comfort range";
    }

    private void renderMatrixRows(List<CityScoreEntry> entries) {
        metricMatrixContainer.getChildren().clear();

        // 1. Personal Comfort Score row
        metricMatrixContainer.getChildren().add(
                createMatrixComparisonBarRow("COMFORT SCORE", entries, e -> (double) e.score.totalScore(), "%.0f pts", true)
        );

        // 2. Temperature row
        metricMatrixContainer.getChildren().add(
                createMatrixComparisonBarRow("TEMPERATURE", entries, e -> e.weather.getTemperature(), "%.1f°C", false)
        );

        // 3. Humidity row
        metricMatrixContainer.getChildren().add(
                createMatrixComparisonBarRow("HUMIDITY", entries, e -> e.weather.getHumidity(), "%.0f%%", false)
        );

        // 4. Wind Speed row
        metricMatrixContainer.getChildren().add(
                createMatrixComparisonBarRow("WIND SPEED", entries, e -> e.weather.getWindSpeed(), "%.1f km/h", false)
        );

        // 5. Air Quality row
        metricMatrixContainer.getChildren().add(
                createMatrixComparisonBarRow("AIR QUALITY", entries, e -> (double) e.weather.getAqi(), "AQI %.0f", false)
        );
    }

    private HBox createMatrixComparisonBarRow(
            String metricTitle,
            List<CityScoreEntry> entries,
            java.util.function.ToDoubleFunction<CityScoreEntry> valueExtractor,
            String formatStr,
            boolean isScore
    ) {
        HBox row = new HBox(16);
        row.getStyleClass().add("matrix-row");
        row.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(metricTitle);
        titleLabel.setPrefWidth(130);
        titleLabel.getStyleClass().add("matrix-label");
        row.getChildren().add(titleLabel);

        double maxVal = entries.stream().mapToDouble(valueExtractor).max().orElse(100.0);
        double minVal = entries.stream().mapToDouble(valueExtractor).min().orElse(0.0);

        HBox barContainer = new HBox(14);
        barContainer.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(barContainer, Priority.ALWAYS);

        for (CityScoreEntry entry : entries) {
            double val = valueExtractor.applyAsDouble(entry);
            VBox col = new VBox(3);
            col.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(col, Priority.ALWAYS);

            HBox labelRow = new HBox(6);
            labelRow.setAlignment(Pos.CENTER_LEFT);

            Label cityName = new Label(entry.city);
            cityName.getStyleClass().add("status-text");

            Label valLabel = new Label(String.format(Locale.ENGLISH, formatStr, val));
            valLabel.getStyleClass().add("stat-value");

            labelRow.getChildren().addAll(cityName, valLabel);

            // Add Difference Indicator tag
            if (isScore && val == maxVal && entries.size() > 1) {
                Label bestPill = new Label("Highest");
                bestPill.getStyleClass().add("diff-pill-best");
                labelRow.getChildren().add(bestPill);
            } else if (isScore && val == minVal && entries.size() > 1) {
                Label lowPill = new Label("Lowest");
                lowPill.getStyleClass().add("diff-pill-worst");
                labelRow.getChildren().add(lowPill);
            }

            ProgressBar bar = new ProgressBar(isScore ? val / 100.0 : (maxVal > 0 ? val / (maxVal * 1.25) : 0));
            bar.setMaxWidth(Double.MAX_VALUE);
            bar.getStyleClass().add("pollutant-progress");

            col.getChildren().addAll(labelRow, bar);
            barContainer.getChildren().add(col);
        }

        row.getChildren().add(barContainer);
        return row;
    }

    private String findWinningCity(WeatherPreferenceProfile profile) {
        String bestCity = null;
        int maxScore = -1;
        for (String city : comparedCities) {
            WeatherData weather = weatherCache.get(city);
            if (weather != null) {
                int s = PersonalComfortScorer.score(profile, weather).totalScore();
                if (s > maxScore) {
                    maxScore = s;
                    bestCity = city;
                }
            }
        }
        return bestCity;
    }

    private boolean containsCityIgnoreCase(List<String> list, String candidate) {
        return list.stream().anyMatch(c -> c.equalsIgnoreCase(candidate.trim()));
    }

    public void refreshSelectedCity() {
        for (String city : comparedCities) {
            loadCityWeather(city);
        }
    }

    public void dispose() {
        disposed = true;
        executor.shutdownNow();
    }

    private record CityScoreEntry(String city, WeatherData weather, PersonalComfortScore score) {
    }
}
