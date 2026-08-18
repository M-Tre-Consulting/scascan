# ScaScan

A native mobile app that uses AI to instantly retrieve nutritional facts from food — by taking a photo, scanning a barcode, searching by name, or speaking it.

Two native codebases, one product: **`android/`** is the original Kotlin app, **`ios/`** is a SwiftUI port that is now the active line of work. This file is the monorepo overview; for the full iOS technical handover (module layout, the nutrition arithmetic, concurrency and localization traps, how to verify a change) see **[`ios/ARCHITECTURE.md`](ios/ARCHITECTURE.md)**.

---

## Features

| | Android | iOS |
|---|---|---|
| 📷 Photo analysis | ✅ | ✅ |
| 🔍 Barcode scanning (camera) | ✅ (ML Kit / ZXing) | ✅ (VisionKit `DataScanner`) |
| ✏️ Text search | ✅ | ✅ |
| 🎙️ Voice logging (speak what you ate) | ✅ on-device speech recognition, falls back to online | ✅ on-device speech recognition, Siri/Shortcuts intent |
| 🤖 Powered by Gemini | ✅ | ✅ (user's own API key on both) |
| Barcode-photo OCR fallback (photo → digits → OpenFoodFacts → vision ID) | ❌ | ✅ |
| Health sync | ✅ Health Connect | ✅ HealthKit |
| Adaptive daily calorie target (BMR, activity, carry-over, weight trend) | ✅ | ✅ |
| Evening recap (burn settled once at day's end instead of live in the target) | ✅ | ✅ (animated, staged reveal) |
| "Watch wasn't worn" fallback for missing activity data | ✅ (configurable in Settings) | ✅ |
| Smart hydration reminders (reschedule remaining ones by amount logged) | ✅ | ✅ |
| Homescreen widget (calories/macros/water, quick add) | ✅ | ✅ |
| Cloud backup | ✅ Google Drive | ❌ (would need CloudKit + paid account; deliberately absent) |
| Deep links / App Intents | — | ✅ `scascan://` scheme + Siri Shortcuts |
| Offline-capable UI | ✅ (only the AI call needs network) | ✅ |

See [§17 of `ios/ARCHITECTURE.md`](ios/ARCHITECTURE.md#17-where-things-stand--open-threads) for the current list of known gaps and open threads on the iOS side.

---

## Architecture

Both apps follow **Clean Architecture** with an **MVVM** presentation layer.

### Android

Three layers in a single Gradle module:

- **Data Layer**: remote APIs (Gemini REST client & OpenFoodFacts client), local SQLite database (Room), repositories, managers for Health Connect and Drive sync, and background workers.
- **Domain Layer**: business-focused Use Cases representing the app's core feature set.
- **Presentation Layer (UI)**: View Binding and Jetpack Navigation. The single [MainActivity](android/app/src/main/java/com/scascan/app/MainActivity.kt) hosts the main [navigation graph](android/app/src/main/res/navigation/nav_graph.xml), with a central [MainFragment](android/app/src/main/java/com/scascan/app/ui/main/MainFragment.kt) using a ViewPager2 for the Home, Log, and Profile tabs.

Dependency injection is handled by **Hilt**.

### iOS

An Xcode project plus a local Swift package:

- **`ScaScanKit`**: everything that isn't a view — SwiftData models, repositories, HealthKit, the Gemini and OpenFoodFacts clients, notifications, the shared `UserProfileStore`. Shared by the app and the widget extension.
- **`Scascan`** (app target): SwiftUI views, view state, App Intents. Folders map to screens (`Home/`, `Log/`, `Camera/`, `Scan/`, `Search/`, `Voice/`, `Recap/`, `Result/`, `Profile/`, `Setup/`) plus `App/` (composition root — plain manual DI through the SwiftUI environment, no framework), `Main/` (tab host), `Navigation/`, `Shared/`.
- **`ScaScanWidget`**: widget extension, reads the same App Group store as the app.

Full detail — storage, the nutrition math, HealthKit bridging, voice logging, the evening recap, notifications, concurrency traps, localization — lives in [`ios/ARCHITECTURE.md`](ios/ARCHITECTURE.md).

---

## Tech Stack

### Android

| Layer | Technology | Version / Description |
|---|---|---|
| **Language** | Kotlin | 2.2.10 (JVM Target 17) |
| **Dependency Injection** | Hilt | 2.59.2 (using KSP) |
| **Local Database** | Room | 2.7.1 (for log entries and water tracking) |
| **Background Work** | WorkManager | 2.9.0 (offline analysis scheduling) |
| **Camera Feed** | CameraX | 1.6.1 |
| **Barcode Detection** | Google ML Kit / ZXing | Core 3.5.3 |
| **Speech** | `android.speech.SpeechRecognizer` | prefers on-device, falls back to online |
| **Health Sync** | Google Health Connect | 1.1.0-rc01 |
| **Cloud Backups** | Google Drive API | v3 |
| **Networking** | OkHttp & HttpURLConnection | OkHttp 4.12.0 |
| **AI Processing** | Google Gemini API | Raw client (`GeminiRestClient`) |
| **Navigation** | Jetpack Navigation Component | 2.7.7 |
| **Build Tools** | Android Gradle Plugin (AGP) | 9.2.1 (with version catalogs) |

### iOS

| Layer | Technology | Version / Description |
|---|---|---|
| **Language** | Swift | 6.0, Swift 6 language mode, `MainActor` default isolation |
| **UI** | SwiftUI | Deployment target iOS 26.0 |
| **Local Database** | SwiftData | `LogEntry` / `WaterLog`, stored in the App Group container so the widget can read it directly |
| **Preferences / cross-process state** | App Group `UserDefaults` | behind `UserProfileStore` |
| **Secrets** | Keychain | Gemini API key only |
| **Health Sync** | HealthKit | steps, active energy, weight, workouts |
| **Speech** | `SFSpeechRecognizer` + `AVAudioEngine` | on-device, offline voice logging |
| **Camera / Barcode** | AVFoundation + VisionKit `DataScanner` | |
| **AI Processing** | Google Gemini REST API | user's own key, `GeminiRestClient` |
| **Siri / Shortcuts** | App Intents | `StartVoiceLogIntent` |
| **Widget** | WidgetKit | 30-min timeline + on-data-change refresh |

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
│   │       │   │   ├── reminder/   # Hydration reminder scheduling (WorkManager)
│   │       │   │   ├── remote/     # Gemini and OpenFoodFacts API clients
│   │       │   │   ├── repository/ # Log and nutrition repositories
│   │       │   │   ├── sync/       # Google Drive backup and restore
│   │       │   │   ├── voice/      # SpeechRecognizer wrapper for voice logging
│   │       │   │   └── worker/     # WorkManager background analysis workers
│   │       │   ├── di/        # Hilt Dependency Injection modules
│   │       │   ├── domain/    # Domain layer: Feature-specific business logic
│   │       │   │   └── usecase/# Clean Architecture use cases
│   │       │   ├── ui/        # Presentation layer: Fragments and ViewModels
│   │       │   │   ├── camera/ # Live camera scanner feed (CameraX)
│   │       │   │   ├── home/   # Main portal for scanning choices
│   │       │   │   ├── log/    # Meal history, water logging, evening recap
│   │       │   │   ├── main/   # ViewPager2 shell coordinating tabs
│   │       │   │   ├── profile/# User profile, macro targets, settings
│   │       │   │   ├── result/ # Macro/micro nutrition presentation
│   │       │   │   ├── scan/   # ML Kit / ZXing barcode scanner
│   │       │   │   ├── search/ # Food textual query lookup
│   │       │   │   ├── setup/  # First-run API key input
│   │       │   │   ├── util/   # View extensions and notification helpers
│   │       │   │   ├── voice/  # Voice logging screen and view model
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
├── ios/                        # Native iOS app (Swift/SwiftUI)
│   ├── ARCHITECTURE.md         # Full iOS technical handover — read before touching this app
│   ├── Scascan.xcodeproj
│   ├── Scascan/                 # App target: SwiftUI views, view state, App Intents
│   │   ├── Home/ Log/ Camera/ Scan/ Search/ Voice/ Recap/ Result/ Profile/ Setup/
│   │   ├── App/                 # Composition root, root/tab-level view state
│   │   ├── Main/                # Tab host
│   │   ├── Navigation/
│   │   └── Shared/
│   ├── ScaScanKit/              # Local Swift package: models, repositories, HealthKit,
│   │   │                        # Gemini/OpenFoodFacts clients, notifications, UserProfileStore
│   │   └── Tests/
│   └── ScaScanWidget/           # Widget extension target
└── LICENSE
```

### Key Android files
- **App Manifest**: [AndroidManifest.xml](android/app/src/main/AndroidManifest.xml)
- **Navigation Graph**: [nav_graph.xml](android/app/src/main/res/navigation/nav_graph.xml)
- **Dependency Version Catalog**: [libs.versions.toml](android/gradle/libs.versions.toml)
- **Host Activity**: [MainActivity.kt](android/app/src/main/java/com/scascan/app/MainActivity.kt)
- **Application Class**: [ScaScanApplication.kt](android/app/src/main/java/com/scascan/app/ScaScanApplication.kt)
- **App-level Build configuration**: [build.gradle.kts](android/app/build.gradle.kts)
- **Project-level Build configuration**: [build.gradle.kts](android/build.gradle.kts)
- **Settings configuration**: [settings.gradle.kts](android/settings.gradle.kts)
- **Nutrition math & evening recap**: [LogRepository.kt](android/app/src/main/java/com/scascan/app/data/repository/LogRepository.kt)
- **Voice logging**: [VoiceLogViewModel.kt](android/app/src/main/java/com/scascan/app/ui/voice/VoiceLogViewModel.kt)

### Key iOS files
- **Composition root**: [AppContainer.swift](ios/Scascan/App/AppContainer.swift)
- **Nutrition math**: [LogRepository](ios/ScaScanKit) (see [§5 of ARCHITECTURE.md](ios/ARCHITECTURE.md#5-the-nutrition-maths))
- **Voice logging**: [VoiceLogController.swift](ios/Scascan/Voice/VoiceLogController.swift)
- **Evening recap**: [DailyRecapView.swift](ios/Scascan/Recap/DailyRecapView.swift)

---

## Getting Started

### Android

**Prerequisites:** Android Studio Hedgehog or later, Android SDK 26+, a [Google AI Studio](https://aistudio.google.com) API key.

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

### iOS

**Prerequisites:** Xcode with iOS 26 SDK, Swift 6 toolchain.

1. Open `ios/Scascan.xcodeproj` in Xcode and run the `Scascan` scheme on a simulator or device.
2. On first launch, `RootView` stops at the setup screen until a Gemini API key is entered by hand (there is no build-time injection on iOS) — the key is then stored in the Keychain.
3. See [§15 of `ios/ARCHITECTURE.md`](ios/ARCHITECTURE.md#15-building-running-verifying) for command-line build/install/launch steps and for why `swift test` / `xcodebuild test` can't be used to verify changes in this project as configured — "it builds" is not verification.

---

## Screens

### Android

| Screen | Description |
|---|---|
| **API Key Setup** | First-run setup to input and save the Google Gemini API Key. |
| **Main (Tab Host)** | A view pager shell hosting the Home, Log, and Profile views. |
| **Home** | Portal presenting choices for Image Capture, Barcode Scan, Text Search, and Voice Log. |
| **Camera** | Live CameraX viewfinder preview with capture shutter to analyze food images. |
| **Barcode Scan** | Camera feed to capture and process product barcodes. |
| **Search** | Free-text input to quickly lookup food/meal descriptions. |
| **Voice Log** | Speak what you ate; on-device transcription (falls back to online), added to the log immediately with a 4-second Undo. |
| **Nutrition Result** | Detail view highlighting serving details, calories, macronutrients, and custom macro targets. |
| **Meal & Water Log** | Chronological logs of consumed food and daily water intake, including summaries and adjustments, plus an "Evening recap" card that unlocks at 21:00. |
| **Evening Recap** | Settles the day's activity burn as a deduction from intake and shows a verdict (over/under/on target). |
| **Profile & Settings** | Manage user characteristics (Mifflin-St Jeor formula calculation), set Gemini AI model preferences, configure the activity fallback estimate, and back up/restore logs via Google Drive. |

### iOS

| Screen | Description |
|---|---|
| **Setup** | First-run Gemini API key entry. |
| **Scan** | Four cards: photo, barcode, text search, voice. |
| **Camera / Barcode** | Edge-to-edge AVFoundation capture and VisionKit `DataScanner`. |
| **Search** | Free-text food/meal lookup. |
| **Voice** | Speak what you ate; auto-transcribed and auto-logged with a 4-second undo banner. Also reachable via Siri/Shortcuts. |
| **Log** | Date navigation, calorie/macro progress, water quick-add, adaptive-target breakdown, today's workouts, meal list. |
| **Evening Recap** | Unlocks at 21:00; settles the day's activity burn as a deduction from intake and renders an animated verdict (over/under/on target). |
| **Profile & Settings** | Profile fields, goals, AI target computation, API key, model picker, Health, fitness fallback, water amounts, notification toggles. |

---

## AI Disclaimer

Nutritional values are estimated by a large language model and may not reflect exact product composition. Always verify with official food labels, especially for dietary or medical purposes.

---

## License

Distributed under the GNU General Public License v2.0. See [`LICENSE`](LICENSE) for details.
