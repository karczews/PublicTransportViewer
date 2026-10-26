package com.github.karczews.publictransportviewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Golden-image assertions for on-device Compose screenshot tests.
 *
 * JVM screenshot tools (Paparazzi, Roborazzi, Compose Preview screenshots) cannot render real map content: both map
 * SDKs draw through native Surface/GL views (TomTom) or the MapLibre native renderer, which Robolectric/Layoutlib leave
 * blank. Screenshot tests for the Home tab therefore run on an emulator/device via `connectedAndroidTest` and compare
 * [ImageBitmap] captures against PNG goldens stored in `src/androidTest/assets/screenshots/`.
 *
 * Record mode (`-Pandroid.testInstrumentationRunnerArguments.recordScreenshots=true`) saves the capture to the app's
 * external files dir instead of asserting; pull it with adb and commit it as the new golden. Verify mode retries the
 * capture until it matches (map tiles stream in asynchronously, so the first frames after a cold start differ) and
 * fails with the actual/diff images saved to the external files dir when the timeout expires.
 */
object ScreenshotGolden {
    /**
     * Captures repeatedly until the screenshot matches the golden or [timeoutMs] expires.
     *
     * @param capture provides a fresh screenshot, e.g. `{ rule.onRoot().captureToImage() }`.
     * @param goldenAssetPath asset path relative to `src/androidTest/assets`, e.g.
     *   `"screenshots/maplibre_home_default.png"`.
     * @param allowedMismatchFraction 0.0 = pixel-perfect; 0.02 tolerates ~2% differing pixels.
     */
    fun awaitMatchesGolden(
        capture: () -> ImageBitmap,
        goldenAssetPath: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        pollMs: Long = DEFAULT_POLL_MS,
        allowedMismatchFraction: Double = DEFAULT_ALLOWED_MISMATCH_FRACTION,
    ) {
        if (isRecordMode()) {
            val recorded = awaitSettledCapture(capture)
            val out = saveToExternal(recorded, goldenFileName(goldenAssetPath), "recorded")
            recorded.recycle()
            Log.i(TAG, "Recorded screenshot for $goldenAssetPath -> $out. Pull with adb and commit.")
            return
        }
        val golden = loadGolden(goldenAssetPath)
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var attempts = 0
        var lastMismatch = FULL_MISMATCH
        var lastActual: Bitmap? = null
        var capturesFailed = 0
        while (SystemClock.uptimeMillis() < deadline) {
            val actual = tryCapture(capture)
            if (actual == null) {
                capturesFailed++
            } else {
                attempts++
                if (actual.width != golden.width || actual.height != golden.height) {
                    failSizeMismatch(actual, golden, goldenAssetPath)
                }
                lastActual?.recycle()
                lastActual = actual
                lastMismatch = mismatchFraction(golden, actual, allowedMismatchFraction)
                if (lastMismatch <= allowedMismatchFraction) {
                    Log.i(
                        TAG,
                        "Screenshot $goldenAssetPath matches after $attempts capture(s) " +
                            "(mismatch ${pct(lastMismatch)}).",
                    )
                    actual.recycle()
                    golden.recycle()
                    return
                }
            }
            SystemClock.sleep(pollMs)
        }
        val actual = lastActual
        if (actual == null) {
            golden.recycle()
            throw AssertionError(
                "Screenshot $goldenAssetPath could not be captured " +
                    "($capturesFailed failed capture(s)); no comparison was possible.",
            )
        }
        failStillDifferent(
            golden,
            actual,
            goldenAssetPath,
            attempts,
            lastMismatch,
            allowedMismatchFraction,
        )
    }

    fun isRecordMode(): Boolean = InstrumentationRegistry.getArguments().getString(RECORD_ARG) == "true"
}

private const val TAG = "ScreenshotGolden"
private const val RECORD_ARG = "recordScreenshots"

/** Default share of differing pixels tolerated (map tiles shift between runs). */
private const val DEFAULT_ALLOWED_MISMATCH_FRACTION = 0.02

private const val DEFAULT_TIMEOUT_MS = 30_000L
private const val DEFAULT_POLL_MS = 1_500L
private const val RECORD_TIMEOUT_MS = 45_000L
private const val PERCENT_FACTOR = 100.0
private const val PNG_QUALITY = 100

private fun freshCapture(capture: () -> ImageBitmap): Bitmap =
    capture().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)

/**
 * Captures once, returning null when the capture itself transiently fails (e.g. a forceRedraw timeout while the map
 * surface is busy). Any failure is retried by the caller's poll loop, so the broad catch is intentional: a single bad
 * frame must not fail the whole test.
 */
