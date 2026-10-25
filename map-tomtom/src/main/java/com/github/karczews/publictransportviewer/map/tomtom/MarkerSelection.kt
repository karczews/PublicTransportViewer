package com.github.karczews.publictransportviewer.map.tomtom

internal enum class MarkerSelectionChange { SELECT, DESELECT }

/**
 * How a marker's own selection (which shows its balloon) must change to follow the view model.
 * Two-way: a vehicle can arrive already selected, e.g. after switching from another map SDK.
 */
internal fun markerSelectionChange(shouldBeSelected: Boolean, isMarkerSelected: Boolean): MarkerSelectionChange? =
    when {
        shouldBeSelected && !isMarkerSelected -> MarkerSelectionChange.SELECT
        !shouldBeSelected && isMarkerSelected -> MarkerSelectionChange.DESELECT
        else -> null
    }
