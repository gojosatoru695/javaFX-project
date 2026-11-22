package com.atmosphere.controller;

import com.atmosphere.config.AppConfig;
import com.atmosphere.model.PersonalComfortScore;
import com.atmosphere.model.WeatherData;
import com.atmosphere.model.WeatherPreferenceProfile;
import com.atmosphere.service.CityRegistry;
import com.atmosphere.service.PersonalComfortScorer;
import com.atmosphere.service.WeatherService;
import com.atmosphere.util.ToastNotifications;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller for the Personal Weather DNA configuration and real-time simulator screen.
 */
public class WeatherDnaController {

    @FXML private Slider minTempSlider;
    @FXML private Slider maxTempSlider;
    @FXML private Label tempRangeValueLabel;

    @FXML private Slider minHumiditySlider;
    @FXML private Slider maxHumiditySlider;
    @FXML private Label humidityRangeValueLabel;

    @FXML private Slider maxWindSlider;
    @FXML private Label maxWindValueLabel;

    @FXML private ComboBox<WeatherPreferenceProfile.AirQualitySensitivity> aqiSensitivityComboBox;
    @FXML private ComboBox<String> evalCitySelector;

    @FXML private Button saveButton;
    @FXML private Button resetDefaultsButton;
    @FXML private Label statusFeedbackLabel;

    @FXML private Label overallScoreLabel;
    @FXML private Label categoryBadge;
    @FXML private Label simulatedCityConditionLabel;

    @FXML private Label tempFactorLabel;
    @FXML private ProgressBar tempFactorProgress;
    @FXML private Label aqiFactorLabel;
    @FXML private ProgressBar aqiFactorProgress;
    @FXML private Label windFactorLabel;
    @FXML private ProgressBar windFactorProgress;
    @FXML private Label humidityFactorLabel;
    @FXML private ProgressBar humidityFactorProgress;

    @FXML private Label explanationTextLabel;
    @FXML private HBox factorHighlightsBox;

