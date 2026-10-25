package com.github.karczews.publictransportviewer.map

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.karczews.publictransportviewer.ui.home.HomeViewModel

/**
 * One map SDK the app can be built with. Each SDK module owns the whole Home tab: map, markers,
 * location, permission flow and controls. Everything outside the Home tab is shared.
 */
interface MapSdk {
  /** Stable identifier, persisted by the SDK toggle: "tomtom", "maplibre". */
  val id: String

  /** Label shown in the SDK toggle. */
  val displayName: String

  /** Idempotent SDK start-up. Awaited by the Home-tab host before the first [HomeScreen]. */
  suspend fun initialize(context: Context) {}

  /** The whole Home tab for this SDK. */
  @Composable fun HomeScreen(viewModel: HomeViewModel, modifier: Modifier)
}
