
package com.atmosphere.service;

import com.atmosphere.config.AppConfig;
import com.atmosphere.model.WeatherData;
import com.atmosphere.util.TemperatureStyler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Gemini weather insight service with diagnostic error information.
 */
public class GeminiService {

    private static final String MODEL_NAME =
            "gemini-3.8-flash";

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/"
                    + MODEL_NAME
                    + ":generateContent?key=";

    private static final String FALLBACK =
            "AI insight unavailable";

    private final HttpClient httpClient =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

    /**
     * Compatibility method:
     * never throws and always returns display text.
     */
    public String getInsight(WeatherData data) {
        return getInsightResult(data).text();
    }

    /**
     * Returns display text and an actionable diagnostic on failure.
     */
    public InsightResult getInsightResult(WeatherData data) {

        if (data == null) {
            return new InsightResult(
                    FALLBACK,
                    "No weather data was provided.",
                    false
            );
        }

        String apiKey = AppConfig.getGeminiApiKey();

        if (apiKey == null || apiKey.isBlank()) {

            return new InsightResult(
                    FALLBACK,
                    "Gemini API key is missing. Add it in Settings.",
                    false
            );
        }

        String prompt = String.format(
                java.util.Locale.ROOT,

                "Weather in %s: %s, %s, humidity %.0f%%, "
                        + "wind %.1f km/h, AQI %d. "
                        + "Give a single warm, conversational 2-sentence "
                        + "insight for someone planning their day. "
                        + "No bullet points.",

                safe(data.getCityName()),

                TemperatureStyler.formatOneDecimal(
                        data.getTemperature()
                ),

                safe(data.getDescription()),

                data.getHumidity(),
                data.getWindSpeed(),
                data.getAqi()
        );

        try {

            // Build Gemini JSON request.

            JsonObject part = new JsonObject();
            part.addProperty("text", prompt);

            JsonArray parts = new JsonArray();
            parts.add(part);

            JsonObject content = new JsonObject();
            content.add("parts", parts);

            JsonArray contents = new JsonArray();
            contents.add(content);

            JsonObject payload = new JsonObject();
            payload.add("contents", contents);

            String encodedKey = URLEncoder.encode(
                    apiKey.trim(),
                    StandardCharsets.UTF_8
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_URL + encodedKey))
                    .timeout(Duration.ofSeconds(25))
                    .header(
                            "Content-Type",
                            "application/json; charset=UTF-8"
                    )
                    .POST(
                            HttpRequest.BodyPublishers.ofString(
                                    payload.toString()
                            )
                    )
                    .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString(
                                    StandardCharsets.UTF_8
                            )
                    );

            // Preserve HTTP-specific failure details.

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {

                return new InsightResult(
                        FALLBACK,
                        describeHttpFailure(
                                response.statusCode(),
                                response.body()
                        ),
                        false
                );
            }

            // Parse Gemini response.

            JsonObject root = JsonParser.parseString(
                    response.body()
            ).getAsJsonObject();

            JsonArray candidates =
                    root.getAsJsonArray("candidates");

            if (candidates == null || candidates.isEmpty()) {

                return new InsightResult(
                        FALLBACK,
                        "Gemini returned no candidates. "
                                + "The request may have been blocked "
                                + "by safety filters or quota policy.",
                        false
                );
            }

            JsonObject candidate =
                    candidates.get(0).getAsJsonObject();

            JsonObject responseContent =
                    candidate.getAsJsonObject("content");

            if (responseContent == null) {

                return new InsightResult(
                        FALLBACK,
                        "Gemini response did not contain content.",
                        false
                );
            }

            JsonArray responseParts =
                    responseContent.getAsJsonArray("parts");

            if (responseParts == null || responseParts.isEmpty()) {

                return new InsightResult(
                        FALLBACK,
                        "Gemini response contained no text parts.",
                        false
                );
            }

            StringBuilder text = new StringBuilder();

            for (JsonElement element : responseParts) {

                if (element.isJsonObject()) {

                    JsonElement textElement =
                            element.getAsJsonObject().get("text");

                    if (textElement != null
                            && !textElement.isJsonNull()) {

                        if (!text.isEmpty()) {
                            text.append('\n');
                        }

                        text.append(textElement.getAsString());
                    }
                }
            }

            String insight = text.toString().trim();

            if (insight.isBlank()) {

                return new InsightResult(
                        FALLBACK,
                        "Gemini returned an empty text response.",
                        false
                );
            }

            return new InsightResult(
                    insight,
                    "",
                    true
            );

        } catch (InterruptedException exception) {

            Thread.currentThread().interrupt();

            return new InsightResult(
                    FALLBACK,
                    "Gemini request was interrupted.",
                    false
            );

        } catch (IOException exception) {

            return new InsightResult(
                    FALLBACK,
                    "Network error while contacting Gemini: "
                            + safeMessage(exception),
                    false
            );

        } catch (RuntimeException exception) {

            return new InsightResult(
                    FALLBACK,
                    "Could not parse Gemini response: "
                            + safeMessage(exception),
                    false
            );

        } catch (Exception exception) {

            return new InsightResult(
                    FALLBACK,
                    "Unexpected Gemini error: "
                            + safeMessage(exception),
                    false
            );
        }
    }

    /**
     * Produces a useful message for common Gemini HTTP failures.
     */
    private static String describeHttpFailure(
            int status,
            String body
    ) {

        String providerMessage = "";

        try {

            JsonObject root = JsonParser.parseString(body)
                    .getAsJsonObject();

            JsonObject error = root.getAsJsonObject("error");

            if (error != null) {
                providerMessage = string(
                        error,
                        "message",
                        ""
                );
            }

        } catch (Exception ignored) {
            // Avoid exposing unparseable or verbose HTML error bodies.
        }

        String category = switch (status) {

            case 400 ->
                    "Bad request; check the request payload/model name";

            case 401, 403 ->
                    "Authentication or permission error; check the API key";

            case 404 ->
                    "Model or endpoint not found";

            case 429 ->
                    "Quota or rate limit exceeded";

            case 500, 502, 503, 504 ->
                    "Gemini service is temporarily unavailable";

            default ->
                    "Gemini returned an HTTP error";
        };

        return category
                + " (HTTP " + status + ")"
                + (
                        providerMessage.isBlank()
                                ? "."
                                : ": " + providerMessage
                );
    }

    private static String string(
            JsonObject object,
            String key,
            String fallback
    ) {

        JsonElement value = object.get(key);

        return value == null || value.isJsonNull()
                ? fallback
                : value.getAsString();
    }

    private static String safe(String value) {

        return value == null || value.isBlank()
                ? "Unknown"
                : value;
    }

    private static String safeMessage(Throwable throwable) {

        String message = throwable.getMessage();

        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName()
                : message;
    }

    /**
     * Immutable result used by InsightsController.
     */
    public record InsightResult(
            String text,
            String error,
            boolean successful
    ) {
    }
}



