# Map SDK Flavors Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the same app on TomTom and on MapLibre (maplibre-compose). There are per-SDK flavors plus an `all` flavor with a runtime toggle.

**Architecture:**
- **Modules.** Everything SDK-free moves from `:app` into `:core`. Each map SDK becomes a library module (`:map-tomtom`, `:map-maplibre`) that implements one small `MapSdk` interface and owns the whole Home tab.
- **`:app` stays thin.** It holds `PublicTransportApp`, `MainActivity` and product flavors. Each flavor's single source file lists the `MapSdk`s that flavor ships.
- **PRs.** Delivered as three stacked PRs: refactor → MapLibre flavor → toggle.

**Tech Stack:**
- Kotlin 2.4.0 and AGP 9.4.1 with built-in Kotlin.
- Jetpack Compose (BOM 2026.09.00).
- Koin 4.2.2 with the Koin compiler plugin 1.2.1, and Room 2.8.5 with KSP.
- TomTom Maps SDK 2.5.5 (`map-display-compose-standard`).
- maplibre-compose 0.20.0 with the OpenGL runtime; GeoJSON types come from `org.maplibre.spatialk.geojson`.

**Spec:** `docs/superpowers/specs/2026-10-25-map-sdk-flavors-design.md`. Read it before starting any task; section numbers below (§N) refer to it.

## Global Constraints

- Kotlin stays at **2.4.0** (koin-compiler ceiling; see `renovate.json`). Never bump Kotlin to make something compile.
- Every dependency version goes in `gradle/libs.versions.toml`. No inline versions in `build.gradle.kts`.
- Moved Kotlin files keep their **package names**. Only `R` imports change, to `com.github.karczews.publictransportviewer.core.R`. Use `git mv`.
- Module namespaces:
  - `:core` → `com.github.karczews.publictransportviewer.core`
  - `:core-testing` → `com.github.karczews.publictransportviewer.core.testing`
  - `:map-tomtom` → `com.github.karczews.publictransportviewer.map.tomtom`
  - `:map-maplibre` → `com.github.karczews.publictransportviewer.map.maplibre`
  - `:app` → unchanged
- Every module uses `compileSdk = 37`, `minSdk = 32` and Java 11 (`sourceCompatibility`/`targetCompatibility = JavaVersion.VERSION_11`). `:app` keeps `targetSdk = 36` and `abiFilters` `arm64-v8a`, `x86_64`.
- Modules with instrumented tests set `testInstrumentationRunner = "com.github.karczews.publictransportviewer.KoinTestRunner"`.
- TomTom AARs are flavored: `missingDimensionStrategy("tomtom-sdk-version", "complete")` goes in `:map-tomtom` **and** `:app`.
- TomTom deps always keep `exclude(group = "com.google.protobuf", module = "protobuf-javalite")`.
- App flavor dimension `mapSdk`:
  - `tomtom` → `.tomtom`, "PTV TomTom"
  - `maplibre` → `.maplibre`, "PTV MapLibre"
  - `all` → `.all`, "PTV Compare"
- maplibre-compose **0.20.0**. Exactly one runtime, `maplibre-compose-runtime-opengl-android`, `runtimeOnly`, preceded by the comment from spec §11.
- Style URL `https://styles.maptoolkit.org/summer.json`.
- **Every maplibre `SymbolLayer` sets `textFont = const(listOf("Noto Sans Regular"))`.** The Maptoolkit glyph server 404s maplibre-compose's default font stack (`Open Sans Regular, Arial Unicode MS Regular`), and labels would silently not render.
- **Resources across modules use non-transitive R** (AGP default). SDK modules reference `:core` drawables via `import com.github.karczews.publictransportviewer.core.R as CoreR`, and their own drawables via their module `R`.
- Every module with composables sets `buildFeatures { compose = true }`.
- maplibre-compose layer styling parameters are typed `Expression<…>`. Wrap literals in `const(…)`, and write interpolate stops as `zoomLevel to const(value)`. The verified API is in `docs/superpowers/plans/2026-10-25-map-sdk-flavors-maplibre-api.md`.
- Screenshot checks run in **portrait and landscape**. For landscape: `adb shell settings put system accelerometer_rotation 0 && adb shell settings put system user_rotation 1`; set `user_rotation 0` to go back.
- Map composables keep producing frames. If a Compose test times out waiting for idle, use `composeRule.waitUntil(5_000) { …node exists… }` before asserting.
- Maptoolkit attribution:
  - Logo ≥ `24.dp` tall at bottom-start, tag `maptoolkit_logo`, opens `https://www.maptoolkit.org/`.
  - The attribution button stays visible bottom-end.
  - Nothing of ours covers either.
- Test tags:
  - `recenter_button`, `vehicle_info_card`, `maptoolkit_logo`, `map_sdk_toggle`
  - `map_sdk_option_<id>`, `fake_map_<id>`, `map_sdk_loading`, `map_sdk_error`
- Conventional Commits (`type(scope): subject`). Commit only locally; **never push**.
- Every Gradle command runs from the repo root. `tomtomApiKey` comes from `~/.gradle/gradle.properties`; CI passes `-PtomtomApiKey=""`.
- Instrumented tests need a device. If `adb devices` is empty, start the emulator, then run `adb wait-for-device`:
  `~/Library/Android/sdk/emulator/emulator -avd Resizable_Experimental -no-window -no-audio -no-boot-anim &`
- **Baseline before any change:** `assembleDebug testDebugUnitTest assembleDebugAndroidTest` passes with **53 unit tests**.

## Review Focus

1. **Missing static GTFS data.** `routeShortName`/`tripHeadsign` can be null. The label falls back to `routeId` and the title drops the arrow (`"Bus 50"`). Pinned by `VehicleFormattingTest` (Task 3).
2. **Init still running when the user leaves.** The user may leave the Home tab or switch SDK. `initialize` runs once, isn't cancelled by leaving, and a failure is retried on the next entry. Pinned by `MapSdkInitializerTest` (Task 2) and `NavigationTest.switchingDuringInit_initializesOnce` (Task 7).
3. **Selected vehicle drops out of the next feed update.** The MapLibre info card disappears with no crash. Pinned by `MapLibreFeaturesTest.selectedVehicle_returnsNullWhenVehicleMissing` (Task 5).
4. **Route with fewer than 2 shape points** (missing `shapes.txt` rows). No LineString is built (it would be invalid GeoJSON), and stops still render. Pinned by `MapLibreFeaturesTest.routeLine_returnsNullForFewerThanTwoPoints` (Task 5).
5. **Saved SDK id that this flavor doesn't ship**, or none saved. Falls back to the first SDK. Pinned by `ResolveMapSdkTest` (Task 7).

---

## Branches

| Tasks | Branch | Created from |
|---|---|---|
| 0 | `probe/maplibre-compose` (throwaway, deleted at the end of Task 0) | `feat/map-libre` |
| 1–3 (PR 1) | `refactor/extract-core-module` | `feat/map-libre` (holds the spec commit) |
| 4–6 (PR 2) | `feat/map-libre` | fast-forwarded with `git checkout feat/map-libre && git merge --ff-only refactor/extract-core-module` |
| 7–9 (PR 3) | `feat/map-sdk-toggle` | `feat/map-libre` tip |

After PR 1 is squash-merged upstream, the user rebases with
`git rebase --onto origin/main refactor/extract-core-module feat/map-libre` (spec §15). That step is not part of this plan.

---

### Task 0: Compile probe for maplibre-compose (throwaway). DONE, PASSED

