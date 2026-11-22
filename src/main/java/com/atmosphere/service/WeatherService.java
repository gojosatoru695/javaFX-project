
package com.atmosphere.service;

import com.atmosphere.config.AppConfig;
import com.atmosphere.model.ForecastData;
import com.atmosphere.model.ForecastSlot;
import com.atmosphere.model.WeatherData;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class WeatherService {

    private static final String CURRENT_URL =
            "https://api.openweathermap.org/data/2.5/weather";
    private static final String AIR_URL =
            "https://api.openweathermap.org/data/2.5/air_pollution";
    private static final String FORECAST_URL =
            "https://api.openweathermap.org/data/2.5/forecast";

    private static final DateTimeFormatter SUN_FORMAT =
            DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_FORMAT =
            DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public WeatherData getCurrentWeather(String city) {
        validate(city);

        String key = enc(requireApiKey());

        JsonObject root = getJson(
                CURRENT_URL + "?q=" + enc(city.trim())
                        + "&appid=" + key + "&units=metric"
        );

        JsonObject coordinates = object(root, "coord");
        JsonObject main = object(root, "main");
        JsonObject sys = objectOrEmpty(root, "sys");
        JsonObject wind = objectOrEmpty(root, "wind");

        JsonObject condition = firstObject(
                root.getAsJsonArray("weather"),
                "Current weather response did not include a condition."
        );

        double lat = number(coordinates, "lat", Double.NaN);
        double lon = number(coordinates, "lon", Double.NaN);

        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            throw new RuntimeException(
                    "Current weather response did not include coordinates."
            );
        }

        JsonObject airRoot = getJson(
                AIR_URL + "?lat=" + lat
                        + "&lon=" + lon
                        + "&appid=" + key
        );

        JsonObject airEntry = firstObject(
                airRoot.getAsJsonArray("list"),
                "Air-quality response did not include AQI data."
        );

        int aqi = (int) number(
                object(airEntry, "main"),
                "aqi",
                -1
        );

        if (aqi < 1) {
            throw new RuntimeException(
                    "Air-quality response contained an invalid AQI value."
            );
        }

        JsonObject components = objectOrEmpty(airEntry, "components");

        ZoneOffset offset = zoneOffset(
                (int) number(root, "timezone", 0)
        );

        WeatherData data = new WeatherData();

        data.setCityName(string(root, "name", city.trim()));
        data.setCountryCode(string(sys, "country", ""));
        data.setDescription(
                string(condition, "description", "Unknown conditions")
        );
        data.setIcon(string(condition, "icon", ""));
        data.setCondition(string(condition, "main", "Unknown"));

        data.setTemperature(number(main, "temp", 0));
        data.setFeelsLike(number(main, "feels_like", 0));
        data.setHumidity(number(main, "humidity", 0));

        // API returns wind speed in m/s; convert to km/h.
        data.setWindSpeed(number(wind, "speed", 0) * 3.6);

        data.setPressure(number(main, "pressure", 0));

        // API returns visibility in metres; convert to kilometres.
        data.setVisibility(number(root, "visibility", 0) / 1000.0);

        data.setAqi(aqi);
        data.setAqiLabel(aqiLabel(aqi));

        data.setPm25(number(components, "pm2_5", 0));
        data.setPm10(number(components, "pm10", 0));
        data.setNo2(number(components, "no2", 0));
        data.setO3(number(components, "o3", 0));

        data.setSunrise(
                formatTime((long) number(sys, "sunrise", 0), offset)
        );
        data.setSunset(
                formatTime((long) number(sys, "sunset", 0), offset)
        );

        data.setFetchedAt(LocalDateTime.now());

        return data;
    }

    public List<ForecastData> getForecast(String city) {
        JsonObject root = getForecastResponse(city);
        JsonArray entries = root.getAsJsonArray("list");

        JsonObject cityInfo = objectOrEmpty(root, "city");

        ZoneOffset offset = zoneOffset(
                (int) number(cityInfo, "timezone", 0)
        );

        Map<LocalDate, DailySummary> days = new TreeMap<>();

        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject item = element.getAsJsonObject();

            LocalDateTime localDateTime =
                    Instant.ofEpochSecond(
                            (long) number(item, "dt", 0)
                    )
                    .atOffset(offset)
                    .toLocalDateTime();

            JsonObject main = object(item, "main");

            JsonObject condition = firstObject(
                    item.getAsJsonArray("weather"),
                    "Forecast entry did not include a weather condition."
            );

            double temp = number(main, "temp", 0);
            double high = number(main, "temp_max", temp);
            double low = number(main, "temp_min", temp);

            int rain = (int) Math.round(
                    number(item, "pop", 0) * 100
            );

            days.computeIfAbsent(
                    localDateTime.toLocalDate(),
                    d -> new DailySummary()
            ).add(
                    high,
                    low,
                    rain,
                    string(condition, "description", "Unknown conditions"),
                    string(condition, "icon", ""),
                    localDateTime.toLocalTime()
            );
        }

        List<ForecastData> result = new ArrayList<>(5);

        for (Map.Entry<LocalDate, DailySummary> entry : days.entrySet()) {
            if (result.size() == 5) {
                break;
            }

            LocalDate date = entry.getKey();
            DailySummary summary = entry.getValue();

            ForecastData day = new ForecastData();

            day.setDate(date.toString());
            day.setDayName(date.format(DAY_FORMAT));
            day.setDescription(summary.description);
            day.setIcon(summary.icon);
            day.setTempHigh(summary.high);
            day.setTempLow(summary.low);
            day.setRainChance(summary.rainChance);

            result.add(day);
        }

        if (result.size() != 5) {
            throw new RuntimeException(
                    "Forecast API returned fewer than five forecast days."
            );
        }

        return result;
    }

    /**
     * Returns the upcoming three-hour forecast entries in the city's local
     * time. Temperatures are Celsius and wind speeds are km/h.
     */
    public List<ForecastSlot> getForecastSlots(String city) {
        JsonObject root = getForecastResponse(city);
        JsonArray entries = root.getAsJsonArray("list");
        JsonObject cityInfo = objectOrEmpty(root, "city");

        ZoneOffset offset = zoneOffset(
                (int) number(cityInfo, "timezone", 0)
        );

        Instant now = Instant.now();
        List<ForecastSlot> slots = new ArrayList<>();

        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject item = element.getAsJsonObject();
            long epochSeconds = (long) number(item, "dt", 0);

            if (epochSeconds <= 0) {
                continue;
            }

            Instant instant = Instant.ofEpochSecond(epochSeconds);

            if (!instant.isAfter(now)) {
                continue;
            }

            JsonObject main = object(item, "main");
            JsonObject wind = objectOrEmpty(item, "wind");
            JsonObject condition = firstObject(
                    item.getAsJsonArray("weather"),
                    "Forecast entry did not include a weather condition."
            );

            double temperature = number(
                    main,
                    "temp",
                    Double.NaN
            );
            double windSpeedMetersPerSecond = number(
                    wind,
                    "speed",
                    Double.NaN
            );
            double precipitationProbability = number(
                    item,
                    "pop",
                    0
            );

            if (!Double.isFinite(temperature)
                    || !Double.isFinite(windSpeedMetersPerSecond)
                    || windSpeedMetersPerSecond < 0
                    || !Double.isFinite(precipitationProbability)) {
                throw new RuntimeException(
                        "Forecast entry contained invalid weather values."
                );
            }

            ForecastSlot slot = new ForecastSlot();

            slot.setDateTime(
                    instant.atOffset(offset).toLocalDateTime()
            );
            slot.setTemperatureCelsius(temperature);
            slot.setWindSpeedKmh(
                    windSpeedMetersPerSecond * 3.6
            );
            slot.setDescription(
                    string(
                            condition,
                            "description",
                            "Unknown conditions"
                    )
            );
            slot.setPrecipitationProbability(
                    (int) Math.round(
                            Math.max(
                                    0,
                                    Math.min(1, precipitationProbability)
                            ) * 100
                    )
            );

            slots.add(slot);
        }

        if (slots.isEmpty()) {
            throw new RuntimeException(
                    "Forecast API returned no upcoming three-hour slots."
            );
        }

        return slots;
    }

    private JsonObject getForecastResponse(String city) {
        validate(city);

        String url = FORECAST_URL
                + "?q=" + enc(city.trim())
                + "&appid=" + enc(requireApiKey())
                + "&units=metric&cnt=40";

        JsonObject root = getJson(url);
        JsonArray entries = root.getAsJsonArray("list");

        if (entries == null || entries.isEmpty()) {
            throw new RuntimeException(
                    "Forecast API returned no forecast entries."
            );
        }

        return root;
    }

    private String requireApiKey() {
        String key = AppConfig.getOpenWeatherApiKey();

        if (key == null || key.isBlank()) {
            throw new RuntimeException(
                    "OpenWeatherMap API key is missing. "
                            + "Open Settings (⚙) to add it."
            );
        }

        return key;
    }

    private JsonObject getJson(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8
                    )
            );

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {

                String message =
                        "API returned HTTP " + response.statusCode();

                try {
                    message = string(
                            JsonParser.parseString(response.body())
                                    .getAsJsonObject(),
                            "message",
                            message
                    );
                } catch (Exception ignored) {
                    // Keep the HTTP status message when the error body is not JSON.
                }

                throw new RuntimeException(
                        "OpenWeatherMap request failed: " + message
                );
            }

            JsonElement parsed =
                    JsonParser.parseString(response.body());

            if (!parsed.isJsonObject()) {
                throw new RuntimeException(
                        "OpenWeatherMap returned an invalid JSON response."
                );
            }

            return parsed.getAsJsonObject();

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new RuntimeException(
                    "OpenWeatherMap request was interrupted.",
                    exception
            );

        } catch (IOException exception) {
            throw new RuntimeException(
                    "Unable to connect to OpenWeatherMap.",
                    exception
            );

        } catch (RuntimeException exception) {
            throw exception;

        } catch (Exception exception) {
            throw new RuntimeException(
                    "Unable to process OpenWeatherMap response.",
                    exception
            );
        }
    }

    private void validate(String city) {
        if (city == null || city.isBlank()) {
            throw new RuntimeException("City name cannot be empty.");
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);

        if (value == null || !value.isJsonObject()) {
            throw new RuntimeException(
                    "Weather API response is missing '" + key + "'."
            );
        }

        return value.getAsJsonObject();
    }

    private static JsonObject objectOrEmpty(
            JsonObject parent,
            String key
    ) {
        JsonElement value = parent.get(key);

        return value != null && value.isJsonObject()
                ? value.getAsJsonObject()
                : new JsonObject();
    }

    private static JsonObject firstObject(
            JsonArray array,
            String message
    ) {
        if (array == null || array.isEmpty()
                || !array.get(0).isJsonObject()) {
            throw new RuntimeException(message);
        }

        return array.get(0).getAsJsonObject();
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

    private static double number(
            JsonObject object,
            String key,
            double fallback
    ) {
        JsonElement value = object.get(key);

        return value == null || value.isJsonNull()
                ? fallback
                : value.getAsDouble();
    }

    private static ZoneOffset zoneOffset(int seconds) {
        try {
            return ZoneOffset.ofTotalSeconds(seconds);
        } catch (RuntimeException exception) {
            return ZoneOffset.UTC;
        }
    }

    private static String formatTime(
            long epochSeconds,
            ZoneOffset offset
    ) {
        if (epochSeconds <= 0) {
            return "N/A";
        }

        return Instant.ofEpochSecond(epochSeconds)
                .atOffset(offset)
                .toLocalTime()
                .format(SUN_FORMAT);
    }

    /** Maps OpenWeatherMap icon codes to the app's weather emoji. */
    public static String getConditionEmoji(String iconCode) {
        if (iconCode == null || iconCode.isBlank()) {
            return "☁️";
        }

        String code = iconCode.trim().toLowerCase(Locale.ROOT);

        return switch (code) {
            case "01d" -> "☀️";
            case "01n" -> "🌙";
            case "02d" -> "⛅";
            case "02n" -> "🌙";
            default -> {
                if (code.startsWith("03") || code.startsWith("04")) {
                    yield "☁️";
                }
                if (code.startsWith("09")) {
                    yield "🌧️";
                }
                if (code.startsWith("10")) {
                    yield "🌦️";
                }
                if (code.startsWith("11")) {
                    yield "⛈️";
                }
                if (code.startsWith("13")) {
                    yield "❄️";
                }
                if (code.startsWith("50")) {
                    yield "🌫️";
                }
                yield "☁️";
            }
        };
    }

    private static String aqiLabel(int aqi) {
        return switch (aqi) {
            case 1 -> "Good";
            case 2 -> "Fair";
            case 3 -> "Moderate";
            case 4 -> "Poor";
            case 5 -> "Very Poor";
            default -> aqi > 5 ? "Hazardous" : "Unknown";
        };
    }

    private static final class DailySummary {

        private double high = Double.NEGATIVE_INFINITY;
        private double low = Double.POSITIVE_INFINITY;

        private int rainChance;

        private String description = "Unknown conditions";
        private String icon = "";

        private long closestToNoon = Long.MAX_VALUE;

        private void add(
                double entryHigh,
                double entryLow,
                int rain,
                String entryDescription,
                String entryIcon,
                LocalTime time
        ) {
            high = Math.max(high, entryHigh);
            low = Math.min(low, entryLow);
            rainChance = Math.max(rainChance, rain);

            long noonDistance = Math.abs(
                    Duration.between(LocalTime.NOON, time).toMinutes()
            );

            if (noonDistance < closestToNoon) {
                closestToNoon = noonDistance;
                description = entryDescription;
                icon = entryIcon;
            }
        }
    }
}



