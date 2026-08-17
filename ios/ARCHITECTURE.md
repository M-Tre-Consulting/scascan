# ScaScan for iOS — technical handover

Written for someone (or some Claude session) picking this up cold. It covers how
the iOS app is built, the arithmetic it runs on, the conventions that are easy to
break without noticing, and how to verify a change is actually working. The root
`README.md` documents the *Android* original only — this file is the iOS side.

Everything here was true as of **v1.1 (build 2)**, the release that added voice
logging, plus the evening-recap work that landed on `main` after it.

---

## 1. What the app is

Point your phone at food, or say what you ate, and get a nutrition breakdown that
lands in a daily log with an adaptive calorie target. Analysis is done by Google
Gemini via the user's own API key; barcodes are looked up in OpenFoodFacts first.

The iOS app is a **port of an existing Android app** (`android/` in the same
repo). Most iOS types name their Android counterpart in a doc comment
(`/// Mirrors Android's ...`). That mapping is worth preserving when you add
things — it's how the two stay comparable — but the port deliberately diverges
where the platform has a better answer, and those divergences are always
explained in a comment at the point of divergence. Don't "restore parity" with
Android without reading why the difference exists.

---

## 2. Layout

```
ios/
├─ Scascan.xcodeproj
├─ Scascan/            app target — SwiftUI views, view state, App Intents
├─ ScaScanKit/         local Swift package — all logic, models, I/O
└─ ScaScanWidget/      widget extension target
```

`ScaScanKit` holds everything that isn't a view: the SwiftData models, the
repositories, HealthKit, the Gemini and OpenFoodFacts clients, notifications, and
the shared `UserProfileStore`. Both the app and the widget depend on it. **If
logic needs to be reachable from the widget, it belongs in ScaScanKit**, not in
the app target.

App target folders map to screens: `Home/`, `Log/`, `Camera/`, `Scan/` (barcode),
`Search/`, `Voice/`, `Recap/`, `Result/`, `Profile/`, `Setup/`, plus `App/`
(composition root), `Main/` (tab host), `Navigation/`, `Shared/`.

---

## 3. Targets and build settings

| | |
|---|---|
| App bundle id | `com.nicoloperri.Scascan` |
| Widget bundle id | `com.nicoloperri.Scascan.ScaScanWidget` |
| App Group | `group.com.nicoloperri.Scascan` |
| Deployment target | iOS 26.0 |
| Swift | 6.0, Swift 6 language mode |
| Version | `MARKETING_VERSION = 1.1`, `CURRENT_PROJECT_VERSION = 2` |

Two things about the project file:

- **`SWIFT_DEFAULT_ACTOR_ISOLATION = MainActor`** is set on every target. See
  §12 — this has bitten us with a real crash and will again.
- The app target uses Xcode 16+ **synchronized folder groups**
  (`PBXFileSystemSynchronizedRootGroup`), so **a new `.swift` file under
  `Scascan/` is picked up automatically** — no `project.pbxproj` edit needed. The
  widget target uses the same mechanism. Adding a whole new *target* does need
  pbxproj surgery (that's how the abandoned Control Center extension was added,
  and then fully removed).

Info.plist keys live in build settings (`INFOPLIST_KEY_*`): camera, microphone,
speech recognition, and both HealthKit usage strings.

---

## 4. Storage

Three separate stores, each with a different reason to exist:

1. **SwiftData** — `LogEntry` and `WaterLog`, defined in `ScaScanSchema`. The
   store file lives in the **App Group container** (`ScaScan.sqlite`) so the
   widget process can read it directly. `cloudKitDatabase: .none` is explicit and
   must stay: this app carries no CloudKit entitlement, and SwiftData's automatic
   mirroring would demand every attribute be optional.
   `makeContainer` deliberately **recreates the store rather than crashing** if it
   fails to open — a failed migration used to brick the app in a permanent
   launch-crash loop.