**Result (2026-10-25):**
- maplibre-compose 0.20.0 + the OpenGL runtime compiled and assembled under Kotlin 2.4.0, AGP 9.4.1 and compileSdk 37 on the first try.
- The probe needed `alias(libs.plugins.android.library) apply false` in the root build file, which Task 1 adds.
- Compose resolves to 1.12.1 on both classpaths, so there's no drift. maplibre-compose pulls in no OkHttp.
- The verified API, with a full compiling sample, is in `2026-10-25-map-sdk-flavors-maplibre-api.md`.
- The probe worktree and branch are deleted. The steps below are kept for the record.

This task answers one question: does maplibre-compose 0.20.0 compile and package here (Kotlin 2.4.0, AGP 9.4.1, compileSdk 37)? Nothing from it is kept.

**Files:**
- Create (throwaway): `probe/build.gradle.kts`, `probe/src/main/java/probe/Probe.kt`
- Modify (throwaway): `settings.gradle.kts`, `gradle/libs.versions.toml`

- [ ] **Step 1: Create the branch:** `git checkout -b probe/maplibre-compose feat/map-libre`.
- [ ] **Step 2: Add the probe dependencies.**
  - Add the four catalog entries from spec §11 (`maplibreCompose = "0.20.0"`, `maplibre-compose`, `maplibre-compose-location`, `maplibre-compose-runtime-opengl`, `maplibre-compose-runtime-vulkan`).
  - Add `android-library = { id = "com.android.library", version.ref = "agp" }` under `[plugins]`.
  - Add `include(":probe")`.
- [ ] **Step 3: Write `:probe`.**
  - The library module uses the `android.library` + `kotlin.compose` plugins, namespace `probe`, compileSdk 37, minSdk 32, compose on, and the Compose BOM.
  - Dependencies: `implementation(libs.maplibre.compose)`, `implementation(libs.maplibre.compose.location)`, `runtimeOnly(libs.maplibre.compose.runtime.opengl)`.
  - `Probe.kt` holds one composable that touches every API later tasks use:
    - `MaplibreMap(state = rememberMapState(baseStyle = BaseStyle.Uri("https://styles.maptoolkit.org/summer.json"), initialCameraPosition = CameraPosition(center = Position(longitude = 19.4560, latitude = 51.7592), zoom = 13.0)) { … }`
    - Inside the content: `rememberGeoJsonSource(GeoJsonData.Features(…))`, one `SymbolLayer` with `iconImage = image(painterResource(…))` and an `onClick`, one `LineLayer` with `interpolate(linear(), zoom(), …)`, and `LocationIndicatorLayer`.
    - `MapInteractions { callbacks { click { onUnhandled { ClickResult.Consume } } } }`.
    - `ExpandingAttributionButton()` inside `overlay`.
    - `rememberLocationState(rememberDefaultLocationProvider())`.
- [ ] **Step 4: Run** `./gradlew :probe:assembleDebug`.
  Expected: `BUILD SUCCESSFUL`. If it fails on Kotlin metadata or the compiler plugin, retry with older maplibre-compose minors (`0.19.0`, `0.18.0`, …).
  **If none compiles: STOP and ask the user** (spec §16.1). Falling back to `org.maplibre.gl:android-sdk` is their decision.
- [ ] **Step 5: Record findings.**
  - Note the exact API names that compiled, in particular the spatialk `Feature`/`FeatureCollection` constructors, `LocationPermission` subtypes and the `CameraUpdate` constructor.
  - Save them to the scratchpad file `maplibre-probe-notes.md` for Tasks 4–6.
  - Record `./gradlew :probe:dependencies --configuration debugRuntimeClasspath | grep -E "androidx.compose.(ui|runtime|foundation):"` versions against BOM 2026.09.00 (spec §16.2).
- [ ] **Step 6: Clean up** with `git checkout -f feat/map-libre && git branch -D probe/maplibre-compose`.
  Expected: `git status` clean; there is no `probe/` dir.

---

### Task 1: Extract `:core` (data, DI, view models, Stops/Alerts, theme)

**Files:**
- Modify: `settings.gradle.kts` (add `include(":core")`), `build.gradle.kts` (add `alias(libs.plugins.android.library) apply false`, `alias(libs.plugins.room) apply false`), `gradle/libs.versions.toml` (`android-library` plugin alias), `app/build.gradle.kts`, `.github/workflows/run-tests.yml`
- Create: `core/build.gradle.kts`, `core/src/main/AndroidManifest.xml` (empty `<manifest/>`)
- Move (`git mv`, same packages under `core/src/main/java/com/github/karczews/publictransportviewer/`):
  - `data/**`, `di/AppModule.kt` → `di/CoreModule.kt` (class renamed `CoreModule`)
  - `ui/alerts/**`, `ui/stops/**`, `ui/theme/**`, `ui/home/HomeViewModel.kt`
- Move resources: `app/src/main/res/drawable/{ic_alert,ic_bus,ic_home,ic_my_location,ic_stop_dot,ic_tram}.xml` → `core/src/main/res/drawable/`
- Move schemas: `app/schemas/` → `core/schemas/`
- Move tests: `app/src/test/**` → `core/src/test/**`
- Modify in place: `app/.../PublicTransportApp.kt` (`AppModule` → `CoreModule`), `app/.../MainActivity.kt` and `app/.../ui/home/HomeScreen.kt` (`R` imports → `core.R`, still in `:app` for now)

**Interfaces:**
- Produces: the `:core` module with all SDK-free code, and `CoreModule` (same body as `AppModule`, `@Module @ComponentScan("com.github.karczews.publictransportviewer")`).

- [ ] **Step 1: Write `core/build.gradle.kts`.**
  - Plugins: `android.library`, `kotlin.compose`, `ksp`, `room`, `koin.compiler`.
  - `android { … }`: namespace per Global Constraints, `buildFeatures { compose = true }`, `testOptions { unitTests.isReturnDefaultValues = true }`.
  - `room { schemaDirectory("$projectDir/schemas") }`.
  - The `koinCompiler { compileSafety = true; strictSafety = true }` block copied from `:app`.
  - Dependencies: move from `:app` every library the moved sources use (Compose BOM + material3 + adaptive-navigation-suite + ui + ui-graphics + tooling-preview, core-ktx, lifecycle runtime/viewmodel-compose, room runtime/ktx + `ksp(room.compiler)`, work, okhttp, gtfs-rt-bindings, koin bom/android/androidx-compose/workmanager/annotations).
  - Test dependencies: `testImplementation` junit, coroutines-test, gtfs-rt-bindings.
- [ ] **Step 2: Move the files** listed above with `git mv`. Rename `AppModule` → `CoreModule` (file and class) and update `PublicTransportApp`'s `@KoinApplication(modules = [CoreModule::class])`.
- [ ] **Step 3: Update `app/build.gradle.kts`.**
  - Add `implementation(project(":core"))`.
  - Remove the `ksp` and `room` plugins, the `room {}` block and every dependency no longer referenced by `:app` sources (room, ksp, okhttp, gtfs-rt-bindings).
  - Keep compose, activity-compose, koin, work and TomTom.
  - Add `lint { checkDependencies = true }` inside `android {}`.
  - **Koin fallback:** if `:app:compileDebugKotlin` fails in the Koin safety pass with types it can't resolve (e.g. `OkHttpClient`, `GtfsDatabase`, coming from `:core`'s cross-module hints), switch those `:core` dependencies (okhttp, room-runtime) from `implementation` to `api`. Note it in the commit message.
- [ ] **Step 4: Fix the `R` imports** in `MainActivity.kt` and `HomeScreen.kt` (`com.github.karczews.publictransportviewer.core.R`).
- [ ] **Step 5: Change the CI upload path.** In `.github/workflows/run-tests.yml`, `path: app/build/reports/tests/` → `path: '*/build/reports/tests/'`.
- [ ] **Step 6: Verify.** Run `./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug -PtomtomApiKey=""`.
  - Expected: `BUILD SUCCESSFUL`.
  - Then run `find core/build/test-results -name '*.xml' | xargs grep -ho 'tests="[0-9]*"' | awk -F'"' '{s+=$2} END {print s}'`. Expected `53`.
  - `ls core/schemas/*/` shows the same JSON files as before.
