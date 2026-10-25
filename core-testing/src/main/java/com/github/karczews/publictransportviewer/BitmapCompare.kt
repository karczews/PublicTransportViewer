package com.github.karczews.publictransportviewer

import android.graphics.Bitmap
import android.graphics.Color

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
