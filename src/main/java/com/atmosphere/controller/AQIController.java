
package com.atmosphere.controller;

import com.atmosphere.model.WeatherData;
import com.atmosphere.service.CityRegistry;
import com.atmosphere.service.WeatherService;
import com.atmosphere.util.ToastNotifications;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Air-quality screen: AQI gauge, health guidance and pollutant breakdown. */
public class AQIController {

    private static final double GAUGE_CENTER = 140.0;
    private static final double GAUGE_RADIUS = 104.0;
    private static final double GAUGE_STROKE = 19.0;
    private static final double GAUGE_START = 135.0;
    private static final double GAUGE_SWEEP = 270.0;
    private static final double GAUGE_LEVELS = 5.0;

    private static final Color[] AQI_COLORS = {
            Color.web("#10B981"), // Good
            Color.web("#F59E0B"), // Fair
            Color.web("#F97316"), // Moderate
            Color.web("#EF4444"), // Poor
            Color.web("#8B5CF6")  // Very Poor
    };

    @FXML private ComboBox<String> citySelector;
    @FXML private Canvas gaugeCanvas;
    @FXML private Label gaugeStatusLabel;
    @FXML private Label healthAdvisoryLabel;
    @FXML private Label aqiSourceLabel;
    @FXML private Label aqiErrorLabel;

    @FXML private Label pm25ValueLabel;
    @FXML private Label pm10ValueLabel;
    @FXML private Label no2ValueLabel;
    @FXML private Label o3ValueLabel;

    @FXML private ProgressBar pm25Progress;
    @FXML private ProgressBar pm10Progress;
    @FXML private ProgressBar no2Progress;
    @FXML private ProgressBar o3Progress;

