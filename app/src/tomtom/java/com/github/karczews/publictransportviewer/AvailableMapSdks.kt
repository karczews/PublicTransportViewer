package com.github.karczews.publictransportviewer

import com.github.karczews.publictransportviewer.map.MapSdk
import com.github.karczews.publictransportviewer.map.tomtom.TomTomMapSdk

/** The map SDKs this build ships; the first one is the default. */
internal val availableMapSdks: List<MapSdk> = listOf(TomTomMapSdk())
