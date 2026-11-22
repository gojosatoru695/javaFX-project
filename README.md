
# AtmoSphere — Weather Intelligence

A Java 21 / JavaFX 21 desktop weather dashboard built with Maven.

## Requirements

- JDK 21
- Maven 3.9+
- Internet connection
- OpenWeatherMap API key
- Gemini API key (optional)

## Configure API keys and preferences

1. Start AtmoSphere with `mvn clean javafx:run`.
2. Open **Settings** from the sidebar.
3. Enter your OpenWeatherMap and Gemini API keys in the masked fields.
4. Choose Celsius or Fahrenheit and save.

Settings are stored in:

```text
~/.atmosphere/config.properties
```

On Windows:

```text
%USERPROFILE%\.atmosphere\config.properties
```

The application creates the directory and writes through a temporary
file before replacing its own settings file.

On POSIX filesystems, file permissions are restricted to the
current user.

The key values are stored as ordinary Java properties, not encrypted.
Protect access to your operating-system account and do not share
the configuration file.

The configuration file is outside the repository and is not committed.

Example file:

```properties
openweathermap.apiKey=YOUR_OPENWEATHERMAP_KEY
gemini.apiKey=YOUR_GEMINI_KEY
temperature.unit=C
```

## Run

```bash
mvn clean javafx:run
```

## Features

- Multi-city current weather dashboard:
  Bengaluru, Mumbai and Delhi by default.
- OpenWeatherMap current conditions and air-pollution components.
- Java HttpClient for weather API requests.
- Five-day forecast aggregation and temperature chart.
- Smart Activity Planner for walking, jogging and cycling using upcoming
  three-hour weather slots and explainable activity-specific scores.
- Intelligent Weather Alert Centre for current AQI/conditions and upcoming
  forecast thresholds across registered cities, with severity filtering
  and read-state tracking.
- Weather History & Anomaly Detection with local SQLite snapshots, trend
  charts, recent readings and deterministic change thresholds.
- AQI gauge, health advisory and pollutant cards.
- Optional Gemini daily weather insights.
- Celsius/Fahrenheit display preference.
- Temperature color coding.
- Startup splash screen.
- Application icon fallback.
- Card hover animations.
- API error notifications.

## Screen loading behavior

The Dashboard screen is loaded at startup.

Forecast, Activity Planner, Weather Alerts, Weather History, Air Quality and AI Insights controllers
are loaded the first time their navigation item is selected,
then cached.

This prevents unseen screens from issuing network requests
during startup.

The Activity Planner uses the latest current AQI reading as context
for its scores. It does not treat AQI as a forecast.

The Weather Alert Centre checks current AQI, temperature and wind, plus
upcoming three-hour temperature, wind and precipitation probabilities.
Default thresholds are 4 AQI, 70% precipitation, 40 km/h wind, 35°C high
temperature and 5°C low temperature; the alert service accepts alternate
threshold sets for configuration. Alerts use actual API readings and do
not generate synthetic weather data. Temperature thresholds remain defined
in Celsius, while displayed alert temperatures follow the selected unit.
Alerts at the configured threshold are Warnings; extreme readings are
escalated to Critical.

The Weather History screen records a snapshot only after a successful
Dashboard weather response. Its SQLite database is stored outside the
project at `~/.atmosphere/weather-history.db`. Temperature changes of at
least 5°C, AQI changes of at least two levels and wind increases of at
least 20 km/h are shown when consecutive saved readings are available.
History temperature values follow the selected display unit; anomaly
thresholds and stored readings remain in Celsius. History begins empty and
is never populated with sample weather data.

The dashboard's three default city requests run in the background.

Newly searched cities are added to shared selectors only after
a successful weather lookup. Failed searches are removed.

## Gemini diagnostics

`GeminiService.getInsight()` retains its simple fallback-string API.

`getInsightResult()` additionally returns a diagnostic message
for missing keys, HTTP authentication errors, permission errors,
quota/rate limits, network failures and invalid responses.

The UI displays that diagnostic while remaining operational.