    private final WeatherService weatherService =
            new WeatherService();

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "atmosphere-aqi-worker"
                );
                thread.setDaemon(true);
                return thread;
            });

    private final DoubleProperty gaugeProgress =
            new SimpleDoubleProperty(0.0);

    private Task<WeatherData> activeTask;
    private Timeline gaugeTimeline;

    private int displayedAqi;
    private String displayedAqiLabel = "Loading";

    @FXML
    private void initialize() {
        gaugeProgress.addListener(
                (observable, oldValue, newValue) ->
                        drawGauge(newValue.doubleValue())
        );

        drawGauge(0.0);

        CityRegistry.registerAll(
                java.util.List.of(
                        "Bengaluru",
                        "Mumbai",
                        "Delhi"
                )
        );

        citySelector.setItems(CityRegistry.getCities());

        citySelector.valueProperty().addListener(
                (observable, oldCity, newCity) -> {
                    if (newCity != null
                            && !newCity.isBlank()
                            && !newCity.equals(oldCity)) {
                        loadAirQuality(newCity);
                    }
                }
        );

        if (!citySelector.getItems().isEmpty()) {
            citySelector.getSelectionModel().selectFirst();
        }
    }

    private void loadAirQuality(String city) {
        cancelActiveWork();

        aqiErrorLabel.setVisible(false);
        aqiErrorLabel.setManaged(false);

        displayedAqi = 0;
        displayedAqiLabel = "Loading";

        gaugeStatusLabel.setText(
                "Loading air quality for " + city + "…"
        );

        healthAdvisoryLabel.setText(
                "Retrieving the latest pollution measurements…"
        );

        setPollutantPlaceholders();
        gaugeProgress.set(0.0);

        Task<WeatherData> task = new Task<>() {
            @Override
            protected WeatherData call() {
                return weatherService.getCurrentWeather(city);
            }
        };

        activeTask = task;

        task.setOnSucceeded(event -> {
            if (activeTask != task) {
                return;
            }

            WeatherData data = task.getValue();
            renderAirQuality(data);
        });

        task.setOnFailed(event -> {
            if (activeTask != task) {
                return;
            }

            Throwable error = task.getException();

            String message = error == null || error.getMessage() == null
                    ? "Air-quality data could not be loaded."
                    : error.getMessage();

            gaugeStatusLabel.setText(
                    "Unable to load air-quality data"
            );

            healthAdvisoryLabel.setText(
                    "Check your connection and OpenWeatherMap API key, "
                            + "then try another city."
            );

            aqiErrorLabel.setText(message);
            aqiErrorLabel.setVisible(true);
            aqiErrorLabel.setManaged(true);

            animateGauge(0);

            ToastNotifications.show(gaugeCanvas, message);
        });

        executor.execute(task);
    }

    private void renderAirQuality(WeatherData data) {
        displayedAqi = Math.max(
                1,
                Math.min(5, data.getAqi())
        );

        displayedAqiLabel = safe(
                data.getAqiLabel(),
                labelFor(displayedAqi)
        );

        gaugeStatusLabel.setText(
                data.getCityName() + " · " + data.getCountryCode()
        );

        healthAdvisoryLabel.setText(
                healthAdvisory(displayedAqi)
        );

        aqiSourceLabel.setText(
                "AQI " + data.getAqi()
                        + " / 5 · OpenWeatherMap · Updated just now"
        );

        updatePollutant(
                pm25ValueLabel,
                pm25Progress,
                data.getPm25(),
                75.0
        );

        updatePollutant(
                pm10ValueLabel,
                pm10Progress,
                data.getPm10(),
                150.0
        );

        updatePollutant(
                no2ValueLabel,
                no2Progress,
                data.getNo2(),
                200.0
        );

        updatePollutant(
                o3ValueLabel,
                o3Progress,
                data.getO3(),
                180.0
        );

        animateGauge(displayedAqi);
    }

    private void animateGauge(int targetAqi) {
        if (gaugeTimeline != null) {
            gaugeTimeline.stop();
        }

        gaugeProgress.set(0.0);

        double targetProgress = Math.max(
                0,
                Math.min(5, targetAqi)
        );

        gaugeTimeline = new Timeline(
                new KeyFrame(
                        Duration.ZERO,
                        new KeyValue(gaugeProgress, 0.0)
                ),
                new KeyFrame(
                        Duration.millis(1200),
                        new KeyValue(
                                gaugeProgress,
                                targetProgress,
                                Interpolator.EASE_BOTH
                        )
                )
        );

        gaugeTimeline.playFromStart();
    }

    private void drawGauge(double progress) {
        GraphicsContext gc =
                gaugeCanvas.getGraphicsContext2D();

        double width = gaugeCanvas.getWidth();
        double height = gaugeCanvas.getHeight();

        gc.clearRect(0, 0, width, height);

        // Full dark track from 135° through 405°.
        drawArc(
                gc,
                GAUGE_START,
                GAUGE_START + GAUGE_SWEEP,
                Color.web("#263244"),
                GAUGE_STROKE
        );

        // Display the five color segments softly.
        gc.setGlobalAlpha(0.24);

        for (int segment = 0;
             segment < AQI_COLORS.length;
             segment++) {

            double segmentStart =
                    GAUGE_START
                            + segment * (GAUGE_SWEEP / GAUGE_LEVELS)
                            + 1.5;

            double segmentEnd =
                    segmentStart
                            + GAUGE_SWEEP / GAUGE_LEVELS
                            - 3.0;

            drawArc(
                    gc,
                    segmentStart,
                    segmentEnd,
                    AQI_COLORS[segment],
                    GAUGE_STROKE
            );
        }

        gc.setGlobalAlpha(1.0);

        double remainingDegrees =
                (Math.max(0, Math.min(5, progress))
                        / GAUGE_LEVELS)
                        * GAUGE_SWEEP;

        // Animate the active color segments.
        for (int segment = 0;
             segment < AQI_COLORS.length
                     && remainingDegrees > 0;
             segment++) {

            double segmentStart =
                    GAUGE_START
                            + segment * (GAUGE_SWEEP / GAUGE_LEVELS)
                            + 1.5;

            double segmentLength = Math.min(
                    GAUGE_SWEEP / GAUGE_LEVELS - 3.0,
                    remainingDegrees
            );

            if (segmentLength > 0) {
                drawArc(
                        gc,
                        segmentStart,
                        segmentStart + segmentLength,
                        AQI_COLORS[segment],
                        GAUGE_STROKE
                );
            }

            remainingDegrees -= GAUGE_SWEEP / GAUGE_LEVELS;
        }

        // Needle moves along the same 270° scale.
        double needleAngle = Math.toRadians(
                GAUGE_START
                        + (Math.max(0, Math.min(5, progress))
                        / GAUGE_LEVELS)
                        * GAUGE_SWEEP
        );

        double needleLength = GAUGE_RADIUS * 0.72;

        double needleX = GAUGE_CENTER
                + Math.cos(needleAngle) * needleLength;

        double needleY = GAUGE_CENTER
                + Math.sin(needleAngle) * needleLength;

        gc.setStroke(Color.web("#F8FAFC"));
        gc.setLineWidth(2.4);

        gc.strokeLine(
                GAUGE_CENTER,
                GAUGE_CENTER,
                needleX,
                needleY
        );

        gc.setFill(Color.web("#F8FAFC"));

        gc.fillOval(
                GAUGE_CENTER - 6,
                GAUGE_CENTER - 6,
                12,
                12
        );

        // Center reading and descriptor.
        gc.setTextAlign(
                javafx.scene.text.TextAlignment.CENTER
        );

        gc.setFill(Color.web("#F1F5F9"));

        gc.setFont(
                javafx.scene.text.Font.font(
                        "Segoe UI",
                        javafx.scene.text.FontWeight.BOLD,
                        48
                )
        );

        String number = displayedAqi > 0
                ? Integer.toString(displayedAqi)
                : "—";

        gc.fillText(number, GAUGE_CENTER, 158);

        gc.setFill(colorForAqi(displayedAqi));

        gc.setFont(
                javafx.scene.text.Font.font(
                        "Segoe UI",
                        javafx.scene.text.FontWeight.BOLD,
                        13
                )
        );

        gc.fillText(
                displayedAqi > 0 ? displayedAqiLabel : "AQI",
                GAUGE_CENTER,
                183
        );
    }

    /** Canvas polyline arc avoids ambiguity in Canvas strokeArc angle direction. */
    private void drawArc(
            GraphicsContext gc,
            double start,
            double end,
            Color color,
            double width
    ) {
        gc.beginPath();

        for (double degree = start;
             degree <= end;
             degree += 2.0) {

            double radians = Math.toRadians(degree);

            double x = GAUGE_CENTER
                    + Math.cos(radians) * GAUGE_RADIUS;

            double y = GAUGE_CENTER
                    + Math.sin(radians) * GAUGE_RADIUS;

            if (degree == start) {
                gc.moveTo(x, y);
            } else {
                gc.lineTo(x, y);
            }
        }

        double endRadians = Math.toRadians(end);

        gc.lineTo(
                GAUGE_CENTER + Math.cos(endRadians) * GAUGE_RADIUS,
                GAUGE_CENTER + Math.sin(endRadians) * GAUGE_RADIUS
        );

        gc.setStroke(color);
        gc.setLineWidth(width);
        gc.setLineCap(
                javafx.scene.shape.StrokeLineCap.ROUND
        );

        gc.stroke();
    }

    private void updatePollutant(
            Label valueLabel,
            ProgressBar bar,
            double value,
            double displayMax
    ) {
        valueLabel.setText(
                String.format(
                        Locale.ENGLISH,
                        "%.1f µg/m³",
                        value
                )
        );

        double ratio = Math.max(
                0.0,
                Math.min(1.0, value / displayMax)
        );

        bar.setProgress(ratio);

        Color color = ratio < 0.33
                ? AQI_COLORS[0]
                : ratio < 0.66
                ? AQI_COLORS[1]
                : ratio < 0.85
                ? AQI_COLORS[2]
                : AQI_COLORS[3];

        bar.setStyle(
                "-fx-accent: " + toHex(color) + ";"
        );
    }

    private void setPollutantPlaceholders() {
        pm25ValueLabel.setText("— µg/m³");
        pm10ValueLabel.setText("— µg/m³");
        no2ValueLabel.setText("— µg/m³");
        o3ValueLabel.setText("— µg/m³");

        pm25Progress.setProgress(0);
        pm10Progress.setProgress(0);
        no2Progress.setProgress(0);
        o3Progress.setProgress(0);
    }

    private String healthAdvisory(int aqi) {
        return switch (aqi) {
            case 1 ->
                    "Air quality is good. Enjoy outdoor activities as usual; "
                            + "no special precautions are needed for most people.";

            case 2 ->
                    "Air quality is fair. Most people can continue normal "
                            + "activities, while unusually sensitive people may "
                            + "prefer shorter periods of strenuous outdoor activity.";

            case 3 ->
                    "Air quality is moderate. Consider taking breaks during "
                            + "prolonged outdoor exertion, especially if you are "
                            + "sensitive to pollution.";

            case 4 ->
                    "Air quality is poor. Limit prolonged or strenuous outdoor "
                            + "activity and consider moving exercise indoors.";

            case 5 ->
                    "Air quality is very poor. Reduce time outdoors, keep "
                            + "windows closed where practical, and choose "
                            + "indoor activities.";

            default ->
                    "Air quality is very poor. Reduce time outdoors "
                            + "and follow local public-health guidance.";
        };
    }

    private static String labelFor(int aqi) {
        return switch (aqi) {
            case 1 -> "Good";
            case 2 -> "Fair";
            case 3 -> "Moderate";
            case 4 -> "Poor";
            case 5 -> "Very Poor";
            default -> "Unknown";
        };
    }

    private static Color colorForAqi(int aqi) {
        if (aqi < 1) {
            return Color.web("#94A3B8");
        }

        return AQI_COLORS[Math.min(aqi, 5) - 1];
    }

    private static String toHex(Color color) {
        return String.format(
                "#%02X%02X%02X",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255)
        );
    }

    private static String safe(
            String value,
            String fallback
    ) {
        return value == null || value.isBlank()
                ? fallback
                : value;
    }

    /** Re-fetch the selected city after the user changes provider settings. */
    public void refreshSelectedCity() {
        String city = citySelector.getValue();

        if (city != null && !city.isBlank()) {
            loadAirQuality(city);
        }
    }

    public void dispose() {
        cancelActiveWork();
        executor.shutdownNow();
    }

    private void cancelActiveWork() {
        if (activeTask != null && !activeTask.isDone()) {
            activeTask.cancel(true);
        }

        if (gaugeTimeline != null) {
            gaugeTimeline.stop();
        }
    }
}
