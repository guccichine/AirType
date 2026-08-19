package com.airtype.keyboard.recognition

import android.util.Log
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI

/**
 * Offline geometric feature matcher for single characters (A–Z, 0–9).
 *
 * Designed for air-written strokes. Prefers high-confidence structural
 * matches and deliberately keeps the generic "i" / "1" rules very strict
 * so letters like h, b, d, k, r are not collapsed into them.
 */
class SimpleGeometricRecognizer {

    companion object {
        private const val TAG = "GeoRecognizer"
        private const val MIN_CONFIDENCE = 0.48f
    }

    fun recognize(normalizedPoints: List<Pair<Float, Float>>): String? {
        if (normalizedPoints.size < 4) return null
        val features = extractFeatures(normalizedPoints)
        val (letter, score) = matchFeatures(features)
        Log.d(TAG, "features corners=${features.cornerCount} aspect=${"%.2f".format(features.aspect)} " +
                "rot=${"%.2f".format(features.netRotation)} complexity=${"%.2f".format(features.pathComplexity)} " +
                "→ $letter ($score)")
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
        val endInLeft: Boolean,
        val midRightBulge: Boolean   // true when the path spends significant time on the right half
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
            // Slightly more sensitive corner detection than before
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
            endInLeft = end.first < -0.15f,
            midRightBulge = midRightBulge
        )
    }

    private fun matchFeatures(f: Features): Pair<String?, Float> {

        // ── Closed shapes ──────────────────────────────────────────────
        if (f.isClosed && abs(f.netRotation) in 0.45f..1.4f && f.cornerCount <= 4) {
            return "o" to 0.85f
        }
        if (f.isClosed && abs(f.netRotation) > 1.2f && f.cornerCount >= 3) {
            return "8" to 0.72f
        }

        // ── C / G-like open arcs ────────────────────────────────────────
        if (!f.isClosed && abs(f.netRotation) in 0.35f..0.95f && f.cornerCount <= 3) {
            return "c" to 0.78f
        }

        // ── V / U ──────────────────────────────────────────────────────
        if (!f.isClosed && f.cornerCount in 1..3) {
            if (f.startAngle > 0.4f && f.endAngle < -0.4f) return "v" to 0.75f
            if (abs(f.startAngle) > 1.0f && abs(f.endAngle) > 1.0f) return "u" to 0.70f
        }

        // ── L ──────────────────────────────────────────────────────────
        if (f.cornerCount in 1..2 && f.aspect > 0.55f) {
            if (f.startAngle < -0.35f && f.endInRight) return "l" to 0.80f
        }

        // ── H / B / D / K / R family (vertical stem + right-side structure) ──
        // These must come BEFORE the generic vertical "1"/"i" rules.
        if (f.midRightBulge && f.cornerCount >= 2) {
            // Tall with a clear right-side bulge → most likely h or b
            if (f.verticalDominance || f.aspect < 0.95f) {
                // More corners / higher complexity → b or k
                if (f.cornerCount >= 4 || f.pathComplexity > 2.8f) {
                    return "b" to 0.68f
                }
                // Moderate complexity ending lower → h
                if (f.endInBottom || f.startInTop) {
                    return "h" to 0.72f
                }
                // Ending on the right → r
                if (f.endInRight) {
                    return "r" to 0.65f
                }
                return "h" to 0.62f
            }
        }

        // Explicit "h" heuristic: vertical-ish, at least one corner, right bulge
        if (f.cornerCount in 2..5 && f.midRightBulge && f.aspect < 1.1f) {
            return "h" to 0.70f
        }

        // ── N / M ──────────────────────────────────────────────────────
        if (f.cornerCount >= 3 && (f.verticalDominance || f.aspect < 0.9f)) {
            return if (f.cornerCount >= 4 || f.pathComplexity > 3.0f) "m" to 0.66f else "n" to 0.68f
        }

        // ── S / Z ──────────────────────────────────────────────────────
        if (!f.isClosed && f.cornerCount in 2..4 && abs(f.netRotation) < 0.45f) {
            if (f.horizontalDominance) return "z" to 0.72f
            return "s" to 0.66f
        }
        if (f.cornerCount in 2..3 && f.horizontalDominance) {
            return "z" to 0.70f
        }

        // ── T ──────────────────────────────────────────────────────────
        if (f.cornerCount in 1..2 && f.startInTop && f.aspect > 0.7f) {
            return "t" to 0.62f
        }

        // ── X ──────────────────────────────────────────────────────────
        if (f.cornerCount >= 2 && abs(f.netRotation) < 0.3f && !f.isClosed && f.aspect in 0.6f..1.6f) {
            return "x" to 0.58f
        }

        // ── A ──────────────────────────────────────────────────────────
        if (f.cornerCount in 2..4 && f.startInTop && f.endInBottom) {
            return "a" to 0.60f
        }

        // ── E (many corners, vertical) ─────────────────────────────────
        if (f.cornerCount >= 3 && f.verticalDominance && f.pathComplexity > 2.2f) {
            return "e" to 0.58f
        }

        // ── Very strict "1" / "i" (almost pure vertical stroke) ────────
        // Only fire when there is virtually no horizontal structure.
        if (f.cornerCount == 0 && f.verticalDominance && !f.midRightBulge && f.pathComplexity < 0.9f) {
            // Extremely straight vertical line
            return "1" to 0.78f
        }
        if (f.cornerCount <= 1 && f.verticalDominance && !f.midRightBulge && f.aspect < 0.45f) {
            // Very skinny, almost no corners → i
            return "i" to 0.58f
        }

        // Horizontal dash-like → ignore (not a letter)
        if (f.cornerCount <= 1 && f.horizontalDominance) return null to 0f

        Log.d(TAG, "No strong match (corners=${f.cornerCount} aspect=${"%.2f".format(f.aspect)} " +
                "rot=${"%.2f".format(f.netRotation)} closed=${f.isClosed} rightBulge=${f.midRightBulge})")

        return null to 0f
    }
}
