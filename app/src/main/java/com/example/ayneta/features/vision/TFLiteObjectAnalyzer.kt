package com.example.ayneta.features.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.ObjectDetector

class TFLiteObjectAnalyzer(private val context: Context) {

    data class Detection(
        val label: String,
        val confidence: Float,
        val boundingBox: RectF
    )

    private var objectDetector: ObjectDetector? = null

    private val confidenceThreshold = 0.4f

    companion object {
        private const val TAG = "TFLiteObjectAnalyzer"
        private const val MODEL_ASSET = "efficientdet_lite0.tflite"
        private const val INPUT_SIZE = 320
    }

    init {
        try {
            val baseOptions = BaseOptions.builder()
                .setNumThreads(4)
                .build()

            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setScoreThreshold(confidenceThreshold)
                .setMaxResults(25)
                .build()

            objectDetector = ObjectDetector.createFromFileAndOptions(
                context,
                MODEL_ASSET,
                options
            )

            Log.i(TAG, "EfficientDet-Lite0 loaded successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load EfficientDet-Lite0", e)
        }
    }

    fun analyze(bitmap: Bitmap): List<Detection> {

        val detector = objectDetector
            ?: return emptyList()

        val resized = Bitmap.createScaledBitmap(
            bitmap,
            INPUT_SIZE,
            INPUT_SIZE,
            true
        )

        val tensorImage = TensorImage.fromBitmap(resized)

        val results = detector.detect(tensorImage)

        if (resized !== bitmap) {
            resized.recycle()
        }

        val width = INPUT_SIZE.toFloat()
        val height = INPUT_SIZE.toFloat()

        val detections = ArrayList<Detection>(results.size)

        for (result in results) {

            val category = result.categories
                .firstOrNull()
                ?: continue

            val label = category.label

            if (label.isNullOrBlank()) {
                continue
            }

            val box = result.boundingBox

            val normalizedBox = RectF(
                box.left / width,
                box.top / height,
                box.right / width,
                box.bottom / height
            )

            detections.add(
                Detection(
                    label = label,
                    confidence = category.score,
                    boundingBox = normalizedBox
                )
            )
        }

        Log.d(
            TAG,
            "Returning ${detections.size} detections"
        )

        return detections
    }

    fun close() {
        objectDetector?.close()
        objectDetector = null
    }
}