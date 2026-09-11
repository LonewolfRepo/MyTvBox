package com.itv.blockbuster.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import coil.size.Dimension
import coil.size.Size
import coil.transform.Transformation
import kotlin.math.ln
import kotlin.math.max

/**
 * Content-aware crop for turning a portrait poster into a landscape banner image,
 * without any ML model - a fast, on-device, dependency-free heuristic instead of
 * something like MediaPipe face detection.
 *
 * The idea: slide a horizontal band (sized to match the destination's aspect
 * ratio) down the poster and score each candidate position by how visually
 * "busy" it is - the Shannon entropy of its luminance histogram. Empty sky, a
 * plain-color backdrop, or a large flat block of a single color score LOW
 * entropy; a face, varied color, texture, or a title/credits block score HIGH
 * entropy. We pick the band with the highest entropy, which in practice lands on
 * the actual subject of the poster far more reliably than a fixed "always skip
 * N% off the top" guess - and unlike a fixed guess, it adapts per-poster instead
 * of assuming every poster is laid out the same way.
 *
 * For speed, the search runs on a small downscaled copy of the bitmap (a full-res
 * poster is way more pixels than this analysis needs); the winning band position
 * is then applied to the FULL-resolution bitmap for the actual crop, so output
 * quality is unaffected. If the destination size isn't known yet (e.g. Coil calls
 * this before layout has resolved the target's pixel size) or the image doesn't
 * actually need any vertical cropping, it degrades gracefully to a small fixed
 * top-skip rather than failing.
 */
class EntropyCropTransformation(
    private val fallbackTopSkipFraction: Float = 0.07f
) : Transformation {

    override val cacheKey: String = "EntropyCropTransformation"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val targetAspect = targetAspectRatio(size)
            ?: return topSkipFallback(input, fallbackTopSkipFraction)

        // A band as wide as the source and exactly as tall as that aspect ratio
        // implies - this mirrors what ContentScale.Crop itself does (scale to
        // cover by width, crop the overflowing height), so the band we search
        // over matches what will actually end up on screen.
        val bandHeight = (input.width / targetAspect).toInt().coerceIn(1, input.height)
        if (bandHeight >= input.height) return input // no vertical crop needed at all
        val maxStartY = input.height - bandHeight

        // Analyze a small downscaled copy for speed - entropy scoring doesn't
        // need full resolution, just the rough distribution of light/dark/color.
        val analysisWidth = 80
        val analysisScale = analysisWidth.toFloat() / input.width
        val analysisHeight = max(1, (input.height * analysisScale).toInt())
        val analysisBandHeight = max(1, (bandHeight * analysisScale).toInt())
        val analysisMaxStartY = (analysisHeight - analysisBandHeight).coerceAtLeast(0)

        val analysisBitmap = Bitmap.createScaledBitmap(input, analysisWidth, analysisHeight, true)
        val pixels = IntArray(analysisWidth * analysisHeight)
        analysisBitmap.getPixels(pixels, 0, analysisWidth, 0, 0, analysisWidth, analysisHeight)
        analysisBitmap.recycle()

        // Step through a fixed number of candidate band positions (cheap, since
        // the analysis bitmap is tiny) and keep whichever scores highest.
        val steps = 24
        var bestScore = -1.0
        var bestStartYFraction = 0f
        for (i in 0..steps) {
            val startY = if (analysisMaxStartY == 0) 0 else (analysisMaxStartY * i / steps)
            val score = bandEntropy(pixels, analysisWidth, analysisHeight, startY, analysisBandHeight)
            if (score > bestScore) {
                bestScore = score
                bestStartYFraction = if (analysisMaxStartY == 0) 0f else startY.toFloat() / analysisMaxStartY
            }
        }

        val startY = (maxStartY * bestStartYFraction).toInt().coerceIn(0, maxStartY)
        return Bitmap.createBitmap(input, 0, startY, input.width, bandHeight)
    }

    /** Reads the destination's resolved pixel size, if Coil/Compose has one yet. */
    private fun targetAspectRatio(size: Size): Float? {
        val w = (size.width as? Dimension.Pixels)?.px
        val h = (size.height as? Dimension.Pixels)?.px
        if (w == null || h == null || w <= 0 || h <= 0) return null
        return w.toFloat() / h.toFloat()
    }

    /** Shannon entropy of the luminance histogram over one candidate band. */
    private fun bandEntropy(pixels: IntArray, width: Int, height: Int, startY: Int, bandHeight: Int): Double {
        val bins = 24
        val histogram = IntArray(bins)
        var count = 0
        val endY = (startY + bandHeight).coerceAtMost(height)
        for (y in startY until endY) {
            val rowStart = y * width
            for (x in 0 until width) {
                val pixel = pixels[rowStart + x]
                val luminance = (0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel))
                    .toInt()
                    .coerceIn(0, 255)
                histogram[luminance * bins / 256]++
                count++
            }
        }
        if (count == 0) return 0.0
        var entropy = 0.0
        for (binCount in histogram) {
            if (binCount == 0) continue
            val p = binCount.toDouble() / count
            entropy -= p * ln(p)
        }
        return entropy
    }

    private fun topSkipFallback(input: Bitmap, skipFraction: Float): Bitmap {
        val skipPx = (input.height * skipFraction).toInt().coerceIn(0, input.height - 1)
        val remainingHeight = (input.height - skipPx).coerceAtLeast(1)
        return Bitmap.createBitmap(input, 0, skipPx, input.width, remainingHeight)
    }
}
