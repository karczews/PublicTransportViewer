package com.github.karczews.publictransportviewer

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry

/** Per-channel absolute difference above which a pixel counts as different. */
internal const val CHANNEL_TOLERANCE = 12

/** Share of drift between consecutive captures below which the map is considered settled. */
internal const val STABLE_THRESHOLD_FRACTION = 0.003

/**
 * Share of pixels allowed to equal the center pixel before a capture counts as a blank, still-loading map. Loaded maps
 * vary far more; overlays alone stay well under this.
 */
internal const val BLANK_THRESHOLD_FRACTION = 0.95

internal const val FULL_MISMATCH = 1.0

private const val TAG = "BitmapCompare"
private const val SHARED_PNG_QUALITY = 100

/**
 * Mirrors a screenshot PNG to shared storage (`Pictures/screenshots`), where it survives the APK uninstall that
 * `connectedDebugAndroidTest` performs after its run. The app-private external files dir is wiped by that uninstall, so
 * CI pulls failure artifacts from shared storage instead. Failures here must not mask the screenshot assertion being
 * reported, so they only log.
 */
/**
 * Mirrors a screenshot PNG to shared storage (`Pictures/screenshots`), where it survives the APK uninstall that
 * `connectedDebugAndroidTest` performs after its run. The app-private external files dir is wiped by that uninstall, so
 * CI pulls failure artifacts from shared storage instead. A row is only kept when the PNG write verifiably succeeds;
 * every failure path deletes the incomplete row (logging when even the delete fails) so artifact collection never picks
 * up a partial file. Failures here must not mask the screenshot assertion being reported, so they only log.
 */
internal fun saveToSharedScreenshots(bitmap: Bitmap, fileName: String, suffix: String) {
    val name = fileName.removeSuffix(".png") + "_$suffix.png"
    val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
    val uri = runCatching {
        requireNotNull(
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues(name)),
        )
    }
        .getOrElse {
            rethrowFatal(it)
            Log.w(TAG, "Could not create shared screenshot entry: $name.", it)
            return
        }
    val written = runCatching {
        resolver.openOutputStream(uri)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, SHARED_PNG_QUALITY, stream)
        } ?: false
    }
        .getOrElse {
            rethrowFatal(it)
            Log.w(TAG, "Could not write screenshot to shared storage: $name.", it)
            false
        }
    if (!written) {
        deleteSharedScreenshot(resolver, uri, name)
        return
    }
    Log.i(TAG, "Mirrored screenshot to shared storage: $name")
}

private fun contentValues(name: String) =
    ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/screenshots")
    }

/** VM-fatal errors must never be swallowed by diagnostic storage handling. */
private fun rethrowFatal(t: Throwable) {
    if (t is Error) throw t
}

private fun deleteSharedScreenshot(resolver: ContentResolver, uri: Uri, name: String) {
    runCatching { resolver.delete(uri, null, null) }
        .onSuccess { deleted ->
            if (deleted == 0) Log.w(TAG, "Shared screenshot row had nothing to delete: $name.")
        }
        .onFailure {
            rethrowFatal(it)
            Log.w(TAG, "Could not delete incomplete shared screenshot: $name.", it)
        }
}

/** A still-loading map is a near-uniform background; loaded maps vary far more. */
internal fun isBlankCapture(bitmap: Bitmap): Boolean {
    val center = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
    var other = 0L
    val total = bitmap.width.toLong() * bitmap.height
    val limit = (total * (FULL_MISMATCH - BLANK_THRESHOLD_FRACTION)).toLong()
    for (y in 0 until bitmap.height) {
        for (x in 0 until bitmap.width) {
            if (!pixelsMatch(center, bitmap.getPixel(x, y))) {
                other++
                if (other > limit) return false
            }
        }
    }
    return true
}

/**
 * Share of differing pixels. Aborts early once [allowed] is exceeded, so cold-start blank maps fail fast per iteration
 * instead of scanning all 2.6M pixels every poll.
 */
internal fun mismatchFraction(expected: Bitmap, actual: Bitmap, allowed: Double): Double {
    var different = 0L
    val total = expected.width.toLong() * expected.height
    val limit = (allowed * total).toLong()
    for (y in 0 until expected.height) {
        for (x in 0 until expected.width) {
            if (!pixelsMatch(expected.getPixel(x, y), actual.getPixel(x, y))) {
                different++
                if (different > limit && allowed < FULL_MISMATCH) {
                    return different.toDouble() / total
                }
            }
        }
    }
    return different.toDouble() / total
}

internal fun pixelsMatch(a: Int, b: Int): Boolean =
    kotlin.math.abs(Color.red(a) - Color.red(b)) <= CHANNEL_TOLERANCE &&
        kotlin.math.abs(Color.green(a) - Color.green(b)) <= CHANNEL_TOLERANCE &&
        kotlin.math.abs(Color.blue(a) - Color.blue(b)) <= CHANNEL_TOLERANCE &&
        kotlin.math.abs(Color.alpha(a) - Color.alpha(b)) <= CHANNEL_TOLERANCE

internal fun diffImage(expected: Bitmap, actual: Bitmap): Bitmap {
    val diff = Bitmap.createBitmap(expected.width, expected.height, Bitmap.Config.ARGB_8888)
    for (y in 0 until expected.height) {
        for (x in 0 until expected.width) {
            val e = expected.getPixel(x, y)
            diff.setPixel(x, y, if (pixelsMatch(e, actual.getPixel(x, y))) e else Color.RED)
        }
    }
    return diff
}
