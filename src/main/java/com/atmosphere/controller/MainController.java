
package com.atmosphere.controller;

import com.atmosphere.config.AppConfig;
import com.atmosphere.util.CardHoverEffects;
import com.atmosphere.util.ToastNotifications;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;

import javafx.geometry.Insets;

import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;

import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;

import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Application shell with lazy, cached navigation and settings.
 */
public class MainController {

    private static final Duration NAVIGATION_FADE_DURATION =
            Duration.millis(200);

    private static final DateTimeFormatter CLOCK_FORMAT =
            DateTimeFormatter.ofPattern(
                    "HH:mm:ss '·' EEE, dd MMM",
                    Locale.ENGLISH
            );

    private static final Map<String, String> SCREEN_FILES = Map.of(
            "dashboard", "dashboard.fxml",
            "forecast", "forecast.fxml",
            "activity-planner", "activity-planner.fxml",
            "alerts", "alerts.fxml",
            "weather-history", "weather-history.fxml",
            "air-quality", "aqi.fxml",
            "ai-insights", "insights.fxml"
    );

    @FXML private StackPane contentArea;

    @FXML private Button dashboardButton;
    @FXML private Button forecastButton;
    @FXML private Button activityPlannerButton;
    @FXML private Button alertsButton;
    @FXML private Button weatherHistoryButton;
    @FXML private Button airQualityButton;
    @FXML private Button aiInsightsButton;

    @FXML private Label clockLabel;

    // Screens are loaded once and cached after their first navigation.
    private final Map<String, Parent> screens =
            new LinkedHashMap<>();

    private final Map<String, Button> navigationButtons =
            new LinkedHashMap<>();

    private final Map<String, Object> screenControllers =
            new LinkedHashMap<>();

    private Timeline clockTimeline;
    private FadeTransition activeFade;
    private boolean disposed;

    @FXML
    private void initialize() throws IOException {

        registerNavigationButtons();

        updateClock();
        startClock();

        // Only the dashboard initializes during application startup.
        // Other screens load lazily on their first navigation.
        loadScreen("dashboard");

        showScreen("dashboard");
    }

    private void registerNavigationButtons() {

        navigationButtons.put("dashboard", dashboardButton);
        navigationButtons.put("forecast", forecastButton);
        navigationButtons.put("activity-planner", activityPlannerButton);
        navigationButtons.put("alerts", alertsButton);
        navigationButtons.put("weather-history", weatherHistoryButton);
        navigationButtons.put("air-quality", airQualityButton);
        navigationButtons.put("ai-insights", aiInsightsButton);
    }

    private void loadScreen(String key) throws IOException {

        if (screens.containsKey(key)) {
            return;
        }

        String fileName = SCREEN_FILES.get(key);

        if (fileName == null) {
            throw new IllegalArgumentException(
                    "Unknown screen: " + key
            );
        }

        String resourcePath =
                "/com/atmosphere/fxml/" + fileName;

        URL resource = getClass().getResource(resourcePath);

        if (resource == null) {
            throw new IOException(
                    "Unable to load screen: " + resourcePath
            );
        }

        FXMLLoader loader = new FXMLLoader(resource);

        Parent screen = loader.load();

        screens.put(key, screen);

        screenControllers.put(
                key,
                loader.getController()
        );

        Object controller = loader.getController();
        if (controller instanceof DashboardController dashboardController) {
            dashboardController.setWeatherUpdatedListener(
                    this::scheduleAlertsRefreshAfterWeatherUpdate
            );
            dashboardController.setWeatherHistorySavedListener(
                    this::scheduleWeatherHistoryRefreshAfterSnapshot
            );
        }

        CardHoverEffects.installRecursively(screen);
    }

    // Navigation handlers.

    @FXML
    private void showDashboard() {
        navigate("dashboard");
    }

    @FXML
    private void showForecast() {
        navigate("forecast");
    }

    @FXML
    private void showActivityPlanner() {
        navigate("activity-planner");
    }

    @FXML
    private void showAlerts() {
        navigate("alerts");
    }

    @FXML
    private void showWeatherHistory() {
        navigate("weather-history");
    }

    @FXML
    private void showAirQuality() {
        navigate("air-quality");
    }

    @FXML
    private void showAiInsights() {
        navigate("ai-insights");
    }

