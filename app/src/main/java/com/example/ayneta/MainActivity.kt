package com.example.ayneta

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import java.util.concurrent.Executors
import com.example.ayneta.features.vision.TFLiteObjectAnalyzer
import com.example.ayneta.features.vision.SpatialReasoner
import com.example.ayneta.features.vision.Detection
/**
 * A single detection that we want to draw on the camera preview.
 */
data class VisualDetection(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF,
    val imageWidth: Int,
    val imageHeight: Int
)

/**
 * Custom camera overlay.
 *
 * It receives object detections and draws:
 *
 *     ┌─────────────────┐
 *     │  BOOK 0.72      │
 *     │                 │
 *     │      BOOK       │
 *     │                 │
 *     └─────────────────┘
 */
class DetectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.GREEN
        isAntiAlias = true
    }

    private val labelBackgroundPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.GREEN
        isAntiAlias = true
    }

    private val labelPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.BLACK
        textSize = 42f
        isAntiAlias = true
    }

    @Volatile
    private var detections: List<VisualDetection> = emptyList()

    /**
     * Called whenever the AI produces a new detection result.
     */
    fun updateDetections(newDetections: List<VisualDetection>) {
        detections = newDetections
        postInvalidate()
    }

    /**
     * Remove boxes when there are no detections.
     */
    fun clearDetections() {
        detections = emptyList()
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val currentDetections = detections

        for (detection in currentDetections) {

            val imageWidth = detection.imageWidth.toFloat()
            val imageHeight = detection.imageHeight.toFloat()

            if (imageWidth <= 0f || imageHeight <= 0f) {
                continue
            }

            /*
             * Convert coordinates from the camera image
             * into coordinates of our overlay view.
             */
            val scaleX = width / imageWidth
            val scaleY = height / imageHeight

            val box = detection.boundingBox

            val left = box.left * scaleX
            val top = box.top * scaleY
            val right = box.right * scaleX
            val bottom = box.bottom * scaleY

            val screenBox = RectF(
                left,
                top,
                right,
                bottom
            )

            // Draw bounding box.
            canvas.drawRect(
                screenBox,
                boxPaint
            )

            // Example:
            // "book 0.72"
            val label = String.format(
                "%s %.2f",
                detection.label,
                detection.confidence
            )

            val textWidth = labelPaint.measureText(label)
            val textHeight = labelPaint.textSize

            // Put the label above the box.
            val labelLeft = screenBox.left
            val labelTop = (screenBox.top - textHeight - 12f)
                .coerceAtLeast(0f)

            val labelRight = labelLeft + textWidth + 24f
            val labelBottom = labelTop + textHeight + 16f

            canvas.drawRect(
                labelLeft,
                labelTop,
                labelRight,
                labelBottom,
                labelBackgroundPaint
            )

            canvas.drawText(
                label,
                labelLeft + 12f,
                labelBottom - 10f,
                labelPaint
            )
        }
    }
}


class MainActivity : ComponentActivity() {

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private lateinit var previewView: PreviewView
    private lateinit var detectionOverlay: DetectionOverlayView
    private lateinit var objectDetector: ObjectDetector

    private var isModelReady = false
    private var isProcessing = false

    // Application-level confidence threshold.
    private val minConfidence = 0.60f

