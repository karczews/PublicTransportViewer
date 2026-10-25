# PublicTransportViewer - Architecture Documentation

PublicTransportViewer is an Android application that displays real-time public transport
data for the city of Lodz (Poland) on an interactive map. It shows live vehicle
positions (trams and buses), stop departure boards with real-time delays, and
service alerts.

## Modules

```mermaid
graph LR
    app[":app"] --> core[":core"]
    app -- tomtom flavor --> tomtom[":map-tomtom"]
    app -- maplibre flavor --> maplibre[":map-maplibre"]
    app -. all flavor: both .-> tomtom
    tomtom --> core
    maplibre --> core
    testing[":core-testing"] --> core
    core -. androidTest .-> testing
    tomtom -. androidTest .-> testing
    maplibre -. androidTest .-> testing
```

| Module | Contents |
|--------|----------|
| `:app` | `PublicTransportApp`, `MainActivity`; flavors `tomtom` / `maplibre` / `all`, each with its own `src/<flavor>/…/AvailableMapSdks.kt` (the map SDKs that build ships) |
| `:core` | Room, GTFS static/RT pipelines, repositories, worker, `CoreModule` (Koin), view models, Stops/Alerts screens, theme, nav scaffold, `MapSdk` contract, `MapSdkHost` |
| `:map-tomtom` | `TomTomMapSdk` + `TomTomHomeScreen` (TomTom SDK only) |
| `:map-maplibre` | `MapLibreMapSdk` + `MapLibreHomeScreen` (maplibre-compose only) |
| `:core-testing` | Instrumented-test fakes: `KoinTestRunner`, `TestPublicTransportApp`, `FixtureData`, `FakeMapSdk` |

## Map SDKs

Each map SDK implements `MapSdk` and owns the whole Home tab:

```kotlin
interface MapSdk {
    val id: String                                   // stable id, e.g. "tomtom"
    val displayName: String
    suspend fun initialize(context: Context) {}      // idempotent SDK start-up
    @Composable fun HomeScreen(viewModel: HomeViewModel, modifier: Modifier)
}
```

`MapSdkHost` (in `:core`) awaits `initialize()` through `MapSdkInitializer`, which runs it once at a
time outside the composition, so leaving the tab does not cancel it; a failed initialization is
logged and retried on the next entry. Meanwhile the Home tab shows a spinner or the error. Stops and
Alerts never wait for a map SDK.

When a build ships more than one SDK (the `all` flavor, "PTV Compare"), `MapSdkHost` draws a
segmented toggle top-center; the choice is saved in SharedPreferences (`map_sdk` / `map_sdk_id`) and an
unknown or missing id falls back to the first SDK. Switching disposes the old map completely
(`key(sdk.id)`), so only one map is alive at a time. Each SDK screen reads `HomeViewModel.camera` for its
initial camera and reports `onCameraChanged` when its camera settles, so the next SDK opens on the same
area and zoom; selection and route live in `HomeViewModel` and survive the switch. An initialized SDK
stays loaded for the process, so size/startup/memory are measured with the single-SDK flavors.

### Adding a new map SDK

1. Create a `:map-xyz` library module depending on `:core` (add it to `settings.gradle.kts`).
2. Implement `MapSdk` (`id`, `displayName`, `initialize()`, `HomeScreen()`); the module owns the whole Home tab.
3. Add an `xyz` flavor in `app/build.gradle.kts` with `"xyzImplementation"(project(":map-xyz"))` and
   `app/src/xyz/java/…/AvailableMapSdks.kt`.
4. Add the module to the `all` flavor's dependencies and to `app/src/all/java/…/AvailableMapSdks.kt`.
5. Add an instrumented smoke test (e.g. the recenter button is displayed) using `:core-testing` fakes.

### TomTom

