package com.github.karczews.publictransportviewer.ui.home

import android.util.Log
import com.github.karczews.publictransportviewer.map.MapSdk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

sealed interface MapSdkInitState {
    data object Loading : MapSdkInitState

    data object Ready : MapSdkInitState

    data class Failed(val message: String) : MapSdkInitState
}

private const val TAG = "MapSdkInitializer"

/**
 * Runs each [MapSdk.initialize] at most once at a time, in [scope] rather than in the caller's coroutine: leaving the
 * Home tab or switching SDKs mid-init cancels the wait, not the init. A failed init is reported to [onFailure] (logged
 * by default) and forgotten, so the next [ensureInitialized] retries it.
 *
 * Not thread-safe; call it from the main thread (callers are composables).
 */
class MapSdkInitializer(
    private val scope: CoroutineScope,
    private val onFailure: (MapSdk, Throwable) -> Unit = { sdk, error ->
        Log.e(TAG, "Couldn't initialize map SDK '${sdk.id}'", error)
    },
    private val initialize: suspend (MapSdk) -> Unit,
) {
    private val inFlight = mutableMapOf<String, Deferred<Result<Unit>>>()
    private val ready = mutableSetOf<String>()

    fun isReady(id: String): Boolean = id in ready

    // Third-party SDK init must never crash the Home tab: any non-cancellation failure is
    // reported and converted to a Result so the UI can show a retry state.
    @Suppress("TooGenericExceptionCaught")
    fun ensureInitialized(sdk: MapSdk): Deferred<Result<Unit>> {
        inFlight[sdk.id]?.let {
            return it
        }
        // Store before starting: on an immediate dispatcher the body (and its failure cleanup)
        // could otherwise run before the entry exists, leaving the failure cached forever.
        lateinit var deferred: Deferred<Result<Unit>>
        deferred =
            scope.async(start = CoroutineStart.LAZY) {
                try {
                    initialize(sdk)
                    ready += sdk.id
                    Result.success(Unit)
                } catch (error: CancellationException) {
                    inFlight.remove(sdk.id, deferred)
                    throw error
                } catch (error: Throwable) {
                    inFlight.remove(sdk.id, deferred)
                    onFailure(sdk, error)
                    Result.failure(error)
                }
            }
        inFlight[sdk.id] = deferred
        deferred.start()
        return deferred
    }
}