    private final WeatherService weatherService = new WeatherService();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "atmosphere-dna-worker");
        t.setDaemon(true);
        return t;
    });

    private WeatherPreferenceProfile currentProfile;
    private WeatherData simulatedWeatherData;
    private boolean updatingControls;
    private boolean disposed;

    @FXML
    private void initialize() {
        currentProfile = AppConfig.getWeatherPreferenceProfile();

        aqiSensitivityComboBox.setItems(
                FXCollections.observableArrayList(WeatherPreferenceProfile.AirQualitySensitivity.values())
        );

        setupCitySelector();
        populateControlsFromProfile(currentProfile);
        setupListeners();

        // Create initial fallback weather for instant evaluation
        simulatedWeatherData = createDefaultSampleWeather("Bengaluru");
        updateLiveSimulation();

        // If registered cities exist, attempt to load live weather for the selected city
        String selectedCity = evalCitySelector.getValue();
        if (selectedCity != null && !selectedCity.isBlank()) {
            loadCityWeatherForSimulation(selectedCity);
        }
    }

    private void setupCitySelector() {
        evalCitySelector.setItems(CityRegistry.getCities());
        if (!evalCitySelector.getItems().isEmpty()) {
            evalCitySelector.getSelectionModel().selectFirst();
        }
        evalCitySelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isBlank()) {
                loadCityWeatherForSimulation(newVal);
            }
        });
    }

    private void setupListeners() {
        minTempSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (updatingControls) return;
            if (newVal.doubleValue() > maxTempSlider.getValue()) {
                maxTempSlider.setValue(newVal.doubleValue());
            }
            syncProfileFromControls();
            updateLiveSimulation();
        });

        maxTempSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (updatingControls) return;
            if (newVal.doubleValue() < minTempSlider.getValue()) {
                minTempSlider.setValue(newVal.doubleValue());
            }
            syncProfileFromControls();
            updateLiveSimulation();
        });

        minHumiditySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (updatingControls) return;
            if (newVal.doubleValue() > maxHumiditySlider.getValue()) {
                maxHumiditySlider.setValue(newVal.doubleValue());
            }
            syncProfileFromControls();
            updateLiveSimulation();
        });

        maxHumiditySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (updatingControls) return;
            if (newVal.doubleValue() < minHumiditySlider.getValue()) {
                minHumiditySlider.setValue(newVal.doubleValue());
            }
            syncProfileFromControls();
            updateLiveSimulation();
        });

        maxWindSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (updatingControls) return;
            syncProfileFromControls();
            updateLiveSimulation();
        });

        aqiSensitivityComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (updatingControls) return;
            syncProfileFromControls();
            updateLiveSimulation();
        });
    }

    private void populateControlsFromProfile(WeatherPreferenceProfile profile) {
        updatingControls = true;
        try {
            minTempSlider.setValue(profile.getMinTemperatureCelsius());
            maxTempSlider.setValue(profile.getMaxTemperatureCelsius());
            minHumiditySlider.setValue(profile.getMinHumidityPercent());
            maxHumiditySlider.setValue(profile.getMaxHumidityPercent());
            maxWindSlider.setValue(profile.getMaxComfortableWindKmh());
            aqiSensitivityComboBox.setValue(profile.getAirQualitySensitivity());
            updateControlLabels(profile);
        } finally {
            updatingControls = false;
        }
    }

    private void updateControlLabels(WeatherPreferenceProfile profile) {
        tempRangeValueLabel.setText(String.format(Locale.ENGLISH, "%.1f°C – %.1f°C",
                profile.getMinTemperatureCelsius(), profile.getMaxTemperatureCelsius()));
        humidityRangeValueLabel.setText(String.format(Locale.ENGLISH, "%.0f%% – %.0f%%",
                profile.getMinHumidityPercent(), profile.getMaxHumidityPercent()));
        maxWindValueLabel.setText(String.format(Locale.ENGLISH, "%.1f km/h",
                profile.getMaxComfortableWindKmh()));
    }

    private void syncProfileFromControls() {
        currentProfile.setTemperatureRange(
                Math.round(minTempSlider.getValue() * 10.0) / 10.0,
                Math.round(maxTempSlider.getValue() * 10.0) / 10.0
        );
        currentProfile.setHumidityRange(
                Math.round(minHumiditySlider.getValue()),
                Math.round(maxHumiditySlider.getValue())
        );
        currentProfile.setMaxComfortableWindKmh(
                Math.round(maxWindSlider.getValue() * 10.0) / 10.0
        );
        currentProfile.setAirQualitySensitivity(aqiSensitivityComboBox.getValue());
        updateControlLabels(currentProfile);
    }

    private void updateLiveSimulation() {
        if (simulatedWeatherData == null) {
            return;
        }

        PersonalComfortScore score = PersonalComfortScorer.score(currentProfile, simulatedWeatherData);

        overallScoreLabel.setText(String.valueOf(score.totalScore()));

        categoryBadge.setText(score.category().label().toUpperCase(Locale.ENGLISH));
        categoryBadge.getStyleClass().removeAll("badge-optimal", "badge-favorable", "badge-moderate", "badge-suboptimal", "badge-harsh");
        categoryBadge.getStyleClass().add(score.category().badgeStyleClass());

        simulatedCityConditionLabel.setText(String.format(
                Locale.ENGLISH,
                "%s · %.1f°C · Humidity %.0f%% · Wind %.1f km/h · AQI %d (%s)",
                simulatedWeatherData.getCityName(),
                simulatedWeatherData.getTemperature(),
                simulatedWeatherData.getHumidity(),
                simulatedWeatherData.getWindSpeed(),
                simulatedWeatherData.getAqi(),
                simulatedWeatherData.getAqiLabel()
        ));

        // Update Factor bars & labels
        tempFactorProgress.setProgress(score.temperatureScore() / 100.0);
        tempFactorLabel.setText(String.format(Locale.ENGLISH, "%.1f / 40 pts (%d%%)",
                score.temperatureContribution(), score.temperatureScore()));

        aqiFactorProgress.setProgress(score.airQualityScore() / 100.0);
        aqiFactorLabel.setText(String.format(Locale.ENGLISH, "%.1f / 25 pts (%d%%)",
                score.airQualityContribution(), score.airQualityScore()));

        windFactorProgress.setProgress(score.windScore() / 100.0);
        windFactorLabel.setText(String.format(Locale.ENGLISH, "%.1f / 20 pts (%d%%)",
                score.windContribution(), score.windScore()));

        humidityFactorProgress.setProgress(score.humidityScore() / 100.0);
        humidityFactorLabel.setText(String.format(Locale.ENGLISH, "%.1f / 15 pts (%d%%)",
                score.humidityContribution(), score.humidityScore()));

        explanationTextLabel.setText(score.explanation());

        // Update Highlight Pills
        factorHighlightsBox.getChildren().clear();
        for (String highlight : score.factorHighlights()) {
            Label pill = new Label(highlight);
            pill.getStyleClass().addAll("badge", "badge-optimal");
            factorHighlightsBox.getChildren().add(pill);
        }
    }

    private void loadCityWeatherForSimulation(String city) {
        statusFeedbackLabel.setText("Fetching live weather for " + city + " simulator…");

        Task<WeatherData> task = new Task<>() {
            @Override
            protected WeatherData call() {
                return weatherService.getCurrentWeather(city);
            }
        };

        task.setOnSucceeded(e -> {
            simulatedWeatherData = task.getValue();
            updateLiveSimulation();
            statusFeedbackLabel.setText("Live weather updated for " + city + ". Scoring active.");
        });

        task.setOnFailed(e -> {
            // Keep fallback on error
            simulatedWeatherData = createDefaultSampleWeather(city);
            updateLiveSimulation();
            statusFeedbackLabel.setText("Using baseline profile for " + city + " (API unavailable).");
        });

        executor.execute(task);
    }

    @FXML
    private void handleSave() {
        syncProfileFromControls();
        try {
            AppConfig.savePreferences(currentProfile);
            statusFeedbackLabel.setText("Personal Weather DNA saved successfully to " + AppConfig.getConfigFile());
            ToastNotifications.show(saveButton, "Weather DNA profile saved!");
        } catch (IOException ex) {
            statusFeedbackLabel.setText("Error saving profile: " + ex.getMessage());
            ToastNotifications.show(saveButton, "Could not save profile: " + ex.getMessage());
        }
    }

    @FXML
    private void handleResetDefaults() {
        currentProfile = WeatherPreferenceProfile.defaultProfile();
        populateControlsFromProfile(currentProfile);
        updateLiveSimulation();
        statusFeedbackLabel.setText("Reset to default thresholds. Click 'Save DNA Profile' to persist.");
    }

    private WeatherData createDefaultSampleWeather(String cityName) {
        WeatherData data = new WeatherData();
        data.setCityName(cityName == null ? "Bengaluru" : cityName);
        data.setTemperature(22.4);
        data.setFeelsLike(22.0);
        data.setHumidity(50.0);
        data.setWindSpeed(8.0);
        data.setAqi(1);
        data.setAqiLabel("Good");
        data.setDescription("Clear sky");
        return data;
    }

    public void dispose() {
        disposed = true;
        executor.shutdownNow();
    }
}