The TomTom renderer (`:map-tomtom`) uses the **TomTom Maps SDK for Android** Jetpack Compose
integration (`map-display-compose-standard`). `TomTomMapSdk.initialize()` initializes the SDK with
an API key provided via the `tomtomApiKey` Gradle property (injected into `:map-tomtom`'s `BuildConfig`).

Key TomTom Compose components used:

| Component | Purpose |
|-----------|---------|
| `TomTomMap` | Root composable hosting the map surface |
| `Marker` / `MarkerData` / `MarkerProperties` | Vehicle and stop markers with labels and balloon text |
| `Polyline` / `PolylineData` / `PolylineProperties` | Route shape overlays with color and width-by-zoom |
| `CurrentLocationMarker` / `CurrentLocationMarkerProperties` | Blue dot showing device position |
| `MapLocationInfrastructure` | Connects the TomTom location provider to the map |
| `rememberMapViewState` | Holds camera state, supports `animateCamera()` |

The map is configured in `MapDisplayInfrastructure` which binds `TomTomSdk.sdkContext`
and `TomTomSdk.locationProvider` to the map display.

### MapLibre

The MapLibre renderer (`:map-maplibre`) uses **maplibre-compose** (`org.maplibre.compose`, backed by
`maplibre-native-ffi`) with the Maptoolkit community style `https://styles.maptoolkit.org/summer.json`
(no API key). Map content is declarative: GeoJSON sources plus Symbol/Line layers inside
`rememberMapState(content = { … })`. Our own map `overlay` draws the mandatory Maptoolkit logo
(≥ 24dp, bottom-start) and keeps the library's attribution button (bottom-end); it replaces
`MapOverlay.Default`, which also drops the MapLibre logo. Exactly one native render runtime
(OpenGL by default, Vulkan optional) is declared in `map-maplibre/build.gradle.kts`.


## Data Sources and Formats

All transit data comes from the **Lodz Open Data portal**
(`otwarte.miasto.lodz.pl`). Two distinct data pipelines feed the app:

### GTFS Static (Schedule Data)

**Format:** Standard GTFS ZIP archive containing CSV text files.
**URL pattern:** `https://otwarte.miasto.lodz.pl/wp-content/uploads/{year}/{month}/GTFS.zip`
**Current:** `2025/06/GTFS.zip`

The ZIP contains these files (parsed by the app):

| File | Contents | Room Table |
|------|----------|------------|
| `routes.txt` | Route definitions (id, short name, type, color) | `gtfs_routes` |
| `stops.txt` | Stop locations (id, name, lat/lon, code) | `gtfs_stops` |
| `trips.txt` | Trip definitions (id, route, headsign, shape) | `gtfs_trips` |
| `stop_times.txt` | Scheduled arrival/departure times per trip+stop | `gtfs_stop_times` |
| `shapes.txt` | Geographic shape points for route polylines | `gtfs_shapes` |

The CSV parsing is handled by `GtfsCsvParser`, which supports:
- Header-based column lookup (column order varies between feeds)
- Quoted field handling
- BOM stripping
- Streaming batch inserts for large files (`stop_times.txt`, `shapes.txt`)
  with 1000-row batches to limit memory pressure

The static data is downloaded on first launch and refreshed weekly via
`GtfsRefreshWorker` (AndroidX WorkManager `PeriodicWorkRequest`).

```mermaid
flowchart LR
    subgraph Download
        ZIP["GTFS.zip<br/>(HTTP GET)"]
    end

    subgraph Parse["ZipInputStream"]
        R[routes.txt]
        S[stops.txt]
        T[trips.txt]
        ST[stop_times.txt<br/>streamed 1K batches]
        SH[shapes.txt<br/>streamed 1K batches]
    end

    subgraph Store["Room Database"]
        RT[gtfs_routes]
        SP[gtfs_stops]
        TR[gtfs_trips]
        STT[gtfs_stop_times]
        SHP[gtfs_shapes]
    end

    ZIP --> R --> RT
    ZIP --> S --> SP
    ZIP --> T --> TR
    ZIP --> ST --> STT
    ZIP --> SH --> SHP
```

### GTFS Realtime (Live Data)

**Format:** Protocol Buffer binary, defined by the
[GTFS Realtime specification](https://gtfs.org/realtime/).
**Library:** `org.mobilitydata:gtfs-realtime-bindings:0.0.8`

Three RT feeds are consumed:

| Feed | URL pattern | Polling interval | Purpose |
|------|-------------|-----------------|---------|
| Vehicle Positions | `.../vehicle_positions.bin` | 5 seconds | Live tram/bus locations on the map |
| Trip Updates | `.../trip_updates.bin` | 15 seconds | Real-time arrival/departure delays |
| Service Alerts | `.../alerts.bin` | 60 seconds | Disruption notifications |

Each feed is fetched as raw bytes via OkHttp, then deserialized with
`GtfsRealtime.FeedMessage.parseFrom(bytes)`. The protobuf `FeedMessage`
contains a list of `FeedEntity`, each wrapping either a `VehiclePosition`,
`TripUpdate`, or `Alert` message.

**URL date-path caveat:** The city publishes feeds under a year/month path
(e.g., `2025/06/`). This path changes when feeds are updated. The current
implementation hardcodes `year=2025, month=6` in `GtfsEndpoints`.

## Local Database

**Room v2.8.4** with KSP annotation processing. Database class: `GtfsDatabase`
(version 2, destructive migration enabled).

Six tables store the parsed GTFS static data:

| Table | Entity | Primary Key |
|-------|--------|-------------|
| `gtfs_routes` | `RouteEntity` | `route_id` |
| `gtfs_stops` | `StopEntity` | `stop_id` (indexed on `stop_name`) |
| `gtfs_trips` | `TripEntity` | `trip_id` (indexed on `route_id`) |
| `gtfs_stop_times` | `StopTimeEntity` | (`trip_id`, `stop_sequence`) (indexed on `stop_id`) |
| `gtfs_shapes` | `ShapePointEntity` | (`shape_id`, `shape_pt_sequence`) (indexed on `shape_id`) |
| `gtfs_metadata` | `GtfsMetadataEntity` | `key` (key-value store for download timestamps) |

```mermaid
erDiagram
    gtfs_routes {
        string route_id PK
        string agency_id
        string route_short_name
        string route_long_name
        int route_type
        string route_color
        string route_text_color
    }

    gtfs_trips {
        string trip_id PK
        string route_id FK
        string service_id
        string trip_headsign
        int direction_id
        string shape_id
    }

    gtfs_stops {
        string stop_id PK
        string stop_name
        double stop_lat
        double stop_lon
        string stop_code
        int location_type
        string parent_station
    }

    gtfs_stop_times {
        string trip_id PK
        int stop_sequence PK
        string stop_id FK
        string arrival_time
        string departure_time
        int pickup_type
        int drop_off_type
    }

    gtfs_shapes {
        string shape_id PK
        int shape_pt_sequence PK
        double shape_pt_lat
        double shape_pt_lon
    }

    gtfs_metadata {
        string key PK
        string value
    }

    gtfs_routes ||--o{ gtfs_trips : "has"
    gtfs_trips ||--o{ gtfs_stop_times : "has"
    gtfs_stops ||--o{ gtfs_stop_times : "served by"
    gtfs_trips }o--o| gtfs_shapes : "follows"
```

## Application Architecture

The app follows **MVVM** with a unidirectional data flow. Dependency injection is
handled by **Koin** (with the Koin compiler plugin): the graph is defined in
`:core`'s `di/CoreModule.kt`, view models are `@KoinViewModel`, the WorkManager worker is
`@KoinWorker`, and `PublicTransportApp` (the `Application` subclass) starts Koin
via `startKoin<PublicTransportKoinConfiguration>`.

```mermaid
graph TB
    subgraph UI["UI Layer (Jetpack Compose)"]
        HS[HomeScreen]
        SS[StopsScreen]
        AS[AlertsScreen]
        HVM[HomeViewModel]
        SVM[StopsViewModel]
        AVM[AlertsViewModel]
    end

    subgraph Repo["Repository Layer"]
        VR[VehicleRepository]
        RDR[RouteDisplayRepository]
        SR[StopRepository]
        AR[AlertRepository]
    end

    subgraph Data["Data Source Layer"]
        VDS[GtfsRtVehicleDataSource]
        TUDS[GtfsRtTripUpdateDataSource]
        ADS[GtfsRtAlertDataSource]
        SDS[GtfsStaticDataSource]
        CSV[GtfsCsvParser]
    end

    subgraph Infra["Infrastructure"]
        API[GtfsRtApi<br/>OkHttp]
        DB[(Room Database<br/>GtfsDatabase)]
        WM[GtfsRefreshWorker<br/>WorkManager]
    end

    subgraph External["External Services"]
        LODZ[("Lodz Open Data<br/>otwarte.miasto.lodz.pl")]
        TT["TomTom Maps SDK"]
    end

    HS --> HVM
    SS --> SVM
    AS --> AVM
    HS --> TT

    HVM --> VR
    HVM --> RDR
    SVM --> SR
    AVM --> AR

    VR --> VDS
    VR --> DB
    SR --> DB
    SR --> TUDS
    RDR --> DB
    AR --> ADS
    AR --> DB

    VDS --> API
    TUDS --> API
    ADS --> API
    SDS --> API
    SDS --> CSV
    SDS --> DB
    WM --> SDS

    API --> LODZ
```

### Data Flow: Vehicle Positions

```mermaid
sequenceDiagram
    participant UI as HomeScreen
    participant VM as HomeViewModel
    participant Repo as VehicleRepository
    participant DS as GtfsRtVehicleDataSource
    participant API as GtfsRtApi
    participant Lodz as Lodz Open Data
    participant Room as Room DB

    VM->>Repo: observeVehiclePositions()
    loop Every 5 seconds
        Repo->>DS: getVehiclePositions()
        DS->>API: fetchVehiclePositions()
        API->>Lodz: GET vehicle_positions.bin
        Lodz-->>API: Protobuf bytes
        API-->>DS: FeedMessage
        DS-->>Repo: List<VehiclePosition>
        Repo->>Room: getRouteById(), getTripById()
        Room-->>Repo: Route names, colors, headsigns
        Repo-->>VM: Enriched List<VehiclePosition>
        VM-->>UI: StateFlow update
        UI->>UI: Render Markers on TomTom map
    end

    Note over Repo: On network failure,<br/>returns last cached positions
```

Vehicle type is determined from `route_type` in the static GTFS data (0 = Tram, 3 = Bus);
falls back to a heuristic (route numbers 1-46 = Tram) when static data is unavailable.

### Data Flow: Stop Departures

```mermaid
sequenceDiagram
    participant User
    participant UI as StopsScreen
    participant VM as StopsViewModel
    participant Repo as StopRepository
    participant Room as Room DB
    participant DS as TripUpdateDataSource
    participant Lodz as Lodz Open Data

    User->>UI: Type stop name
    UI->>VM: onQueryChange(text)
    Note over VM: Debounce 300ms
    VM->>Repo: searchStops(query)
    Repo->>Room: searchStopsByName()
    Room-->>UI: List<StopEntity>

    User->>UI: Tap stop
    UI->>VM: onStopSelected(stop)
    VM->>Repo: observeDeparturesForStop(stopId)

    loop Every 15 seconds
        Repo->>Room: getStopTimesForStop()
        Room-->>Repo: Scheduled departures (next 60 min)
        Repo->>DS: getTripUpdates()
        DS->>Lodz: GET trip_updates.bin
        Lodz-->>DS: Protobuf bytes
        DS-->>Repo: List<TripUpdate>
        Repo->>Repo: Merge schedule + RT delays
        Repo-->>VM: Sorted List<StopDeparture>
        VM-->>UI: Departure board
    end
```

### Data Flow: Route Display

When a vehicle marker is selected:

```mermaid
sequenceDiagram
    participant UI as HomeScreen
    participant VM as HomeViewModel
    participant Repo as RouteDisplayRepository
    participant Room as Room DB

    UI->>VM: onVehicleSelected(vehicle)
    VM->>Repo: loadRouteForTrip(tripId)
    Repo->>Room: getTripById(tripId)
    Room-->>Repo: TripEntity (routeId, shapeId)
    Repo->>Room: getRouteById(routeId)
    Room-->>Repo: RouteEntity (color, name)
    Repo->>Room: getShapePoints(shapeId)
    Room-->>Repo: List<ShapePointEntity>
    Repo->>Room: getStopTimesForTrip() + getStopById()
    Room-->>Repo: Stop locations
    Repo-->>VM: RouteDisplayData
    VM-->>UI: Polyline + Stop markers on map
```

### Data Flow: Service Alerts

```mermaid
sequenceDiagram
    participant UI as AlertsScreen
    participant VM as AlertsViewModel
    participant Repo as AlertRepository
    participant DS as GtfsRtAlertDataSource
    participant Lodz as Lodz Open Data
    participant Room as Room DB

    VM->>Repo: observeAlerts()
    loop Every 60 seconds
        Repo->>DS: getAlerts()
        DS->>Lodz: GET alerts.bin
        Lodz-->>DS: Protobuf bytes
        DS-->>Repo: List<ServiceAlert>
        Repo->>Room: getRouteById() for affected routes
        Room-->>Repo: Route short names
        Repo-->>VM: Enriched List<ServiceAlert>
        VM-->>UI: Alert cards
    end
```

### App Startup Sequence

```mermaid
sequenceDiagram
    participant OS as Android OS
    participant App as PublicTransportApp
    participant WM as WorkManager
    participant SDS as GtfsStaticDataSource
    participant Act as MainActivity
    participant Host as MapSdkHost
    participant SDK as TomTomSdk

    OS->>App: onCreate()
    App->>WM: Schedule weekly GtfsRefreshWorker
    App->>SDS: isDataAvailable()?
    alt No GTFS data yet
        SDS-->>App: false
        App->>SDS: downloadAndImport()
        SDS->>SDS: Download GTFS.zip, parse CSVs, insert to Room
    else Data exists
        SDS-->>App: true
    end
    OS->>Act: onCreate()
    Act->>Act: Render NavigationSuiteScaffold
    Act->>Host: Home tab shown
    Host->>SDK: TomTomMapSdk.initialize() → TomTomSdk.initialize(apiKey)
    SDK-->>Host: Ready
    Host->>Host: Render TomTomHomeScreen
```

## UI Layer

Built entirely with **Jetpack Compose** and **Material 3**.

### Navigation

`NavigationSuiteScaffold` (Material 3 Adaptive) with three destinations:

| Tab | Screen | Icon |
|-----|--------|------|
| Home | `MapSdkHost` → the selected SDK's Home screen (e.g. `TomTomHomeScreen`) - full-screen map with vehicle markers | `ic_home` |
| Stops | `StopsScreen` - Stop search with departure boards | `ic_bus` |
| Alerts | `AlertsScreen` - Service disruption cards | `ic_alert` |

```mermaid
stateDiagram-v2
    [*] --> Loading: App launch
    Loading --> Home: TomTomSdk initialized

    state NavigationSuiteScaffold {
        Home --> Stops: Tab tap
        Home --> Alerts: Tab tap
        Stops --> Home: Tab tap
        Stops --> Alerts: Tab tap
        Alerts --> Home: Tab tap
        Alerts --> Stops: Tab tap
    }

    state Home {
        MapView --> VehicleSelected: Tap marker
        VehicleSelected --> MapView: Tap map / deselect
        VehicleSelected: Route polyline + stops shown
    }

    state Stops {
        Search --> StopList: Type query
        StopList --> DepartureBoard: Tap stop
        DepartureBoard --> StopList: Back
    }

    state Alerts {
        AlertList --> AlertExpanded: Tap card
        AlertExpanded --> AlertList: Tap card
    }
```

### Home Screen Features

- Real-time vehicle markers (tram/bus icons with route labels)
- Marker selection shows balloon text with route and headsign
- Selected vehicle displays route polyline and stop markers on the map
- Current location blue dot (`CurrentLocationMarker`)
- Recenter-to-location button (bottom-right `FilledTonalIconButton`)
- Runtime location permission handling via `ActivityResultContracts`
- Location provider lifecycle management (`enable()`/`disable()` in `DisposableEffect`)

### Stops Screen Features

- Text search with 300ms debounce
- Stop list with name and code
- Departure board showing:
  - Route badge (colored by route color)
  - Trip headsign
  - Scheduled time
  - Minutes until arrival
  - Delay indicator (red for late, green for early)

### Alerts Screen Features

- Loading spinner during initial fetch
- Expandable alert cards
- Color-coded effect indicators (NO_SERVICE = red, DETOUR = blue, etc.)
- Route badges for affected routes
- Description text revealed on tap

## Key Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| TomTom Maps SDK | 2.2.1 | Map rendering, markers, polylines, location |
| maplibre-compose | 0.20.0 | MapLibre map rendering (`:map-maplibre`), GeoJSON layers, location |
| Jetpack Compose BOM | 2025.12.00 | UI framework |
| Material 3 | (from BOM) | Design system, adaptive navigation |
| Room | 2.8.4 | Local SQLite database for GTFS static data |
| OkHttp | 4.12.0 | HTTP client for all network requests |
| GTFS RT Bindings | 0.0.8 | Protocol Buffer classes for GTFS Realtime |
| WorkManager | 2.11.0 | Periodic background GTFS refresh |
| KSP | 2.2.10-2.0.2 | Annotation processing for Room |
| Kotlin | 2.2.10 | Language |
| AGP | 9.2.0 | Android build toolchain |

## Build Configuration

- **Min SDK:** 32 (Android 12L)
- **Target/Compile SDK:** 36
- **ABI filters:** `arm64-v8a`, `x86_64`
- **TomTom SDK flavor:** `complete` (via `missingDimensionStrategy` in `:app` and `:map-tomtom`)
- **TomTom API key:** Provided via `gradle.properties` as `tomtomApiKey`,
  injected into `:map-tomtom`'s `BuildConfig.TOMTOM_API_KEY`

## Project Structure

```
app/src/main/java/com/github/karczews/publictransportviewer/
├── PublicTransportApp.kt          # Application class, starts Koin, schedules GTFS refresh
└── MainActivity.kt                # setContent { PublicTransportViewerApp(availableMapSdks) }
app/src/{tomtom,maplibre,all}/java/com/github/karczews/publictransportviewer/
└── AvailableMapSdks.kt            # Map SDKs this flavor ships (first = default)

map-tomtom/src/main/java/com/github/karczews/publictransportviewer/map/tomtom/
├── TomTomMapSdk.kt                # MapSdk: TomTom SDK init + Home screen
├── TomTomHomeScreen.kt            # Map with vehicles, route overlay, location
└── MarkerSelection.kt             # Mirrors view-model selection onto marker balloons

map-maplibre/src/main/java/com/github/karczews/publictransportviewer/map/maplibre/
├── MapLibreMapSdk.kt              # MapSdk: Maptoolkit style + Home screen
├── MapLibreHomeScreen.kt          # Map, GeoJSON layers, info card, location, attribution
└── MapLibreFeatures.kt            # Vehicles/route/stops -> GeoJSON features (unit-tested)

core-testing/src/main/java/com/github/karczews/publictransportviewer/
├── KoinTestRunner.kt              # Instrumented runner installing TestPublicTransportApp
├── TestPublicTransportApp.kt      # Koin test module, FixtureData, Fixed* repositories
└── map/FakeMapSdk.kt              # MapSdk with an empty, tagged Home screen

core/src/main/java/com/github/karczews/publictransportviewer/
├── di/CoreModule.kt               # Koin graph
├── map/
│   ├── MapSdk.kt                  # Contract every map SDK module implements
│   ├── MapSdkSelection.kt         # resolveMapSdk: saved choice or the flavor's default
│   └── MapSdkPreference.kt        # Toggle choice in SharedPreferences
├── data/
│   ├── db/
│   │   ├── GtfsDatabase.kt        # Room database definition
│   │   ├── dao/                    # Room DAOs (Route, Stop, Trip, StopTime, ShapePoint, Metadata)
│   │   └── entity/                 # Room entities matching GTFS tables
│   ├── model/
│   │   ├── VehiclePosition.kt      # Vehicle with position, route, type, status
│   │   ├── RouteDisplayData.kt     # Route polyline + stops for map overlay
│   │   ├── StopDeparture.kt        # Departure with schedule + real-time delay
│   │   ├── TripUpdate.kt           # Real-time trip delay updates
│   │   └── ServiceAlert.kt         # Alert with cause, effect, affected entities
│   ├── network/
│   │   ├── GtfsEndpoints.kt        # URL construction for all GTFS feeds
│   │   └── GtfsRtApi.kt            # OkHttp-based protobuf feed fetcher
│   ├── repository/
│   │   ├── VehicleRepository.kt     # Polls RT positions, enriches from Room
│   │   ├── RouteDisplayRepository.kt # Loads route shapes and stops for a trip
│   │   ├── StopRepository.kt        # Stop search + departure board with RT delays
│   │   └── AlertRepository.kt       # Polls RT alerts, enriches route names
│   ├── source/
│   │   ├── GtfsRtVehicleDataSource.kt   # Protobuf -> VehiclePosition mapping
│   │   ├── GtfsRtTripUpdateDataSource.kt # Protobuf -> TripUpdate mapping
│   │   ├── GtfsRtAlertDataSource.kt     # Protobuf -> ServiceAlert mapping
│   │   ├── GtfsStaticDataSource.kt      # GTFS ZIP download and Room import
│   │   ├── GtfsCsvParser.kt             # CSV parsing for all GTFS text files
│   │   └── HardcodedVehicleDataSource.kt # Legacy test data source
│   └── worker/
│       └── GtfsRefreshWorker.kt     # Weekly GTFS static data refresh
└── ui/
    ├── PublicTransportViewerApp.kt  # Nav scaffold (Home/Stops/Alerts)
    ├── home/
    │   ├── MapSdkHost.kt            # Home tab: init gate, then MapSdk.HomeScreen
    │   ├── MapSdkInitializer.kt     # Runs each MapSdk.initialize() once, outside composition
    │   ├── HomeMapDefaults.kt       # Shared start camera / recenter zoom
    │   ├── VehicleFormatting.kt     # Shared vehicle label/title/icon helpers
    │   ├── MapCamera.kt             # Camera handed from one SDK screen to the next
    │   └── HomeViewModel.kt         # Vehicle polling, route selection
    ├── stops/
    │   ├── StopsScreen.kt           # Search + departure board UI
    │   └── StopsViewModel.kt        # Search debounce, departure polling
    ├── alerts/
    │   ├── AlertsScreen.kt          # Alert cards with expand/collapse
    │   └── AlertsViewModel.kt       # Alert polling
    └── theme/
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```
