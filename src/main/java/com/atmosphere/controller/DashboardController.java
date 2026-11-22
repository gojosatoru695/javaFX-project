
package com.atmosphere.controller;

import com.atmosphere.config.AppConfig;
import com.atmosphere.model.WeatherData;
import com.atmosphere.model.WeatherSnapshot;
import com.atmosphere.service.CityRegistry;
import com.atmosphere.service.WeatherHistoryService;
import com.atmosphere.service.WeatherService;
import com.atmosphere.util.CardHoverEffects;
import com.atmosphere.util.TemperatureStyler;
import com.atmosphere.util.ToastNotifications;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads and presents weather for the dashboard's city comparison cards.
 * All OpenWeatherMap requests run on worker threads; JavaFX nodes are only
 * created or changed by Task/Service handlers on the JavaFX Application Thread.
 */
public class DashboardController {

    private static final List<String> DEFAULT_CITIES =
            List.of("Bengaluru", "Mumbai", "Delhi");

    private static final int GRID_COLUMNS = 2;
    private static final double CITY_CARD_HEIGHT = 258;

    private static final Duration CARD_ENTRANCE_DURATION =
            Duration.millis(300);

    private static final DateTimeFormatter UPDATED_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH);

    @FXML private GridPane cityCardsGrid;
    @FXML private TextField citySearchField;
    @FXML private Button searchButton;
    @FXML private Label feedbackLabel;
    @FXML private Label alertBanner;

    @FXML private Label averageTempLabel;
    @FXML private LineChart<String, Number> temperatureChart;
    @FXML private Label windSummaryLabel;
    @FXML private VBox windIndicatorsContainer;
    @FXML private Label aqiOverallLabel;
    @FXML private Label aqiSummaryLabel;

    private final WeatherService weatherService =
            new WeatherService();
    private final WeatherHistoryService weatherHistoryService =
            new WeatherHistoryService();

    /**
     * Initial city order is stable and also determines the positions in the
     * two-column grid. Newly searched cities are appended after these cities.
     */
    private final List<String> cityNames = new ArrayList<>();

    /** Latest successful weather result, keyed by a normalized requested city. */
    private final Map<String, WeatherData> weatherByCity =
            new LinkedHashMap<>();

    /** Current node occupying each city's stable grid slot. */
    private final Map<String, Node> cityNodes =
            new LinkedHashMap<>();

    /** A few worker threads allow the three default city requests to run concurrently. */
    private final ExecutorService weatherExecutor =
            Executors.newFixedThreadPool(4, runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "atmosphere-weather-worker"
                );
                thread.setDaemon(true);
                return thread;
            });

    private ScheduledService<List<RefreshResult>> autoRefreshService;
    private TranslateTransition alertTransition;
    private Runnable weatherUpdatedListener = () -> { };
    private volatile Runnable weatherHistorySavedListener = () -> { };
    private volatile boolean disposed;

    @FXML
    private void initialize() {
        citySearchField.setOnAction(event -> handleSearch());

        configureTemperatureChart();
        averageTempLabel.setText(
                "--" + TemperatureStyler.unitSuffix()
        );

        CardHoverEffects.install(temperatureChart.getParent());
        CardHoverEffects.install(windIndicatorsContainer.getParent());
        CardHoverEffects.install(aqiOverallLabel.getParent());

        AppConfig.fahrenheitProperty().addListener(
                (observable, oldValue, newValue) ->
                        refreshTemperatureDisplay()
        );

        CityRegistry.registerAll(DEFAULT_CITIES);

        for (String city : DEFAULT_CITIES) {
            cityNames.add(city);
            showLoadingCard(city);
        }

        DEFAULT_CITIES.forEach(this::loadCityInBackground);

        startAutoRefresh();
    }

    /** Search button handler; Enter in the search field uses the same action. */
    @FXML
    private void handleSearch() {
        String city = citySearchField.getText() == null
                ? ""
                : citySearchField.getText().trim();

        if (city.isBlank()) {
            feedbackLabel.setText(
                    "Enter a city name to add it to your dashboard."
            );
            citySearchField.requestFocus();
            return;
        }

        String key = cityKey(city);
        String existingCity = findCityByKey(key);

        if (existingCity != null) {
            // Re-request an existing city instead of adding a duplicate.
            city = existingCity;
            feedbackLabel.setText(
                    "Refreshing " + city + "..."
            );
        } else {
            // Keep a temporary card slot while checking the city.
            // Do not publish it to shared selectors yet.
            cityNames.add(city);
            showLoadingCard(city);
            feedbackLabel.setText(
                    "Checking " + city + "..."
            );
        }

        citySearchField.clear();
        loadCityInBackground(city);
    }

    /**
     * Each city gets its own JavaFX Task. Task.call() runs on the executor;
     * success and failure callbacks are delivered on the JavaFX Application
     * Thread, where it is safe to replace scene-graph nodes.
     */
    private void loadCityInBackground(String city) {
        Task<WeatherData> task = new Task<>() {
            @Override
            protected WeatherData call() {
                return weatherService.getCurrentWeather(city);
            }
        };

        task.setOnSucceeded(event -> {
            WeatherData weather = task.getValue();

            // Register only after the API confirms a valid city.
            CityRegistry.register(city);

            persistSnapshot(city, weather);
            weatherByCity.put(cityKey(city), weather);

            VBox card = createWeatherCard(city, weather);
            replaceCityNode(city, card);
            playCardEntrance(card);

            updateOverview();
            updateAirQualityAlert();

            feedbackLabel.setText(
                    "Weather updated for " + weather.getCityName()
                            + " \u00b7 "
                            + UPDATED_FORMAT.format(LocalDateTime.now())
            );

            weatherUpdatedListener.run();
        });

        task.setOnFailed(event -> {
            Throwable error = task.getException();

            String message = error == null || error.getMessage() == null
                    ? "Weather data could not be loaded."
                    : error.getMessage();

            // A failed new search must not pollute shared selectors.
            // Existing registered cities retain their error card.
            if (!CityRegistry.contains(city)) {
                cityNames.removeIf(
                        existing ->
                                cityKey(existing).equals(cityKey(city))
                );

                Node failedNode = cityNodes.remove(cityKey(city));

                if (failedNode != null) {
                    cityCardsGrid.getChildren().remove(failedNode);
                }

                reflowCityCardsGrid();
            } else {
                VBox errorCard = createErrorCard(city, message);
                replaceCityNode(city, errorCard);
            }

            feedbackLabel.setText(
                    "Couldn't load " + city + ": " + message
            );

            ToastNotifications.show(
                    cityCardsGrid,
                    "Could not load " + city + ": " + message
            );
        });

        weatherExecutor.execute(task);
    }

    // ---------------------------------------------------------------------
    // Loading placeholders
    // ---------------------------------------------------------------------

    private void showLoadingCard(String city) {
        StackPane placeholder = createShimmerPlaceholder();
        placeholder.setAccessibleText(
                "Loading weather for " + city
        );
        replaceCityNode(city, placeholder);
    }

    /** A card-sized set of Rectangle skeletons with a moving highlight strip. */
    private StackPane createShimmerPlaceholder() {
        Rectangle base = new Rectangle(340, CITY_CARD_HEIGHT);
        base.setArcWidth(24);
        base.setArcHeight(24);
        base.setFill(Color.web("#111827"));
        base.getStyleClass().add("shimmer");

        VBox skeletonContent = new VBox(16);
        skeletonContent.setPadding(new Insets(20));
        skeletonContent.setMouseTransparent(true);

        HBox titleRow = new HBox(12);

        VBox titleLines = new VBox(
                8,
                skeletonRectangle(130, 13),
                skeletonRectangle(76, 9)
        );

        Region spacer = new Region();
        HBox.setHgrow(
                spacer,
                javafx.scene.layout.Priority.ALWAYS
        );

        Rectangle icon = skeletonRectangle(38, 38);

        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.getChildren().addAll(
                titleLines,
                spacer,
                icon
        );

        HBox temperatureRow = new HBox(
                16,
                skeletonRectangle(100, 54),
                new VBox(
                        9,
                        skeletonRectangle(122, 11),
                        skeletonRectangle(88, 9)
                )
        );

        temperatureRow.setAlignment(Pos.CENTER_LEFT);

        VBox statLines = new VBox(
                10,
                skeletonRectangle(292, 10),
                skeletonRectangle(292, 10),
                skeletonRectangle(292, 10)
        );

        HBox footer = new HBox(
                12,
                skeletonRectangle(90, 10),
                skeletonRectangle(90, 10)
        );

        skeletonContent.getChildren().addAll(
                titleRow,
                temperatureRow,
                statLines,
                footer
        );

        Rectangle highlight = new Rectangle(
                100,
                CITY_CARD_HEIGHT
        );

        highlight.setArcWidth(24);
        highlight.setArcHeight(24);

        highlight.setFill(new LinearGradient(
                0, 0, 1, 0,
                true,
                CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.TRANSPARENT),
                new Stop(
                        0.5,
                        Color.rgb(148, 163, 184, 0.12)
                ),
                new Stop(1.0, Color.TRANSPARENT)
        ));

        highlight.setMouseTransparent(true);

        StackPane placeholder = new StackPane(
                base,
                skeletonContent,
                highlight
        );

        placeholder.setPrefSize(340, CITY_CARD_HEIGHT);
        placeholder.setMinHeight(CITY_CARD_HEIGHT);
        placeholder.setMaxWidth(Double.MAX_VALUE);
        placeholder.getStyleClass().add("skeleton-card");

        CardHoverEffects.install(placeholder);

        Rectangle clip = new Rectangle(
                340,
                CITY_CARD_HEIGHT
        );

        clip.setArcWidth(24);
        clip.setArcHeight(24);

        placeholder.setClip(clip);

        TranslateTransition shimmer = new TranslateTransition(
                Duration.seconds(1.5),
                highlight
        );

        shimmer.setFromX(-230);
        shimmer.setToX(230);
        shimmer.setCycleCount(
                javafx.animation.Animation.INDEFINITE
        );

        shimmer.play();

        return placeholder;
    }

    private Rectangle skeletonRectangle(
            double width,
            double height
    ) {
        Rectangle rectangle = new Rectangle(width, height);
        rectangle.setArcWidth(7);
        rectangle.setArcHeight(7);
        rectangle.setFill(Color.web("#1E293B"));
        rectangle.getStyleClass().add("skeleton-line");

        return rectangle;
    }

    // ---------------------------------------------------------------------
    // City card construction
    // ---------------------------------------------------------------------

    private VBox createWeatherCard(
            String requestedCity,
            WeatherData weather
    ) {
        VBox card = new VBox(14);
        card.getStyleClass().addAll(
                "card",
                "city-weather-card"
        );

        card.setPrefWidth(340);
        card.setMinWidth(280);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMinHeight(CITY_CARD_HEIGHT);

        // City, country and weather symbol.
        Label cityName = new Label(
                safe(weather.getCityName(), requestedCity)
        );
        cityName.getStyleClass().add("city-name");

        Label country = new Label(
                weather.getCountryCode() == null
                        || weather.getCountryCode().isBlank()
                        ? "LIVE CONDITIONS"
                        : weather.getCountryCode().toUpperCase(
                                Locale.ENGLISH
                        )
        );

        country.getStyleClass().add("city-country");

        VBox cityIdentity = new VBox(
                4,
                cityName,
                country
        );

        Region topSpacer = new Region();

        HBox.setHgrow(
                topSpacer,
                javafx.scene.layout.Priority.ALWAYS
        );

        Label weatherEmoji = new Label(
                weatherEmoji(weather)
        );

        weatherEmoji.getStyleClass().add("city-weather-emoji");

        HBox topRow = new HBox(
                10,
                cityIdentity,
                topSpacer,
                weatherEmoji
        );

        topRow.setAlignment(Pos.CENTER_LEFT);

        // Temperature and descriptive conditions.
        Label temperature = new Label();
        temperature.getStyleClass().add("temperature-large");

        TemperatureStyler.apply(
                temperature,
                weather.getTemperature()
        );

        Label feelsLike = new Label();
        feelsLike.getStyleClass().add("temperature-feels");

        TemperatureStyler.apply(
                feelsLike,
                weather.getFeelsLike(),
                "Feels like "
        );

        Label description = new Label(
                capitalize(weather.getDescription())
        );

        description.getStyleClass().add(
                "temperature-description"
        );
        description.setWrapText(true);

        VBox temperatureDetails = new VBox(
                6,
                feelsLike,
                description
        );

        HBox temperatureRow = new HBox(
                14,
                temperature,
                temperatureDetails
        );

        temperatureRow.setAlignment(Pos.CENTER_LEFT);

        // Three stat rows.
        VBox stats = new VBox(
                9,
                createStatRow(
                        "HUMIDITY",
                        String.format(
                                Locale.ENGLISH,
                                "%.0f%%",
                                weather.getHumidity()
                        )
                ),
                createStatRow(
                        "WIND",
                        String.format(
                                Locale.ENGLISH,
                                "%.1f km/h",
                                weather.getWindSpeed()
                        )
                ),
                createStatRow(
                        "PRESSURE",
                        String.format(
                                Locale.ENGLISH,
                                "%.0f hPa",
                                weather.getPressure()
                        )
                )
        );

        stats.getStyleClass().add("city-stats");

        // Sunrise/sunset and AQI badge.
        VBox sunTimes = new VBox(
                5,
                smallInfo(
                        "SUNRISE",
                        safe(weather.getSunrise(), "N/A")
                ),
                smallInfo(
                        "SUNSET",
                        safe(weather.getSunset(), "N/A")
                )
        );

        Region footerSpacer = new Region();

        HBox.setHgrow(
                footerSpacer,
                javafx.scene.layout.Priority.ALWAYS
        );

        Label aqiBadge = new Label(
                "AQI " + weather.getAqi()
                        + " · "
                        + safe(weather.getAqiLabel(), "Unknown")
        );

        aqiBadge.getStyleClass().addAll(
                "badge",
                aqiBadgeClass(weather.getAqi())
        );

        HBox footer = new HBox(
                10,
                sunTimes,
                footerSpacer,
                aqiBadge
        );

        footer.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(
                topRow,
                temperatureRow,
                stats,
                footer
        );

        CardHoverEffects.install(card);

        return card;
    }

    private HBox createStatRow(
            String labelText,
            String valueText
    ) {
        Label label = new Label(labelText);
        label.getStyleClass().add("stat-label");

        Region spacer = new Region();

        HBox.setHgrow(
                spacer,
                javafx.scene.layout.Priority.ALWAYS
        );

        Label value = new Label(valueText);
        value.getStyleClass().add("stat-value");

        HBox row = new HBox(
                10,
                label,
                spacer,
                value
        );

        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("city-stat-row");

        return row;
    }

    private HBox smallInfo(
            String labelText,
            String valueText
    ) {
        Label label = new Label(labelText);
        label.getStyleClass().add("sun-time-label");

        Label value = new Label(valueText);
        value.getStyleClass().add("sun-time-value");

        HBox row = new HBox(
                6,
                label,
                value
        );

        row.setAlignment(Pos.CENTER_LEFT);

        return row;
    }

    private VBox createErrorCard(
            String city,
            String message
    ) {
        Label title = new Label(city);
        title.getStyleClass().add("city-name");

        Label details = new Label(message);
        details.getStyleClass().add("error-message");
        details.setWrapText(true);

        Button retry = new Button("Retry");

        retry.getStyleClass().addAll(
                "badge",
                "badge-blue",
                "retry-button"
        );

        retry.setOnAction(event -> {
            showLoadingCard(city);
            loadCityInBackground(city);
        });

        VBox card = new VBox(
                14,
                title,
                details,
                retry
        );

        card.getStyleClass().addAll(
                "card",
                "city-weather-card",
                "city-error-card"
        );

        card.setPrefWidth(340);
        card.setMinWidth(280);
        card.setMinHeight(CITY_CARD_HEIGHT);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setAlignment(Pos.CENTER_LEFT);

        CardHoverEffects.install(card);

        return card;
    }

    // ---------------------------------------------------------------------
    // Stable two-column grid placement and entrance animation
    // ---------------------------------------------------------------------

    private void replaceCityNode(
            String city,
            Node node
    ) {
        String key = cityKey(city);

        Node oldNode = cityNodes.put(key, node);

        if (oldNode != null) {
            cityCardsGrid.getChildren().remove(oldNode);
        }

        int index = indexOfCity(city);
        int column = index % GRID_COLUMNS;
        int row = index / GRID_COLUMNS;

        GridPane.setColumnIndex(node, column);
        GridPane.setRowIndex(node, row);
        GridPane.setHgrow(
                node,
                javafx.scene.layout.Priority.ALWAYS
        );
        GridPane.setFillWidth(node, true);

        cityCardsGrid.getChildren().add(node);
    }

    private void reflowCityCardsGrid() {
        for (int i = 0; i < cityNames.size(); i++) {
            String city = cityNames.get(i);
            Node node = cityNodes.get(cityKey(city));

            if (node == null) {
                continue;
            }

            GridPane.setColumnIndex(
                    node,
                    i % GRID_COLUMNS
            );

            GridPane.setRowIndex(
                    node,
                    i / GRID_COLUMNS
            );
        }
    }

    private int indexOfCity(String city) {
        String key = cityKey(city);

        for (int i = 0; i < cityNames.size(); i++) {
            if (cityKey(cityNames.get(i)).equals(key)) {
                return i;
            }
        }

        return Math.max(0, cityNames.size() - 1);
    }

    private void playCardEntrance(Node card) {
        ScaleTransition scale = new ScaleTransition(
                CARD_ENTRANCE_DURATION,
                card
        );

        scale.setFromX(0.95);
        scale.setFromY(0.95);
        scale.setToX(1.0);
        scale.setToY(1.0);

        scale.play();
    }

    // ---------------------------------------------------------------------
    // Overview cards and chart
    // ---------------------------------------------------------------------

    private void configureTemperatureChart() {
        temperatureChart.setLegendVisible(false);
        temperatureChart.setAnimated(false);
        temperatureChart.setCreateSymbols(true);

        temperatureChart.getXAxis().setTickLabelsVisible(false);
        temperatureChart.getXAxis().setTickMarkVisible(false);

        temperatureChart.getYAxis().setTickLabelsVisible(false);
        temperatureChart.getYAxis().setTickMarkVisible(false);

        temperatureChart.getYAxis().setOpacity(0);
        temperatureChart.getXAxis().setOpacity(0);
    }

    private void updateOverview() {
        List<Map.Entry<String, WeatherData>> firstThree =
                new ArrayList<>();

        for (String city : cityNames) {
            WeatherData data = weatherByCity.get(cityKey(city));

            if (data != null) {
                firstThree.add(Map.entry(city, data));

                if (firstThree.size() == 3) {
                    break;
                }
            }
        }

        updateTemperatureOverview(firstThree);
        updateWindOverview(firstThree);
        updateAqiOverview();
    }

    private void updateTemperatureOverview(
            List<Map.Entry<String, WeatherData>> weatherEntries
    ) {
        temperatureChart.getData().clear();

        if (weatherEntries.isEmpty()) {
            averageTempLabel.setText(
                    "--" + TemperatureStyler.unitSuffix()
            );
            return;
        }

        double average = weatherEntries.stream()
                .mapToDouble(entry ->
                        entry.getValue().getTemperature()
                )
                .average()
                .orElse(0.0);

        TemperatureStyler.apply(averageTempLabel, average);

        XYChart.Series<String, Number> series =
                new XYChart.Series<>();

        series.setName("Temperature");

        for (Map.Entry<String, WeatherData> entry : weatherEntries) {
            series.getData().add(
                    new XYChart.Data<>(
                            entry.getKey(),
                            TemperatureStyler.toDisplayValue(
                                    entry.getValue().getTemperature()
                            )
                    )
            );
        }

        temperatureChart.getData().add(series);
    }

    private void updateWindOverview(
            List<Map.Entry<String, WeatherData>> weatherEntries
    ) {
        windIndicatorsContainer.getChildren().clear();

        if (weatherEntries.isEmpty()) {
            windSummaryLabel.setText(
                    "Waiting for live wind readings..."
            );
            return;
        }

        Map.Entry<String, WeatherData> strongest =
                weatherEntries.stream()
                        .max((first, second) ->
                                Double.compare(
                                        first.getValue().getWindSpeed(),
                                        second.getValue().getWindSpeed()
                                )
                        )
                        .orElseThrow();

        windSummaryLabel.setText(
                String.format(
                        Locale.ENGLISH,
                        "Strongest · %s · %.1f km/h",
                        strongest.getKey(),
                        strongest.getValue().getWindSpeed()
                )
        );

        for (Map.Entry<String, WeatherData> entry : weatherEntries) {
            Label city = new Label(entry.getKey());
            city.getStyleClass().add("overview-indicator-city");

            Region spacer = new Region();

            HBox.setHgrow(
                    spacer,
                    javafx.scene.layout.Priority.ALWAYS
            );

            Label speed = new Label(
                    String.format(
                            Locale.ENGLISH,
                            "%.1f km/h",
                            entry.getValue().getWindSpeed()
                    )
            );

            speed.getStyleClass().add(
                    "overview-indicator-value"
            );

            HBox row = new HBox(
                    8,
                    city,
                    spacer,
                    speed
            );

            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("overview-indicator-row");

            windIndicatorsContainer.getChildren().add(row);
        }
    }

    private void updateAqiOverview() {
        Map.Entry<String, WeatherData> worst =
                weatherByCity.entrySet().stream()
                        .max((first, second) ->
                                Integer.compare(
                                        first.getValue().getAqi(),
                                        second.getValue().getAqi()
                                )
                        )
                        .orElse(null);

        if (worst == null) {
            aqiOverallLabel.setText("--");
            setAqiBadgeStyle(1);

            aqiSummaryLabel.setText(
                    "Waiting for air-quality readings..."
            );

            return;
        }

        WeatherData data = worst.getValue();

        aqiOverallLabel.setText(
                "AQI " + data.getAqi()
                        + " · "
                        + safe(data.getAqiLabel(), "Unknown")
        );

        setAqiBadgeStyle(data.getAqi());

        aqiSummaryLabel.setText(
                "Highest reading · "
                        + safe(data.getCityName(), worst.getKey())
        );
    }

    private void setAqiBadgeStyle(int aqi) {
        aqiOverallLabel.getStyleClass().removeAll(
                "badge-green",
                "badge-yellow",
                "badge-red",
                "badge-blue"
        );

        aqiOverallLabel.getStyleClass().add("badge");
        aqiOverallLabel.getStyleClass().add(aqiBadgeClass(aqi));
    }

    // ---------------------------------------------------------------------
    // Air-quality alert banner
    // ---------------------------------------------------------------------

    private void updateAirQualityAlert() {
        List<String> alerts = new ArrayList<>();

        for (String city : cityNames) {
            WeatherData data = weatherByCity.get(cityKey(city));

            if (data != null && data.getAqi() >= 4) {
                alerts.add(
                        safe(data.getCityName(), city)
                                + " · "
                                + safe(data.getAqiLabel(), "Poor")
                                + " (AQI " + data.getAqi() + ")"
                );
            }
        }

        if (alerts.isEmpty()) {
            alertBanner.setVisible(false);
            alertBanner.setManaged(false);

            if (alertTransition != null) {
                alertTransition.stop();
            }

            return;
        }

        alertBanner.setText(
                "AIR QUALITY ALERT  ·  "
                        + String.join("     |     ", alerts)
        );

        boolean wasHidden = !alertBanner.isVisible();

        alertBanner.setManaged(true);
        alertBanner.setVisible(true);

        if (wasHidden) {
            alertBanner.setTranslateY(-50);

            if (alertTransition != null) {
                alertTransition.stop();
            }

            alertTransition = new TranslateTransition(
                    Duration.millis(350),
                    alertBanner
            );

            alertTransition.setFromY(-50);
            alertTransition.setToY(0);
            alertTransition.play();
        }
    }

    // ---------------------------------------------------------------------
    // Ten-minute background refresh
    // ---------------------------------------------------------------------

    private void startAutoRefresh() {
        autoRefreshService = new ScheduledService<>() {

            @Override
            protected Task<List<RefreshResult>> createTask() {
                // The task performs network I/O on a worker thread. It captures
                // the current city list so recently searched cities are included.
                List<String> citySnapshot = List.copyOf(cityNames);

                return new Task<>() {

                    @Override
                    protected List<RefreshResult> call() {
                        List<RefreshResult> results =
                                new ArrayList<>();

                        for (String city : citySnapshot) {
                            if (isCancelled()) {
                                break;
                            }

                            try {
                                results.add(
                                        new RefreshResult(
                                                city,
                                                weatherService.getCurrentWeather(city),
                                                null
                                        )
                                );

                            } catch (RuntimeException exception) {
                                results.add(
                                        new RefreshResult(
                                                city,
                                                null,
                                                exception.getMessage()
                                        )
                                );
                            }
                        }

                        return results;
                    }
                };
            }
        };

        autoRefreshService.setDelay(Duration.minutes(10));
        autoRefreshService.setPeriod(Duration.minutes(10));
        autoRefreshService.setExecutor(weatherExecutor);
        autoRefreshService.setRestartOnFailure(true);

        autoRefreshService.setOnScheduled(event ->
                feedbackLabel.setText(
                        "Refreshing weather in the background..."
                )
        );

        autoRefreshService.setOnSucceeded(event -> {
            List<RefreshResult> results =
                    autoRefreshService.getValue();

            int updated = 0;
            int failed = 0;

            for (RefreshResult result : results) {
                if (result.weather() == null) {
                    failed++;

                    // Keep the most recent successful card on refresh failure.
                    continue;
                }

                weatherByCity.put(
                        cityKey(result.city()),
                        result.weather()
                );
                persistSnapshot(result.city(), result.weather());

                VBox card = createWeatherCard(
                        result.city(),
                        result.weather()
                );

                replaceCityNode(result.city(), card);
                playCardEntrance(card);

                updated++;
            }

            updateOverview();
            updateAirQualityAlert();

            if (updated > 0) {
                weatherUpdatedListener.run();
            }

            if (failed > 0) {
                ToastNotifications.show(
                        cityCardsGrid,
                        failed + " city weather request(s) failed during auto-refresh."
                );
            }

            feedbackLabel.setText(
                    "Auto-refresh complete · "
                            + updated + "/" + results.size()
                            + " cities updated · "
                            + UPDATED_FORMAT.format(LocalDateTime.now())
            );
        });

        autoRefreshService.setOnFailed(event -> {
            feedbackLabel.setText(
                    "Automatic weather refresh failed. Existing readings are retained."
            );

            ToastNotifications.show(
                    cityCardsGrid,
                    "Automatic weather refresh failed. Existing readings are retained."
            );
        });

        autoRefreshService.start();
    }

    // ---------------------------------------------------------------------
    // Formatting and helpers
    // ---------------------------------------------------------------------

    private String findCityByKey(String key) {
        for (String city : cityNames) {
            if (cityKey(city).equals(key)) {
                return city;
            }
        }

        return null;
    }

    private static String cityKey(String city) {
        return city == null
                ? ""
                : city.trim().toLowerCase(Locale.ROOT);
    }

    private void persistSnapshot(String city, WeatherData weather) {
        if (disposed) {
            return;
        }

        try {
            WeatherSnapshot snapshot = WeatherSnapshot.from(city, weather);
            weatherHistoryService.saveSnapshotAsync(snapshot)
                    .whenComplete((ignored, error) -> {
                        if (disposed) {
                            return;
                        }

                        if (error == null) {
                            Platform.runLater(() -> {
                                if (!disposed) {
                                    weatherHistorySavedListener.run();
                                }
                            });
                            return;
                        }

                        Throwable cause = error;
                        while (cause instanceof CompletionException
                                && cause.getCause() != null) {
                            cause = cause.getCause();
                        }

                        String message = cause.getMessage() == null
                                || cause.getMessage().isBlank()
                                ? "The database could not save this reading."
                                : cause.getMessage();

                        Platform.runLater(() -> {
                            if (!disposed) {
                                ToastNotifications.show(
                                        cityCardsGrid,
                                        "Could not save weather history: "
                                                + message
                                );
                            }
                        });
                    });
        } catch (IllegalArgumentException exception) {
            ToastNotifications.show(
                    cityCardsGrid,
                    "Could not record weather history: "
                            + exception.getMessage()
            );
        }
    }

    public void setWeatherUpdatedListener(Runnable listener) {
        weatherUpdatedListener = listener == null
                ? () -> { }
                : listener;
    }

    public void setWeatherHistorySavedListener(Runnable listener) {
        weatherHistorySavedListener = listener == null
                ? () -> { }
                : listener;
    }

    private static String safe(
            String value,
            String fallback
    ) {
        return value == null || value.isBlank()
                ? fallback
                : value;
    }

    private void refreshTemperatureDisplay() {
        for (String city : cityNames) {
            WeatherData weather =
                    weatherByCity.get(cityKey(city));

            if (weather != null) {
                VBox card = createWeatherCard(city, weather);
                replaceCityNode(city, card);
            }
        }

        updateOverview();
    }

    /** Reload all known cities after API settings have been changed. */
    public void refreshAll() {
        for (String city : List.copyOf(cityNames)) {
            showLoadingCard(city);
            loadCityInBackground(city);
        }
    }

    private static String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown conditions";
        }

        return value.substring(0, 1).toUpperCase(Locale.ENGLISH)
                + value.substring(1);
    }

    private static String weatherEmoji(WeatherData weather) {
        return WeatherService.getConditionEmoji(weather.getIcon());
    }

    private static String aqiBadgeClass(int aqi) {
        if (aqi <= 2) return "badge-green";
        if (aqi == 3) return "badge-yellow";
        return "badge-red";
    }

    /**
     * Called by the application shell if the dashboard is ever permanently
     * removed. The daemon workers otherwise cannot keep the JVM alive.
     */
    public void dispose() {
        disposed = true;

        if (autoRefreshService != null) {
            autoRefreshService.cancel();
        }

        if (alertTransition != null) {
            alertTransition.stop();
        }

        weatherExecutor.shutdownNow();
        weatherHistoryService.close();
    }

    private record RefreshResult(
            String city,
            WeatherData weather,
            String error
    ) {
    }
}

