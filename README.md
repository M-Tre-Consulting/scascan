# ScaScan

A native mobile app that uses AI to instantly retrieve nutritional facts from food — by taking a photo, scanning a barcode, or searching by name.

---

## Features

- 📷 **Photo analysis** — point your camera at any food and get an AI-generated nutrition breakdown
- 🔍 **Barcode scanning** — scan a product barcode for detailed nutritional data
- ✏️ **Text search** — type any food or meal description to look up its facts
- 🤖 **Powered by Gemini** — Google's Gemini 1.5 Flash model handles all food recognition and analysis
- **Offline-capable UI** — only the AI call requires a network connection

---

## Architecture

ScaScan follows **Clean Architecture** with an **MVVM** presentation layer, organized into three layers:

- **Data Layer**: Houses remote APIs (Gemini rest client & OpenFoodFacts client), local SQLite database (Room), repositories, managers for Health Connect and Drive sync, and background workers.
- **Domain Layer**: Contains business-focused Use Cases representing the app's core feature set.
- **Presentation Layer (UI)**: Built with View Binding and Jetpack Navigation. The single [MainActivity](file:///home/quark/Projects/scascan/android/app/src/main/java/com/scascan/app/MainActivity.kt) hosts the main [navigation graph](file:///home/quark/Projects/scascan/android/app/src/main/res/navigation/nav_graph.xml). It uses a central [MainFragment](file:///home/quark/Projects/scascan/android/app/src/main/java/com/scascan/app/ui/main/MainFragment.kt) with a ViewPager2 to manage the Home, Log, and Profile tabs.

Dependency injection is handled by **Hilt**.

---

## Tech Stack

| Layer | Technology | Version / Description |
|---|---|---|
| **Language** | Kotlin | 2.2.10 (JVM Target 17) |
| **Dependency Injection** | Hilt | 2.59.2 (using KSP) |
| **Local Database** | Room | 2.7.1 (for log entries and water tracking) |
| **Background Work** | WorkManager | 2.9.0 (offline analysis scheduling) |
| **Camera Feed** | CameraX | 1.6.1 |
| **Barcode Detection** | Google ML Kit / ZXing | Core 3.5.3 |
| **Health Sync** | Google Health Connect | 1.1.0-rc01 |
| **Cloud Backups** | Google Drive API | v3 |
| **Networking** | OkHttp & HttpURLConnection | OkHttp 4.12.0 |
| **AI Processing** | Google Gemini API | Raw client (`GeminiRestClient`) |
| **Navigation** | Jetpack Navigation Component | 2.7.7 |
| **Build Tools** | Android Gradle Plugin (AGP) | 9.2.1 (with version catalogs) |

---

## Repository Structure

```
scascan/
├── android/                   # Native Android codebase (Kotlin)
│   ├── app/
│   │   ├── build.gradle.kts   # App Gradle configuration
│   │   └── src/main/
│   │       ├── AndroidManifest.xml
│   │       ├── java/com/scascan/app/
│   │       │   ├── data/      # Data layer: APIs, databases, repositories, workers
│   │       │   │   ├── analysis/   # Analysis states (AnalysisManager)
│   │       │   │   ├── health/     # Google Health Connect integration
│   │       │   │   ├── local/      # Room database, SharedPreferences, models
│   │       │   │   ├── model/      # Data transfer models (NutritionFacts, targets)
│   │       │   │   ├── receiver/   # Broadcast receivers (reminders)
│   │       │   │   ├── reminder/   # Meal notification scheduling
│   │       │   │   ├── remote/     # Gemini and OpenFoodFacts API clients
│   │       │   │   ├── repository/ # Log and nutrition repositories
│   │       │   │   ├── sync/       # Google Drive backup and restore
│   │       │   │   └── worker/     # WorkManager background analysis workers
│   │       │   ├── di/        # Hilt Dependency Injection modules
│   │       │   ├── domain/    # Domain layer: Feature-specific business logic
│   │       │   │   └── usecase/# Clean Architecture use cases
│   │       │   ├── ui/        # Presentation layer: Fragments and ViewModels
│   │       │   │   ├── camera/ # Live camera scanner feed (CameraX)
│   │       │   │   ├── home/   # Main portal for scanning choices
│   │       │   │   ├── log/    # Meal history and water logging
│   │       │   │   ├── main/   # ViewPager2 shell coordinating tabs
│   │       │   │   ├── profile/# User profile, macro targets, settings
│   │       │   │   ├── result/ # Macro/micro nutrition presentation
│   │       │   │   ├── scan/   # ML Kit / ZXing barcode scanner
│   │       │   │   ├── search/ # Food textual query lookup
│   │       │   │   ├── setup/  # First-run API key input
│   │       │   │   ├── util/   # View extensions and notification helpers
│   │       │   │   └── widget/ # Daily nutrition homescreen widget
│   │       │   ├── MainActivity.kt
│   │       │   └── ScaScanApplication.kt
│   │       └── res/           # UI layout files, drawables, navigation graph
│   │           └── navigation/
│   │               └── nav_graph.xml
│   ├── gradle/
│   │   └── libs.versions.toml # Centralized Gradle version catalog
│   ├── build.gradle.kts       # Project-level build configuration
│   └── settings.gradle.kts
└── ios/                       # Native iOS placeholder (coming soon)
```

### Key Files
- **App Manifest**: [AndroidManifest.xml](file:///home/quark/Projects/scascan/android/app/src/main/AndroidManifest.xml)
- **Navigation Graph**: [nav_graph.xml](file:///home/quark/Projects/scascan/android/app/src/main/res/navigation/nav_graph.xml)
- **Dependency Version Catalog**: [libs.versions.toml](file:///home/quark/Projects/scascan/android/gradle/libs.versions.toml)
- **Host Activity**: [MainActivity.kt](file:///home/quark/Projects/scascan/android/app/src/main/java/com/scascan/app/MainActivity.kt)
- **Application Class**: [ScaScanApplication.kt](file:///home/quark/Projects/scascan/android/app/src/main/java/com/scascan/app/ScaScanApplication.kt)
- **App-level Build configuration**: [build.gradle.kts](file:///home/quark/Projects/scascan/android/app/build.gradle.kts)
- **Project-level Build configuration**: [build.gradle.kts](file:///home/quark/Projects/scascan/android/build.gradle.kts)
- **Settings configuration**: [settings.gradle.kts](file:///home/quark/Projects/scascan/android/settings.gradle.kts)


---

## Getting Started

### Prerequisites

- Android Studio Hedgehog or later
- Android SDK 26+
- A [Google AI Studio](https://aistudio.google.com) API key

### Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/m4ce-w1ndu/scascan.git
   cd scascan/android
   ```

2. Create a `local.properties` file in the `android/` directory and add your API key:
   ```properties
   GEMINI_API_KEY=your_api_key_here
   ```

3. Open the `android/` folder in Android Studio, let Gradle sync, then run the app on a physical device or emulator with camera support.

> **Note:** The Gemini API key is injected at build time via `BuildConfig` and never shipped in source control. Do not commit `local.properties`.

---

## Screens

| Screen | Description |
|---|---|
| **API Key Setup** | First-run setup to input and save the Google Gemini API Key. |
| **Main (Tab Host)** | A view pager shell hosting the Home, Log, and Profile views. |
| **Home** | Portal presenting choices for Image Capture, Barcode Scan, and Text Search. |
| **Camera** | Live CameraX viewfinder preview with capture shutter to analyze food images. |
| **Barcode Scan** | Camera feed to capture and process product barcodes. |
| **Search** | Free-text input to quickly lookup food/meal descriptions. |
| **Nutrition Result** | Detail view highlighting serving details, calories, macronutrients, and custom macro targets. |
| **Meal & Water Log** | Chronological logs of consumed food and daily water intake, including summaries and adjustments. |
| **Profile & Settings** | Manage user characteristics (Mifflin-St Jeor formula calculation), set Gemini AI model preferences, and back up/restore logs via Google Drive. |

---

## AI Disclaimer

Nutritional values are estimated by a large language model and may not reflect exact product composition. Always verify with official food labels, especially for dietary or medical purposes.

---

## License

Distributed under the GNU General Public License v2.0. See [`LICENSE`](LICENSE) for details.
