package com.atmosphere;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class FxmlLoadingTest {

    @BeforeAll
    static void initJavaFX() throws InterruptedException {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/com/atmosphere/fxml/main.fxml",
            "/com/atmosphere/fxml/dashboard.fxml",
            "/com/atmosphere/fxml/forecast.fxml",
            "/com/atmosphere/fxml/comparison.fxml",
            "/com/atmosphere/fxml/weather-dna.fxml",
            "/com/atmosphere/fxml/aqi.fxml",
            "/com/atmosphere/fxml/insights.fxml",
            "/com/atmosphere/fxml/activity-planner.fxml",
            "/com/atmosphere/fxml/alerts.fxml",
            "/com/atmosphere/fxml/weather-history.fxml"
    })
    @DisplayName("Verify that each FXML view loads and connects its controller without error")
    void testFxmlLoading(String fxmlPath) throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Void> future = new CompletableFuture<>();

        Platform.runLater(() -> {
            try {
                URL resource = getClass().getResource(fxmlPath);
                assertNotNull(resource, "FXML resource not found: " + fxmlPath);

                FXMLLoader loader = new FXMLLoader(resource);
                Object root = loader.load();
                assertNotNull(root, "Root node should not be null for " + fxmlPath);
                assertNotNull(loader.getController(), "Controller should be set for " + fxmlPath);

                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        future.get(10, TimeUnit.SECONDS);
    }
}
