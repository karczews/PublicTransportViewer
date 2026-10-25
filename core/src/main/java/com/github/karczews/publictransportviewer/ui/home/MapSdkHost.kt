package com.github.karczews.publictransportviewer.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.karczews.publictransportviewer.map.MapSdk
import com.github.karczews.publictransportviewer.map.MapSdkPreference
import com.github.karczews.publictransportviewer.map.resolveMapSdk
import org.koin.androidx.compose.koinViewModel

/**
 * The Home tab: waits for the chosen [MapSdk] to initialize, then hands the whole tab to its
 * [MapSdk.HomeScreen]. Initialization runs in [initializer], so leaving the tab doesn't cancel it.
 * When the build ships more than one SDK, a toggle on top switches between them.
 */
@Composable
fun MapSdkHost(
    mapSdks: List<MapSdk>,
    initializer: MapSdkInitializer,
    modifier: Modifier = Modifier,
) {
    val appContext = LocalContext.current.applicationContext
    val preference = remember { MapSdkPreference(appContext) }
    var selectedId by rememberSaveable { mutableStateOf(preference.savedId()) }
    val sdk = resolveMapSdk(mapSdks, selectedId)

    Box(modifier.fillMaxSize()) {
        key(sdk.id) {
            MapSdkContent(sdk, initializer)
        }

        if (mapSdks.size > 1) {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
                    .testTag("map_sdk_toggle"),
            ) {
                mapSdks.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option.id == sdk.id,
                        onClick = {
                            selectedId = option.id
                            preference.save(option.id)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = mapSdks.size),
                        // Opaque, so the unselected label stays readable over the map.
                        colors = SegmentedButtonDefaults.colors(
                            inactiveContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                        modifier = Modifier.testTag("map_sdk_option_${option.id}"),
                    ) {
                        Text(option.displayName)
                    }
                }
            }
        }
    }
}

@Composable
private fun MapSdkContent(sdk: MapSdk, initializer: MapSdkInitializer) {
    val initialState = if (initializer.isReady(sdk.id)) MapSdkInitState.Ready else MapSdkInitState.Loading
    val state by produceState(initialState, sdk.id) {
        if (value != MapSdkInitState.Ready) {
            value = initializer.ensureInitialized(sdk).await().fold(
                onSuccess = { MapSdkInitState.Ready },
                onFailure = { MapSdkInitState.Failed(it.message ?: it::class.simpleName.orEmpty()) },
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (val current = state) {
            MapSdkInitState.Loading -> CircularProgressIndicator(
                Modifier.align(Alignment.Center).testTag("map_sdk_loading"),
            )
            is MapSdkInitState.Failed -> Text(
                "Couldn't start ${sdk.displayName}: ${current.message}",
                Modifier.align(Alignment.Center).padding(16.dp).testTag("map_sdk_error"),
            )
            MapSdkInitState.Ready -> sdk.HomeScreen(koinViewModel<HomeViewModel>(), Modifier.fillMaxSize())
        }
    }
}
