package com.github.karczews.publictransportviewer.map

/** The SDK saved by the toggle, or the build's default (first) one when it isn't shipped here. */
fun resolveMapSdk(available: List<MapSdk>, savedId: String?): MapSdk =
    available.firstOrNull { it.id == savedId } ?: available.first()
