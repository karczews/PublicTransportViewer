package com.github.karczews.publictransportviewer.map

import android.content.Context
import androidx.core.content.edit

/** Remembers which map SDK the toggle last selected. */
class MapSdkPreference(context: Context) {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun savedId(): String? = prefs.getString(KEY_ID, null)

    fun save(id: String) = prefs.edit { putString(KEY_ID, id) }

    companion object {
        const val FILE_NAME = "map_sdk"
        private const val KEY_ID = "map_sdk_id"
    }
}