    private void navigate(String screenKey) {

        try {

            loadScreen(screenKey);

            showScreen(screenKey);

        } catch (IOException | RuntimeException exception) {

            ToastNotifications.show(
                    contentArea,
                    "Could not open "
                            + screenKey
                            + ": "
                            + exception.getMessage()
            );
        }
    }

    private void showScreen(String screenKey) {

        Parent screen = screens.get(screenKey);

        Button selectedButton =
                navigationButtons.get(screenKey);

        if (screen == null || selectedButton == null) {
            throw new IllegalArgumentException(
                    "Unknown AtmoSphere screen: " + screenKey
            );
        }

        if (activeFade != null) {
            activeFade.stop();
        }

        navigationButtons.values().forEach(
                button -> button.getStyleClass().remove("active")
        );

        if (!selectedButton.getStyleClass().contains("active")) {
            selectedButton.getStyleClass().add("active");
        }

        contentArea.getChildren().setAll(screen);

        screen.setOpacity(0.0);

        activeFade = new FadeTransition(
                NAVIGATION_FADE_DURATION,
                screen
        );

        activeFade.setFromValue(0.0);
        activeFade.setToValue(1.0);

        activeFade.play();

        if ("alerts".equals(screenKey)) {
            Object controller = screenControllers.get("alerts");
            if (controller instanceof AlertsController alertsController) {
                alertsController.refreshAlerts();
            }
        }

        if ("weather-history".equals(screenKey)) {
            Object controller = screenControllers.get("weather-history");
            if (controller instanceof WeatherHistoryController historyController) {
                historyController.refreshSelectedCity();
            }
        }
    }

    private void scheduleAlertsRefreshAfterWeatherUpdate() {
        Object controller = screenControllers.get("alerts");
        if (controller instanceof AlertsController alertsController) {
            alertsController.scheduleRefreshAfterWeatherUpdate();
        }
    }

    private void scheduleWeatherHistoryRefreshAfterSnapshot() {
        Object controller = screenControllers.get("weather-history");
        if (controller instanceof WeatherHistoryController historyController) {
            historyController.scheduleRefreshAfterWeatherUpdate();
        }
    }

    // -----------------------------------------------------------------
    // SETTINGS DIALOG
    // -----------------------------------------------------------------

    @FXML
    private void openSettings() {

        PasswordField weatherKeyField =
                new PasswordField();

        weatherKeyField.setText(
                AppConfig.getOpenWeatherApiKey()
        );

        weatherKeyField.setPromptText(
                "OpenWeatherMap API key"
        );

        weatherKeyField.setPrefColumnCount(30);

        weatherKeyField.getStyleClass().add(
                "search-field"
        );

        PasswordField geminiKeyField =
                new PasswordField();

        geminiKeyField.setText(
                AppConfig.getGeminiApiKey()
        );

        geminiKeyField.setPromptText(
                "Gemini API key"
        );

        geminiKeyField.setPrefColumnCount(30);

        geminiKeyField.getStyleClass().add(
                "search-field"
        );

        // Temperature unit selection.

        ToggleGroup units = new ToggleGroup();

        RadioButton celsiusButton =
                new RadioButton("Celsius (°C)");

        RadioButton fahrenheitButton =
                new RadioButton("Fahrenheit (°F)");

        celsiusButton.setToggleGroup(units);
        fahrenheitButton.setToggleGroup(units);

        if (AppConfig.isFahrenheit()) {
            fahrenheitButton.setSelected(true);
        } else {
            celsiusButton.setSelected(true);
        }

        // Settings form.

        GridPane form = new GridPane();

        form.setHgap(14);
        form.setVgap(14);

        form.setPadding(
                new Insets(8, 4, 8, 4)
        );

        Label weatherLabel =
                new Label("OpenWeatherMap API key");

        Label geminiLabel =
                new Label("Gemini API key");

        Label unitLabel =
                new Label("Temperature unit");

        weatherLabel.getStyleClass().add("settings-label");
        geminiLabel.getStyleClass().add("settings-label");
        unitLabel.getStyleClass().add("settings-label");

        form.add(weatherLabel, 0, 0);
        form.add(weatherKeyField, 1, 0);

        form.add(geminiLabel, 0, 1);
        form.add(geminiKeyField, 1, 1);

        form.add(unitLabel, 0, 2);

        javafx.scene.layout.HBox unitOptions =
                new javafx.scene.layout.HBox(
                        16,
                        celsiusButton,
                        fahrenheitButton
                );

        form.add(unitOptions, 1, 2);

        Dialog<ButtonType> dialog =
                new Dialog<>();

        dialog.setTitle("AtmoSphere Settings");

        dialog.initOwner(
                contentArea.getScene().getWindow()
        );

        dialog.setHeaderText(
                "Weather providers & display preferences"
        );

        dialog.getDialogPane().setContent(form);

        dialog.getDialogPane()
                .getStyleClass()
                .add("settings-dialog");

        URL stylesheet = getClass().getResource(
                "/com/atmosphere/css/theme.css"
        );

        if (stylesheet != null) {
            dialog.getDialogPane()
                    .getStylesheets()
                    .add(stylesheet.toExternalForm());
        }

        ButtonType saveButton = new ButtonType(
                "Save",
                ButtonBar.ButtonData.OK_DONE
        );

        ButtonType cancelButton = new ButtonType(
                "Cancel",
                ButtonBar.ButtonData.CANCEL_CLOSE
        );

        dialog.getDialogPane()
                .getButtonTypes()
                .addAll(saveButton, cancelButton);

        dialog.setResultConverter(button -> button);

        Optional<ButtonType> result =
                dialog.showAndWait();

        if (result.isPresent()
                && result.get() == saveButton) {

            try {

                AppConfig.save(
                        weatherKeyField.getText(),
                        geminiKeyField.getText(),
                        fahrenheitButton.isSelected()
                );

                refreshLoadedScreensAfterSettingsSave();

                ToastNotifications.show(
                        contentArea,
                        "Settings saved to "
                                + AppConfig.getConfigFile()
                                + "."
                );

            } catch (IOException exception) {

                ToastNotifications.show(
                        contentArea,
                        "Could not save settings: "
                                + exception.getMessage()
                );
            }
        }
    }

