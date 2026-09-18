package com.example.ayneta.features.vision

import android.graphics.RectF

data class Detection(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF
)