2. **App Group `UserDefaults`** (`AppGroup.sharedDefaults`) behind
   `UserProfileStore` — profile, goals, AI targets, cached Health figures,
   hydration schedule, feature toggles. Anything the widget or an App Intent
   needs to read is here.
3. **Keychain** — the Gemini API key only (`GeminiKeyStore`). The selected model
   name is not a secret and sits in standard `UserDefaults`.

`UserProfileStore` is the single source of truth for the nutrition maths and is
meant to stay byte-for-byte in sync with the Kotlin original's constants.

---

## 5. The nutrition maths

This is the part most likely to be broken by accident, and it changed
significantly after 1.1. Read this before touching `LogRepository`.

### 5.1 BMR and the base target

Mifflin-St Jeor. `dailyCalorieTarget()` returns the AI-computed target if one
exists (`aiCalorieTarget > 0`, set from Profile ▸ compute targets), otherwise
`BMR × activity multiplier + goalOffset`, where the multiplier runs 1.1
(sedentary) → 1.6 (extra active) and `goalOffset` is −500 / 0 / +250 for lose /
maintain / build.

`LogRepository.baseTarget(hasHealth:)` picks between two shapes:

- **No Health** → the plain `dailyCalorieTarget()` above.
- **Health connected, no AI target** → `1.2 × BMR + goalOffset`. The lower
  multiplier is intentional: measured activity is accounted for separately, so
  the base must not also bake in an activity estimate.

### 5.2 The daily target shown in the app

```
target = base + carryOver + weightTrend      (floored at 0.8 × BMR)
```

`LogRepository.finalTarget(base:bleedthrough:trend:)`.

**Today's active burn is deliberately NOT in here.** It used to be (subtracted,
briefly), and that made the target a moving goalpost: every Fitness sync shifted
the line the user was aiming at, so mid-afternoon the number meant nothing. The
burn is now settled once, in the evening recap, as a deduction from intake. If
you find yourself "fixing" the target to include activity, you are undoing a
deliberate decision — read §5.4 first.

**Weight trend** (`trendAdjustment`) is a stepped ±200 kcal correction from the
last 28 days of Health weight readings; it needs ≥2 readings spanning ≥5 days,
and its thresholds depend on the goal (lose: faster than −0.75 kg/wk → +200,
slower than −0.20 → −200; build: faster than +0.45 → −200, slower than +0.10 →
+200; maintain: no correction).

### 5.3 Carry-over ("previous day balance")

`carryOverBalance(intoDayStarting:)` — what the day *before* the given day left
behind:

```
raw     = (base + thatDaysBurn) − thatDaysIntake
balance = raw > 0 ? raw × 0.8 : raw          clamped to ±500
```

Savings carry at 80% (body efficiency); overspend carries at 100% (accountability).
`yesterdayBleedthrough()` is just this function applied to today.

Note the sign convention: **a day's measured burn is part of what that day could
afford to eat**. It was the other way round for one release; both the carry-over
and the recap now agree.

### 5.4 The evening recap ledger

`LogRepository.dailyRecap(forDateOffset:)` → `DailyRecap` (in ScaScanKit's
`Domain/Models/`). This is where burn finally enters:

```
net    = eaten − burned − carryOver
target = base + weightTrend          (floored; no carry-over, no burn)
```

Presented as deductions from intake rather than additions to the target, which is
the whole point: "you ate 2340, you burned 620, so 1720 counts" reads as a
result. Algebraically it's the same comparison, but it only happens once, at the
end of the day.

Verdict thresholds (`DailyRecap.verdict`): `over` above target + 100 kcal (a
grace band roughly the size of one piece of fruit — finer than any food log's
real precision); `under` below 75% of target, because under-eating is a failure
mode too, not a bonus; `onTarget` in between.

A past day's weight-trend correction isn't recoverable (it's computed from the
*current* 28-day window), so `trendKcal` is only non-zero for today.

### 5.5 "The Watch wasn't worn"

