package com.airtype.keyboard.recognition

import android.content.Context
import android.util.Log
import com.google.mlkit.common.MlKitException
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Offline-capable ML Kit Digital Ink Recognition wrapper.
 *
 * Improvements over the first version:
 * - WritingArea is supplied so the model understands relative letter size
 * - Optional pre-context (previous text) for better disambiguation
 */
class MlKitInkRecognizer(private val context: Context) {

    companion object {
        private const val TAG = "MlKitInk"
        private const val LANGUAGE_TAG = "en-US"
        // Normalized writing area used for single-letter air strokes
        private const val WRITE_AREA_WIDTH = 1.2f
        private const val WRITE_AREA_HEIGHT = 1.4f
    }

    private var recognizer: DigitalInkRecognizer? = null
    private val modelReady = AtomicBoolean(false)
    private var model: DigitalInkRecognitionModel? = null

    init {
        try {
            val identifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(LANGUAGE_TAG)
            if (identifier != null) {
                model = DigitalInkRecognitionModel.builder(identifier).build()
                checkAndDownloadModel()
            } else {
                Log.e(TAG, "No model identifier for $LANGUAGE_TAG")
            }
        } catch (e: MlKitException) {
            Log.e(TAG, "Failed to create model identifier", e)
        }
    }

    private fun checkAndDownloadModel() {
        val m = model ?: return
        val remoteModelManager = RemoteModelManager.getInstance()

        remoteModelManager.isModelDownloaded(m)
            .addOnSuccessListener { downloaded ->
                if (downloaded) {
                    Log.i(TAG, "ML Kit model already downloaded")
                    createRecognizer(m)
                } else {
                    Log.i(TAG, "Downloading ML Kit Digital Ink model…")
                    remoteModelManager.download(m, DownloadConditions.Builder().build())
                        .addOnSuccessListener {
                            Log.i(TAG, "ML Kit model download complete")
                            createRecognizer(m)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "ML Kit model download failed", e)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to check model download status", e)
            }
    }

    private fun createRecognizer(m: DigitalInkRecognitionModel) {
        recognizer = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(m).build()
        )
        modelReady.set(true)
        Log.i(TAG, "ML Kit recognizer ready")
    }

    val isReady: Boolean
        get() = modelReady.get() && recognizer != null

    /**
     * @param points normalized stroke points (roughly -0.5..0.5 range after PathPreprocessor)
     * @param preContext up to 20 characters of text that already appear before the cursor
     */
    suspend fun recognize(
        points: List<Pair<Float, Float>>,
        preContext: String = ""
    ): String? {
        if (!isReady || points.size < 4) return null
        val rec = recognizer ?: return null

        val ink = buildInk(points) ?: return null

        val contextBuilder = RecognitionContext.builder()
            .setWritingArea(WritingArea(WRITE_AREA_WIDTH, WRITE_AREA_HEIGHT))
        if (preContext.isNotBlank()) {
            // ML Kit only uses the last 20 characters
            contextBuilder.setPreContext(preContext.takeLast(20))
        }
        val recognitionContext = contextBuilder.build()

        return suspendCoroutine { cont ->
            rec.recognize(ink, recognitionContext)
                .addOnSuccessListener { result ->
                    val candidates = result.candidates
                    val top = candidates.firstOrNull()?.text
                    Log.i(TAG, "ML Kit top: \"$top\" (candidates=${candidates.map { it.text }})")
                    // Prefer a single character when the user is writing letter-by-letter
                    val best = candidates
                        .map { it.text.trim() }
                        .firstOrNull { it.length == 1 && it[0].isLetterOrDigit() }
                        ?: top?.trim()
                    cont.resume(best)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "ML Kit recognition failed", e)
                    cont.resume(null)
                }
        }
    }

    private fun buildInk(points: List<Pair<Float, Float>>): Ink? {
        if (points.isEmpty()) return null

        // Map normalized points into the WritingArea coordinate space
        val scaleX = WRITE_AREA_WIDTH * 0.85f
        val scaleY = WRITE_AREA_HEIGHT * 0.85f
        val offsetX = WRITE_AREA_WIDTH / 2f
        val offsetY = WRITE_AREA_HEIGHT / 2f
        val now = System.currentTimeMillis()

        val strokeBuilder = Ink.Stroke.builder()
        points.forEachIndexed { index, (x, y) ->
            val px = x * scaleX + offsetX
            val py = -y * scaleY + offsetY   // flip Y so up is negative in model space if needed
            val t = now + index * 16L
            strokeBuilder.addPoint(Ink.Point.create(px, py, t))
        }

        return Ink.builder()
            .addStroke(strokeBuilder.build())
            .build()
    }

    fun close() {
        recognizer?.close()
        recognizer = null
        modelReady.set(false)
    }
}
