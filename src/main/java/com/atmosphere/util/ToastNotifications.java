
package com.atmosphere.util;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.beans.value.ChangeListener;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.Map;
import java.util.WeakHashMap;

/** Small bottom-right notification shared by every AtmoSphere screen. */
public final class ToastNotifications {

    private static final Map<Window, Popup> ACTIVE_TOASTS =
            new WeakHashMap<>();

    private static Window fallbackOwner;

    private ToastNotifications() {
    }

    public static void setOwnerWindow(Window owner) {
        fallbackOwner = owner;
    }

    public static void show(Node anchor, String message) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> show(anchor, message));
            return;
        }

        Window resolvedOwner = null;

        if (anchor != null && anchor.getScene() != null) {
            resolvedOwner = anchor.getScene().getWindow();
        }

        // A screen can report a problem before its view is attached; use the
        // main window until the anchor has a visible scene.
        if (resolvedOwner == null) {
            resolvedOwner = fallbackOwner;
        }

        if (resolvedOwner == null) {
            return;
        }

        final Window owner = resolvedOwner;

        if (!owner.isShowing()) {
            ChangeListener<Boolean> listener = new ChangeListener<>() {
                @Override
                public void changed(
                        javafx.beans.value.ObservableValue<? extends Boolean> observable,
                        Boolean wasShowing,
                        Boolean isShowing
                ) {
                    if (isShowing) {
                        owner.showingProperty().removeListener(this);
                        Platform.runLater(() -> show(anchor, message));
                    }
                }
            };

            owner.showingProperty().addListener(listener);
            return;
        }

        Popup previous = ACTIVE_TOASTS.remove(owner);

        if (previous != null) {
            previous.hide();
        }

        Label text = new Label(
                message == null || message.isBlank()
                        ? "Something went wrong. Please try again."
                        : message
        );

        text.setWrapText(true);
        text.setMaxWidth(340);
        text.setStyle(
                "-fx-text-fill: #F0F3F4;"
                        + "-fx-font-size: 12px;"
                        + "-fx-font-weight: 600;"
        );

        VBox toast = new VBox(text);
        toast.setMaxWidth(380);
        toast.setStyle(
                "-fx-background-color: #141B21;"
                        + "-fx-border-color: #29353D;"
                        + "-fx-border-width: 1px;"
                        + "-fx-background-radius: 10px;"
                        + "-fx-border-radius: 10px;"
                        + "-fx-padding: 14px 18px 14px 18px;"
                        + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.50), 18px, 0.12, 0, 4px);"
        );

        toast.setOpacity(0.0);
        toast.setTranslateY(18.0);

        Popup popup = new Popup();
        popup.getContent().add(toast);
        popup.setAutoFix(true);
        popup.setAutoHide(false);
        popup.setHideOnEscape(false);
        popup.show(owner);

        toast.applyCss();
        toast.layout();

        double x = owner.getX()
                + owner.getWidth()
                - toast.prefWidth(-1)
                - 24.0;

        double y = owner.getY()
                + owner.getHeight()
                - toast.prefHeight(-1)
                - 28.0;

        popup.setX(Math.max(8.0, x));
        popup.setY(Math.max(8.0, y));

        ACTIVE_TOASTS.put(owner, popup);

        FadeTransition fadeIn =
                new FadeTransition(Duration.millis(180), toast);

        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        TranslateTransition slideIn =
                new TranslateTransition(Duration.millis(180), toast);

        slideIn.setFromY(18.0);
        slideIn.setToY(0.0);

        new ParallelTransition(fadeIn, slideIn).play();

        javafx.animation.PauseTransition dismiss =
                new javafx.animation.PauseTransition(Duration.seconds(3));

        dismiss.setOnFinished(event -> {
            FadeTransition fadeOut =
                    new FadeTransition(Duration.millis(180), toast);

            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);

            TranslateTransition slideOut =
                    new TranslateTransition(Duration.millis(180), toast);

            slideOut.setFromY(0.0);
            slideOut.setToY(12.0);

            ParallelTransition hide =
                    new ParallelTransition(fadeOut, slideOut);

            hide.setOnFinished(done -> {
                popup.hide();
                ACTIVE_TOASTS.remove(owner, popup);
            });

            hide.play();
        });

        dismiss.play();
    }
}