- [ ] **Step 7: Commit** with `git add -A && git commit -m "refactor: extract SDK-free code into core module"`.

---

### Task 2: `MapSdk` contract, Home-tab host with init gate, `:core-testing`

**Files:**
- Create in `:core`:
  - `core/src/main/java/com/github/karczews/publictransportviewer/map/MapSdk.kt`
  - `core/src/main/java/com/github/karczews/publictransportviewer/ui/home/MapSdkInitializer.kt`
  - `core/src/main/java/com/github/karczews/publictransportviewer/ui/home/MapSdkHost.kt`
  - `core/src/main/java/com/github/karczews/publictransportviewer/ui/PublicTransportViewerApp.kt`: `PublicTransportViewerApp` + `AppDestinations`, moved out of `MainActivity.kt`
- Create `:core-testing`:
  - `core-testing/build.gradle.kts`, `core-testing/src/main/AndroidManifest.xml`
  - `git mv` from `app/src/androidTest/java/com/github/karczews/publictransportviewer/`: `KoinTestRunner.kt` and `TestPublicTransportApp.kt` → `core-testing/src/main/java/com/github/karczews/publictransportviewer/`
  - `core-testing/src/main/java/com/github/karczews/publictransportviewer/map/FakeMapSdk.kt`
- Move: `app/src/androidTest/.../ui/{NavigationTest,StopsScreenTest,AlertsScreenTest}.kt` → `core/src/androidTest/java/com/github/karczews/publictransportviewer/ui/`
- Test: `core/src/test/java/com/github/karczews/publictransportviewer/ui/home/MapSdkInitializerTest.kt`
- Create in `:app`, temporary until Task 3 moves it to `:map-tomtom`: `app/src/main/java/com/github/karczews/publictransportviewer/map/tomtom/TomTomMapSdk.kt`
- Modify: `app/.../MainActivity.kt`, `app/build.gradle.kts`, `core/build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`

**Interfaces:**
- Produces:
  - `interface MapSdk { val id: String; val displayName: String; suspend fun initialize(context: Context) {}; @Composable fun HomeScreen(viewModel: HomeViewModel, modifier: Modifier) }`, package `com.github.karczews.publictransportviewer.map`.
  - `sealed interface MapSdkInitState { data object Loading; data object Ready; data class Failed(val message: String) }`
  - `class MapSdkInitializer(scope: CoroutineScope, initialize: suspend (MapSdk) -> Unit)` with `fun isReady(id: String): Boolean` and `fun ensureInitialized(sdk: MapSdk): Deferred<Result<Unit>>`. Initialization runs in `scope`, so cancelling a waiting caller doesn't cancel it. A repeat call while one is in flight or done returns the same `Deferred`. A failed entry is dropped so the next call retries.
  - `@Composable fun MapSdkHost(mapSdks: List<MapSdk>, initializer: MapSdkInitializer, modifier: Modifier = Modifier)`
    - While `Loading`: a centered `CircularProgressIndicator`, tag `map_sdk_loading`.
    - On `Failed`: the text `"Couldn't start ${sdk.displayName}: ${message}"`, tag `map_sdk_error`. `message` is `throwable.message ?: throwable::class.simpleName`.
    - On `Ready`: `key(sdk.id) { sdk.HomeScreen(koinViewModel<HomeViewModel>(), Modifier.fillMaxSize()) }`.
    - Its initial state is `Ready` when `initializer.isReady(sdk.id)`, so there's no spinner flash. In this task the SDK is always `mapSdks.first()`.
  - `@Composable fun PublicTransportViewerApp(mapSdks: List<MapSdk>)` with `require(mapSdks.isNotEmpty())`.
    - Hoists `val scope = rememberCoroutineScope()` and `val appContext = LocalContext.current.applicationContext`, then builds `remember { MapSdkInitializer(scope, initialize = { it.initialize(appContext) }) }`.
    - The Home destination renders `MapSdkHost`.
  - `FakeMapSdk(id: String = "fake", displayName: String = "Fake", private val onInitialize: suspend () -> Unit = {}) : MapSdk`. Its `HomeScreen` is `Box(modifier.testTag("fake_map_$id"))`.
  - `TomTomMapSdk(apiKey: String) : MapSdk` in `:app`, `id = "tomtom"`, `displayName = "TomTom"`:
    - `initialize` = today's `MainActivity.initTomTomSdk()` body.
    - `HomeScreen` = today's `HomeScreen(viewModel, modifier)`.

- [ ] **Step 1: Write the failing `MapSdkInitializerTest`.** Use `runTest`, with `StandardTestDispatcher` as the initializer scope via `backgroundScope` or `TestScope`, and a private fake `MapSdk` in the test file. Tests:
  - `ensureInitialized_runsInitializeOnceForRepeatedCalls`: call twice before completion and once after. Assert the initialize counter `== 1` and the same `Deferred` instance each time.
  - `ensureInitialized_survivesCallerCancellation`: launch a job awaiting `ensureInitialized(sdk).await()`, cancel it, `advanceUntilIdle()`. Assert `isReady("a")` is `true`.
  - `ensureInitialized_retriesAfterFailure`: the first initialize throws `IllegalStateException("boom")`. Assert the result is a failure with message `"boom"`, `isReady("a") == false`. The second call runs initialize again (counter `== 2`).
  - `isReady_falseBeforeInit_trueAfter`
- [ ] **Step 2: Confirm it fails.** Run `./gradlew :core:testDebugUnitTest --tests "*.MapSdkInitializerTest"`. Expected: compilation FAIL (`MapSdkInitializer` unresolved).
- [ ] **Step 3: Implement** `MapSdk`, `MapSdkInitState` and `MapSdkInitializer` per Interfaces. Use a `mutableMapOf<String, Deferred<Result<Unit>>>` guarded by main-thread use; callers are composables.
- [ ] **Step 4: Confirm it passes** with the same command. Expected: 4 tests PASS.
- [ ] **Step 5: Create `:core-testing`.**
  - Plugins `android.library` + `kotlin.compose`, `buildFeatures { compose = true }`. Add `include(":core-testing")`.
  - Dependencies:
    - `implementation(project(":core"))`.
    - `implementation(platform(libs.koin.bom))` + `libs.koin.android`. Koin core and viewmodel come transitively; there's no `koin-core` alias.
    - `implementation(platform(libs.androidx.compose.bom))` + `libs.androidx.compose.material3`. Material3 brings foundation, which `Box` needs; `ui` alone doesn't.
    - The new catalog entries `testRunner = "1.7.0"` under `[versions]` and `androidx-test-runner = { group = "androidx.test", name = "runner", version.ref = "testRunner" }`. `KoinTestRunner` extends `AndroidJUnitRunner`.
  - `git mv` `KoinTestRunner.kt` and `TestPublicTransportApp.kt` into it unchanged, and write `FakeMapSdk`.
  - Wire `:core` now: set `testInstrumentationRunner`, add `androidTestImplementation(project(":core-testing"))`, and run `./gradlew :core:assembleDebugAndroidTest`. Expected `BUILD SUCCESSFUL`: the `:core-testing` ↔ `:core` loop works (spec §16.5). If Gradle reports a circular dependency, STOP and report.
