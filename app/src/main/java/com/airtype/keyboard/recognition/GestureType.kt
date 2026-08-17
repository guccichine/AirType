package com.airtype.keyboard.recognition

/**
 * High-level classification of a finished stroke before character recognition.
 */
enum class GestureType {
    /** Short, compact stroke → treat as a single letter or digit */
    SHORT_LETTER,

    /** Longer continuous path → potential multi-character / word (if recognizer supports) */
    LONG_WORD,

    /** Quick horizontal flick left */
    FLICK_LEFT,

    /** Quick horizontal flick right */
    FLICK_RIGHT,

    /** Approximate clockwise circle */
    CIRCLE_CW,

    /** Approximate counter-clockwise circle */
    CIRCLE_CCW,

    /** Degenerate / unrecognizable */
    UNKNOWN
}

/**
 * Result of geometric analysis of a stroke.
 */
data class GestureAnalysis(
    val type: GestureType,
    val confidence: Float,          // 0..1
    val pathLength: Float,
    val boundingSize: Float,
    val isClosed: Boolean           // start ≈ end → possible circle
)
