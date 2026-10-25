package com.github.karczews.publictransportviewer.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.karczews.publictransportviewer.map.MapSdk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapSdkInitializerTest {

    private class TestSdk(override val id: String = "a") : MapSdk {
        override val displayName = "A"

        @Composable override fun HomeScreen(viewModel: HomeViewModel, modifier: Modifier) = Unit
    }

    private val sdk = TestSdk()

    @Test
    fun ensureInitialized_runsInitializeOnceForRepeatedCalls() = runTest {
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val initializer =
            MapSdkInitializer(backgroundScope) {
                calls++
                gate.await()
            }

        val first = initializer.ensureInitialized(sdk)
        val second = initializer.ensureInitialized(sdk)
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        val third = initializer.ensureInitialized(sdk)

        assertEquals(1, calls)
        assertSame(first, second)
        assertSame(first, third)
    }

    @Test
    fun ensureInitialized_survivesCallerCancellation() = runTest {
        val gate = CompletableDeferred<Unit>()
        val initializer = MapSdkInitializer(backgroundScope) { gate.await() }

        val waiter = launch { initializer.ensureInitialized(sdk).await() }
        runCurrent()
        waiter.cancel()
        gate.complete(Unit)
        runCurrent()

        assertTrue(initializer.isReady("a"))
    }

    @Test
    fun ensureInitialized_retriesAfterFailure() = runTest {
        var calls = 0
        val initializer =
            MapSdkInitializer(backgroundScope) {
                calls++
                if (calls == 1) throw IllegalStateException("boom")
            }

        val failed = initializer.ensureInitialized(sdk).await()

        assertTrue(failed.isFailure)
        assertEquals("boom", failed.exceptionOrNull()?.message)
        assertFalse(initializer.isReady("a"))

        val retried = initializer.ensureInitialized(sdk).await()

        assertTrue(retried.isSuccess)
        assertEquals(2, calls)
    }

    @Test
    fun isReady_falseBeforeInit_trueAfter() = runTest {
        val initializer = MapSdkInitializer(backgroundScope) {}

        assertFalse(initializer.isReady("a"))
        initializer.ensureInitialized(sdk).await()
        assertTrue(initializer.isReady("a"))
    }

    @Test
    fun ensureInitialized_retriesAfterSynchronousFailureOnImmediateDispatcher() = runTest {
        // An unconfined scope runs the init inline, before ensureInitialized has stored its
        // Deferred.
        var calls = 0
        val initializer =
            MapSdkInitializer(CoroutineScope(UnconfinedTestDispatcher(testScheduler))) {
                calls++
                if (calls == 1) throw IllegalStateException("boom")
            }

        assertTrue(initializer.ensureInitialized(sdk).await().isFailure)
        val retried = initializer.ensureInitialized(sdk).await()

        assertTrue(retried.isSuccess)
        assertEquals(2, calls)
    }

    @Test
    fun ensureInitialized_reportsFailureWithSdkAndThrowable() = runTest {
        val failure = IllegalStateException("boom")
        var reported: Pair<MapSdk, Throwable>? = null
        val initializer =
            MapSdkInitializer(
                scope = backgroundScope,
                initialize = { throw failure },
                onFailure = { failedSdk, error -> reported = failedSdk to error },
            )

        initializer.ensureInitialized(sdk).await()

        assertNotNull(reported)
        assertSame(sdk, reported!!.first)
        assertSame(failure, reported!!.second)
    }

    @Test
    fun ensureInitialized_rethrowsCancellationWithoutReportingFailure() = runTest {
        val cancellation = CancellationException("scope torn down")
        var reported: Throwable? = null
        val initializer =
            MapSdkInitializer(
                scope = backgroundScope,
                initialize = { throw cancellation },
                onFailure = { _, error -> reported = error },
            )

        var thrown: CancellationException? = null
        try {
            initializer.ensureInitialized(sdk).await()
            fail("expected CancellationException")
        } catch (e: CancellationException) {
            thrown = e
        }

        assertEquals("scope torn down", thrown?.message)
        assertNull(reported)
        assertFalse(initializer.isReady("a"))
    }
}