- [ ] **Step 6: Move `PublicTransportViewerApp` + `AppDestinations`** into `core/.../ui/PublicTransportViewerApp.kt` with the new signature. Write `MapSdkHost`.
- [ ] **Step 7: Update `MainActivity`.**
  - Its `onCreate` becomes `enableEdgeToEdge(); setContent { PublicTransportViewerTheme { PublicTransportViewerApp(mapSdks = listOf(TomTomMapSdk(BuildConfig.TOMTOM_API_KEY))) } }`.
  - Delete `sdkInitialized`/`initTomTomSdk` and move their logic into `app/.../map/tomtom/TomTomMapSdk.kt`.
- [ ] **Step 8: Rewrite the three instrumented tests in `core/src/androidTest`.**
  - Each uses `createComposeRule()` and a helper `setApp(vararg sdks: MapSdk)` that calls `composeRule.setContent { PublicTransportViewerTheme { PublicTransportViewerApp(sdks.toList()) } }`.
  - `StopsScreenTest` and `AlertsScreenTest`: `@Before` calls `setApp(FakeMapSdk())`, then the existing tab click. The test bodies stay as they are.
  - `NavigationTest` keeps `bottomNavigation_showsAllTabs`, `navigateToStops_showsSearchField` and `navigateToAlerts_showsTitle`, each calling `setApp(FakeMapSdk())` first. It drops `homeScreen_showsRecenterButton` (moved in Task 3) and adds:
    - `homeTab_showsFakeMap`: `onNodeWithTag("fake_map_fake").assertIsDisplayed()`
    - `homeTab_showsSpinnerWhileInitializing`: `FakeMapSdk(onInitialize = { awaitCancellation() })` → `map_sdk_loading` displayed, `fake_map_fake` `assertDoesNotExist()`
    - `homeTab_showsErrorWhenInitFails`: `FakeMapSdk(onInitialize = { error("boom") })` → `onNodeWithText("Couldn't start Fake: boom").assertIsDisplayed()`
  - `:core` additionally gets `androidTestImplementation` compose ui-test-junit4 (with the Compose BOM platform) and androidx-junit, plus `debugImplementation(libs.androidx.compose.ui.test.manifest)`.
  - Remove the now-empty `app/src/androidTest` tree, plus `:app`'s `testInstrumentationRunner` line and androidTest dependencies.
- [ ] **Step 9: Verify.**
  - Run `./gradlew :core:assembleDebugAndroidTest testDebugUnitTest assembleDebug -PtomtomApiKey=""`. Expected: `BUILD SUCCESSFUL`. This also proves Gradle accepts the `:core-testing` ↔ `:core` androidTest dependency (spec §16.5).
  - Then, with a device, run `./gradlew :core:connectedDebugAndroidTest`. Expected: all Navigation/Stops/Alerts tests PASS.
- [ ] **Step 10: Commit** with `git add -A && git commit -m "refactor: add MapSdk contract and Home-tab host with init gate"`.

---

### Task 3: `:map-tomtom` module, Home helpers, API key move, PR 1 docs

**Files:**
- Create: `map-tomtom/build.gradle.kts`, `map-tomtom/src/main/AndroidManifest.xml`
- Move:
  - `app/.../map/tomtom/TomTomMapSdk.kt` → `map-tomtom/src/main/java/com/github/karczews/publictransportviewer/map/tomtom/TomTomMapSdk.kt`
  - `app/.../ui/home/HomeScreen.kt` → `map-tomtom/.../map/tomtom/TomTomHomeScreen.kt`. The function is renamed `TomTomHomeScreen` and the package becomes `…map.tomtom`.
- Create in `:core`:
  - `core/.../ui/home/HomeMapDefaults.kt`
  - `core/.../ui/home/VehicleFormatting.kt`
  - test `core/src/test/.../ui/home/VehicleFormattingTest.kt`
- Create: `map-tomtom/src/androidTest/java/com/github/karczews/publictransportviewer/map/tomtom/TomTomHomeScreenTest.kt`
- Create: `app/src/main/java/com/github/karczews/publictransportviewer/AvailableMapSdks.kt`
- Modify: `app/build.gradle.kts`, `app/.../MainActivity.kt`, `settings.gradle.kts`, `AGENTS.md`, `docs/ARCHITECTURE.md`

**Interfaces:**
- Consumes: `MapSdk`, `HomeViewModel`, `FixedVehicleRepository`, `FixedRouteDisplayRepository` (Task 2).
- Produces:
  - `object HomeMapDefaults { const val CENTER_LAT = 51.7592; const val CENTER_LON = 19.4560; const val INITIAL_ZOOM = 13.0; const val RECENTER_ZOOM = 15.0 }`
  - `fun vehicleLabel(vehicle: VehiclePosition): String`
  - `fun vehicleTitle(vehicle: VehiclePosition): String`
  - `@DrawableRes fun vehicleIconRes(type: VehicleType): Int`
  - `class TomTomMapSdk(private val apiKey: String = BuildConfig.TOMTOM_API_KEY) : MapSdk` (`id = "tomtom"`, `displayName = "TomTom"`)
  - `internal val availableMapSdks: List<MapSdk>` in `:app` (package `com.github.karczews.publictransportviewer`)

- [ ] **Step 1: Write the failing `VehicleFormattingTest`.** Use `FixtureData`-like values built inline in the test:
  - `title_tramWithHeadsign`: `vehicleTitle(tram10)` == `"Tram 10 → Chocianowice IKEA"`
  - `title_busWithoutHeadsign`: `vehicleTitle(bus50.copy(tripHeadsign = null))` == `"Bus 50"`
  - `label_fallsBackToRouteId`: `vehicleLabel(bus50.copy(routeShortName = null, routeId = "50A"))` == `"50A"`
  - `title_fallsBackToRouteId`: `vehicleTitle(tram10.copy(routeShortName = null, routeId = "10X", tripHeadsign = null))` == `"Tram 10X"`
  - `iconRes_matchesType`: `vehicleIconRes(VehicleType.TRAM) == R.drawable.ic_tram`, `vehicleIconRes(VehicleType.BUS) == R.drawable.ic_bus`
- [ ] **Step 2: Confirm it fails.** Run `./gradlew :core:testDebugUnitTest --tests "*.VehicleFormattingTest"`. Expected: compilation FAIL.
- [ ] **Step 3: Implement** `HomeMapDefaults` and `VehicleFormatting.kt`. `vehicleTitle` is today's `buildBalloonText` logic.
- [ ] **Step 4: Confirm it passes** with the same command. Expected: 5 PASS.
- [ ] **Step 5: Create `:map-tomtom`.**
  - Plugins `android.library` + `kotlin.compose`. Add `include(":map-tomtom")`.
  - Move the `tomtomApiKey` lookup and `buildConfigField("String", "TOMTOM_API_KEY", …)` from `:app`, and set `buildFeatures { compose = true; buildConfig = true }`.
  - `missingDimensionStrategy` and `testInstrumentationRunner` in `defaultConfig`.
  - Dependencies: `implementation(project(":core"))`, Compose BOM + material3 + ui, activity-compose, core-ktx, and both TomTom deps with the exclusion. `collectAsStateWithLifecycle` comes through compose `ui`; there's no alias to add.
  - `TomTomHomeScreen` references `:core` drawables as `CoreR.drawable.*`.
  - androidTest dependencies: `:core-testing`, compose ui-test-junit4, androidx-junit, and `debugImplementation` ui-test-manifest.
- [ ] **Step 6: `git mv` the two TomTom files.**
  - In `TomTomHomeScreen`, replace the `LODZ_CENTER`/`INITIAL_ZOOM`/`15.0` constants with `HomeMapDefaults`, and `buildBalloonText`/label/icon logic with `vehicleTitle`/`vehicleLabel`/`vehicleIconRes`.
  - Behavior and tags stay unchanged.
  - Give `TomTomMapSdk` the default `apiKey` parameter.
