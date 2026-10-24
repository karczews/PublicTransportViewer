# Map SDK flavors — design

**Date:** 2026-10-25
**Status:** Approved in brainstorming; awaiting written-spec review
**Branch:** `feat/map-libre`

## 1. Goal

Build the same app on top of different map SDKs so they can be compared:
TomTom (today) and MapLibre now, others later. Two kinds of comparison are wanted:

- **Build-level** (APK size, cold start, memory, integration effort): one APK per SDK,
  containing only that SDK.
- **Look and feel on one install**: an "all" APK with a runtime toggle between SDKs.

Each SDK implementation must be free to use SDK-specific capabilities (the user intends to
experiment per SDK), so the map screen is owned entirely by the SDK module. Shared code offers
optional helpers, not a lowest-common-denominator map API.

## 2. Decisions

| Topic | Decision |
|---|---|
| Comparison mode | Both: per-SDK flavors **and** an `all` flavor with a runtime toggle |
| MapLibre integration | `org.maplibre.compose` (maplibre-compose) `0.20.0`, not the View-based `org.maplibre.gl:android-sdk` |
| Shared/SDK boundary | **Full Home screen per SDK.** Each SDK module ships its own Home screen; `HomeViewModel` and everything outside the Home tab are shared |
| Activities | One `MainActivity` for all flavors; the flavor only decides which `MapSdk`s are passed in |
| MapLibre render backend | OpenGL runtime by default, with a comment in the build file documenting the Vulkan switch |
| MapLibre style | Maptoolkit community style `https://styles.maptoolkit.org/summer.json` (no API key) |

## 3. Non-goals

- A generic, SDK-neutral map API (markers/polylines abstraction).
- The per-SDK experiments themselves. This work only delivers a TomTom-parity baseline for MapLibre.
- In-app performance metrics or benchmark modules (possible follow-up: Macrobenchmark per flavor).
- Any change to the Stops or Alerts tabs.
- Running instrumented tests in CI (unchanged: CI runs unit tests, lint and build only).

## 4. Module layout

```
:app            PublicTransportApp, MainActivity (no SDK code), product flavors,
 │              src/<flavor>/java/…/AvailableMapSdks.kt
 ├─ :core       Room (+ schemas/), GTFS/RT, repositories, worker, CoreModule (Koin),
 │              view models, Stops/Alerts screens, theme, drawables, nav scaffold,
 │              MapSdk interface, Home-tab host (init gate + toggle), optional Home helpers
 ├─ :map-tomtom    → :core   TomTomMapSdk + TomTomHomeScreen (TomTom SDK only), BuildConfig.TOMTOM_API_KEY
 ├─ :map-maplibre  → :core   MapLibreMapSdk + MapLibreHomeScreen (maplibre-compose only)
 └─ :core-testing  → :core   test fakes and runner (androidTestImplementation only)
```

| Module | Gradle plugins | Namespace |
|---|---|---|
| `:app` | `com.android.application`, kotlin compose, koin compiler | `com.github.karczews.publictransportviewer` (unchanged) |
| `:core` | `com.android.library`, kotlin compose, ksp, room, koin compiler | `com.github.karczews.publictransportviewer.core` |
| `:map-tomtom` | `com.android.library`, kotlin compose | `com.github.karczews.publictransportviewer.map.tomtom` |
| `:map-maplibre` | `com.android.library`, kotlin compose | `com.github.karczews.publictransportviewer.map.maplibre` |
| `:core-testing` | `com.android.library`, kotlin compose | `com.github.karczews.publictransportviewer.core.testing` |

- Add `android-library = { id = "com.android.library", version.ref = "agp" }` to the version catalog.
  Library modules use AGP's built-in Kotlin exactly like `:app` does today (no separate Kotlin Android plugin).
- `:app` keeps the NDK `abiFilters` (`arm64-v8a`, `x86_64`), `compileSdk`/`minSdk`/`targetSdk`
  and Java 11 settings; library modules use the same `compileSdk`, `minSdk` and Java 11.
- SDK modules depend on `:core` only. They never touch Koin directly; they receive `HomeViewModel`
  as a parameter. If an experiment later needs SDK-specific DI, that module adds its own Koin
  `@Module` then (not designed here).

