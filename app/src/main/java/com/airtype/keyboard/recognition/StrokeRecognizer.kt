package com.airtype.keyboard.recognition

import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Result returned by any stroke recognizer.
 */
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
 * Main offline stroke recognition entry point.
 *
 * Order:
 * 1. Gesture classification (flick / circle / letter)
 * 2. Map special gestures via user-configurable GesturePrefs
 * 3. Prefer ML Kit (with WritingArea + pre-context) when model is ready
 * 4. Fall back to geometric recognizer
 */
class StrokeRecognizer(context: Context) {

    companion object {
        private const val TAG = "StrokeRecognizer"
        private const val ML_KIT_TIMEOUT_MS = 900L
    }

    private val geometric = SimpleGeometricRecognizer()
    private val mlKit = MlKitInkRecognizer(context.applicationContext)
    private val gesturePrefs = GesturePrefs(context.applicationContext)

    val isMlKitReady: Boolean
        get() = mlKit.isReady

    /**
     * @param preContext text already present before the cursor (helps ML Kit)
     */
    fun recognize(
        rawPoints: List<Pair<Float, Float>>,
        isUppercase: Boolean = false,
        preContext: String = ""
    ): RecognitionResult {
        if (rawPoints.size < 3) return RecognitionResult.None

        // 1. High-level gesture classification
        val analysis = GestureClassifier.analyze(rawPoints)
        Log.d(TAG, "Gesture: ${analysis.type} conf=${analysis.confidence} " +
                "len=${analysis.pathLength} size=${analysis.boundingSize}")

        // 2. Special commands (user-configurable)
        when (analysis.type) {
            GestureType.FLICK_LEFT,
            GestureType.FLICK_RIGHT,
            GestureType.CIRCLE_CW,
            GestureType.CIRCLE_CCW -> {
                val action = gesturePrefs.getAction(analysis.type)
                Log.i(TAG, "Mapped ${analysis.type} → $action")
                return RecognitionResult.Command(action)
            }
            GestureType.UNKNOWN -> return RecognitionResult.None
            else -> { /* letter path */ }
        }

        // 3. Preprocess
        val processed = PathPreprocessor.process(rawPoints)
        if (processed.size < 4) {
            Log.d(TAG, "Path too short after preprocessing")
            return RecognitionResult.None
        }

        // 4. Prefer ML Kit when the model is available
        if (mlKit.isReady) {
            val mlText = runBlocking {
                withTimeoutOrNull(ML_KIT_TIMEOUT_MS) {
                    mlKit.recognize(rawPoints, preContext)
                }
            }
            if (!mlText.isNullOrBlank()) {
                val text = if (isUppercase) mlText.uppercase() else mlText.lowercase()
                Log.i(TAG, "ML Kit → \"$text\"")
                return RecognitionResult.Text(text)
            }
            Log.d(TAG, "ML Kit returned empty – falling back to geometric")
        } else {
            Log.d(TAG, "ML Kit model not ready yet – using geometric only")
        }

        // 5. Geometric fallback
        val geometricLetter = geometric.recognize(processed)
        if (geometricLetter != null) {
            val text = if (isUppercase) geometricLetter.uppercase() else geometricLetter.lowercase()
            Log.i(TAG, "Geometric → \"$text\"")
            return RecognitionResult.Text(text)
        }

        return RecognitionResult.None
    }

    fun close() {
        mlKit.close()
    }
}