`effectiveActiveCalories(_:)` substitutes the user's configured flat estimate
(Settings ▸ Fitness base fallback, default 500 kcal) whenever a whole day's
active-energy reading comes in under **100 kcal** — a worn Watch or even a
pocketed iPhone accumulates far more than that, so a lower figure is much more
likely a missing device than a genuinely motionless day. Applies to every day the
app reads, not just today.

### 5.6 Water goal

`UserProfileStore.waterTargetMl` — ~35 ml per kg of body weight, rounded to 100
ml, clamped to 1500–4000, defaulting to 2 L when no weight is known. Only used to
give the recap's water bar something to fill toward; nothing warns or blocks on
it.

---

## 6. Health

`HealthKitManager` (ScaScanKit) conforms to `HealthProviding`, the protocol
`LogRepository` actually depends on — which is what lets the widget run on
`NoopHealthProvider` and tests run on a mock. The protocol is `@MainActor`
because it passes SwiftData `@Model` types (not `Sendable`) around.

Reads: steps, active energy (by day and by range), weight history, latest weight
and height, today's workouts. Writes: every logged meal (as a dietary
correlation) and every water entry, plus deletes on removal, so the app's data
shows up in Health's own Nutrition tab.

**The widget has no HealthKit access of its own.** Two shared-cache values in
`UserProfileStore` bridge that gap:

- `isHealthConnected` — set by the app once it confirms authorization, so the
  widget knows to use the *adaptive* base target rather than the plain one.
- `lastYesterdayActiveCalories` + `lastYesterdayActiveCaloriesDayStart` — the
  day-stamp is checked before reuse, so a stale reading from three days ago can't
  masquerade as yesterday's.

`disconnect()` clears all of these. Forgetting to clear a cache there was a real
bug: the widget kept showing an adaptive target after the user disconnected.

---

## 7. The AI pipeline

`NutritionRepository` (ScaScanKit) owns every prompt and the response schema:

- `analyzeImage` — photo → Gemini vision → `NutritionFacts`.
- `analyzeBarcodeImage` — photo of a barcode: Gemini OCRs the digits, then
  OpenFoodFacts, then falls back to plain vision identification.
- `analyzeBarcode` — barcode already decoded natively by VisionKit, so it goes
  straight to OpenFoodFacts.
- `searchFood` — free text (also what voice logging calls).
- `fixEntry` — "actually it was half of that" corrections.
- `computeTargets` — profile → AI daily calorie and macro targets.

`GeminiRestClient` talks to the REST API with the user's key, and `listModels`
populates the model picker live. `postWithRetry` retries transient failures — its
`Task.sleep` must stay `try await` (not `try?`) so cancellation propagates rather
than being swallowed.

`AnalysisManager` is the shared observable coordinator: any screen can start an
analysis, and `MainTabView` presents the result sheet and the error alert from
one place. It also posts a local notification when a result arrives.

---

## 8. Screens

- **Scan (tab 0)** — four cards: photo, barcode, text search, voice.
- **Log (tab 1)** — date navigation, calorie and macro progress, water quick-add,
  the "Evening recap" entry card, the adaptive-target breakdown, today's
  workouts, and the meal list (each row can be corrected or deleted). Tapping the
  calorie or macro card opens `DailySummarySheet`.
- **Profile (tab 2)** — profile fields, goals, AI target computation, and
  Settings (`AppSettingsView`: API key, model, Health, fitness fallback, water
  amounts, notification toggles).
- **Camera / Barcode** — `AVFoundation` capture and VisionKit `DataScanner`, both
  edge-to-edge with the native floating back button (`.toolbarBackground(.hidden,
  for: .navigationBar)` — *not* a custom back button; that was fixed deliberately).
- **Voice** (`Voice/`) — see §9.
- **Recap** (`Recap/`) — see §10.

---

## 9. Voice logging