## 5. The `MapSdk` contract

In `:core`, package `com.github.karczews.publictransportviewer.map`:

```kotlin
interface MapSdk {
    /** Stable identifier, persisted by the toggle: "tomtom", "maplibre". */
    val id: String

    /** Label shown in the toggle. */
    val displayName: String

    /** Idempotent SDK start-up. Awaited by the Home-tab host before the first [HomeScreen]. */
    suspend fun initialize(context: Context) {}

    /** The whole Home tab: map, markers, location, permission flow, controls, experiments. */
    @Composable
    fun HomeScreen(viewModel: HomeViewModel, modifier: Modifier)
}
```

Rules for SDK screens:

- They own everything inside the Home tab and may ignore any part of `HomeViewModel`
  or keep extra local state.
- In the `all` flavor the toggle is drawn top-center under the status bar, so SDK screens keep
  that strip free.

Optional helpers in `:core` (package `…ui.home`). SDK screens may use them, nothing enforces it:

- `HomeMapDefaults`: Łódź center (`51.7592, 19.4560`), initial zoom `13.0`, recenter zoom `15.0`.
- `VehicleFormatting.kt`: `vehicleTitle(vehicle)` (`"Tram 10 → Chocianowice IKEA"`, today's
  `buildBalloonText`), `vehicleLabel(vehicle)` (`routeShortName ?: routeId`) and
  `vehicleIconRes(type)` (`R.drawable.ic_tram` / `ic_bus`).
- Drawables `ic_tram`, `ic_bus`, `ic_stop_dot`, `ic_my_location`.

## 6. File moves (PR 1)

Kotlin packages of moved files stay unchanged. Only `R` imports change, to
`com.github.karczews.publictransportviewer.core.R`. Use `git mv` so history is kept.

| From (`app/…`) | To |
|---|---|
| `data/**`, `di/AppModule.kt` (renamed `CoreModule`), `ui/alerts/**`, `ui/stops/**`, `ui/theme/**`, `ui/home/HomeViewModel.kt` | `core/src/main/java/…` (same packages) |
| `PublicTransportViewerApp` + `AppDestinations` from `MainActivity.kt` | `core/…/ui/PublicTransportViewerApp.kt` (package `…ui`) |
| `ui/home/HomeScreen.kt` | `map-tomtom/…/map/tomtom/TomTomHomeScreen.kt`, nearly verbatim; formatting and defaults swapped for the helpers |
| SDK init from `MainActivity.initTomTomSdk()` | `TomTomMapSdk.initialize()` |
| `res/drawable/ic_alert, ic_bus, ic_home, ic_my_location, ic_stop_dot, ic_tram` | `core/src/main/res/drawable/` |
| `app/schemas/` | `core/schemas/` (`room { schemaDirectory("$projectDir/schemas") }` in `:core`) |
| `src/test/**` (6 test classes) | `core/src/test/**` |
| `src/androidTest/…/KoinTestRunner.kt`, `TestPublicTransportApp.kt` (test module, `FixtureData`, `Fixed*` repositories) | `core-testing/src/main/java/…` |
| `src/androidTest/…/ui/AlertsScreenTest.kt`, `StopsScreenTest.kt`, `NavigationTest.kt` | `core/src/androidTest/…`. All three launch `MainActivity` today, which stays in `:app`, so they switch to `createComposeRule()` (§12) |

What stays in `:app`:

- `PublicTransportApp`, the `@KoinApplication(modules = [CoreModule::class])` aggregator and `MainActivity`.
- `AndroidManifest.xml`, unchanged: permissions, WorkManager initializer removal, application, activity.
- Launcher icons/mipmaps, `colors.xml`, `themes.xml`, backup/data-extraction XML and `strings.xml`.
  `strings.xml` loses `app_name` in PR 2.
- The `koinCompiler { compileSafety = true; strictSafety = true }` block. `:core` gets the same block.

What moves out of `:app`: the `tomtomApiKey` property lookup and the `TOMTOM_API_KEY` `buildConfigField`
go to `:map-tomtom` (§10). Two reasons:
- The `maplibre` APK then carries no TomTom key.
- `:map-tomtom`'s own instrumented test can initialize the SDK.

`:app` drops `buildConfig = true` unless something else still needs it.

