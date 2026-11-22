
package com.atmosphere.controller;

import com.atmosphere.model.WeatherData;
import com.atmosphere.service.CityRegistry;
import com.atmosphere.service.GeminiService;
import com.atmosphere.service.WeatherService;
import com.atmosphere.util.ToastNotifications;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Gemini-powered conversational weather insights. */
public class InsightsController {

    private static final DateTimeFormatter GENERATED_FORMAT =
            DateTimeFormatter.ofPattern(
                    "EEE, dd MMM · HH:mm",
                    Locale.ENGLISH
            );

    private static final Duration TYPEWRITER_INTERVAL =
            Duration.millis(20);

    @FXML private ComboBox<String> citySelector;
    @FXML private StackPane typingIndicator;

    @FXML private Circle typingDotOne;
    @FXML private Circle typingDotTwo;
    @FXML private Circle typingDotThree;

    @FXML private TextArea insightTextArea;

    @FXML private Label cityTimestampLabel;
    @FXML private Label insightStatusLabel;

    @FXML private Button regenerateButton;

    private final WeatherService weatherService =
            new WeatherService();

    private final GeminiService geminiService =
            new GeminiService();

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "atmosphere-gemini-worker"
                );
                thread.setDaemon(true);
                return thread;
            });

    private Task<InsightResult> activeTask;

    private Timeline typingTimeline;
    private Timeline typewriterTimeline;

    private String selectedCity;

    @FXML
    private void initialize() {

        CityRegistry.registerAll(
                List.of("Bengaluru", "Mumbai", "Delhi")
        );

        citySelector.setItems(CityRegistry.getCities());

        citySelector.valueProperty().addListener(
                (observable, oldCity, newCity) -> {
                    if (newCity != null
                            && !newCity.isBlank()
                            && !newCity.equals(oldCity)) {

                        loadInsight(newCity);
                    }
                }
        );

        typingIndicator.setVisible(false);
        typingIndicator.setManaged(false);

        insightTextArea.setVisible(false);
        insightTextArea.setManaged(false);

        if (!citySelector.getItems().isEmpty()) {
            citySelector.getSelectionModel().selectFirst();
        }
    }

    @FXML
    private void handleRegenerate() {
        String city = citySelector.getValue();

        if (city != null && !city.isBlank()) {
            loadInsight(city);
        }
    }

    private void loadInsight(String city) {
        selectedCity = city;

        cancelActiveWork();
        showTypingIndicator();

        regenerateButton.setDisable(true);
        insightTextArea.clear();

        cityTimestampLabel.setText(
                city + " · Generating insight…"
        );

        insightStatusLabel.setText(
                "Checking current conditions and asking Gemini "
                        + "for a useful daily perspective."
        );

        Task<InsightResult> task = new Task<>() {

            @Override
            protected InsightResult call() {

                // Fetch current weather before requesting the Gemini insight.
                WeatherData weather =
                        weatherService.getCurrentWeather(city);

                GeminiService.InsightResult insight =
                        geminiService.getInsightResult(weather);

                return new InsightResult(weather, insight);
            }
        };

        activeTask = task;

        task.setOnSucceeded(event -> {
            if (activeTask != task) {
                return;
            }

            InsightResult result = task.getValue();

            stopTypingIndicator();
            showInsightArea();

            cityTimestampLabel.setText(
                    result.weather().getCityName()
                            + " · "
                            + LocalDateTime.now().format(GENERATED_FORMAT)
            );

            GeminiService.InsightResult insightResult =
                    result.insightResult();

            String insight = insightResult.text();

            if (!insightResult.successful()) {
                insightStatusLabel.setText(
                        insightResult.error()
                );

                ToastNotifications.show(
                        insightTextArea,
                        insightResult.error()
                );
            } else {
                insightStatusLabel.setText(
                        "Generated from current weather conditions · Gemini"
                );
            }

            startTypewriter(insight);
            regenerateButton.setDisable(false);
        });

        task.setOnFailed(event -> {
            if (activeTask != task) {
                return;
            }

            stopTypingIndicator();
            showInsightArea();

            insightTextArea.setText("AI insight unavailable");

            Throwable error = task.getException();

            String message = error == null
                    || error.getMessage() == null
                    ? "Weather data could not be loaded."
                    : error.getMessage();

            cityTimestampLabel.setText(
                    city + " · "
                            + LocalDateTime.now().format(GENERATED_FORMAT)
            );

            insightStatusLabel.setText(message);

            ToastNotifications.show(
                    insightTextArea,
                    message
            );

            regenerateButton.setDisable(false);
        });

        executor.execute(task);
    }

    private void showTypingIndicator() {
        stopTypewriter();

        insightTextArea.setVisible(false);
        insightTextArea.setManaged(false);

        typingIndicator.setVisible(true);
        typingIndicator.setManaged(true);

        typingDotOne.setOpacity(0.3);
        typingDotTwo.setOpacity(0.3);
        typingDotThree.setOpacity(0.3);

        typingTimeline = new Timeline(

                new KeyFrame(
                        Duration.ZERO,
                        new KeyValue(
                                typingDotOne.opacityProperty(),
                                0.3
                        ),
                        new KeyValue(
                                typingDotTwo.opacityProperty(),
                                0.3
                        ),
                        new KeyValue(
                                typingDotThree.opacityProperty(),
                                0.3
                        )
                ),

                new KeyFrame(
                        Duration.millis(180),
                        new KeyValue(
                                typingDotOne.opacityProperty(),
                                1.0
                        )
                ),

                new KeyFrame(
                        Duration.millis(360),
                        new KeyValue(
                                typingDotOne.opacityProperty(),
                                0.3
                        ),
                        new KeyValue(
                                typingDotTwo.opacityProperty(),
                                1.0
                        )
                ),

                new KeyFrame(
                        Duration.millis(540),
                        new KeyValue(
                                typingDotTwo.opacityProperty(),
                                0.3
                        ),
                        new KeyValue(
                                typingDotThree.opacityProperty(),
                                1.0
                        )
                ),

                new KeyFrame(
                        Duration.millis(720),
                        new KeyValue(
                                typingDotThree.opacityProperty(),
                                0.3
                        )
                )
        );

        typingTimeline.setCycleCount(Timeline.INDEFINITE);
        typingTimeline.playFromStart();
    }

    private void stopTypingIndicator() {
        if (typingTimeline != null) {
            typingTimeline.stop();
        }

        typingIndicator.setVisible(false);
        typingIndicator.setManaged(false);
    }

    private void showInsightArea() {
        insightTextArea.setVisible(true);
        insightTextArea.setManaged(true);
    }

    private void startTypewriter(String text) {
        stopTypewriter();
        insightTextArea.clear();

        if (text == null || text.isEmpty()) {
            insightTextArea.setText("AI insight unavailable");
            return;
        }

        final int[] index = {0};

        typewriterTimeline = new Timeline(
                new KeyFrame(
                        TYPEWRITER_INTERVAL,
                        event -> {
                            index[0]++;

                            insightTextArea.setText(
                                    text.substring(
                                            0,
                                            Math.min(index[0], text.length())
                                    )
                            );

                            insightTextArea.positionCaret(
                                    insightTextArea.getLength()
                            );
                        }
                )
        );

        typewriterTimeline.setCycleCount(text.length());
        typewriterTimeline.playFromStart();
    }

    private void stopTypewriter() {
        if (typewriterTimeline != null) {
            typewriterTimeline.stop();
        }
    }

    private void cancelActiveWork() {
        if (activeTask != null && !activeTask.isDone()) {
            activeTask.cancel(true);
        }

        stopTypingIndicator();
        stopTypewriter();
    }

    /** Re-fetch the selected city after the user changes provider settings. */
    public void refreshSelectedCity() {
        String city = citySelector.getValue();

        if (city != null && !city.isBlank()) {
            loadInsight(city);
        }
    }

    public void dispose() {
        cancelActiveWork();
        executor.shutdownNow();
    }

    private record InsightResult(
            WeatherData weather,
            GeminiService.InsightResult insightResult
    ) {
    }
}

