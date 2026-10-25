package com.github.karczews.publictransportviewer.map

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.github.karczews.publictransportviewer.ui.home.HomeViewModel

/** A [MapSdk] with no map: its Home screen is an empty box tagged `fake_map_<id>`. */
class FakeMapSdk(
    override val id: String = "fake",
    override val displayName: String = "Fake",
    private val onInitialize: suspend () -> Unit = {},
) : MapSdk {

    override suspend fun initialize(context: Context) = onInitialize()

    @Composable
    override fun HomeScreen(viewModel: HomeViewModel, modifier: Modifier) {
        Box(modifier.testTag("fake_map_$id"))
    }
}