`MainActivity` becomes:

```kotlin
setContent {
    PublicTransportViewerTheme {
        PublicTransportViewerApp(mapSdks = availableMapSdks)
    }
}
```

It no longer has an SDK-init spinner (see §7).

## 7. Shell: Home-tab host, init gate, toggle

`PublicTransportViewerApp(mapSdks: List<MapSdk>)` (requires a non-empty list) keeps today's
`NavigationSuiteScaffold` with Home/Stops/Alerts. The Home destination renders a host composable
(`MapSdkHost`, in `:core` `ui/home`) that:

1. **Picks the SDK.**
   - PR 1: always `mapSdks.first()`.
   - PR 3: `resolveMapSdk(available, savedId)` returns the SDK whose `id == savedId`, otherwise
     `available.first()`. `savedId` comes from `MapSdkPreference`, a SharedPreferences wrapper
     (file `map_sdk`, key `map_sdk_id`).
2. **Gates on init.** It awaits `sdk.initialize(context.applicationContext)` and shows a centered
   `CircularProgressIndicator` in the Home tab until it returns. If it throws, the tab shows
   `"Couldn't start <displayName>: <message>"` (no retry button). Successful ids are kept in a
   set remembered at the `PublicTransportViewerApp` level, so returning to an SDK doesn't flash the spinner.
   - **Behavior change from today:** Stops and Alerts no longer wait for map-SDK init.
     Today the whole UI waits in `MainActivity`.
3. **Renders.** `key(sdk.id) { sdk.HomeScreen(koinViewModel<HomeViewModel>(), Modifier.fillMaxSize()) }`.
   The `key` makes a switch dispose the old map completely, so only one map is alive at a time.
4. **Toggle (PR 3).** Shown only when `mapSdks.size > 1`. It is a Material 3
   `SingleChoiceSegmentedButtonRow`, aligned top-center with `statusBarsPadding()` + `8.dp` top
   padding. It has test tag `map_sdk_toggle`, one `SegmentedButton` per SDK labeled
   `displayName` with tag `map_sdk_option_<id>`, and it writes the chosen id to `MapSdkPreference`.

⚠ An initialized SDK stays loaded for the rest of the process (TomTom has no clean teardown).
After a switch in the `all` flavor, memory and startup figures are polluted.
**Measurements come from the single-SDK flavors**; `all` is for look-and-feel comparison.

## 8. Flavors (`:app`, PR 2 and PR 3)

```kotlin
android {
    defaultConfig {
        missingDimensionStrategy("tomtom-sdk-version", "complete") // stays: TomTom AARs are flavored
    }
    buildFeatures { resValues = true } // per-flavor app_name; set explicitly, AGP 9 may default it off
    flavorDimensions += "mapSdk"
    productFlavors {
        create("tomtom")   { dimension = "mapSdk"; applicationIdSuffix = ".tomtom";   resValue("string", "app_name", "PTV TomTom") }
        create("maplibre") { dimension = "mapSdk"; applicationIdSuffix = ".maplibre"; resValue("string", "app_name", "PTV MapLibre") }
        create("all")      { dimension = "mapSdk"; applicationIdSuffix = ".all";      resValue("string", "app_name", "PTV Compare") } // PR 3
    }
    lint { checkDependencies = true }
}

dependencies {
    implementation(project(":core"))
    "tomtomImplementation"(project(":map-tomtom"))
    "maplibreImplementation"(project(":map-maplibre"))
    "allImplementation"(project(":map-tomtom"))   // PR 3
    "allImplementation"(project(":map-maplibre")) // PR 3
}
```

- The dimension is named `mapSdk` so it can't collide with TomTom's `tomtom-sdk-version`.
- Each flavor has exactly one source file,
  `app/src/<flavor>/java/com/github/karczews/publictransportviewer/AvailableMapSdks.kt`:
  - `tomtom`: `internal val availableMapSdks: List<MapSdk> = listOf(TomTomMapSdk())`
  - `maplibre`: `… = listOf(MapLibreMapSdk())`
  - `all`: `… = listOf(TomTomMapSdk(), MapLibreMapSdk())`. List order is the toggle order and the default.