`VoiceLogController` drives `SFSpeechRecognizer` + `AVAudioEngine` for on-device,
offline, free transcription. It auto-stops after **1.8 s** of silence, hands the
transcript to `NutritionRepository.searchFood`, adds the result to the log
immediately, and sets `AppContainer.pendingUndo` — `MainTabView` renders that as
a 4-second "Undo" banner. Auto-add with an undo was an explicit product decision
over a confirm-first sheet.

Reachable from the Home card, from `scascan://voice`, or from **Siri / Shortcuts**
via `StartVoiceLogIntent` (`Voice/VoiceLogShortcut.swift`). The intent sets
`UserProfileStore.pendingVoiceLogRequest` and posts `VoiceLogSignal`.

`VoiceLogSignal` is a **Darwin notification**
(`CFNotificationCenterGetDarwinNotifyCenter`). It exists because the flag alone
isn't enough: if the app is already foregrounded when the intent runs, there's no
`scenePhase` transition to react to, and the flag would sit unnoticed. Darwin
notifications are delivered immediately regardless of app state or process. The
`@convention(c)` callback can't capture context, hence the static handler box.

`ScascanApp` also polls the flag briefly (15 × 200 ms) on launch and foreground,
because there's no ordering guarantee between "app becomes active" and "the
intent that caused it finishes writing the flag".

> A Control Center toggle was built for this and **abandoned** — the code is
> gone. It required its own extension target (a `ControlWidget` can't live in an
> existing `WidgetBundle`), and even once it registered correctly it never
> reliably drove navigation. Siri/Shortcuts does the same job in-process. Don't
> resurrect it without a specific reason.

---

## 10. The evening recap

`Recap/DailyRecapView.swift` + `DailyRecapState.swift`, fed by
`LogRepository.dailyRecap(forDateOffset:)`.

- **Unlocks at 21:00** (`NotificationHelper.recapHour`) for the current day; past
  days are always available. Before then the screen shows a locked card with a
  live countdown and a shortcut to yesterday. A timer unlocks it in place if the
  user is already sitting there when 21:00 arrives.
- **Day picker** in the toolbar reaches 30 days back, and always at least far
  enough to include the day currently selected.
- **The reveal is sequenced** (`play(_:proxy:)`): meals stagger in, the water bar
  fills, the deductions land as counting numbers, then the verdict ring draws and
  the view scrolls to it. Phases are a `Comparable` enum; cancellation unwinds
  cleanly through `Task.sleep`, so switching days mid-play is safe.
- **A day with no meals gets no verdict** — a neutral card instead. Announcing
  "well under target" from the absence of data is a lie.
- `CountingNumber` is a `View` conforming to `Animatable`; SwiftUI hands it every
  interpolated value, which is what makes digits roll. The water fill is eased,
  not sprung, because a spring overshoots and an overshooting *number* flashes a
  total the user never drank.

---

## 11. Notifications and the widget

`NotificationHelper` (ScaScanKit) owns three things:

- **Hydration reminders** — 3 per day, evenly spaced across 10:00–20:00, each a
  one-shot `UNCalendarNotificationTrigger`. Logging water pushes the remaining
  ones back proportionally to the amount (relative to the user's typical
  quick-add), clamped to the window. Today's times are persisted so a delay
  survives a relaunch, and the schedule is topped up on every foreground. **The
  widget's quick-add delays them too** — most water gets logged from the widget,
  so doing it only in-app was a real gap.
- **The 21:00 recap notification** — a single repeating trigger (its time never
  moves), so it keeps firing without the app being opened. The trade-off is that
  its text is fixed at scheduling time and can't quote the day's numbers.
- **Analysis-complete** notifications from `AnalysisManager`.

`NotificationCoordinator` (app target) routes taps: a recap notification opens
that day's recap — **deriving which day from the notification's delivery date**,
so tapping last night's banner over breakfast opens last night's recap, not an
empty one. A hydration tap opens the Log tab. It's constructed in
`AppContainer.init` because a tap that *launched* the app is only delivered if a
delegate already exists by the time launching finishes.

