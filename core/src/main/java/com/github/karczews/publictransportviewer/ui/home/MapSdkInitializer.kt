package com.github.karczews.publictransportviewer.ui.home

import com.github.karczews.publictransportviewer.map.MapSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

sealed interface MapSdkInitState {
    data object Loading : MapSdkInitState
    data object Ready : MapSdkInitState
    data class Failed(val message: String) : MapSdkInitState
}

/**
 * Runs each [MapSdk.initialize] at most once at a time, in [scope] rather than in the caller's
 * coroutine: leaving the Home tab or switching SDKs mid-init cancels the wait, not the init.
 * A failed init is forgotten so the next [ensureInitialized] retries it.
 *
 * Not thread-safe; call it from the main thread (callers are composables).
 */
class MapSdkInitializer(
    private val scope: CoroutineScope,
    private val initialize: suspend (MapSdk) -> Unit,
) {
    private val inFlight = mutableMapOf<String, Deferred<Result<Unit>>>()
    private val ready = mutableSetOf<String>()

    fun isReady(id: String): Boolean = id in ready

    fun ensureInitialized(sdk: MapSdk): Deferred<Result<Unit>> =
        inFlight.getOrPut(sdk.id) {
            scope.async {
                runCatching { initialize(sdk) }
                    .onSuccess { ready += sdk.id }
                    .onFailure { inFlight.remove(sdk.id) }
            }
        }
}