- The `maplibre` flavor cannot reference TomTom classes at compile time, so no SDK leaks into another flavor's APK.
- In PR 1, which has no flavors, `AvailableMapSdks.kt` lives in `app/src/main/java/…` with the TomTom
  line and `:app` has `implementation(project(":map-tomtom"))`. PR 2 moves the file into the flavor dirs.
- The three APKs install side by side. Each has its own Room DB and does its own first GTFS download.

## 9. Camera handoff (PR 3)

In `:core` `ui/home`:

```kotlin
data class MapCamera(val lat: Double, val lon: Double, val zoom: Double)
```

`HomeViewModel` gains:

```kotlin
private val _camera = MutableStateFlow<MapCamera?>(null)
val camera: StateFlow<MapCamera?> = _camera.asStateFlow()   // null → use HomeMapDefaults
fun onCameraChanged(camera: MapCamera) { _camera.value = camera }
```

Each SDK screen reads `camera.value` once for its initial camera, falling back to `HomeMapDefaults`.
It calls `onCameraChanged` when the camera settles, or at minimum from a `DisposableEffect`'s `onDispose`.
Bearing and tilt are not handed off. The selected vehicle and route already survive a switch:
`HomeViewModel` is scoped to the Activity's `ViewModelStore`.

## 10. `:map-tomtom`

- `TomTomMapSdk(private val apiKey: String = BuildConfig.TOMTOM_API_KEY) : MapSdk` with `id = "tomtom"`,
  `displayName = "TomTom"`. This is `:map-tomtom`'s own `BuildConfig`: the module sets `buildConfig = true` and
  `buildConfigField("String", "TOMTOM_API_KEY", …)` from the `tomtomApiKey` Gradle property, logic moved from `:app`.
- `initialize()` contains today's logic: `if (!TomTomSdk.isInitialized) TomTomSdk.initialize(...)`
  with `buildSdkConfiguration(apiKey = apiKey, telemetryUserConsent = { UserConsent.TelemetryOn })`.
- `HomeScreen` calls `TomTomHomeScreen(viewModel, modifier)`, today's `HomeScreen.kt` with unchanged
  behavior and test tags (`recenter_button`).
- Dependencies:
  - `:core` and the Compose BOM set it needs.
  - `libs.tomtom.sdk.init` and `libs.tomtom.sdk.map.display.compose`, both keeping today's
    `exclude(group = "com.google.protobuf", module = "protobuf-javalite")`.
- `defaultConfig { missingDimensionStrategy("tomtom-sdk-version", "complete") }`.

## 11. `:map-maplibre`

### Dependencies

Version catalog:

```toml
[versions]
maplibreCompose = "0.20.0"

[libraries]
maplibre-compose = { group = "org.maplibre.compose", name = "maplibre-compose", version.ref = "maplibreCompose" }
maplibre-compose-location = { group = "org.maplibre.compose", name = "location", version.ref = "maplibreCompose" }
maplibre-compose-runtime-opengl = { group = "org.maplibre.compose", name = "maplibre-compose-runtime-opengl-android", version.ref = "maplibreCompose" }
maplibre-compose-runtime-vulkan = { group = "org.maplibre.compose", name = "maplibre-compose-runtime-vulkan-android", version.ref = "maplibreCompose" }
```

`map-maplibre/build.gradle.kts` (the comment is required):

```kotlin
    implementation(libs.maplibre.compose)
    implementation(libs.maplibre.compose.location)

    // MapLibre render backend. maplibre-compose needs exactly ONE native runtime on the classpath:
    //   - OpenGL (default): widest device/emulator support  -> libs.maplibre.compose.runtime.opengl
    //   - Vulkan:           replace the line below with      -> libs.maplibre.compose.runtime.vulkan
    // Never add both. The runtime must match the maplibre-compose version; both use the
    // `maplibreCompose` version ref in gradle/libs.versions.toml, so bump them together.
    runtimeOnly(libs.maplibre.compose.runtime.opengl)
```

### `MapLibreMapSdk`

`MapLibreMapSdk(private val styleUrl: String = MAPTOOLKIT_SUMMER_STYLE) : MapSdk`, with
`id = "maplibre"` and `displayName = "MapLibre"`. `initialize()` is a no-op.
`MAPTOOLKIT_SUMMER_STYLE = "https://styles.maptoolkit.org/summer.json"`.

