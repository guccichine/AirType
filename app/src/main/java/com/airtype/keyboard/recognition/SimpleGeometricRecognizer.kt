package com.airtype.keyboard.recognition

import android.util.Log
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI

/**
 * Offline geometric matcher – restored permissive rules so recognition
 * always produces output for typical air-written letters (like the first
 * working build), with improved h/b/r detection.
 */
class SimpleGeometricRecognizer {

    companion object {
        private const val TAG = "GeoRecognizer"
        private const val MIN_CONFIDENCE = 0.40f
    }

    fun recognize(normalizedPoints: List<Pair<Float, Float>>): String? {
        if (normalizedPoints.size < 4) return null
        val f = extractFeatures(normalizedPoints)
        val (letter, score) = matchFeatures(f)
        Log.d(TAG, "corners=${f.cornerCount} aspect=${"%.2f".format(f.aspect)} " +
                "rot=${"%.2f".format(f.netRotation)} right=${f.midRightBulge} → $letter ($score)")
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
        val endInRight: Boolean,
        val midRightBulge: Boolean
    )

    private fun extractFeatures(pts: List<Pair<Float, Float>>): Features {
        val start = pts.first()
        val end = pts.last()

        val startAngle = atan2(pts[1].second - start.second, pts[1].first - start.first)
        val endAngle = atan2(
            end.second - pts[pts.size - 2].second,
            end.first - pts[pts.size - 2].first
        )

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var rightHalfCount = 0
        for ((x, y) in pts) {
            minX = min(minX, x); maxX = max(maxX, x)
            minY = min(minY, y); maxY = max(maxY, y)
            if (x > 0.05f) rightHalfCount++
        }
        val w = (maxX - minX).coerceAtLeast(1e-5f)
        val h = (maxY - minY).coerceAtLeast(1e-5f)
        val aspect = w / h
        val midRightBulge = rightHalfCount > pts.size / 3

        var corners = 0
        var totalTurning = 0f
        var prevAngle = startAngle
        for (i in 2 until pts.size) {
            val a = atan2(pts[i].second - pts[i - 1].second, pts[i].first - pts[i - 1].first)
            var delta = a - prevAngle
            while (delta > PI) delta -= (2 * PI).toFloat()
            while (delta < -PI) delta += (2 * PI).toFloat()
            totalTurning += abs(delta)
            if (abs(delta) > 0.55f) corners++
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
            endInRight = end.first > 0.15f,
            midRightBulge = midRightBulge
        )
    }

    private fun matchFeatures(f: Features): Pair<String?, Float> {
        // Closed shapes
        if (f.isClosed && abs(f.netRotation) in 0.45f..1.4f && f.cornerCount <= 4) {
            return "o" to 0.85f
        }
        if (f.isClosed && abs(f.netRotation) > 1.2f && f.cornerCount >= 3) {
            return "8" to 0.72f
        }

        // Open arcs → c
        if (!f.isClosed && abs(f.netRotation) in 0.35f..0.95f && f.cornerCount <= 3) {
            return "c" to 0.78f
        }

        // V / U
        if (!f.isClosed && f.cornerCount in 1..3) {
            if (f.startAngle > 0.4f && f.endAngle < -0.4f) return "v" to 0.75f
            if (abs(f.startAngle) > 1.0f && abs(f.endAngle) > 1.0f) return "u" to 0.70f
        }

        // L
        if (f.cornerCount in 1..2 && f.aspect > 0.55f) {
            if (f.startAngle < -0.35f && f.endInRight) return "l" to 0.80f
        }

        // H / B / R – right-side structure (before generic vertical rules)
        if (f.midRightBulge && f.cornerCount >= 2 && f.aspect < 1.15f) {
            if (f.cornerCount >= 4 || f.pathComplexity > 2.8f) return "b" to 0.68f
            if (f.endInRight && !f.endInBottom) return "r" to 0.65f
            return "h" to 0.72f
        }
        if (f.cornerCount in 2..5 && f.midRightBulge && f.aspect < 1.1f) {
            return "h" to 0.68f
        }

        // N / M
        if (f.cornerCount >= 3 && (f.verticalDominance || f.aspect < 0.9f)) {
            return if (f.cornerCount >= 4 || f.pathComplexity > 3.0f) "m" to 0.66f else "n" to 0.68f
        }

        // S / Z
        if (!f.isClosed && f.cornerCount in 2..4 && abs(f.netRotation) < 0.45f) {
            if (f.horizontalDominance) return "z" to 0.72f
            return "s" to 0.66f
        }
        if (f.cornerCount in 2..3 && f.horizontalDominance) {
            return "z" to 0.70f
        }

        // T
        if (f.cornerCount in 1..2 && f.startInTop && f.aspect > 0.7f) {
            return "t" to 0.62f
        }

        // X
        if (f.cornerCount >= 2 && abs(f.netRotation) < 0.3f && !f.isClosed && f.aspect in 0.6f..1.6f) {
            return "x" to 0.58f
        }

        // A
        if (f.cornerCount in 2..4 && f.startInTop && f.endInBottom) {
            return "a" to 0.60f
        }

        // E
        if (f.cornerCount >= 3 && f.verticalDominance && f.pathComplexity > 2.2f) {
            return "e" to 0.58f
        }

        // Permissive vertical fallbacks (like original v1 – always produce output)
        if (f.verticalDominance && f.cornerCount <= 2) {
            return if (f.cornerCount == 0) "1" to 0.75f else "i" to 0.55f
        }
        if (f.cornerCount <= 1 && f.verticalDominance) {
            return "i" to 0.50f
        }

        // Any remaining stroke with some structure → best-effort letter
        if (f.cornerCount >= 1) {
            return "n" to 0.42f
        }
        if (f.horizontalDominance) {
            return null to 0f
        }

        // Last resort: treat as i so the user always gets feedback
        return "i" to 0.40f
    }
}