@Suppress("TooGenericExceptionCaught")
private fun tryCapture(capture: () -> ImageBitmap): Bitmap? =
    try {
        freshCapture(capture)
    } catch (e: Exception) {
        Log.w(TAG, "Screenshot capture failed (${e.message}); retrying.")
        null
    }

private fun pct(fraction: Double): String = "%.2f%%".format(fraction * PERCENT_FACTOR)

private fun goldenFileName(goldenAssetPath: String): String = File(goldenAssetPath).name

/**
 * Captures until two consecutive frames stop changing and the frame is not a blank, still-loading map. Falls back to
 * the last frame on timeout so recording never hangs (verify the saved image before committing it as golden).
 */
private fun awaitSettledCapture(
    capture: () -> ImageBitmap,
    timeoutMs: Long = RECORD_TIMEOUT_MS,
    pollMs: Long = DEFAULT_POLL_MS,
): Bitmap {
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    var previous: Bitmap? = null
    var settled: Bitmap? = null
    while (settled == null && SystemClock.uptimeMillis() < deadline) {
        val current = tryCapture(capture)
        if (current != null) {
            val prev = previous
            previous = current
            if (prev != null) {
                val drift = mismatchFraction(prev, current, STABLE_THRESHOLD_FRACTION)
                prev.recycle()
                if (drift <= STABLE_THRESHOLD_FRACTION && !isBlankCapture(current)) {
                    settled = current
                }
            }
        }
        SystemClock.sleep(pollMs)
    }
    val result = settled ?: previous ?: freshCapture(capture)
    if (settled == null) {
        Log.w(TAG, "Recording unsettled/blank capture after timeout; verify the image.")
        if (result !== previous) previous?.recycle()
    }
    return result
}

private fun loadGolden(goldenAssetPath: String): Bitmap {
    val testContext = InstrumentationRegistry.getInstrumentation().context
    val stream =
        try {
            testContext.assets.open(goldenAssetPath)
        } catch (e: IOException) {
            failMissingGolden(goldenAssetPath, e)
        }
    stream.use { input ->
        val bitmap = BitmapFactory.decodeStream(input)
        requireNotNull(bitmap) { "Could not decode golden $goldenAssetPath." }
        return bitmap.copy(Bitmap.Config.ARGB_8888, false)
    }
}

private fun saveToExternal(bitmap: Bitmap, fileName: String, suffix: String): String {
    val baseName = fileName.removeSuffix(".png")
    val dir =
        InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getExternalFilesDir(
                "screenshots",
            ) ?: throw IllegalStateException("No external files dir available.")
    val out = File(dir, "${baseName}_${suffix}.png")
    FileOutputStream(out).use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, stream) }
    return out.absolutePath
}

private fun failSizeMismatch(actual: Bitmap, golden: Bitmap, goldenAssetPath: String): Nothing {
    val out = saveToExternal(actual, goldenFileName(goldenAssetPath), "size-mismatch")
    actual.recycle()
    golden.recycle()
    throw AssertionError(
        "Screenshot size ${actual.width}x${actual.height} does not match golden " +
            "$goldenAssetPath (${golden.width}x${golden.height}). " +
            "Record goldens on the same emulator config as CI " +
            "(see screenshot-tests.yml). Actual saved to $out.",
    )
}

private fun failStillDifferent(
    golden: Bitmap,
    lastActual: Bitmap,
    goldenAssetPath: String,
    attempts: Int,
    lastMismatch: Double,
    allowedMismatchFraction: Double,
): Nothing {
    val actualOut = saveToExternal(lastActual, goldenFileName(goldenAssetPath), "actual")
    val diffOut =
        saveToExternal(
            diffImage(golden, lastActual),
            goldenFileName(goldenAssetPath),
            "diff",
        )
    lastActual.recycle()
    golden.recycle()
    throw AssertionError(
        "Screenshot $goldenAssetPath still differs after $attempts capture(s): " +
            "${pct(lastMismatch)} pixels mismatch (allowed ${pct(allowedMismatchFraction)}). " +
            "Actual: $actualOut Diff (red = changed): $diffOut",
    )
}

private fun failMissingGolden(goldenAssetPath: String, cause: IOException): Nothing {
    throw AssertionError(
        "Missing golden $goldenAssetPath. Record it first: " +
            "./gradlew :<module>:connectedDebugAndroidTest " +
            "-Pandroid.testInstrumentationRunnerArguments.recordScreenshots=true, " +
            "then adb-pull from the device external files dir and commit to " +
            "src/androidTest/assets/$goldenAssetPath.",
        cause,
    )
}
