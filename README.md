# AtmoSphere — Weather Intelligence Observatory

A premium desktop weather intelligence dashboard built with **Java 21**, **JavaFX 21**, and **Maven**. Designed with a deep graphite atmospheric observatory aesthetic, deterministic comfort intelligence, and multi-city comparative analytics.

---

## 🌟 Key Features

### 1. Visual Transformation — Atmospheric Observatory Aesthetic
- **Graphite Foundation Palette**: Deep graphite surfaces (`#0B0F13`, `#141B21`, `#1B252D`, `#29353D`) accented with calming mint (`#80D6C5`), warm amber (`#F2BB72`), and coral alert (`#EF7D7D`).
- **Refined Typography**: High-legibility modern typographic hierarchy (`Manrope` display, `Inter` body, and `JetBrains Mono` / `Consolas` monospaced data readouts) with system fallbacks.
- **Layered Surfaces & Visual Depth**: 1px subtle borders, gentle drop shadows, and dark graphite skeleton loading states.
- **Fluid Micro-Interactions**: Restrained 150ms hover elevations, animated gauge needles, and smooth screen transitions.

### 2. Feature One — Personal Weather DNA
- **Personalized Preference System**: Configure ideal minimum and maximum temperature, humidity comfort bands, maximum wind tolerance, and air quality sensitivity (`Low`, `Moderate`, `High`).
- **Deterministic Comfort Scoring (0–100)**: Evaluates live conditions using a clear weighted algorithm:
  - **Temperature comfort (40%)**: 100 within preferred range; linear penalty for deviation.
  - **Wind comfort (20%)**: 100 for calm winds; progressive penalty past comfortable thresholds.
  - **Humidity comfort (15%)**: 100 within optimal range; scaled penalty for dry or muggy air.
  - **Air quality (25%)**: OpenWeatherMap AQI 1–5 adjusted by personal sensitivity.
- **Dynamic Phrasing**: Automatically generates transparent explanations based on actual metrics (e.g., *"Your current conditions score 84/100 (Favorable). Temperature is optimal at 22.4°C, humidity is comfortable, and wind conditions are favourable."*).
- **Persistent Biometric Profile**: Stored non-destructively in `~/.atmosphere/config.properties`, surviving application restarts.
- **Forecast Suitability Integration**: Daily 5-day forecast cards display a Personal DNA Suitability Index based on forecast temperatures and precipitation probability.

### 3. Feature Two — City Comparison Studio
- **Head-to-Head Multi-City Studio**: Compare between 2 and 4 cities simultaneously.
- **Intelligent Recommendation Engine**: Evaluates all compared cities against your Personal Weather DNA to determine the #1 best match with a dedicated winner crown card.
- **Transparent Justification**: Clear explanations detailing why the winner ranked first and specific factors where competitors underperformed (e.g., *"Outperforms Mumbai by +14 points due to lower humidity and superior air quality."*).
- **Metric Comparison Matrix**: Side-by-side comparative bars with difference indicators (highlighting highest comfort, lowest humidity, calmest wind, and cleanest air).
- **Fault-Tolerant Async Loading**: Background JavaFX tasks fetch cities independently; if one city fails, it renders an isolated retry card without breaking the rest of the comparison.

### 4. Core Weather Intelligence Capabilities
- **Multi-City Live Dashboard**: Real-time conditions for default cities (Bengaluru, Mumbai, Delhi) and dynamically searched locations.
- **5-Day Forecast Aggregation**: High/low temperature trend chart and daily breakdown cards.
- **Air Quality Observatory**: Animated 270° AQI gauge, pollutant breakdown (PM2.5, PM10, NO2, O3), and health guidance.
- **AI Insights (Gemini 3.8 Flash)**: Generative conversational weather perspectives with error diagnostics.
- **Outdoor Activity Planner**: Suitability scoring for walking, jogging, and cycling over upcoming 3-hour forecast slots.
- **Weather Alert Centre**: Severity-ranked warnings for temperature extremes, high wind, precipitation, and degraded AQI.
- **Historical Snapshot & Anomalies**: Local SQLite-backed weather history tracking significant changes over time.

