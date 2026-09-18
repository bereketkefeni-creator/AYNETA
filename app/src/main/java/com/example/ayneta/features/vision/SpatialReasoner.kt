package com.example.ayneta.features.vision

import android.graphics.RectF

enum class HorizontalDirection {
    LEFT,
    CENTER,
    RIGHT
}

object SpatialReasoner {

    /**
     * Determines the horizontal direction based on raw float bounds.
     * This version is testable in pure JVM unit tests.
     */
    fun horizontalDirection(left: Float, right: Float): HorizontalDirection {
        val centerX = (left + right) / 2f

        return when {
            centerX < 0.33f -> HorizontalDirection.LEFT
            centerX < 0.66f -> HorizontalDirection.CENTER
            else -> HorizontalDirection.RIGHT
        }
    }

    /**
     * Overload for Android's RectF.
     * Note: In local JVM unit tests, RectF returns 0 for all properties.
     */
    fun horizontalDirection(box: RectF): HorizontalDirection {
        return horizontalDirection(box.left, box.right)
    }
}