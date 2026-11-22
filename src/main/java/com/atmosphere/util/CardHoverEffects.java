
package com.atmosphere.util;

import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.util.Duration;

/** Reusable 150ms hover scale animation for dashboard cards. */
public final class CardHoverEffects {

    private static final Duration DURATION = Duration.millis(150);

    private static final String TRANSITION_KEY =
            CardHoverEffects.class.getName() + ".transition";

    private CardHoverEffects() {
    }

    public static void install(Node node) {
        if (node == null
                || Boolean.TRUE.equals(
                        node.getProperties().get(TRANSITION_KEY + ".installed")
                )) {
            return;
        }

        node.getProperties().put(
                TRANSITION_KEY + ".installed",
                Boolean.TRUE
        );

        node.setOnMouseEntered(event -> animate(node, 1.02));
        node.setOnMouseExited(event -> animate(node, 1.0));
    }

    public static void installRecursively(Node node) {
        if (node == null) {
            return;
        }

        if (isCard(node)) {
            install(node);
        }

        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                installRecursively(child);
            }
        }
    }

    private static boolean isCard(Node node) {
        return node.getStyleClass().contains("card")
                || node.getStyleClass().contains("hero-weather-card")
                || node.getStyleClass().contains("metric-card")
                || node.getStyleClass().contains("forecast-card");
    }

    private static void animate(Node node, double targetScale) {
        Object previous = node.getProperties().get(TRANSITION_KEY);

        if (previous instanceof ScaleTransition transition) {
            transition.stop();
        }

        ScaleTransition transition =
                new ScaleTransition(DURATION, node);

        transition.setToX(targetScale);
        transition.setToY(targetScale);
        transition.setInterpolator(Interpolator.EASE_BOTH);

        node.getProperties().put(TRANSITION_KEY, transition);
        transition.playFromStart();
    }
}

