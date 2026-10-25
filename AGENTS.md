# AGENTS.md

Guidance for AI coding agents working in this repository.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APKs of every flavor
./gradlew installTomtomDebug     # Install the TomTom build ("PTV TomTom")
./gradlew installMaplibreDebug   # Install the MapLibre build ("PTV MapLibre")
./gradlew test                   # Run unit tests
./gradlew lintTomtomDebug lintMaplibreDebug  # Android lint
./gradlew spotlessCheck          # ktfmt formatting check (kotlinlang style); run spotlessApply to fix
./gradlew detekt                 # detekt static analysis (core rules + compose-rules), config in config/detekt/detekt.yml
./gradlew connectedAndroidTest   # Run instrumented tests on a connected device/emulator
./gradlew :core:testDebugUnitTest --tests "*.HomeViewModelTest"  # Run a single unit test class
# Single instrumented test:
./gradlew :core:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.github.karczews.publictransportviewer.ui.StopsScreenTest
```

The TomTom API key must be set in `gradle.properties` as `tomtomApiKey=YOUR_KEY`. It is injected into `:map-tomtom`'s `BuildConfig.TOMTOM_API_KEY` at build time.

## Architecture

See `docs/ARCHITECTURE.md` for data-flow diagrams, the Room schema, and component relationships.

Multi-module Android app using MVVM with Jetpack Compose. `:app` holds only `PublicTransportApp`, `MainActivity` and the list of map SDKs it ships (`AvailableMapSdks.kt`); `:core` holds everything SDK-free (data, DI, view models, Stops/Alerts, nav scaffold); each map SDK is a `:map-<sdk>` module; `:core-testing` holds instrumented-test fakes. Dependency injection uses **Koin with the Koin Compiler Plugin** (`io.insert-koin.compiler.plugin`) — the annotation-based, compile-time-verified config (not KSP). The graph lives in `:core`'s `di/CoreModule.kt` (a `@Module @ComponentScan` class of `@Single` provider functions); view models are `@KoinViewModel`, the WorkManager worker is `@KoinWorker`, and `PublicTransportApp` starts Koin via `startKoin<PublicTransportKoinConfiguration>` (a `@KoinApplication` aggregator). `compileSafety`/`strictSafety` validate the whole graph at build time. Composables obtain view models with `koinViewModel()`. Instrumented tests swap the graph through `TestPublicTransportApp` + `KoinTestRunner` (a plain DSL `testModule` of fakes).

### Data Pipeline

Two separate data pipelines feed the app from the Lodz Open Data portal (`otwarte.miasto.lodz.pl`):

**GTFS Static** — A ZIP of CSV files (`routes.txt`, `stops.txt`, `trips.txt`, `stop_times.txt`, `shapes.txt`) downloaded on first launch and refreshed weekly via WorkManager. Parsed by `GtfsCsvParser` and stored in a Room database (`GtfsDatabase`, 6 tables). Large files (`stop_times.txt`, `shapes.txt`) are streamed in 1000-row batches.

**GTFS Realtime** — Three protobuf binary feeds (`.bin` files) fetched via OkHttp and deserialized with `gtfs-realtime-bindings`. Vehicle positions poll every 5s, trip updates every 15s, alerts every 60s. Each repository caches the last successful response in-memory as a fallback on network failure.

Feed URLs contain a hardcoded year/month path in `GtfsEndpoints` (currently `2025/06`). When the city rotates feeds, this must be updated.

### Map SDKs

Each map SDK implements `MapSdk` (`:core`, `map/MapSdk.kt`) and owns the **whole Home tab** — map, markers, location, permission flow, controls — so SDK-specific experiments stay inside that module. `MapSdkHost` awaits `MapSdk.initialize()` (showing a spinner in the Home tab) before rendering `MapSdk.HomeScreen`. `HomeMapDefaults` and `vehicleTitle`/`vehicleLabel`/`vehicleIconRes` in `:core` are optional shared helpers.

`:app` has one product flavor per map SDK (dimension `mapSdk`: `tomtom`, `maplibre`); each flavor's `src/<flavor>/…/AvailableMapSdks.kt` lists the SDKs it ships and the flavor pulls in only that SDK's module. Flavors install side by side (`applicationIdSuffix`).

**TomTom** (`:map-tomtom`) uses the TomTom Maps SDK Compose API (`map-display-compose-standard`, version `tomtomSdk` in the catalog). `TomTomMapSdk.initialize()` runs `TomTomSdk.initialize()`.

Key composables: `TomTomMap` (map surface), `Marker` (vehicles/stops), `Polyline` (route shapes), `CurrentLocationMarker` (blue dot). The location provider lifecycle (`enable()`/`disable()`) must be managed manually in a `DisposableEffect`.

**MapLibre** (`:map-maplibre`) uses maplibre-compose (pre-1.0; the public docs lag the API — read the `maplibre-compose-android` sources jar for the pinned version) with the Maptoolkit `summer.json` style. Every `SymbolLayer` sets `textFont` to a font the style's glyph server serves (Noto Sans / Rosario); maplibre-compose's default font stack 404s there and labels silently disappear. Maptoolkit attribution is mandatory: its logo (≥ 24dp, bottom-start, links to maptoolkit.org) and the attribution button must stay visible and uncovered. The OpenGL/Vulkan render backend is a single `runtimeOnly` line, documented in `map-maplibre/build.gradle.kts`. Instrumented tests use `androidx.compose.ui.test.junit4.v2.createComposeRule` — the v1 rule's unconfined dispatcher trips maplibre-compose's main-thread guard.

### Enrichment Pattern

Realtime data is enriched with static GTFS data from Room. Repositories fetch raw RT data from data sources, then look up route names, colors, headsigns, and vehicle types from Room DAOs before emitting to ViewModels. Vehicle type is determined from GTFS `route_type` (0=Tram, 3=Bus) with a fallback heuristic (route numbers 1–46 = Tram) when static data hasn't loaded yet.

## Conventions

- Base package: `com.github.karczews.publictransportviewer`
- TomTom SDK flavor: `complete` (set via `missingDimensionStrategy` in both `:app` and `:map-tomtom`); the app's own flavor dimension is `mapSdk`
- Room schemas exported to `core/schemas/`
- R classes are non-transitive: SDK modules reference `:core` resources via `import com.github.karczews.publictransportviewer.core.R as CoreR`
- NDK ABI filters: `arm64-v8a` and `x86_64` only
- Dependency versions live in the version catalog `gradle/libs.versions.toml` — resolve and bump them there, not in module build files
- Commits follow Conventional Commits (`type(scope): subject`); branches follow `type/short-task-description` (e.g. `chore/xxx`, `feat/xxx`) — see `CONTRIBUTING.md`