### Baseline: TomTom parity

| Behavior | TomTom today | `MapLibreHomeScreen` |
|---|---|---|
| Initial camera | Łódź center, zoom 13 | `MaplibreMap` with the Maptoolkit style; camera from `viewModel.camera` (PR 3) or `HomeMapDefaults` |
| Vehicles | One `Marker` per vehicle: tram/bus icon, route label white text, black outline | One in-memory GeoJSON point source rebuilt on each vehicle-list emission. Feature properties: `id`, `label`, `type`. A `SymbolLayer` picks the icon by `type` and draws the `label` text with a dark halo |
| Select vehicle | Marker click → `onVehicleSelected` | Vehicle layer `onClick`: read `id` of the first feature → look up the vehicle → `viewModel.onVehicleSelected(it)` → `ClickResult.Consume` |
| Selected route | `Polyline` in route color, widths by zoom, translucent black outline | Two `LineLayer`s on a LineString source, outline below and color on top, widths via `interpolate(…, zoom(), …)` at the same stops as `TomTomHomeScreen`'s `WidthByZoom` list. Check `WidthByZoom`'s (width, zoom) parameter order when porting |
| Route stops | `Marker` with `ic_stop_dot` + dark-gray name label, white outline | `SymbolLayer` on a points source: `ic_stop_dot` icon + name text, white halo |
| Selected-vehicle info | Native balloon with `vehicleTitle` | MapLibre has no balloons: an `ElevatedCard` (tag `vehicle_info_card`) showing `vehicleTitle(vehicle)` while a vehicle is selected |
| Tap empty map | `clearSelection()` | `callbacks { click { onUnhandled → viewModel.clearSelection() } }` |
| Blue dot | TomTom `LocationProvider` + `CurrentLocationMarker` | `rememberDefaultLocationProvider()` (platform provider, no Play Services) + `rememberLocationState(...)` + `LocationIndicatorLayer`, only while permission is granted |
| Recenter | 48dp `FilledTonalIconButton`, tag `recenter_button`, bottom-end: request permission if missing, else animate to the last location at zoom 15 | Same button and tag. Uses the library's `locationState.permission` / `requestPermission` and animates `cameraState` to the current location at `HomeMapDefaults` recenter zoom |

### Maptoolkit attribution (mandatory)

Per https://docs.maptoolkit.org/attribution/:

- **Logo.** `maptoolkit-attribution.png` from `https://docs.maptoolkit.org/images/maptoolkit-attribution.png`,
  committed as `map-maplibre/src/main/res/drawable-nodpi/maptoolkit_attribution.png`.
  - Shown as a fixed overlay on the map, bottom-start, at least `24.dp` tall, with test tag `maptoolkit_logo`.
  - Tapping it opens `https://www.maptoolkit.org/`.
  - If the downloaded asset isn't high-density enough for 24dp on xxhdpi, record that in the PR.
- **Copyright line.** `© Maptoolkit © OpenStreetMap` comes through the library's built-in attribution
  ornament, which reads it from the style's TileJSON. That ornament stays enabled.
- **Visibility.** Neither element may be covered at any size, orientation or zoom. The recenter button and
  `vehicle_info_card` are padded to clear both; verify by screenshot on a phone emulator in portrait and landscape.
- **MapLibre's own logo ornament.** Disable it if the library enables it by default. MapLibre's
  BSD license doesn't require it, and it would crowd the Maptoolkit logo.

### API details to confirm against 0.20.0 while implementing

The names above come from the maplibre-compose docs. Confirm the exact signatures in the 0.20.0 API reference:

- How a vector drawable is registered as a layer icon image.
- How a clicked feature's properties are read.
- The camera-state read and animate API.
- Whether `locationState.permission` notices a grant made earlier in the session.
- That a built-in attribution ornament exists on the 0.20.0 (`maplibre-native-ffi`) backend and shows the
  style's attribution. If it doesn't, draw `© Maptoolkit © OpenStreetMap` ourselves, bottom-end, with both links
  (`https://www.maptoolkit.com/copyright/`, `https://www.openstreetmap.org/copyright`).
  Bottom-end is where `recenter_button` sits, so the button must be offset above the attribution, not just padded.

