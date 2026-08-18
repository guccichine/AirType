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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Offline-capable ML Kit Digital Ink Recognition wrapper.
 */
class MlKitInkRecognizer(private val context: Context) {

    companion object {
        private const val TAG = "MlKitInk"
        private const val LANGUAGE_TAG = "en-US"
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

    suspend fun recognize(points: List<Pair<Float, Float>>): String? {
        if (!isReady || points.size < 4) return null
        val rec = recognizer ?: return null

        val ink = buildInk(points) ?: return null

        return suspendCoroutine { cont ->
            rec.recognize(ink)
                .addOnSuccessListener { result ->
                    val text = result.candidates.firstOrNull()?.text
                    Log.i(TAG, "ML Kit result: \"$text\"")
                    cont.resume(text)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "ML Kit recognition failed", e)
                    cont.resume(null)
                }
        }
    }

    private fun buildInk(points: List<Pair<Float, Float>>): Ink? {
        if (points.isEmpty()) return null

        val scale = 400f
        val offsetX = 200f
        val offsetY = 200f
        val now = System.currentTimeMillis()

        val strokeBuilder = Ink.Stroke.builder()
        points.forEachIndexed { index, (x, y) ->
            val px = x * scale + offsetX
            val py = -y * scale + offsetY
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
