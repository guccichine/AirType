package com.airtype.keyboard.recognition

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Preprocesses raw absolute air-writing paths for recognition.
 *
 * Pipeline:
 * 1. Noise filtering (minimum inter-point distance)
 * 2. Smoothing (simple moving average)
 * 3. Resampling to a fixed number of points
 * 4. Normalization (translate to origin + scale to unit box)
 */
object PathPreprocessor {

    private const val MIN_DISTANCE = 0.008f
    private const val SMOOTH_WINDOW = 3
    private const val TARGET_POINT_COUNT = 64

    fun process(rawPoints: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        if (rawPoints.size < 3) return emptyList()

        val filtered = filterNoise(rawPoints)
        if (filtered.size < 3) return emptyList()

        val smoothed = smooth(filtered)
        val resampled = resample(smoothed, TARGET_POINT_COUNT)
        return normalize(resampled)
    }

    fun filterNoise(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        if (points.isEmpty()) return emptyList()
        val result = mutableListOf(points.first())
        for (i in 1 until points.size) {
            val prev = result.last()
            val curr = points[i]
            if (hypot(curr.first - prev.first, curr.second - prev.second) >= MIN_DISTANCE) {
                result.add(curr)
            }
        }
        return result
    }

    fun smooth(points: List<Pair<Float, Float>>, window: Int = SMOOTH_WINDOW): List<Pair<Float, Float>> {
        if (points.size <= window) return points
        val half = window / 2
        val result = mutableListOf<Pair<Float, Float>>()
        for (i in points.indices) {
            var sx = 0f
            var sy = 0f
            var count = 0
            for (j in max(0, i - half)..min(points.lastIndex, i + half)) {
                sx += points[j].first
                sy += points[j].second
                count++
            }
            result.add((sx / count) to (sy / count))
        }
        return result
    }

    fun resample(points: List<Pair<Float, Float>>, n: Int): List<Pair<Float, Float>> {
        if (points.size < 2 || n < 2) return points

        val distances = FloatArray(points.size)
        var total = 0f
        for (i in 1 until points.size) {
            val d = hypot(
                points[i].first - points[i - 1].first,
                points[i].second - points[i - 1].second
            )
            total += d
            distances[i] = total
        }
        if (total < 1e-6f) return listOf(points.first())

        val interval = total / (n - 1)
        val result = mutableListOf(points.first())
        var target = interval
        var i = 1

        while (result.size < n - 1 && i < points.size) {
            if (distances[i] >= target) {
                val prev = points[i - 1]
                val curr = points[i]
                val segLen = distances[i] - distances[i - 1]
                val t = if (segLen > 1e-6f) (target - distances[i - 1]) / segLen else 0f
                val x = prev.first + t * (curr.first - prev.first)
                val y = prev.second + t * (curr.second - prev.second)
                result.add(x to y)
                target += interval
            } else {
                i++
            }
        }
        result.add(points.last())
        return result
    }

    fun normalize(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        if (points.isEmpty()) return emptyList()

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for ((x, y) in points) {
            minX = min(minX, x)
            maxX = max(maxX, x)
            minY = min(minY, y)
            maxY = max(maxY, y)
        }

        val width = maxX - minX
        val height = maxY - minY
        val scale = max(width, height).coerceAtLeast(1e-6f)

        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f

        return points.map { ((it.first - cx) / scale) to ((it.second - cy) / scale) }
    }

    fun boundingSize(points: List<Pair<Float, Float>>): Float {
        if (points.isEmpty()) return 0f
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for ((x, y) in points) {
            minX = min(minX, x)
            maxX = max(maxX, x)
            minY = min(minY, y)
            maxY = max(maxY, y)
        }
        return max(maxX - minX, maxY - minY)
    }

    fun pathLength(points: List<Pair<Float, Float>>): Float {
        var len = 0f
        for (i in 1 until points.size) {
            len += hypot(
                points[i].first - points[i - 1].first,
                points[i].second - points[i - 1].second
            )
        }
        return len
    }
}
