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

```
data/       → models, repository (Gemini integration)
domain/     → use cases (one per feature)
ui/         → fragments + ViewModels (one package per screen)
```

Dependency injection is handled by **Hilt**. The single `MainActivity` hosts a **Navigation Component** graph with five fragments: Home, Camera, Barcode Scan, Search, and Nutrition Result.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.0 |
| DI | Hilt 2.51 (KSP) |
| Camera | CameraX 1.3 |
| Barcode | ML Kit Barcode Scanning |
| AI | Google Generative AI SDK (`gemini-1.5-flash`) |
| Navigation | Jetpack Navigation Component |
| Build | AGP 8.5 · Gradle version catalog |

---

## Repository Structure

```
scascan/
├── android/          # Native Android (Kotlin)
│   ├── app/
│   │   └── src/main/
│   │       ├── java/com/scascan/app/
│   │       │   ├── data/
│   │       │   ├── domain/
│   │       │   ├── ui/
│   │       │   ├── di/
│   │       │   ├── MainActivity.kt
│   │       │   └── ScaScanApplication.kt
│   │       └── res/
│   └── gradle/libs.versions.toml
└── ios/              # Placeholder (coming soon)
```

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
| Home | Entry point with the three analysis options |
| Camera | Live CameraX preview with a capture button |
| Barcode Scan | Continuous ML Kit scanning — navigates automatically on detection |
| Search | Free-text input for any food or meal description |
| Nutrition Result | Displays calories, macros, fiber, sugar, and sodium per serving |

---

## AI Disclaimer

Nutritional values are estimated by a large language model and may not reflect exact product composition. Always verify with official food labels, especially for dietary or medical purposes.

---

## License

Distributed under the MIT License. See [`LICENSE`](LICENSE) for details.