The **widget** (`ScaScanWidget/`) shows today's calories, macros and water, with
`AppIntent` buttons for water add/undo and deep links for quick scans. It runs
`LogRepository` against the shared store with `NoopHealthProvider`. Timeline
refresh is every 30 minutes plus on data change (`WidgetCenter.reloadAllTimelines`
from `AppContainer`'s `onDataChanged` hook).

---

## 12. Concurrency — the trap in this project

`SWIFT_DEFAULT_ACTOR_ISOLATION = MainActor` means a closure without an explicit
isolation annotation is inferred `@MainActor`. When a framework then invokes that
closure **from its own background thread**, you get
`_dispatch_assert_queue_fail` / `EXC_BREAKPOINT` — a crash that looks like a
threading bug in the framework and isn't.

This actually happened with `AVAudioEngine.installTap` and the speech recognition
callbacks. The fix pattern, used in `VoiceLogController` and
`NotificationCoordinator`:

1. Mark the closure or delegate method `@Sendable` / `nonisolated` so it is
   genuinely non-isolated.
2. Pull plain values out of any non-`Sendable` argument right there.
3. Hop back explicitly with `Task { @MainActor in ... }`.

Do not assume a delegate protocol is main-actor isolated because "it usually is".
`UNUserNotificationCenterDelegate` carries no such annotation — that was checked
in the SDK header, not guessed.

---

## 13. Navigation and deep links

`AppContainer` (app target) is the composition root — plain manual DI through the
SwiftUI environment, no framework. It holds the repositories plus `deepLinkTab`
and `deepLinkRoute`.

`MainTabView` hosts three tabs; Scan and Log each own a `NavigationStack` path,
because a shared path would push screens under whichever tab happened to be
showing. Deep links are drained **on appearance as well as on change** — a link
resolved during a cold launch is set before the view exists, and `onChange`
wouldn't fire for it.

URL scheme (`ScascanApp.handle`):

```
scascan://log      scascan://camera    scascan://barcode
scascan://search   scascan://voice     scascan://recap[?day=-1]
```

`?day=` is a non-positive day offset. `scascan://recap` is also the most
convenient way to reach the recap when testing (see §15).

---

## 14. Localization — read this before adding UI text

The app ships **Italian** (`Scascan/Localizable.xcstrings`, ~270 keys) plus the
Siri phrases in `AppShortcuts.xcstrings`. Source language is English; all code,
comments and commits are English.

Two traps, both of which have already caused shipped-in-English UI:

1. **A `String` handed to `Text` is never extracted.** Only literals in
   `Text("…")` / `LocalizedStringKey` / `LocalizedStringResource` positions reach
   the catalog. So a computed `var headline: String` is invisible to translation
   no matter how complete the catalog is. Use `LocalizedStringResource` for any
   computed or parameterised label — that's why `DailyRecapView`'s verdict text,
   countdown and ledger rows, and `LogView`'s breakdown rows, are typed that way.
   Verify with the compiler's own output:
   `DerivedData/.../Objects-normal/arm64/<File>.stringsdata`.
2. **`xcodebuild` does not write back to `.xcstrings` — only the Xcode IDE
   does.** If you add strings and only ever build from the terminal, the catalog
   silently stays behind. The workaround used here: read the authoritative key
   list out of the `.stringsdata` files and edit the catalog directly, preserving
   Xcode's formatting (2-space indent, `"key" : value` with the space before the
   colon). Xcode may re-sort keys later with its own collation; that's cosmetic.

Don't smuggle a formatted date through a `LocalizedStringResource` — it produces
a junk `"%@"` catalog key. Keep translated words and locale-formatted dates
apart (see `DailyRecapState.DayLabel`).

Verify translations landed by reading them out of the built app:

```bash
plutil -convert json -o - "$BUILT/Scascan.app/it.lproj/Localizable.strings"
```

---

## 15. Building, running, verifying

Simulator used throughout: `F0A8FA48-FB86-45BF-8A93-AE91BBA66055`.

