
package com.atmosphere.util;

import com.atmosphere.config.AppConfig;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;

import java.util.Locale;

/** Formats Celsius readings in the user's preferred unit and applies the requested heat colors. */
public final class TemperatureStyler {

    private TemperatureStyler() {
    }

    public static double toDisplayValue(double celsius) {
        return AppConfig.isFahrenheit()
                ? celsius * 9.0 / 5.0 + 32.0
                : celsius;
    }

    public static String unitSuffix() {
        return AppConfig.isFahrenheit() ? "°F" : "°C";
    }

    public static String format(double celsius) {
        return String.format(
                Locale.ENGLISH,
                "%.0f%s",
                toDisplayValue(celsius),
                unitSuffix()
        );
    }

    public static String formatOneDecimal(double celsius) {
        return String.format(
                Locale.ENGLISH,
                "%.1f%s",
                toDisplayValue(celsius),
                unitSuffix()
        );
    }

    public static void apply(Label label, double celsius) {
        apply(label, celsius, "");
    }

    public static void apply(
            Label label,
            double celsius,
            String prefix
    ) {
        if (label == null) {
            return;
        }

        label.setText(
                (prefix == null ? "" : prefix) + format(celsius)
        );

        label.setTextFill(colorForCelsius(celsius));
    }

    public static Color colorForCelsius(double celsius) {
        if (celsius < 10.0) {
            return Color.web("#80D6C5");
        }

        if (celsius <= 25.0) {
            return Color.web("#F0F3F4");
        }

        if (celsius <= 35.0) {
            return Color.web("#F2BB72");
        }

        return Color.web("#EF7D7D");
    }
}