---

## 🏗️ Architecture Overview

The application adheres to clean separation of concerns:

```
src/main/java/com/atmosphere/
├── AtmosphereApp.java                  # Application launcher, splash screen, and lifecycle
├── config/
│   └── AppConfig.java                  # Atomic persistence for API keys, units, and Weather DNA
├── model/
│   ├── WeatherData.java                # Live city weather, air components, and sun times
│   ├── ForecastData.java               # Aggregated daily forecast
│   ├── ForecastSlot.java               # 3-hour forecast time slots
│   ├── WeatherPreferenceProfile.java   # Personal Weather DNA user thresholds
│   ├── PersonalComfortScore.java       # Deterministic score result, categories, and breakdown
│   ├── WeatherAlert.java               # Threshold-based weather alerts
│   └── WeatherSnapshot.java            # SQLite historical snapshot
├── service/
│   ├── WeatherService.java             # OpenWeatherMap HTTP client and JSON parsing
│   ├── PersonalComfortScorer.java      # Deterministic Weather DNA scoring engine
│   ├── GeminiService.java              # Google Gemini REST client and diagnostics
│   ├── CityRegistry.java               # Shared observable registry of active cities
│   ├── ActivitySuitabilityScorer.java  # Outdoor activity suitability algorithm
│   ├── WeatherAlertService.java        # Anomaly and threshold alert detector
│   └── WeatherHistoryService.java      # SQLite database snapshot management
├── controller/
│   ├── MainController.java             # Shell, lazy-loaded screen router, and settings modal
│   ├── DashboardController.java        # Multi-city dashboard and comfort overview
│   ├── ComparisonController.java       # City Comparison Studio and intelligent winner box
│   ├── WeatherDnaController.java       # Personal Weather DNA profile sliders and simulator
│   ├── ForecastController.java         # 5-day forecast with DNA suitability badges
│   ├── AQIController.java              # Air quality gauge and pollutant breakdown
│   └── InsightsController.java         # Gemini conversational intelligence
└── util/
    ├── TemperatureStyler.java          # Unit conversion and color styling
    ├── CardHoverEffects.java           # JavaFX hover scale micro-interactions
    └── ToastNotifications.java         # Non-blocking transient feedback overlays
```

---

## 🛠️ Technologies Used

- **Language**: Java 21 (LTS)
- **UI Framework**: JavaFX 21 (Controls, FXML, Graphics, Swing)
- **JSON Processing**: Google Gson 2.10.1
- **Storage**: SQLite JDBC 3.46.1.0
- **Build System**: Apache Maven 3.9+
- **Testing**: JUnit Jupiter 5.10.2 (Unit & Parameterized FXML tests)

---

## ⚙️ Configuration & API Setup

Configuration is stored locally in `~/.atmosphere/config.properties` (Windows: `%USERPROFILE%\.atmosphere\config.properties`):

```properties
openweathermap.apiKey=YOUR_OPENWEATHERMAP_KEY
gemini.apiKey=YOUR_GEMINI_KEY
temperature.unit=C
weatherdna.temp.min=18.0
weatherdna.temp.max=26.0
weatherdna.humidity.min=35.0
weatherdna.humidity.max=65.0
weatherdna.wind.max=20.0
weatherdna.aqi.sensitivity=MODERATE
```

> **Note**: API keys can be entered directly in the application via **⚙ Settings** in the sidebar.

---

## 🚀 How to Run

### Requirements
- **JDK 21** or later
- **Maven 3.9+**

### Build and Test
```powershell
mvn clean test
```

### Run Application
```powershell
mvn javafx:run
```

---

## 🧮 How the Personal Comfort Score Works

The Comfort Score is calculated deterministically on a scale of **0–100**:

$$\text{Comfort Score} = 0.40 \times S_{\text{temp}} + 0.20 \times S_{\text{wind}} + 0.15 \times S_{\text{humidity}} + 0.25 \times S_{\text{aqi}}$$