- [ ] **Step 7: Update `:app`.**
  - Create `AvailableMapSdks.kt` with `internal val availableMapSdks: List<MapSdk> = listOf(TomTomMapSdk())`. `MainActivity` uses `availableMapSdks`.
  - In `app/build.gradle.kts`: `implementation(project(":map-tomtom"))`. Remove the TomTom deps, the `tomtomApiKey`/`buildConfigField` lines and `buildConfig = true`. Keep `missingDimensionStrategy`.
- [ ] **Step 8: Write `TomTomHomeScreenTest.homeScreen_showsRecenterButton`.**
  - Use `createComposeRule()`.
  - Call `runBlocking(Dispatchers.Main) { TomTomMapSdk().initialize(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext) }`.
  - Then `setContent { TomTomMapSdk().HomeScreen(HomeViewModel(FixedVehicleRepository(), FixedRouteDisplayRepository()), Modifier.fillMaxSize()) }`.
  - Then `onNodeWithTag("recenter_button").assertIsDisplayed()`.
- [ ] **Step 9: Verify the protobuf exclusion.** Run `./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep protobuf`. Expected: `protobuf-java` only, no `protobuf-javalite` (spec §16.4).
- [ ] **Step 10: Update the docs.**
  - `AGENTS.md`:
    - Build Commands: single-test examples target `:core` (spec §14).
    - Note that the API key is injected into `:map-tomtom`'s `BuildConfig`.
    - Architecture: add a module list and the `MapSdk` contract. "Map Rendering" becomes the TomTom section, pointing at `:map-tomtom`.
    - Fix stale lines:
      - "Single-module Android app (`app/`)" and `di/AppModule.kt` → `:core` / `CoreModule`.
      - SDK init "in `MainActivity`" → `TomTomMapSdk.initialize()` via `MapSdkHost`.
      - Room schemas `app/schemas/` → `core/schemas/`.
  - `docs/ARCHITECTURE.md`:
    - Add a mermaid module graph (`app → core, map-tomtom`; `map-tomtom → core`; `core-testing → core`).
    - Rename "Map Renderer" to "Map SDKs" with a TomTom subsection.
    - Fix stale `AppModule` mentions, the `MainActivity` init-sequence description and the file-tree listing to match the new modules.
    - Find all of them with `grep -n "AppModule\|MainActivity\|app/src\|app/schemas\|Single-module" AGENTS.md docs/ARCHITECTURE.md`.
- [ ] **Step 11: Verify all of PR 1.**
  - Run `./gradlew assembleDebug testDebugUnitTest lintDebug -PtomtomApiKey=""`. Expected: `BUILD SUCCESSFUL`, with 53 + 4 + 5 = **62** unit tests in `core/build/test-results`.
  - With a device, run `./gradlew :core:connectedDebugAndroidTest :map-tomtom:connectedDebugAndroidTest`. Expected: PASS.
  - Run `./gradlew installDebug` and launch the app. Expected:
    - The Home tab shows a spinner, then the TomTom map with vehicles.
    - Tapping a vehicle shows a balloon and the route.
    - Recenter asks for permission.
    - Stops and Alerts work.
    - Take a screenshot with `adb exec-out screencap -p > <scratchpad>/pr1-home.png` and look at it.
- [ ] **Step 12: Commit** with `git add -A && git commit -m "refactor: move TomTom Home screen into map-tomtom module"`.

---

### Task 4: `:map-maplibre` module, map with Maptoolkit style and attribution, `tomtom`/`maplibre` flavors

**Files:**
- Modify: `gradle/libs.versions.toml` (the four maplibre entries from spec §11), `settings.gradle.kts`, `app/build.gradle.kts`, `app/src/main/res/values/strings.xml` (remove `app_name`), `.github/workflows/lint.yml`, `AGENTS.md`, `docs/ARCHITECTURE.md`
- Create:
  - `map-maplibre/build.gradle.kts`, `map-maplibre/src/main/AndroidManifest.xml`
  - `map-maplibre/src/main/java/com/github/karczews/publictransportviewer/map/maplibre/MapLibreMapSdk.kt`
  - `map-maplibre/src/main/java/com/github/karczews/publictransportviewer/map/maplibre/MapLibreHomeScreen.kt`
  - `map-maplibre/src/main/res/drawable-nodpi/maptoolkit_attribution.png`
  - `map-maplibre/src/androidTest/java/com/github/karczews/publictransportviewer/map/maplibre/MapLibreHomeScreenTest.kt`
- Move: `app/src/main/java/com/github/karczews/publictransportviewer/AvailableMapSdks.kt` → `app/src/tomtom/java/com/github/karczews/publictransportviewer/AvailableMapSdks.kt`
- Create: `app/src/maplibre/java/com/github/karczews/publictransportviewer/AvailableMapSdks.kt`

**Interfaces:**
- Consumes: `MapSdk`, `HomeViewModel`, `HomeMapDefaults` (Tasks 2–3), and `2026-10-25-map-sdk-flavors-maplibre-api.md` (Task 0).
- Produces:
  - `class MapLibreMapSdk(private val styleUrl: String = MAPTOOLKIT_SUMMER_STYLE) : MapSdk` (`id = "maplibre"`, `displayName = "MapLibre"`, no-op `initialize`)
  - `const val MAPTOOLKIT_SUMMER_STYLE = "https://styles.maptoolkit.org/summer.json"`
  - `@Composable internal fun MapLibreHomeScreen(viewModel: HomeViewModel, styleUrl: String, modifier: Modifier)`

- [ ] **Step 1: Write the failing `MapLibreHomeScreenTest.homeScreen_showsMaptoolkitLogo`.**
  `createComposeRule()`, then `setContent { MapLibreMapSdk().HomeScreen(HomeViewModel(FixedVehicleRepository(), FixedRouteDisplayRepository()), Modifier.fillMaxSize()) }`, then `onNodeWithTag("maptoolkit_logo").assertIsDisplayed()`.
- [ ] **Step 2: Create `:map-maplibre`.**
  - Plugins `android.library` + `kotlin.compose`; `buildFeatures { compose = true }`; `testInstrumentationRunner`.
  - Dependencies: `:core`, Compose BOM + material3 + ui, `libs.maplibre.compose`, `libs.maplibre.compose.location`, and `runtimeOnly(libs.maplibre.compose.runtime.opengl)` **preceded verbatim by the comment block in spec §11**.
  - Test dependencies: `testImplementation(libs.junit)`, the same androidTest set as `:map-tomtom`.
  - Add `include(":map-maplibre")`.
- [ ] **Step 3: Confirm the test fails.** Run `./gradlew :map-maplibre:assembleDebugAndroidTest`. Expected: compilation FAIL (`MapLibreMapSdk` unresolved).
- [ ] **Step 4: Download the logo** with `curl -fL -o map-maplibre/src/main/res/drawable-nodpi/maptoolkit_attribution.png https://docs.maptoolkit.org/images/maptoolkit-attribution.png`. Check it with `file` (PNG) and note its pixel height. If it's under 72 px (24dp @ xxhdpi), note that for the PR description.
- [ ] **Step 5: Implement `MapLibreMapSdk` and `MapLibreHomeScreen`.**
  - Root: `Box(modifier.fillMaxSize())` containing `MaplibreMap(…)`:
    - `state = rememberMapState(baseStyle = BaseStyle.Uri(styleUrl), initialCameraPosition = CameraPosition(center = Position(longitude = HomeMapDefaults.CENTER_LON, latitude = HomeMapDefaults.CENTER_LAT), zoom = HomeMapDefaults.INITIAL_ZOOM))`
    - The `overlay` replaces `MapOverlay.Default` (no MapLibre logo) with a `Box(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp))` holding:
      - `Image(painterResource(R.drawable.maptoolkit_attribution), "Maptoolkit", Modifier.align(Alignment.BottomStart).height(24.dp).testTag("maptoolkit_logo").clickable { uriHandler.openUri("https://www.maptoolkit.org/") })`
      - `ExpandingAttributionButton(Modifier.align(Alignment.BottomEnd))`
