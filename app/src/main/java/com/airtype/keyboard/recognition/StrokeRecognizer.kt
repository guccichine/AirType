package com.airtype.keyboard.recognition

import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

sealed class RecognitionResult {
    data class Text(val text: String) : RecognitionResult()
    data class Command(val command: SpecialCommand) : RecognitionResult()
    object None : RecognitionResult()
}

enum class SpecialCommand {
    UNDO,
    SPACE,
    ENTER,
    BACKSPACE,
    CURSOR_LEFT,
    CURSOR_RIGHT,
    TOGGLE_SHIFT
}

/**
 * Restored reliable recognition path (matches early working behaviour):
 * 1. Classify gesture
 * 2. Only fire special commands for HIGH-confidence flicks/circles
 * 3. Geometric first (always works offline, like v1)
 * 4. ML Kit only if geometric fails and model is ready
 */
class StrokeRecognizer(context: Context) {

    companion object {
        private const val TAG = "StrokeRecognizer"
        private const val ML_KIT_TIMEOUT_MS = 700L
        private const val SPECIAL_GESTURE_MIN_CONF = 0.78f
    }

    private val geometric = SimpleGeometricRecognizer()
    private val mlKit = MlKitInkRecognizer(context.applicationContext)
    private val gesturePrefs = GesturePrefs(context.applicationContext)

    val isMlKitReady: Boolean
        get() = mlKit.isReady

    fun recognize(
        rawPoints: List<Pair<Float, Float>>,
        isUppercase: Boolean = false,
        preContext: String = ""
    ): RecognitionResult {
        if (rawPoints.size < 3) {
            Log.d(TAG, "Too few points: ${rawPoints.size}")
            return RecognitionResult.None
        }

        val analysis = GestureClassifier.analyze(rawPoints)
        Log.d(TAG, "Gesture=${analysis.type} conf=${analysis.confidence} " +
                "len=${analysis.pathLength} size=${analysis.boundingSize}")

        // Special commands only when classifier is confident
        val isSpecial = analysis.type == GestureType.FLICK_LEFT ||
                analysis.type == GestureType.FLICK_RIGHT ||
                analysis.type == GestureType.CIRCLE_CW ||
                analysis.type == GestureType.CIRCLE_CCW

        if (isSpecial && analysis.confidence >= SPECIAL_GESTURE_MIN_CONF) {
            val action = gesturePrefs.getAction(analysis.type)
            Log.i(TAG, "Special ${analysis.type} → $action")
            return RecognitionResult.Command(action)
        }

        // Preprocess for letter recognition
        val processed = PathPreprocessor.process(rawPoints)
        Log.d(TAG, "Processed points: ${processed.size} (raw=${rawPoints.size})")
        if (processed.size < 4) {
            Log.w(TAG, "Too few points after preprocess")
            return RecognitionResult.None
        }

        // 1) Geometric FIRST – this is what worked in v1
        val geoLetter = geometric.recognize(processed)
        if (geoLetter != null) {
            val text = if (isUppercase) geoLetter.uppercase() else geoLetter.lowercase()
            Log.i(TAG, "Geometric → \"$text\"")
            return RecognitionResult.Text(text)
        }

        // 2) ML Kit only as fallback when geometric has no match
        if (mlKit.isReady) {
            try {
                val mlText = runBlocking {
                    withTimeoutOrNull(ML_KIT_TIMEOUT_MS) {
                        mlKit.recognize(processed, preContext)
                    }
                }
                if (!mlText.isNullOrBlank()) {
                    val text = if (isUppercase) mlText.uppercase() else mlText.lowercase()
                    Log.i(TAG, "ML Kit → \"$text\"")
                    return RecognitionResult.Text(text)
                }
            } catch (e: Exception) {
                Log.e(TAG, "ML Kit error", e)
            }
        }

        Log.w(TAG, "No match from geometric or ML Kit")
        return RecognitionResult.None
    }

    fun close() {
        mlKit.close()
    }
}
