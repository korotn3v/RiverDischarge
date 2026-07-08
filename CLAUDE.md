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

The app is a single module, single package (`com.example.riverdischarge`), split into focused source files by layer. There is no multi-module setup, no ViewModel, and no navigation library — all state is plain Compose `remember`/`mutableStateOf` held in `RiverDischargeApp`. Because everything shares one package, declarations are visible across files without imports; cross-file top-level helpers are `internal` (file-private `private` only stays for things used within a single file). Each `.kt` file currently carries the full shared import block (harmless unused-import warnings) — run "Optimize Imports" if trimming is wanted.

**Source files:**

- `MainActivity.kt` — the `MainActivity` Activity, `RiverDischargeApp` root (holds all state, switches `HomeScreen` ↔ `EditorScreen`), `HomeScreen`/`SurveyCard`, and `EditorScreen` with its `NavigationBar`.
- `Model.kt` — all enums (`BankSide`, `BankType`, `VelocityPoint`, `MeasurementMethod`), `*Input` types, parsed/domain types, `ParseState`, `LocalDepthPreview`.
- `EditorSteps.kt` — the five tab composables (`PassportStep`, `SectionStep`, `VelocityStep`, `BanksStep`, `ResultStep`) plus their sub-editors (`BankSelector`, `SectionPointEditor`, `VelocityEditorCard`).
- `Charts.kt` — canvas drawings: `SectionProfileCard` (cross-section) and the velocity эпюры (`VelocityProfilesCard`/`VelocityProfileChart`, `buildEpureProfile`, `smoothPathThrough`).
- `Components.kt` — generic UI bits (`SimpleDataTable`, `TableRow`, `SegmentCard`, `MetricRow`, `EmptyCard`, `InfoCard`, `ErrorCard`).
- `Parsing.kt` — `validatePassport`, `parseSection`, `previewLocalDepth`, `parseVelocityStage`, `parseBanks`, `parseSurvey`.
- `Calculation.kt` — `calculateDischarge`, `depthAt`, `integrateArea`.
- `ClipboardExport.kt` — `copyTextToClipboard` and the `build*ClipboardText` / `buildFullClipboardExport` helpers.
- `SurveyRepository.kt` — `SurveyRepository` interface + `DataStoreSurveyRepository` (Preferences DataStore + `kotlinx.serialization`), plus a one-time migration from the legacy SharedPreferences blob.
- `Util.kt` — small shared helpers (`parseFlexibleDouble`, `parseNullableDouble`, `formatNumber`, `todayText`, `ParseState.getOrNull`/`errorOrNull`, list-replace extensions).
- `ui/theme/Theme.kt` — `RiverDischargeTheme` (Material You dynamic color on Android 12+, with a hand-tuned blue/teal "river" fallback for older devices and a dark variant).

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

`RiverDischargeApp` switches between `HomeScreen` (saved-survey list, "Новый замер" FAB) and `EditorScreen` (the 5 tabs). Each tab is its own `@Composable` (`PassportStep`, `SectionStep`, `VelocityStep`, `BanksStep`, `ResultStep`). Tabs are designed for one-handed use — primary actions live in the bottom thumb zone: the `NavigationBar` plus a "Сохранить" `ExtendedFloatingActionButton`. There is **no step-gating**: the user can jump to any tab, and each tab degrades gracefully when prerequisite data is missing (e.g. `VelocityStep` shows a "fix the profile first" `ErrorCard` when `sectionState` is `Error`). Validation only runs on save (`onSave` checks the precomputed `surveyState`), surfacing errors through the snackbar.

**Performance conventions (large surveys — dozens of depth points):** each editor tab owns its scroll container (`StepColumn` helper in `MainActivity.kt` for the small tabs); the list-heavy tabs (`SectionStep`, `VelocityStep`) are `LazyColumn`s with `key = input.id`, so only visible rows compose. Row callbacks (`onChange`/`onDelete: (id) -> Unit`) are memoized via `remember` + `rememberUpdatedState(draft)` — they must not capture `draft` directly, or every row recomposes on each keystroke. Parsing runs once per draft change: `parseSection` → `parseVelocityStage(draft, sectionState)` → `assembleSurvey(...)`; don't call `parseSurvey` per keystroke in composables. Hardware/gesture back is caught by `BackHandler` to return to the list.

**Calculation (method of average sections):**

- `parseSection` → `parseSurvey` → `calculateDischarge` is the pipeline.
- `depthAt()` linearly interpolates depth from profile points.
- `integrateArea()` uses the trapezoidal rule over profile points within a segment range.
- The shore segments use a bank coefficient × the nearest vertical's velocity; interior segments average adjacent verticals.
- Three-point velocity: `(v02 + 2·v06 + v08) / 4`.

**Persistence:** `SurveyRepository` (in `SurveyRepository.kt`) is the storage layer behind a swappable interface; the UI holds a `DataStoreSurveyRepository` and never touches storage directly. Surveys are kept as one `kotlinx.serialization` JSON string in **Preferences DataStore**, exposed as a reactive `Flow<List<SavedSurvey>>` (`collectAsState` in `RiverDischargeApp`), with `suspend save`/`delete` run on a coroutine scope — so the list refreshes without manual reloads and there is no main-thread I/O. The persisted types (`SurveyDraft`, `SectionPointInput`, `VelocityVerticalInput`, `SavedSurvey`) are `@Serializable`; the input layer (raw typed strings) is stored, never the computed values. A `DataMigration` copies the legacy SharedPreferences blob (`river_discharge_surveys`) into DataStore once on first run. Surveys are sorted descending by `updatedAt`.

**Clipboard export:** `buildFullClipboardExport` and its siblings produce tab-separated text ready to paste into Excel/Google Sheets.

**Key conventions:**
- All user-entered numeric strings go through `String.parseFlexibleDouble()` which replaces commas with dots before parsing.
- `formatNumber()` rounds to 3 decimal places and trims trailing zeros, always using `Locale.US` for the decimal separator.
- The canvas-drawn cross-section (`SectionProfileCard`) uses Android `Paint` directly via `nativeCanvas` because Compose doesn't expose text baseline control natively.
