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
 * Order of operations:
 * 1. High-level gesture classification (flick / circle / short / long)
 * 2. Map special gestures to commands
 * 3. Preprocess path
 * 4. Geometric classifier (instant, fully offline)
 * 5. If geometric fails and ML Kit model is ready → ML Kit Digital Ink
 */
class StrokeRecognizer(context: Context) {

    companion object {
        private const val TAG = "StrokeRecognizer"
        private const val ML_KIT_TIMEOUT_MS = 800L
    }

    private val geometric = SimpleGeometricRecognizer()
    private val mlKit = MlKitInkRecognizer(context.applicationContext)

    val isMlKitReady: Boolean
        get() = mlKit.isReady

    fun recognize(
        rawPoints: List<Pair<Float, Float>>,
        isUppercase: Boolean = false
    ): RecognitionResult {
        if (rawPoints.size < 3) return RecognitionResult.None

        // 1. High-level gesture classification
        val analysis = GestureClassifier.analyze(rawPoints)
        Log.d(TAG, "Gesture: ${analysis.type} conf=${analysis.confidence} " +
                "len=${analysis.pathLength} size=${analysis.boundingSize}")

        // 2. Special commands
        when (analysis.type) {
            GestureType.FLICK_LEFT -> return RecognitionResult.Command(SpecialCommand.BACKSPACE)
            GestureType.FLICK_RIGHT -> return RecognitionResult.Command(SpecialCommand.CURSOR_RIGHT)
            GestureType.CIRCLE_CW -> return RecognitionResult.Command(SpecialCommand.UNDO)
            GestureType.CIRCLE_CCW -> return RecognitionResult.Command(SpecialCommand.SPACE)
            GestureType.UNKNOWN -> return RecognitionResult.None
            else -> { /* letter path */ }
        }

        // 3. Preprocess
        val processed = PathPreprocessor.process(rawPoints)
        if (processed.size < 4) {
            Log.d(TAG, "Path too short after preprocessing")
            return RecognitionResult.None
        }

        // 4. Geometric (always available offline)
        val geometricLetter = geometric.recognize(processed)
        if (geometricLetter != null) {
            val text = if (isUppercase) geometricLetter.uppercase() else geometricLetter.lowercase()
            Log.i(TAG, "Geometric → \"$text\"")
            return RecognitionResult.Text(text)
        }

        // 5. ML Kit fallback (if model downloaded)
        if (mlKit.isReady) {
            val mlText = runBlocking {
                withTimeoutOrNull(ML_KIT_TIMEOUT_MS) {
                    mlKit.recognize(rawPoints)
                }
            }
            if (!mlText.isNullOrBlank()) {
                val text = if (isUppercase) mlText.uppercase() else mlText.lowercase()
                Log.i(TAG, "ML Kit → \"$text\"")
                return RecognitionResult.Text(text)
            }
        } else {
            Log.d(TAG, "ML Kit model not ready yet – geometric only")
        }

        return RecognitionResult.None
    }

    fun close() {
        mlKit.close()
    }
}
