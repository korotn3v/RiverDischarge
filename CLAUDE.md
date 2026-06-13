# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build commands

Requires `ANDROID_SDK_ROOT` to be set (e.g. `export ANDROID_SDK_ROOT="$HOME/Android/Sdk"`) and Android SDK packages installed (`platform-tools`, `platforms;android-34`, `build-tools;34.0.0`).

```bash
# Build debug APK → app/build/outputs/apk/debug/app-debug.apk
./scripts/build-debug.sh

# Install on connected device (USB debugging enabled)
./scripts/install-debug.sh

# First-time bootstrap (downloads Gradle wrapper if missing)
./scripts/bootstrap-gradle.sh assembleDebug
```

There are no automated tests in this project.

## Architecture

Almost all app code lives in one file: `app/src/main/java/com/example/riverdischarge/MainActivity.kt` (~1900 lines). The only other source file is `ui/theme/Theme.kt`, which defines `RiverDischargeTheme` (Material You dynamic color on Android 12+, with a hand-tuned blue/teal "river" fallback for older devices and a dark variant). There is no multi-module setup, no ViewModel, and no navigation library — all state is plain Compose `remember`/`mutableStateOf` held in `RiverDischargeApp`.

**Data model layers:**

- `*Input` types (`SurveyDraft`, `SectionPointInput`, `VelocityVerticalInput`) — raw string-based UI state exactly as the user typed.
- Parsed/domain types (`SectionData`, `VelocityVertical`, `BankData`, `ParsedSurvey`) — validated, typed values derived from the input layer.
- `ParseState<T>` sealed interface (`Ok`/`Error`) — the return type of every parse function; errors surface as inline `ErrorCard` composables rather than exceptions.

**5-tab editor** (`currentTab` 0–4, free navigation via a bottom `NavigationBar`):
1. Passport (name, river, date)
2. Section profile (cross-section depth points + bank edges)
3. Velocity verticals (speed measurements at 0.6h or 0.2h/0.6h/0.8h)
4. Bank types (empirical coefficient for the two shore segments)
5. Result (discharge calculation + clipboard export)

`RiverDischargeApp` switches between `HomeScreen` (saved-survey list, "Новый замер" FAB) and `EditorScreen` (the 5 tabs). Each tab is its own `@Composable` (`PassportStep`, `SectionStep`, `VelocityStep`, `BanksStep`, `ResultStep`). Tabs are designed for one-handed use — primary actions live in the bottom thumb zone: the `NavigationBar` plus a "Сохранить" `ExtendedFloatingActionButton`. There is **no step-gating**: the user can jump to any tab, and each tab degrades gracefully when prerequisite data is missing (e.g. `VelocityStep` shows a "fix the profile first" `ErrorCard` when `sectionState` is `Error`). Validation only runs on save, in the `onSave` lambda via `parseSurvey`, surfacing errors through the snackbar. Hardware/gesture back is caught by `BackHandler` to return to the list.

**Calculation (method of average sections):**

- `parseSection` → `parseSurvey` → `calculateDischarge` is the pipeline.
- `depthAt()` linearly interpolates depth from profile points.
- `integrateArea()` uses the trapezoidal rule over profile points within a segment range.
- The shore segments use a bank coefficient × the nearest vertical's velocity; interior segments average adjacent verticals.
- Three-point velocity: `(v02 + 2·v06 + v08) / 4`.

**Persistence:** `SurveyStorage` (bottom of the file) serialises surveys to `SharedPreferences` as a JSON array. `SurveyDraft.toJson()` / `JSONObject.toSurveyDraft()` are the serialisation helpers. Surveys are sorted descending by `updatedAt`.

**Clipboard export:** `buildFullClipboardExport` and its siblings produce tab-separated text ready to paste into Excel/Google Sheets.

**Key conventions:**
- All user-entered numeric strings go through `String.parseFlexibleDouble()` which replaces commas with dots before parsing.
- `formatNumber()` rounds to 3 decimal places and trims trailing zeros, always using `Locale.US` for the decimal separator.
- The canvas-drawn cross-section (`SectionProfileCard`) uses Android `Paint` directly via `nativeCanvas` because Compose doesn't expose text baseline control natively.