- [ ] **Step 6: Add the flavors to `:app`** exactly as in spec §8 (`tomtom` and `maplibre` only; `all` comes in Task 9).
  - `buildFeatures { resValues = true }`.
  - Remove `app_name` from `strings.xml`.
  - `"tomtomImplementation"(project(":map-tomtom"))`, `"maplibreImplementation"(project(":map-maplibre"))`, and drop the plain `:map-tomtom` dependency.
  - `git mv` `AvailableMapSdks.kt` into `src/tomtom/java/…`; create `src/maplibre/java/…/AvailableMapSdks.kt` with `listOf(MapLibreMapSdk())`.
- [ ] **Step 7: Change CI lint.** In `.github/workflows/lint.yml`, the run step becomes `./gradlew lintTomtomDebug lintMaplibreDebug -PtomtomApiKey=""`, and the upload paths become `app/build/reports/lint-results-*Debug.html` and `app/build/reports/lint-results-*Debug.txt`.
- [ ] **Step 8: Verify the build.**
  - Run `./gradlew assembleDebug testDebugUnitTest lintTomtomDebug lintMaplibreDebug -PtomtomApiKey=""`. Expected: `BUILD SUCCESSFUL`, with both `app-tomtom-debug.apk` and `app-maplibre-debug.apk` under `app/build/outputs/apk/`.
  - Run `./gradlew :app:dependencies --configuration maplibreDebugRuntimeClasspath | grep -c "com.tomtom"`. Expected `0`.
- [ ] **Step 9: Confirm the test passes** with `./gradlew :map-maplibre:connectedDebugAndroidTest`. Expected: PASS.
- [ ] **Step 10: Check it on a device.**
  - Run `./gradlew installMaplibreDebug` and launch "PTV MapLibre".
  - Expected: the Maptoolkit map is centered on Łódź, the logo is at bottom-left, and the attribution button at bottom-right expands to `© Maptoolkit © OpenStreetMap`.
  - Screenshot it to the scratchpad in portrait **and landscape** and inspect.
  - If there's no attribution text, add the fallback from spec §11: draw the line ourselves, bottom-end, with both links.
- [ ] **Step 11: Update the docs.**
  - `AGENTS.md`: install commands `installTomtomDebug`/`installMaplibreDebug`, lint commands, and a MapLibre section (style, attribution rule, render-backend comment location).
  - `docs/ARCHITECTURE.md`: a MapLibre subsection, and add `map-maplibre` to the module graph.
- [ ] **Step 12: Commit** with `git add -A && git commit -m "feat: add MapLibre map SDK module and tomtom/maplibre flavors"`.

---

### Task 5: MapLibre vehicles, route, stops, selection and info card

**Files:**
- Create:
  - `map-maplibre/src/main/java/com/github/karczews/publictransportviewer/map/maplibre/MapLibreFeatures.kt`
  - `map-maplibre/src/test/java/com/github/karczews/publictransportviewer/map/maplibre/MapLibreFeaturesTest.kt`
- Modify: `MapLibreHomeScreen.kt`, `MapLibreHomeScreenTest.kt`

**Interfaces:**
- Consumes: `vehicleLabel`, `vehicleTitle`, `vehicleIconRes` (Task 3); `HomeViewModel.vehiclePositions`, `selectedVehicleId`, `routeDisplay`, `onVehicleSelected`, `clearSelection`.
- Produces (all `internal`, spatialk geojson types; generics as the 0.20.0 API requires, see `2026-10-25-map-sdk-flavors-maplibre-api.md`):
  - `fun vehicleFeatures(vehicles: List<VehiclePosition>): FeatureCollection`. One Point per vehicle at (lon, lat), with string properties `id` = `vehicleId`, `label` = `vehicleLabel(v)` and `type` = `vehicleType.name`.
  - `fun routeLine(route: RouteDisplayData): FeatureCollection?`. One LineString over `polylinePoints` in order; `null` when there are fewer than 2 points.
  - `fun routeStopFeatures(route: RouteDisplayData): FeatureCollection`. One Point per stop with properties `id` and `name`.
  - `fun selectedVehicle(vehicles: List<VehiclePosition>, selectedId: String?): VehiclePosition?`
  - `fun clickedVehicleId(feature: Feature<*, JsonObject?>): String?`. Reads the `id` string property.

- [ ] **Step 1: Write the failing `MapLibreFeaturesTest`.** Fixtures are inline copies of `FixtureData.tram10`/`bus50`/`routeForTram10`. Tests:
  - `vehicleFeatures_mapsPropertiesAndLonLatOrder`: 2 features. The first has `id == "tram-v1"`, `label == "10"`, `type == "TRAM"`, longitude `19.4560` and latitude `51.7592`.
  - `routeLine_keepsPointOrder`: 3 coordinates, first (lon `19.4500`, lat `51.7550`), last (lon `19.4600`, lat `51.7620`).
  - `routeLine_returnsNullForFewerThanTwoPoints`: `polylinePoints = listOf(LatLon(51.0, 19.0))` → `null`; and `emptyList()` → `null`.
  - `routeStopFeatures_mapsNames`: names `["Piotrkowska Centrum", "Plac Wolności", "Legionów"]`.
  - `selectedVehicle_returnsNullWhenVehicleMissing`: `selectedVehicle(listOf(bus50), "tram-v1") == null`, `selectedVehicle(listOf(tram10), null) == null`, `selectedVehicle(listOf(tram10, bus50), "bus-v2") == bus50`.
  - `clickedVehicleId_readsIdProperty`: `clickedVehicleId(vehicleFeatures(listOf(tram10)).features.first()) == "tram-v1"`.
- [ ] **Step 2: Confirm it fails.** Run `./gradlew :map-maplibre:testDebugUnitTest`. Expected: compilation FAIL.
- [ ] **Step 3: Implement `MapLibreFeatures.kt`.**
- [ ] **Step 4: Confirm it passes** with the same command. Expected: 6 PASS.
- [ ] **Step 5: Add the layers** to the `rememberMapState(…, content = { … })` block, bottom to top.
  All literals are `const(…)`, every `SymbolLayer` sets `textFont = const(listOf("Noto Sans Regular"))`, and drawables are `CoreR.drawable.*`. Layer ids: `route-casing`, `route-line`, `route-stops`, `vehicles`.
  1. `routeLine(route)` (when non-null) as a `rememberGeoJsonSource(GeoJsonData.Features(it))` source with two `LineLayer`s:
     - `route-casing`: `color = const(Color(0x64000000))`, `width = interpolate(linear(), zoom(), 10 to const(10.dp), 14 to const(14.dp), 18 to const(18.dp))`. That's the line widths + 2dp each side, from TomTom's `outlineWidths = WidthByZoom(2.0)`.
     - `route-line`: `color = const(Color(route.routeColor))`, `width = interpolate(linear(), zoom(), 10 to const(6.dp), 14 to const(10.dp), 18 to const(14.dp))`. TomTom's `WidthByZoom(width, zoom)` pairs are (6,10), (10,14), (14,18).
  2. `route-stops` `SymbolLayer` over `routeStopFeatures(route)`:
     - `iconImage = image(painterResource(CoreR.drawable.ic_stop_dot))`, `textField = format(span(feature["name"].asString()))`.
     - `textColor = const(Color.DarkGray)`, `textHaloColor = const(Color.White)`, `textHaloWidth = const(1.dp)`, `textSize = const(10.sp)`, `textAnchor = const(SymbolAnchor.Top)` with a small downward `textOffset`.
     - `onClick = { ClickResult.Consume }`, so a stop tap doesn't fall through to `onUnhandled` and clear the selection. That matches TomTom's no-op stop click.
  3. `vehicles` `SymbolLayer` over `vehicleFeatures(vehicles)`:
     - `iconImage = switch(feature["type"].asString(), case("TRAM", image(painterResource(CoreR.drawable.ic_tram))), fallback = image(painterResource(CoreR.drawable.ic_bus)))`, `textField = format(span(feature["label"].asString()))`.
     - `textColor = const(Color.White)`, `textHaloColor = const(Color.Black)`, `textHaloWidth = const(2.dp)`, `textSize = const(12.sp)`, `iconAllowOverlap = const(true)`, `textAllowOverlap = const(true)`.
     - `onClick = { features -> features.firstOrNull()?.let(::clickedVehicleId)?.let { id -> vehicles.firstOrNull { it.vehicleId == id } }?.let(viewModel::onVehicleSelected); ClickResult.Consume }`
  - `interactions = MapInteractions { callbacks { click { onUnhandled { viewModel.clearSelection(); ClickResult.Consume } } } }`.
  - Info card, outside `MaplibreMap` in the root `Box`: `selectedVehicle(vehicles, selectedId)?.let { ElevatedCard(Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 48.dp).testTag("vehicle_info_card")) { Text(vehicleTitle(it), Modifier.padding(12.dp)) } }`. The 48dp keeps it clear of the logo and attribution.