## 12. Testing

- **Unit tests in `:core`:**
  - The 6 existing classes move unchanged (`DefaultStopRepositoryTest`, `DefaultVehicleRepositoryTest`,
    `GtfsCsvParserTest`, `GtfsRtVehicleDataSourceTest`, `HomeViewModelTest`, `StopsViewModelTest`).
  - New in PR 3:
    - `resolveMapSdk` returns the saved SDK and falls back to the first on an unknown or null id.
    - `HomeViewModel.camera` starts `null` and `onCameraChanged` updates it.
- **`:core-testing`** holds:
  - `KoinTestRunner` and `TestPublicTransportApp` (with `testModule`).
  - `FixtureData` and the `Fixed*` repositories.
  - `FakeMapSdk(id, displayName, initialize: suspend () -> Unit = {})`. Its `HomeScreen` is a `Box`
    with tag `fake_map_<id>`.
  - Consumed as `androidTestImplementation(project(":core-testing"))`. Each module with instrumented
    tests sets `testInstrumentationRunner = "com.github.karczews.publictransportviewer.KoinTestRunner"`.
- **`:core` instrumented tests.** All three use `createComposeRule()` and render
  `PublicTransportViewerTheme { PublicTransportViewerApp(mapSdks = …) }` with fakes, instead of
  `createAndroidComposeRule<MainActivity>()`. `:core` (and any SDK module with Compose UI tests) needs
  `debugImplementation(libs.androidx.compose.ui.test.manifest)`.
  - `StopsScreenTest` and `AlertsScreenTest`: only the rule and a `setContent` in `@Before` change; the test bodies stay.
  - `NavigationTest` covers:
    - the three tabs are shown, and Stops/Alerts navigation still works;
    - with one SDK, the fake map is shown (PR 3 adds: and there's no `map_sdk_toggle`);
    - (PR 3) with two SDKs, the toggle is shown and tapping `map_sdk_option_<id>` swaps `fake_map_<id>`;
    - a suspended `initialize` shows the spinner, and a throwing `initialize` shows the error text.
  - None of these need a map SDK or an API key.
- **`:map-tomtom` instrumented:** `TomTomHomeScreenTest` with a fixture `HomeViewModel`
  (`HomeViewModel(FixedVehicleRepository(), FixedRouteDisplayRepository())`) asserts `recenter_button`
  is displayed. This is today's `homeScreen_showsRecenterButton`, moved. It first awaits
  `TomTomMapSdk().initialize(context)`, which uses the module's `BuildConfig.TOMTOM_API_KEY`.
- **`:map-maplibre` instrumented:** `MapLibreHomeScreenTest`, same setup, asserts `recenter_button`
  and `maptoolkit_logo` are displayed.

## 13. CI

All jobs keep `-PtomtomApiKey=""`.

| Workflow | PR 1 | PR 2 | PR 3 |
|---|---|---|---|
| `build.yml` | `assembleDebug` (unchanged) | same; now builds every flavor, so each flavor is proven to compile in isolation | same |
| `run-tests.yml` | `testDebugUnitTest` (unchanged), upload path → `*/build/reports/tests/` | same | same |
| `lint.yml` | `lintDebug` with `checkDependencies = true`; report path unchanged | `lintTomtomDebug lintMaplibreDebug`; upload `app/build/reports/lint-results-*Debug.*` | `lintAllDebug`; upload `app/build/reports/lint-results-allDebug.*` |

Verify in PR 1 that root `testDebugUnitTest` runs `:core`'s tests, with the same test count as before the move.

## 14. Docs

Each PR updates what it changes:

- **`AGENTS.md`**
  - Build commands: `installTomtomDebug`, `installMaplibreDebug`, `installAllDebug`; lint task per §13;
    single-test commands target `:core`, e.g. `./gradlew :core:testDebugUnitTest --tests "*.HomeViewModelTest"`
    and `./gradlew :core:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.github.karczews.publictransportviewer.ui.StopsScreenTest`.
  - API key: still `tomtomApiKey` in `gradle.properties`; it's now injected into `:map-tomtom`'s `BuildConfig`.
  - Architecture: the module list, the `MapSdk` contract, and per-SDK map sections replacing the single
    TomTom "Map Rendering" section.
- **`docs/ARCHITECTURE.md`**
  - A module dependency diagram (mermaid); "Map Renderer" becomes "Map SDKs", covering TomTom and MapLibre.
  - An "Adding a new map SDK" checklist:
    1. Create a `:map-xyz` library module depending on `:core`.
    2. Implement `MapSdk` and its `HomeScreen`.
    3. Add the `xyz` flavor and `src/xyz/…/AvailableMapSdks.kt`.
    4. Add it to the `all` flavor's dependencies and list.
    5. Add an instrumented smoke test.

## 15. Delivery: three PRs

| # | Branch | Type | Contents |
|---|---|---|---|
| 1 | `refactor/extract-core-module` (from `feat/map-libre`, carries this spec) | `refactor` | `:core`, `:core-testing`, `:map-tomtom` (incl. the API key), `MapSdk`, `MapSdkHost` with init gate, helpers, test moves, CI path/lint tweaks, docs. No flavors, no toggle code. The only user-visible change is the spinner moving into the Home tab |
| 2 | `feat/map-libre`, recreated from `main` after PR 1 is squash-merged (the old branch holds only the spec commit) | `feat` | `:map-maplibre`, flavors `tomtom` + `maplibre`, per-flavor `AvailableMapSdks.kt`, `app_name` via `resValue`, id suffixes, CI lint tasks, docs |
| 3 | `feat/map-sdk-toggle` (from updated `main`) | `feat` | `all` flavor, toggle UI + `resolveMapSdk`/`MapSdkPreference`, camera handoff (`MapCamera`, `HomeViewModel.camera`, both screens), any packaging fixes for bundling both SDKs, NavigationTest toggle cases, CI `lintAllDebug`, docs |

## 16. Risks and verification steps

1. **Kotlin ceiling.** Kotlin is held at 2.4.0 for koin-compiler (see `renovate.json`). maplibre-compose
   0.20.0 and its Compose Multiplatform artifacts must compile under it.
   - First step of PR 2: add the dependency and run `./gradlew :map-maplibre:compileDebugKotlin`.
   - On failure, try the newest older maplibre-compose that compiles. If none works, stop and ask the
     user; falling back to `org.maplibre.gl:android-sdk` is a product decision.
2. **Compose version drift.** maplibre-compose brings `org.jetbrains.*` Compose/lifecycle artifacts that
   may resolve androidx Compose above BOM 2026.09.00 on the maplibre classpaths. Compare
   `./gradlew :app:dependencies --configuration maplibreDebugRuntimeClasspath` against the tomtom one.
   Accept drift if build, tests and a manual run pass, and state the resolved versions in the PR description.
3. **Both SDKs in one APK (`all`).** Run `./gradlew :app:assembleAllDebug :app:checkAllDebugDuplicateClasses`.
   - Duplicate identical native libs (e.g. `libc++_shared.so`): resolve with `packaging { jniLibs { pickFirsts += … } }`.
   - Different versions of the same lib, or duplicate classes: stop and report.
4. **protobuf-javalite exclusion**, now declared in `:map-tomtom`: confirm
   `./gradlew :app:dependencies --configuration tomtomDebugRuntimeClasspath | grep protobuf` shows only `protobuf-java`.
5. **Koin compile safety across modules.** `./gradlew :app:assembleDebug` must pass with `CoreModule` in
   `:core` and the aggregator in `:app`.
   - **`:core-testing` loop.** `:core-testing` depends on `:core`, and `:core` uses it as `androidTestImplementation`.
     Run `./gradlew :core:assembleDebugAndroidTest` early in PR 1 to prove Gradle accepts this.
6. **Room.** Same DB name (`gtfs.db`), version and entities, so existing installs keep their data in PR 1.
   The schema JSONs are present under `core/schemas/`.
7. **maplibre-compose is pre-1.0.** Renovate already blocks automerge for 0.x minors. The library, location
   and runtime artifacts share one `maplibreCompose` version ref, so they always move together.

## 17. Open items (non-blocking)

- Review the Maptoolkit Community License terms (https://www.maptoolkit.org/tos) before any public release.
- Measurement tooling (e.g. a Macrobenchmark module for cold start per flavor) is a possible follow-up.
