
package com.atmosphere;

import com.atmosphere.config.AppConfig;
import com.atmosphere.controller.MainController;
import com.atmosphere.util.ToastNotifications;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class AtmosphereApp extends Application {

    private static final double INITIAL_WIDTH = 1200;
    private static final double INITIAL_HEIGHT = 800;
    private static final Duration SPLASH_DURATION = Duration.seconds(2);

    private Stage splashStage;
    private Timeline splashProgressTimeline;
    private MainController mainController;

    @Override
    public void start(Stage stage) throws IOException {
        // Load stored API keys and display preferences before loading FXML;
        // FXML controller initialize() methods start background API requests.
        AppConfig.load();
        ToastNotifications.setOwnerWindow(stage);

        FXMLLoader mainLoader = new FXMLLoader(
                getClass().getResource("/com/atmosphere/fxml/main.fxml")
        );
        Parent root = mainLoader.load();
        mainController = mainLoader.getController();

        Scene scene = new Scene(root, INITIAL_WIDTH, INITIAL_HEIGHT);
        scene.setFill(Color.web("#0A0F1E"));
        addStylesheet(scene, "/com/atmosphere/css/theme.css");
        addStylesheet(scene, "/com/atmosphere/css/animations.css");

        stage.setTitle("AtmoSphere — Weather Intelligence");
        stage.setScene(scene);
        stage.setMinWidth(INITIAL_WIDTH);
        stage.setMinHeight(INITIAL_HEIGHT);
        stage.getIcons().add(loadApplicationIcon());
        stage.setOnCloseRequest(event -> mainController.dispose());
        stage.centerOnScreen();

        showSplashThen(stage, root);
    }

    private void showSplashThen(Stage mainStage, Parent mainRoot) {
        splashStage = new Stage(StageStyle.UNDECORATED);

        Label title = new Label("AtmoSphere");
        title.getStyleClass().add("splash-title");

        Label subtitle = new Label("Weather Intelligence");
        subtitle.getStyleClass().add("splash-subtitle");

        ProgressBar progress = new ProgressBar(0.0);
        progress.setPrefWidth(300);
        progress.setMaxWidth(300);
        progress.getStyleClass().add("splash-progress");

        Label loading = new Label("Preparing your atmosphere…");
        loading.getStyleClass().add("splash-loading");

        VBox content = new VBox(15, title, subtitle, progress, loading);
        content.setAlignment(Pos.CENTER);
        content.setPrefSize(500, 300);
        content.getStyleClass().add("splash-root");

        Scene splashScene = new Scene(content, 500, 300, Color.web("#0A0F1E"));
        URL theme = getClass().getResource("/com/atmosphere/css/theme.css");
        if (theme != null) splashScene.getStylesheets().add(theme.toExternalForm());
        URL animations = getClass().getResource("/com/atmosphere/css/animations.css");
        if (animations != null) splashScene.getStylesheets().add(animations.toExternalForm());

        splashStage.setScene(splashScene);
        splashStage.setResizable(false);
        splashStage.centerOnScreen();
        splashStage.show();

        splashProgressTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(progress.progressProperty(), 0.0)),
                new KeyFrame(SPLASH_DURATION,
                        new KeyValue(progress.progressProperty(), 1.0, Interpolator.EASE_BOTH))
        );

        splashProgressTimeline.setOnFinished(event -> {
            FadeTransition fadeOut = new FadeTransition(
                    Duration.millis(260),
                    content
            );

            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);

            fadeOut.setOnFinished(done -> {
                splashStage.close();
                mainStage.show();
                mainRoot.setOpacity(0.0);

                FadeTransition mainFade = new FadeTransition(
                        Duration.millis(380),
                        mainRoot
                );

                mainFade.setFromValue(0.0);
                mainFade.setToValue(1.0);
                mainFade.play();
            });

            fadeOut.play();
        });

        splashProgressTimeline.playFromStart();
    }

    private Image loadApplicationIcon() {
        try (InputStream input = getClass().getResourceAsStream(
                "/com/atmosphere/images/app-icon.png")) {

            if (input != null) {
                Image image = new Image(input);
                if (!image.isError()) return image;
            }

        } catch (IOException ignored) {
            // Fall back to a generated icon when no image is packaged.
        }

        return createFallbackIcon();
    }

    /** Draw a blue circular weather mark into a WritableImage as a fallback. */
    private WritableImage createFallbackIcon() {
        Canvas canvas = new Canvas(64, 64);
        GraphicsContext graphics = canvas.getGraphicsContext2D();

        graphics.setFill(Color.web("#0A0F1E"));
        graphics.fillRect(0, 0, 64, 64);

        graphics.setFill(Color.web("#38BDF8"));
        graphics.fillOval(3, 3, 58, 58);

        graphics.setFill(Color.web("#082F49"));
        graphics.setFont(Font.font("Segoe UI Emoji", FontWeight.BOLD, 34));
        graphics.setTextAlign(TextAlignment.CENTER);
        graphics.fillText("☁", 32, 43);

        return canvas.snapshot(null, new WritableImage(64, 64));
    }

    private void addStylesheet(Scene scene, String resourcePath)
            throws IOException {

        URL stylesheet = getClass().getResource(resourcePath);

        if (stylesheet == null) {
            throw new IOException(
                    "Unable to find stylesheet: " + resourcePath
            );
        }

        scene.getStylesheets().add(stylesheet.toExternalForm());
    }

    @Override
    public void stop() {
        if (mainController != null) {
            mainController.dispose();
        }

        if (splashProgressTimeline != null) {
            splashProgressTimeline.stop();
        }

        if (splashStage != null && splashStage.isShowing()) {
            splashStage.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}


