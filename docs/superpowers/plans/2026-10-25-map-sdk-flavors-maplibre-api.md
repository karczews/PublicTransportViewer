# Verified maplibre-compose 0.20.0 API (Task 0 probe)

Companion to `2026-10-25-map-sdk-flavors.md`. Everything below compiled under Kotlin 2.4.0 / AGP 9.4.1 / compileSdk 37
in the Task 0 probe (2026-10-25). The public docs at maplibre.org/maplibre-compose are **out of date** for 0.20.0. Trust this
file and the sources jars over them.

## Re-fetching the sources

```bash
D=<some empty dir>; mkdir -p $D && cd $D
curl -sfLO https://repo1.maven.org/maven2/org/maplibre/compose/maplibre-compose-android/0.20.0/maplibre-compose-android-0.20.0-sources.jar
curl -sfLO https://repo1.maven.org/maven2/org/maplibre/compose/location-android/0.20.0/location-android-0.20.0-sources.jar
curl -sfLO https://repo1.maven.org/maven2/org/maplibre/spatialk/geojson-jvm/0.8.0/geojson-jvm-0.8.0-sources.jar
for j in *.jar; do mkdir -p "${j%.jar}" && (cd "${j%.jar}" && unzip -q ../"$j"); done
```

## Build and runtime facts

- Root `build.gradle.kts` needs `alias(libs.plugins.android.library) apply false`.
- Compose resolves to 1.12.1 with or without maplibre-compose (BOM 2026.09.00), so there's no drift.
- maplibre-compose pulls in no OkHttp.
- Native code comes from `org.maplibre.nativeffi:maplibre-native-ffi(-runtime-opengl)-android:0.202610.0`.
- Glyphs: the Maptoolkit `summer.json` style serves glyphs from `https://fonts.maptoolkit.org/{fontstack}/{range}.pbf`.
  - Only the fonts the style uses exist there, i.e. **Noto Sans** and **Rosario** (Regular, Bold, Italic, …).
  - maplibre-compose's default `textFont` (`Open Sans Regular, Arial Unicode MS Regular`) returns **404**.
  - So every app `SymbolLayer` sets `textFont = const(listOf("Noto Sans Regular"))`.
- `MapOverlay.Default` includes `MaplibreLogo`. Supplying our own `overlay` drops it, and `ExpandingAttributionButton` must then be placed in our overlay.
- spatialk `LineString` **throws** for fewer than 2 positions.
- `LocationPermission.Granted` is a data class with an `accuracy` field; a coarse-only grant also counts as `Granted`.

## API cheat sheet

- `rememberMapState(baseStyle = BaseStyle.Uri(url), initialCameraPosition = CameraPosition(center = Position(longitude = …, latitude = …), zoom = …), content = { /* sources + layers */ })`
- `MaplibreMap(modifier, state, interactions = MapInteractions { callbacks { click { onUnhandled { …; ClickResult.Consume } } } }, overlay = { Box(Modifier.fillMaxSize()) { ExpandingAttributionButton(Modifier.align(Alignment.BottomEnd)) } })`
- `rememberGeoJsonSource(GeoJsonData.Features(featureCollection))`. Passing new data on recomposition rebuilds the source declaratively.
- spatialk:
  - `Feature(geometry, properties: JsonObject, id = null)`
  - `FeatureCollection(vararg features)` / `FeatureCollection(list)`
  - `Point(Position(longitude = …, latitude = …))`
  - `LineString(list or vararg of Position)`
  - `Position.longitude` / `Position.latitude`
- `SymbolLayer(id, source, iconImage = switch(feature["type"].asString(), case("TRAM", image(painter)), fallback = image(painter)), textField = format(span(feature["label"].asString())), textFont = const(listOf("Noto Sans Regular")), textColor = const(Color.White), textHaloColor = const(Color.Black), textHaloWidth = const(2.dp), textSize = const(12.sp), iconAllowOverlap = const(true), onClick = { features -> …; ClickResult.Consume })`
- Clicked feature property: `features.first().properties?.get("id")?.jsonPrimitive?.content`
- `LineLayer(id, source, color = const(Color), width = interpolate(linear(), zoom(), 10 to const(6.dp), 14 to const(10.dp), 18 to const(14.dp)))`
- Location:
  - `rememberLocationState(provider = rememberDefaultLocationProvider())`
  - `locationState.permission is LocationPermission.Granted`
  - `locationState.requestPermission()`
  - `locationState.lastLocation?.position`
- `LocationIndicatorLayer(id = "user_location", locationState = locationState)`
- Camera:
  - `mapState.animateCamera(CameraUpdate(center = position, zoom = 15.0))` (suspend)
  - `mapState.cameraPosition` (`center`, `zoom`)
  - `mapState.isCameraMoving`

## Full probe source that compiled

```kotlin
package probe

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.LocationIndicatorLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberLocationState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.overlay.ExpandingAttributionButton
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

@Composable
fun Probe(onVehicle: (String?) -> Unit, onMapClick: () -> Unit) {
    val points = FeatureCollection(
        Feature(
            Point(Position(longitude = 19.4560, latitude = 51.7592)),
            JsonObject(mapOf("id" to JsonPrimitive("tram-v1"), "label" to JsonPrimitive("10"), "type" to JsonPrimitive("TRAM"))),
        ),
    )
    val line = FeatureCollection(
        Feature(LineString(Position(19.45, 51.755), Position(19.46, 51.762)), JsonObject(emptyMap())),
    )
    val locationState = rememberLocationState(provider = rememberDefaultLocationProvider())
    val tram = painterResource(android.R.drawable.ic_menu_mylocation)
    val bus = painterResource(android.R.drawable.ic_menu_compass)
    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri("https://styles.maptoolkit.org/summer.json"),
        initialCameraPosition = CameraPosition(center = Position(longitude = 19.4560, latitude = 51.7592), zoom = 13.0),
    ) {
        val lineSource = rememberGeoJsonSource(GeoJsonData.Features(line))
        LineLayer(
            id = "route",
            source = lineSource,
            color = const(Color.Red),
            width = interpolate(linear(), zoom(), 10 to const(6.dp), 14 to const(10.dp), 18 to const(14.dp)),
        )
        val vehicles = rememberGeoJsonSource(GeoJsonData.Features(points))
        SymbolLayer(
            id = "vehicles",
            source = vehicles,
            iconImage = switch(feature["type"].asString(), case("TRAM", image(tram)), fallback = image(bus)),
            textField = format(span(feature["label"].asString())),
            textColor = const(Color.White),
            textHaloColor = const(Color.Black),
            textHaloWidth = const(2.dp),
            textSize = const(12.sp),
            iconAllowOverlap = const(true),
            onClick = { features ->
                onVehicle(features.firstOrNull()?.properties?.get("id")?.jsonPrimitive?.content)
                ClickResult.Consume
            },
        )
        if (locationState.permission is LocationPermission.Granted) {
            LocationIndicatorLayer(id = "user_location", locationState = locationState)
        }
    }
    MaplibreMap(
        modifier = Modifier.fillMaxSize(),
        state = mapState,
        interactions = MapInteractions { callbacks { click { onUnhandled { onMapClick(); ClickResult.Consume } } } },
        overlay = {
            Box(Modifier.fillMaxSize()) {
                ExpandingAttributionButton(Modifier.align(Alignment.BottomEnd))
            }
        },
    )
    suspend fun recenter() {
        locationState.lastLocation?.let { mapState.animateCamera(CameraUpdate(center = it.position, zoom = 15.0)) }
    }
    val unused: () -> Unit = { locationState.requestPermission() }
}
```