```bash
cd ios
xcodebuild -project Scascan.xcodeproj -scheme Scascan \
  -destination 'platform=iOS Simulator,id=<SIM>' -configuration Debug build

BUILT=~/Library/Developer/Xcode/DerivedData/Scascan-*/Build/Products/Debug-iphonesimulator
xcrun simctl install  <SIM> "$BUILT/Scascan.app"
xcrun simctl launch   <SIM> com.nicoloperri.Scascan
xcrun simctl spawn    <SIM> launchctl list | grep Scascan   # still alive?
xcrun simctl io       <SIM> screenshot out.png
```

Useful extras: `xcrun simctl launch <SIM> <id> -AppleLanguages "(it)" -AppleLocale it_IT`
to see the Italian UI; `xcrun simctl openurl` works but SpringBoard shows a
"Open in Scascan?" confirmation you can't dismiss headlessly, so driving the app
by deep link from the CLI usually means temporarily setting the route at launch
instead.

**The simulator has no API key**, so `RootView` stops at the setup screen and the
main UI is unreachable without either entering a key by hand or temporarily
bypassing the gate. There's no UI-automation tool wired up here — no taps.

To exercise real data, seed the App Group store directly (it's plain SQLite —
Core Data layout, `ZLOGENTRY` / `ZWATERLOG`, timestamps are seconds since
2001-01-01) and write the profile plist into
`.../Containers/Shared/AppGroup/<uuid>/Library/Preferences/group.com.nicoloperri.Scascan.plist`.
Clean up afterwards. That's how the recap ledger was verified against a hand
calculation: eaten 3050, burned 520, carry-over 326 → 2204 net against a 2208
target.

### Testing situation — be honest about this

`ScaScanKit/Tests/` exists and is meaningful, but **the tests cannot be run in
this project as configured**: `swift test` builds for macOS where `UIKit` is
missing (the package declares iOS only), and `xcodebuild test` reports "no test
bundles available to test" even though the scheme lists the test target. They are
kept compiling and correct, and can be type-checked against the real module:

```bash
xcrun -sdk iphonesimulator swiftc -typecheck -swift-version 6 \
  -target arm64-apple-ios26.0-simulator -enable-testing \
  -plugin-path "$TOOLCHAIN/usr/lib/swift/host/plugins/testing" \
  -I "$BUILT" -I "$PLATFORM/usr/lib" -F "$PLATFORM/Library/Frameworks" \
  ScaScanKit/Tests/ScaScanKitTests/*.swift
```

Until that's fixed, **verification means running the thing and checking real
numbers**, not "it builds". Wiring the test bundle up properly is a genuinely
useful piece of work for whoever has the time.

---

## 16. Working agreements with the repo owner

- He writes in Italian; reply in Italian. The codebase, comments and commit
  messages stay English.
- **Commit locally, push only when he explicitly says to.** Every time.
- Never rewrite or force-push shared history — this was asked for once and
  declined in favour of a normal revert commit.
- He will call out a guessed fix. Root-cause things empirically: compile a probe,
  read the SDK header, run the binary, check the numbers. A fix that hasn't been
  demonstrated to work isn't finished.
- Verify before claiming done: build, install, launch, and look at it.

---

## 17. Where things stand / open threads

- **Siri phrases have never been confirmed on a real device.** Everything
  verifiable without one was checked (the compiled `Metadata.appintents`, the NLU
  assets, the localized phrases), but whether Siri actually recognises "Registra
  a voce con ScaScan" is unknown.
- **The recap notification prompts for notification permission at first launch**,
  since the recap defaults to on. Fine for an existing user who already granted
  it; a first-run user now sees the prompt with no context.
- **Recap for a past day uses today's profile** (base target, weight, water goal)
  — historical snapshots aren't stored. Fine for now, wrong after a big profile
  change.
- **Test bundle isn't runnable** (§15).
- No cloud sync. Android backs up to Google Drive; the iOS equivalent would be
  CloudKit, which needs a paid developer account, so it's deliberately absent.
