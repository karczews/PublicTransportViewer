package com.github.karczews.publictransportviewer.map.tomtom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkerSelectionTest {

    @Test
    fun selectsMarkerWhenVehicleSelectedElsewhere() {
        // e.g. selected on another SDK's map, then this screen is shown with a fresh marker state
        assertEquals(MarkerSelectionChange.SELECT, markerSelectionChange(shouldBeSelected = true, isMarkerSelected = false))
    }

    @Test
    fun deselectsMarkerWhenSelectionCleared() {
        assertEquals(MarkerSelectionChange.DESELECT, markerSelectionChange(shouldBeSelected = false, isMarkerSelected = true))
    }

    @Test
    fun noChangeWhenAlreadyInSync() {
        assertNull(markerSelectionChange(shouldBeSelected = true, isMarkerSelected = true))
        assertNull(markerSelectionChange(shouldBeSelected = false, isMarkerSelected = false))
    }
}