- [ ] **Step 6: Add the instrumented test `selectedVehicle_showsInfoCard`.**
  - Build `vm = HomeViewModel(FixedVehicleRepository(), FixedRouteDisplayRepository())`, `setContent { … vm … }`, then `composeRule.runOnIdle { vm.onVehicleSelected(FixtureData.tram10) }`.
  - Assert `onNodeWithTag("vehicle_info_card").assertIsDisplayed()` and `onNodeWithText("Tram 10 → Chocianowice IKEA").assertIsDisplayed()`.
- [ ] **Step 7: Verify.**
  - Run `./gradlew :map-maplibre:testDebugUnitTest :map-maplibre:connectedDebugAndroidTest`. Expected: PASS.
  - Run `./gradlew installMaplibreDebug`. Expected:
    - Live vehicles render with **visible route-number labels**.
    - Tapping one shows the route line, stops with names and the info card.
    - Tapping a stop keeps the selection; tapping empty map clears it.
  - Screenshot in portrait and landscape and inspect.
- [ ] **Step 8: Commit** with `git add -A && git commit -m "feat(maplibre): render vehicles, selected route and stops"`.

---

### Task 6: MapLibre user location and recenter

**Files:**
- Modify: `MapLibreHomeScreen.kt`, `MapLibreHomeScreenTest.kt`

**Interfaces:**
- Consumes: `HomeMapDefaults.RECENTER_ZOOM`, the maplibre-compose location API (`2026-10-25-map-sdk-flavors-maplibre-api.md`).

- [ ] **Step 1: Add the failing test `homeScreen_showsRecenterButton`** to `MapLibreHomeScreenTest` (same setup as Task 4) and run it. Expected: FAIL, no node with tag `recenter_button`.
- [ ] **Step 2: Implement location.**
  - `val locationState = rememberLocationState(provider = rememberDefaultLocationProvider())`.
  - In the map content, `if (locationState.permission is LocationPermission.Granted) LocationIndicatorLayer(id = "user_location", locationState = locationState)`. `LocationPermission.Granted` is a data class.
  - Recenter button, matching `TomTomHomeScreen`: a `FilledTonalIconButton` with `ic_my_location` and content description `"Recenter to my location"`. Modifier: `.align(Alignment.BottomEnd).safeDrawingPadding().padding(end = 16.dp, bottom = 56.dp).size(48.dp).testTag("recenter_button")`; the 56dp clears the attribution button.
  - On click: if not granted, `locationState.requestPermission()`. Otherwise `locationState.lastLocation?.let { scope.launch { mapState.animateCamera(CameraUpdate(center = it.position, zoom = HomeMapDefaults.RECENTER_ZOOM)) } }`.
- [ ] **Step 3: Confirm it passes** with `./gradlew :map-maplibre:connectedDebugAndroidTest`. Expected: all 3 tests PASS.
- [ ] **Step 4: Check permissions manually** with `installMaplibreDebug`:
  1. Revoke both permissions first, because maplibre treats a coarse-only grant as `Granted`: `adb shell pm revoke com.github.karczews.publictransportviewer.maplibre android.permission.ACCESS_FINE_LOCATION && adb shell pm revoke com.github.karczews.publictransportviewer.maplibre android.permission.ACCESS_COARSE_LOCATION`. Tap recenter: the system dialog appears.
  2. Deny. Tap again: no crash.
  3. Grant. The blue dot shows (set an emulator location via `adb emu geo fix 19.4560 51.7592`), and recenter animates to zoom 15.
  4. Screenshot in portrait and landscape and inspect. The recenter button must not overlap the attribution button or the Maptoolkit logo.
- [ ] **Step 5: Verify PR 2.**
  - Run `./gradlew assembleDebug testDebugUnitTest lintTomtomDebug lintMaplibreDebug -PtomtomApiKey=""`. Expected: `BUILD SUCCESSFUL`.
  - Record the Compose versions resolved on `maplibreDebugRuntimeClasspath` vs `tomtomDebugRuntimeClasspath` (spec §16.2) in the scratchpad `pr2-notes.md` for the PR description.
- [ ] **Step 6: Commit** with `git add -A && git commit -m "feat(maplibre): show user location and recenter button"`.

---

### Task 7: SDK toggle with saved selection

**Files:**
- Create:
  - `core/src/main/java/com/github/karczews/publictransportviewer/map/MapSdkSelection.kt` (holds `resolveMapSdk`)
  - `core/src/main/java/com/github/karczews/publictransportviewer/map/MapSdkPreference.kt`
  - test `core/src/test/java/com/github/karczews/publictransportviewer/map/ResolveMapSdkTest.kt`
- Modify: `core/.../ui/home/MapSdkHost.kt`, `core/src/androidTest/.../ui/NavigationTest.kt`

**Interfaces:**
- Consumes: `MapSdk`, `MapSdkInitializer`, `MapSdkHost` (Task 2), `FakeMapSdk`.
- Produces:
  - `fun resolveMapSdk(available: List<MapSdk>, savedId: String?): MapSdk`. Returns the entry with `id == savedId`, else `available.first()`.
  - `class MapSdkPreference(context: Context)`, backed by SharedPreferences file `"map_sdk"` and key `"map_sdk_id"`, with `fun savedId(): String?` and `fun save(id: String)`.

- [ ] **Step 1: Write the failing `ResolveMapSdkTest`.** It uses a private fake `MapSdk` in the test file. Tests:
  - `returnsSavedSdk`: available `[a, b]`, saved `"b"` → `b`
  - `fallsBackToFirstForUnknownId`: saved `"gone"` → `a`
  - `fallsBackToFirstForNullId`: saved `null` → `a`
