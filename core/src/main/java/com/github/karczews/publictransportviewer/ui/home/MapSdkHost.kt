package com.github.karczews.publictransportviewer.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.karczews.publictransportviewer.map.MapSdk
import org.koin.androidx.compose.koinViewModel

/**
 * The Home tab: waits for the chosen [MapSdk] to initialize, then hands the whole tab to its
 * [MapSdk.HomeScreen]. Initialization runs in [initializer], so leaving the tab doesn't cancel it.
 */
@Composable
fun MapSdkHost(
  mapSdks: List<MapSdk>,
  initializer: MapSdkInitializer,
  modifier: Modifier = Modifier,
) {
  val sdk = mapSdks.first()
  val initialState =
    if (initializer.isReady(sdk.id)) MapSdkInitState.Ready else MapSdkInitState.Loading
  val state by
    produceState(initialState, sdk.id) {
      if (value != MapSdkInitState.Ready) {
        value =
          initializer
            .ensureInitialized(sdk)
            .await()
            .fold(
              onSuccess = { MapSdkInitState.Ready },
              onFailure = { MapSdkInitState.Failed(it.message ?: it::class.simpleName.orEmpty()) },
            )
      }
    }

  Box(modifier.fillMaxSize()) {
    when (val current = state) {
      MapSdkInitState.Loading ->
        CircularProgressIndicator(
          Modifier.align(Alignment.Center).testTag("map_sdk_loading"),
        )
      is MapSdkInitState.Failed ->
        Text(
          "Couldn't start ${sdk.displayName}: ${current.message}",
          Modifier.align(Alignment.Center).padding(16.dp).testTag("map_sdk_error"),
        )
      MapSdkInitState.Ready ->
        key(sdk.id) {
          sdk.HomeScreen(koinViewModel<HomeViewModel>(), Modifier.fillMaxSize())
        }
    }
  }
}