    /**
     * Refreshes only screens that have already been opened.
     * Never loads a screen simply because settings were saved.
     */
    private void refreshLoadedScreensAfterSettingsSave() {

        Object dashboard =
                screenControllers.get("dashboard");

        if (dashboard instanceof DashboardController controller) {
            controller.refreshAll();
        }

        Object forecast =
                screenControllers.get("forecast");

        if (forecast instanceof ForecastController controller) {
            controller.refreshSelectedCity();
        }

        Object activityPlanner =
                screenControllers.get("activity-planner");

        if (activityPlanner instanceof ActivityPlannerController controller) {
            controller.refreshSelectedCity();
        }

        Object aqi =
                screenControllers.get("air-quality");

        if (aqi instanceof AQIController controller) {
            controller.refreshSelectedCity();
        }

        Object insights =
                screenControllers.get("ai-insights");

        if (insights instanceof InsightsController controller) {
            controller.refreshSelectedCity();
        }

        Object alerts = screenControllers.get("alerts");

        if (alerts instanceof AlertsController controller) {
            controller.refreshAlerts();
        }

        Object history = screenControllers.get("weather-history");

        if (history instanceof WeatherHistoryController controller) {
            controller.refreshSelectedCity();
        }
    }

    // -----------------------------------------------------------------
    // CLOCK
    // -----------------------------------------------------------------

    private void startClock() {

        clockTimeline = new Timeline(
                new KeyFrame(
                        Duration.seconds(1),
                        event -> updateClock()
                )
        );

        clockTimeline.setCycleCount(
                Animation.INDEFINITE
        );

        clockTimeline.play();
    }

    private void updateClock() {

        if (clockLabel != null) {

            clockLabel.setText(
                    LocalDateTime.now().format(CLOCK_FORMAT)
            );
        }
    }

    // -----------------------------------------------------------------
    // CLEANUP
    // -----------------------------------------------------------------

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;

        if (clockTimeline != null) {
            clockTimeline.stop();
        }

        if (activeFade != null) {
            activeFade.stop();
        }

        screenControllers.values().forEach(controller -> {

            if (controller instanceof DashboardController value) {
                value.dispose();

            } else if (controller instanceof ForecastController value) {
                value.dispose();

            } else if (controller instanceof ActivityPlannerController value) {
                value.dispose();

            } else if (controller instanceof AlertsController value) {
                value.dispose();

            } else if (controller instanceof WeatherHistoryController value) {
                value.dispose();

            } else if (controller instanceof AQIController value) {
                value.dispose();

            } else if (controller instanceof InsightsController value) {
                value.dispose();
            }
        });
    }
}



