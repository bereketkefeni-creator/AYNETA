package com.example.ayneta.features.vision

import android.graphics.RectF

enum class HorizontalDirection {
    LEFT,
    CENTER,
    RIGHT
}

object SpatialReasoner {

    fun horizontalDirection(
        left: Float,
        right: Float,
        imageWidth: Float
    ): HorizontalDirection {

        require(imageWidth > 0f) {
            "Image width must be greater than zero"
        }

        val centerX = (left + right) / 2f
        val normalizedX = centerX / imageWidth

        return when {
            normalizedX < 0.33f -> HorizontalDirection.LEFT
            normalizedX < 0.66f -> HorizontalDirection.CENTER
            else -> HorizontalDirection.RIGHT
        }
    }

    fun horizontalDirection(
        box: RectF,
        imageWidth: Float
    ): HorizontalDirection {
        return horizontalDirection(
            box.left,
            box.right,
            imageWidth
        )
    }
}