- [ ] **Step 2: Confirm it fails.** Run `./gradlew :core:testDebugUnitTest --tests "*.ResolveMapSdkTest"`. Expected: compilation FAIL.
- [ ] **Step 3: Implement** `resolveMapSdk` and `MapSdkPreference`.
- [ ] **Step 4: Confirm it passes** with the same command. Expected: 3 PASS.
- [ ] **Step 5: Write the failing NavigationTest cases.** `@Before` clears the `map_sdk` SharedPreferences on the target context.
  - `singleSdk_hidesToggle`: `setApp(FakeMapSdk("a", "A"))` → `onNodeWithTag("map_sdk_toggle").assertDoesNotExist()`.
  - `twoSdks_showToggleAndSwitchMap`: `setApp(FakeMapSdk("a", "A"), FakeMapSdk("b", "B"))`.
    - `fake_map_a` is displayed.
    - Click `map_sdk_option_b`: `fake_map_b` is displayed and `fake_map_a` `assertDoesNotExist()`.
  - `switchingDuringInit_initializesOnce`:
    - SDK `a` has `onInitialize = { calls++; gate.await() }`, where `gate = CompletableDeferred<Unit>()`, and sits alongside `FakeMapSdk("b", "B")`.
    - Click `map_sdk_option_b`, then `map_sdk_option_a`, then `gate.complete(Unit)` and wait for idle.
    - Assert `calls == 1` and that `fake_map_a` is displayed.
- [ ] **Step 6: Confirm they fail** with `./gradlew :core:connectedDebugAndroidTest`. Expected: the two toggle tests FAIL (no `map_sdk_toggle`/`map_sdk_option_b` node).
- [ ] **Step 7: Implement the toggle in `MapSdkHost`.**
  - `val preference = remember { MapSdkPreference(appContext) }` and `var selectedId by rememberSaveable { mutableStateOf(preference.savedId()) }`; `sdk = resolveMapSdk(mapSdks, selectedId)`.
  - When `mapSdks.size > 1`, draw a `SingleChoiceSegmentedButtonRow(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp).testTag("map_sdk_toggle"))` over the content.
  - One `SegmentedButton` per SDK: `selected = it.id == sdk.id`, `onClick = { selectedId = it.id; preference.save(it.id) }`, label `Text(it.displayName)`, `Modifier.testTag("map_sdk_option_${it.id}")`.
  - The toggle is drawn after (above) the init gate / `key(sdk.id)` content.
- [ ] **Step 8: Confirm they pass** with `./gradlew :core:connectedDebugAndroidTest`. Expected: all PASS.
- [ ] **Step 9: Commit** with `git add -A && git commit -m "feat: add runtime map SDK toggle with saved selection"`.

---

### Task 8: Camera handoff between SDKs

**Files:**
- Create: `core/src/main/java/com/github/karczews/publictransportviewer/ui/home/MapCamera.kt`
- Modify: `core/.../ui/home/HomeViewModel.kt`, `core/src/test/.../ui/home/HomeViewModelTest.kt`, `map-tomtom/.../TomTomHomeScreen.kt`, `map-maplibre/.../MapLibreHomeScreen.kt`

**Interfaces:**
- Produces:
  - `data class MapCamera(val lat: Double, val lon: Double, val zoom: Double)`
  - `HomeViewModel.camera: StateFlow<MapCamera?>` (initially `null`)
  - `HomeViewModel.onCameraChanged(camera: MapCamera)`

- [ ] **Step 1: Add the failing tests to `HomeViewModelTest`.**
  - `camera_isNullInitially`
  - `onCameraChanged_updatesCamera`: after `onCameraChanged(MapCamera(51.76, 19.45, 14.0))`, `camera.value == MapCamera(51.76, 19.45, 14.0)`.
- [ ] **Step 2: Confirm they fail.** Run `./gradlew :core:testDebugUnitTest --tests "*.HomeViewModelTest"`. Expected: compilation FAIL.
- [ ] **Step 3: Implement** `MapCamera` and the `HomeViewModel` members (spec §9).
- [ ] **Step 4: Confirm they pass** with the same command. Expected: PASS.
- [ ] **Step 5: Wire up TomTom.**
  - The initial camera is `viewModel.camera.value?.let { InitialCameraOptions.LocationBased(position = GeoPoint(it.lat, it.lon), zoom = it.zoom) }`, falling back to the `HomeMapDefaults` options.
  - Report with `LaunchedEffect(mapViewState) { snapshotFlow { mapViewState.cameraState.data }.filter { it.isStable }.collect { viewModel.onCameraChanged(MapCamera(it.position.position.latitude, it.position.position.longitude, it.position.zoom)) } }`.
- [ ] **Step 6: Wire up MapLibre.**
  - `initialCameraPosition` comes from `viewModel.camera.value`, falling back to `HomeMapDefaults`.
  - Report with `LaunchedEffect(mapState) { snapshotFlow { mapState.isCameraMoving to mapState.cameraPosition }.filter { !it.first }.collect { (_, p) -> viewModel.onCameraChanged(MapCamera(p.center.latitude, p.center.longitude, p.zoom)) } }`.
- [ ] **Step 7: Verify.** Run `./gradlew assembleDebug testDebugUnitTest -PtomtomApiKey=""`. Expected: `BUILD SUCCESSFUL`. The manual check of the handoff happens in Task 9, once `all` exists.
- [ ] **Step 8: Commit** with `git add -A && git commit -m "feat: keep camera position when switching map SDK"`.

---

### Task 9: `all` flavor, packaging check, CI and docs

**Files:**
- Create: `app/src/all/java/com/github/karczews/publictransportviewer/AvailableMapSdks.kt`
- Modify: `app/build.gradle.kts`, `.github/workflows/lint.yml`, `AGENTS.md`, `docs/ARCHITECTURE.md`

- [ ] **Step 1: Add the `all` flavor** per spec §8 (`.all`, "PTV Compare"). Use `"allImplementation"` for both map modules, and set `availableMapSdks = listOf(TomTomMapSdk(), MapLibreMapSdk())`.
- [ ] **Step 2: Check packaging.**
  - Run `./gradlew :app:assembleAllDebug :app:checkAllDebugDuplicateClasses`. Expected: `BUILD SUCCESSFUL`.
  - On a duplicate *identical* native lib (e.g. `libc++_shared.so`): add `packaging { jniLibs { pickFirsts += "**/<lib>.so" } }` and rerun.
  - On duplicate classes, or the same lib at different versions: **STOP and report** (spec §16.3).
- [ ] **Step 3: Change CI lint.** In `.github/workflows/lint.yml`, the run step becomes `./gradlew lintAllDebug -PtomtomApiKey=""`, uploading `app/build/reports/lint-results-allDebug.html` and `.txt`.
- [ ] **Step 4: Update the docs.**
  - `AGENTS.md`: `installAllDebug`, the `lintAllDebug` lint command, and the note "measure with single-SDK flavors; `all` is for look-and-feel" (spec §7).
  - `docs/ARCHITECTURE.md`: the toggle and camera handoff, plus the "Adding a new map SDK" checklist from spec §14.
- [ ] **Step 5: Verify PR 3.**
  - Run `./gradlew assembleDebug testDebugUnitTest lintAllDebug -PtomtomApiKey=""`. Expected: `BUILD SUCCESSFUL`, with 62 + 3 + 2 = **67** core unit tests and 6 map-maplibre unit tests.
  - With a device, run `./gradlew connectedDebugAndroidTest` for the library modules (`:core:connectedDebugAndroidTest :map-tomtom:connectedDebugAndroidTest :map-maplibre:connectedDebugAndroidTest`). Expected: PASS.
- [ ] **Step 6: Check the Compare build manually** with `./gradlew installAllDebug` and launch "PTV Compare":
  1. The toggle shows `TomTom | MapLibre`.
  2. Pan and zoom on TomTom, select a vehicle, then switch to MapLibre. Expected: the same area and zoom (±0.5), and the same vehicle selected with its route shown.
  3. Kill the app (`adb shell am force-stop com.github.karczews.publictransportviewer.all`) and relaunch. Expected: MapLibre is still selected.
  4. Screenshot both SDKs in portrait and landscape and inspect. Expected: the toggle doesn't overlap the TomTom or MapLibre controls.
- [ ] **Step 7: Commit** with `git add -A && git commit -m "feat: add all flavor bundling every map SDK"`.
