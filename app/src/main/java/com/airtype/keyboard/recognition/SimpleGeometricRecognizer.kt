package com.airtype.keyboard.recognition

import android.util.Log
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI

/**
 * Expanded offline geometric template matcher for single characters (A–Z, 0–9).
 */
class SimpleGeometricRecognizer {

    companion object {
        private const val TAG = "GeoRecognizer"
        private const val MIN_CONFIDENCE = 0.45f
    }

    fun recognize(normalizedPoints: List<Pair<Float, Float>>): String? {
        if (normalizedPoints.size < 4) return null
        val features = extractFeatures(normalizedPoints)
        val (letter, score) = matchFeatures(features)
        return if (score >= MIN_CONFIDENCE) letter else null
    }

    private data class Features(
        val startAngle: Float,
        val endAngle: Float,
        val aspect: Float,
        val cornerCount: Int,
        val isClosed: Boolean,
        val netRotation: Float,
        val pathComplexity: Float,
        val horizontalDominance: Boolean,
        val verticalDominance: Boolean,
        val startInTop: Boolean,
        val endInBottom: Boolean,
        val endInRight: Boolean
    )

    private fun extractFeatures(pts: List<Pair<Float, Float>>): Features {
        val start = pts.first()
        val end = pts.last()

        val startAngle = atan2(pts[1].second - start.second, pts[1].first - start.first)
        val endAngle = atan2(end.second - pts[pts.size - 2].second, end.first - pts[pts.size - 2].first)

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for ((x, y) in pts) {
            minX = min(minX, x); maxX = max(maxX, x)
            minY = min(minY, y); maxY = max(maxY, y)
        }
        val w = (maxX - minX).coerceAtLeast(1e-5f)
        val h = (maxY - minY).coerceAtLeast(1e-5f)
        val aspect = w / h

        var corners = 0
        var totalTurning = 0f
        var prevAngle = startAngle
        for (i in 2 until pts.size) {
            val a = atan2(pts[i].second - pts[i - 1].second, pts[i].first - pts[i - 1].first)
            var delta = a - prevAngle
            while (delta > PI) delta -= (2 * PI).toFloat()
            while (delta < -PI) delta += (2 * PI).toFloat()
            totalTurning += abs(delta)
            if (abs(delta) > 0.65f) corners++
            prevAngle = a
        }

        val closedDist = hypot(end.first - start.first, end.second - start.second)
        val isClosed = closedDist < 0.28f

        var net = 0f
        prevAngle = startAngle
        for (i in 2 until pts.size) {
            val a = atan2(pts[i].second - pts[i - 1].second, pts[i].first - pts[i - 1].first)
            var delta = a - prevAngle
            while (delta > PI) delta -= (2 * PI).toFloat()
            while (delta < -PI) delta += (2 * PI).toFloat()
            net += delta
            prevAngle = a
        }
        val netRotation = net / (2 * PI).toFloat()

        return Features(
            startAngle = startAngle,
            endAngle = endAngle,
            aspect = aspect,
            cornerCount = corners,
            isClosed = isClosed,
            netRotation = netRotation,
            pathComplexity = totalTurning,
            horizontalDominance = aspect > 1.55f,
            verticalDominance = aspect < 0.65f,
            startInTop = start.second < -0.15f,
            endInBottom = end.second > 0.15f,
            endInRight = end.first > 0.15f
        )
    }

    private fun matchFeatures(f: Features): Pair<String?, Float> {
        if (f.isClosed && abs(f.netRotation) in 0.45f..1.4f && f.cornerCount <= 4) {
            return "o" to 0.82f
        }
        if (f.isClosed && abs(f.netRotation) > 1.2f && f.cornerCount >= 3) {
            return "8" to 0.70f
        }
        if (!f.isClosed && abs(f.netRotation) in 0.35f..0.95f && f.cornerCount <= 3) {
            return "c" to 0.75f
        }
        if (!f.isClosed && f.cornerCount in 1..3) {
            if (f.startAngle > 0.4f && f.endAngle < -0.4f) return "v" to 0.72f
            if (abs(f.startAngle) > 1.0f && abs(f.endAngle) > 1.0f) return "u" to 0.68f
        }
        if (f.cornerCount in 1..2 && f.aspect > 0.6f) {
            if (f.startAngle < -0.4f && f.endInRight) return "l" to 0.78f
        }
        if (f.verticalDominance && f.cornerCount <= 2) {
            return "1" to 0.80f
        }
        if (!f.isClosed && f.cornerCount in 2..4 && abs(f.netRotation) < 0.4f) {
            return "s" to 0.65f
        }
        if (f.cornerCount in 2..3 && f.horizontalDominance) {
            return "z" to 0.70f
        }
        if (f.cornerCount >= 3 && f.verticalDominance) {
            return if (f.cornerCount >= 4) "m" to 0.62f else "n" to 0.65f
        }
        if (f.cornerCount in 1..2 && f.startInTop) {
            return "t" to 0.60f
        }
        if (f.cornerCount >= 2 && abs(f.netRotation) < 0.3f && !f.isClosed) {
            return "x" to 0.55f
        }
        if (f.cornerCount in 2..4 && f.startInTop && f.endInBottom) {
            return "a" to 0.58f
        }
        if (f.cornerCount >= 3 && f.verticalDominance) {
            return "e" to 0.55f
        }
        if (f.cornerCount <= 1 && f.verticalDominance) return "i" to 0.50f
        if (f.cornerCount <= 1 && f.horizontalDominance) return null to 0f

        Log.d(TAG, "No strong match (corners=${f.cornerCount} aspect=${\"%.2f\".format(f.aspect)} " +
                "rot=${\"%.2f\".format(f.netRotation)} closed=${f.isClosed})")
        return null to 0f
    }
}
