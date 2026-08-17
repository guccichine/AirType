package com.airtype.keyboard.recognition

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.PI

/**
 * Classifies a finished absolute path into one of the high-level [GestureType]s
 * used by AirType for special commands vs letter recognition.
 */
object GestureClassifier {

    private const val SHORT_SIZE_THRESHOLD = 0.35f
    private const val LONG_SIZE_THRESHOLD = 0.90f
    private const val FLICK_ASPECT_RATIO = 3.0f
    private const val FLICK_MAX_DEVIATION = 0.25f
    private const val CIRCLE_CLOSURE_RATIO = 0.22f
    private const val CIRCLE_MIN_TURNS = 0.65f
    private const val MIN_POINTS_FOR_CIRCLE = 12

    fun analyze(rawPoints: List<Pair<Float, Float>>): GestureAnalysis {
        if (rawPoints.size < 3) {
            return GestureAnalysis(GestureType.UNKNOWN, 0f, 0f, 0f, false)
        }

        val size = PathPreprocessor.boundingSize(rawPoints)
        val length = PathPreprocessor.pathLength(rawPoints)
        val isClosed = isPathClosed(rawPoints, length)

        val flick = detectFlick(rawPoints, size)
        if (flick != null) {
            return GestureAnalysis(flick, 0.85f, length, size, false)
        }

        if (isClosed && rawPoints.size >= MIN_POINTS_FOR_CIRCLE) {
            val rotation = estimateNetRotation(rawPoints)
            if (abs(rotation) >= CIRCLE_MIN_TURNS) {
                val type = if (rotation > 0) GestureType.CIRCLE_CCW else GestureType.CIRCLE_CW
                return GestureAnalysis(type, 0.80f, length, size, true)
            }
        }

        val type = when {
            size < SHORT_SIZE_THRESHOLD -> GestureType.SHORT_LETTER
            size > LONG_SIZE_THRESHOLD -> GestureType.LONG_WORD
            else -> GestureType.SHORT_LETTER
        }

        return GestureAnalysis(type, 0.70f, length, size, isClosed)
    }

    private fun isPathClosed(points: List<Pair<Float, Float>>, pathLength: Float): Boolean {
        if (pathLength < 1e-4f) return false
        val start = points.first()
        val end = points.last()
        val dist = hypot(end.first - start.first, end.second - start.second)
        return dist / pathLength < CIRCLE_CLOSURE_RATIO
    }

    private fun detectFlick(
        points: List<Pair<Float, Float>>,
        size: Float
    ): GestureType? {
        if (points.size < 4) return null

        val start = points.first()
        val end = points.last()
        val dx = end.first - start.first
        val dy = end.second - start.second
        val netLen = hypot(dx, dy)
        if (netLen < 0.05f) return null

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for ((x, y) in points) {
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        val w = maxX - minX
        val h = maxY - minY
        if (w < 1e-5f || h < 1e-5f) return null

        val aspect = maxOf(w / h, h / w)
        if (aspect < FLICK_ASPECT_RATIO) return null

        if (abs(dx) < abs(dy) * 1.5f) return null

        val totalLen = PathPreprocessor.pathLength(points)
        if (totalLen > 0 && netLen / totalLen < (1f - FLICK_MAX_DEVIATION)) return null

        return if (dx < 0) GestureType.FLICK_LEFT else GestureType.FLICK_RIGHT
    }

    private fun estimateNetRotation(points: List<Pair<Float, Float>>): Float {
        if (points.size < 3) return 0f
        var totalAngle = 0.0
        var prevAngle = atan2(
            (points[1].second - points[0].second).toDouble(),
            (points[1].first - points[0].first).toDouble()
        )
        for (i in 2 until points.size) {
            val angle = atan2(
                (points[i].second - points[i - 1].second).toDouble(),
                (points[i].first - points[i - 1].first).toDouble()
            )
            var delta = angle - prevAngle
            while (delta > PI) delta -= 2 * PI
            while (delta < -PI) delta += 2 * PI
            totalAngle += delta
            prevAngle = angle
        }
        return (totalAngle / (2 * PI)).toFloat()
    }
}