    private val cameraPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                initializeApp()
            } else {
                Log.e("AYNETA", "Camera permission denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lateinit var analyzer: TFLiteObjectAnalyzer

// In onCreate():
        analyzer = TFLiteObjectAnalyzer(this)
        previewView = PreviewView(this)

        detectionOverlay = DetectionOverlayView(this)

        /*
         * Two layers:
         *
         * Layer 1 → camera preview
         * Layer 2 → AI bounding boxes
         */
        setContent {

            Box(
                modifier = Modifier.fillMaxSize()
            ) {

                AndroidView(
                    factory = {
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                AndroidView(
                    factory = {
                        detectionOverlay
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {

            initializeApp()

        } else {

            cameraPermissionLauncher.launch(
                Manifest.permission.CAMERA
            )
        }
    }

    private fun initializeApp() {

        Log.d(
            "AYNETA",
            "Initializing..."
        )

        setupObjectDetector()

        Handler(Looper.getMainLooper()).postDelayed({

            if (isModelReady) {

                startCamera()

            } else {

                initializeApp()
            }

        }, 2000)
    }

    private fun setupObjectDetector() {

        try {

            val baseOptions =
                BaseOptions.builder()
                    .setModelAssetPath(
                        "efficientdet_lite0.tflite"
                    )
                    .build()

            val options =
                ObjectDetector.ObjectDetectorOptions
                    .builder()
                    .setBaseOptions(baseOptions)

                    /*
                     * MediaPipe-level threshold.
                     *
                     * Detections below 0.4 are not returned
                     * by the detector.
                     */
                    .setScoreThreshold(0.4f)

                    .setRunningMode(
                        RunningMode.LIVE_STREAM
                    )

                    .setResultListener {
                            result: ObjectDetectorResult,
                            _ ->

                        handleResult(result)

                        isProcessing = false
                    }

                    .setErrorListener { error ->

                        Log.e(
                            "AYNETA",
                            "Detector Error: ${error.message}"
                        )

                        isProcessing = false
                    }

                    .build()

            objectDetector =
                ObjectDetector.createFromOptions(
                    this,
                    options
                )

            isModelReady = true

            Log.d(
                "AYNETA",
                "Model Ready!"
            )

        } catch (e: Exception) {

            Log.e(
                "AYNETA",
                "Model Failed",
                e
            )
        }
    }

    /**
     * Receives the AI result.
     *
     * This is where:
     *
     * EfficientDet
     *      ↓
     * detections
     *      ↓
     * confidence filtering
     *      ↓
     * bounding boxes
     *      ↓
     * screen overlay
     */
    private fun handleResult(
        result: ObjectDetectorResult
    ) {

        if (result.detections().isEmpty()) {

            detectionOverlay.clearDetections()

            Log.d(
                "AYNETA",
                "No detections"
            )

            return
        }

        val visualDetections =
            result.detections()
                .mapNotNull { detection ->

                    val category =
                        detection.categories()
                            .firstOrNull()
                            ?: return@mapNotNull null

                    val label =
                        category.categoryName()

                    val confidence =
                        category.score()

                    Log.d(
                        "AYNETA",
                        "DETECTION: " +
                                "label=$label " +
                                "score=$confidence"
                    )

                    /*
                     * Application-level filtering.
                     *
                     * Only reasonably confident detections
                     * are drawn on the screen.
                     */
                    if (confidence < minConfidence) {

                        Log.d(
                            "AYNETA",
                            "FILTERED: " +
                                    "$label " +
                                    "($confidence < $minConfidence)"
                        )

                        return@mapNotNull null
                    }

                    /*
                     * MediaPipe gives us the actual
                     * bounding rectangle of the object.
                     */
                    val boundingBox =
                        detection.boundingBox()
                    Log.d(
                        "AYNETA",
                        "BOX: left=${boundingBox.left}, " +
                                "top=${boundingBox.top}, " +
                                "right=${boundingBox.right}, " +
                                "bottom=${boundingBox.bottom}"
                    )

                    Log.d(
                        "AYNETA",
                        "IMAGE SIZE: " +
                                "${detectionImageWidth} x ${detectionImageHeight}"
                    )

                    Log.d(
                        "AYNETA",
                        "OVERLAY SIZE: " +
                                "${detectionOverlay.width} x ${detectionOverlay.height}"
                    )
                    VisualDetection(
                        label = label,
                        confidence = confidence,
                        boundingBox = boundingBox,
                        imageWidth = detectionImageWidth,
                        imageHeight = detectionImageHeight
                    )
                }
                .distinctBy { it.label }
                .take(5)

        /*
         * Send accepted detections to the visual overlay.
         */
        detectionOverlay.updateDetections(
            visualDetections
        )

        Log.d(
            "AYNETA",
            "Visible objects: " +
                    visualDetections.joinToString {
                        "${it.label} ${it.confidence}"
                    }
        )
    }

    /*
     * These values are updated from the actual camera frame.
     */
    private var detectionImageWidth = 0
    private var detectionImageHeight = 0

    private fun startCamera() {

        val future =
            ProcessCameraProvider.getInstance(this)

        future.addListener({

            try {

                val provider =
                    future.get()

                val preview =
                    Preview.Builder()
                        .build()
                        .also {

                            it.surfaceProvider =
                                previewView.surfaceProvider
                        }

                val analyzer =
                    ImageAnalysis.Builder()

                        .setBackpressureStrategy(
                            ImageAnalysis
                                .STRATEGY_KEEP_ONLY_LATEST
                        )

                        .setOutputImageFormat(
                            ImageAnalysis
                                .OUTPUT_IMAGE_FORMAT_RGBA_8888
                        )

                        .build()

                        .also {

                            it.setAnalyzer(
                                cameraExecutor
                            ) { image ->

                                processFrame(image)
                            }
                        }

                provider.unbindAll()

                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analyzer
                )

                Log.d(
                    "AYNETA",
                    "Camera Started"
                )

            } catch (e: Exception) {

                Log.e(
                    "AYNETA",
                    "Camera Failed",
                    e
                )
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processFrame(
        imageProxy: ImageProxy
    ) {

        if (!isModelReady || isProcessing) {

            imageProxy.close()
            return
        }

        isProcessing = true

        try {

            val bitmap =
                imageProxy.toBitmap()

            /*
             * Remember the dimensions of the exact
             * image given to EfficientDet.
             *
             * The bounding boxes returned by the model
             * use this coordinate system.
             */
            detectionImageWidth =
                bitmap.width

            detectionImageHeight =
                bitmap.height

            val mpImage =
                BitmapImageBuilder(
                    bitmap
                ).build()

            objectDetector.detectAsync(
                mpImage,
                System.currentTimeMillis()
            )

        } catch (e: Exception) {

            Log.e(
                "AYNETA",
                "Process Error",
                e
            )

            isProcessing = false

        } finally {

            imageProxy.close()
        }
    }

    override fun onDestroy() {

        super.onDestroy()

        cameraExecutor.shutdown()

        if (::objectDetector.isInitialized) {

            objectDetector.close()
        }
    }
}