| Factor | Weight | Ideal Condition | Penalty Rule |
| :--- | :---: | :--- | :--- |
| **Temperature** | 40% | Within $[\text{minTemp}, \text{maxTemp}]$ | Linear penalty of $-5.5$ pts per degree outside range |
| **Wind Speed** | 20% | $\le 8.0\text{ km/h}$ | $-20\%$ up to max comfortable wind; steep drop thereafter |
| **Humidity** | 15% | Within $[\text{minHumidity}, \text{maxHumidity}]$ | $-2.8$ pts per % below min; $-2.6$ pts per % above max |
| **Air Quality** | 25% | AQI 1 (Good) = 100 pts | AQI 2 = 80, AQI 3 = 55, AQI 4 = 25, AQI 5 = 0; modulated by sensitivity |

### Comfort Categories
- **90–100**: `Optimal` (Mint `#80D6C5`)
- **75–89**: `Favorable` (Mint `#80D6C5`)
- **60–74**: `Moderate` (Warm Amber `#F2BB72`)
- **45–59**: `Suboptimal` (Warm Amber `#F2BB72`)
- **0–44**: `Harsh` (Coral Alert `#EF7D7D`)

---

## 🎓 10-Mark College Project Evaluation Demonstration Script

Follow this step-by-step sequence to present the project during practical evaluation:

1. **Launch & Observatory UI/UX (Marks: Visual Design & Desktop Polish)**
   - Run `mvn javafx:run`.
   - Highlight the undecorated startup splash screen with loading progress bar.
   - Point out the deep graphite foundation (`#0B0F13`), subtle 1px card borders, hover micro-interactions, and high-contrast typography.
   - Show the live clock in the sidebar ticking accurately.

2. **Multi-City Live Dashboard (Marks: API Integration & Asynchronous JavaFX)**
   - Note the concurrent loading of Bengaluru, Mumbai, and Delhi.
   - Point out the **DNA Comfort Badge** displayed on every city card alongside OpenWeatherMap AQI and temperature metrics.
   - Search for a new city (e.g., `London` or `Tokyo`) and demonstrate non-blocking background fetching and card entrance animation.

3. **Personal Weather DNA (Marks: Deterministic Algorithms & Preference Persistence)**
   - Navigate to **◈ Weather DNA** from the sidebar.
   - Demonstrate the interactive sliders for Temperature Range, Humidity Range, Max Wind, and Air Quality Sensitivity.
   - Move the temperature slider to a very narrow range (e.g., 20°C–22°C) or change AQI sensitivity to `High`.
   - Show how the **Live Comfort Simulator** instantly recalculates the score and dynamically updates the narrative explanation and factor breakdown bars.
   - Click **Save DNA Profile**. Mention that preferences are written atomically to `~/.atmosphere/config.properties`.

4. **City Comparison Studio (Marks: Multi-Entity Analytics & Decision Support)**
   - Navigate to **◈ City Comparison**.
   - Show the side-by-side comparison of 2–4 cities.
   - Show the **Intelligent Winner Box**: Point out how the algorithm crowns the best-matching city, displays its Comfort Score, and gives a clear comparative explanation of why it outperformed the runner-up.
   - Highlight the **Metric Comparison Matrix** at the bottom with comparative bars and difference indicator pills (*Highest*, *Lowest*).
   - Use the `+ Add City` search bar to add a fourth city. Show how the comparison recalculates dynamically.
   - Remove a city using the `✕` button to demonstrate dynamic layout adjustment.

5. **Forecast Integration (Marks: Data Aggregation & Chart Visualization)**
   - Navigate to **◈ 5-Day Forecast**.
   - Show the animated area chart with temperature curves.
   - Point out the **DNA Fit Badge** on each daily card, illustrating how user preferences extend to future weather planning.

6. **Automated Verification (Marks: Code Quality & Testing Rigor)**
   - Close the application and run `mvn clean test`.
   - Show that all **34 unit and FXML integration tests** pass with 0 errors.
