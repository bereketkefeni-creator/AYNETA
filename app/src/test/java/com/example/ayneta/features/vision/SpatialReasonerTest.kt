package com.example.ayneta.features.vision

import org.junit.Assert.assertEquals
import org.junit.Test

class SpatialReasonerTest {

    @Test
    fun objectOnLeft_returnsLeft() {
        // Using raw floats to avoid Android SDK dependency in unit tests
        val result = SpatialReasoner.horizontalDirection(0.10f, 0.30f)
        assertEquals(HorizontalDirection.LEFT, result)
    }

    @Test
    fun objectInCenter_returnsCenter() {
        val result = SpatialReasoner.horizontalDirection(0.40f, 0.60f)
        assertEquals(HorizontalDirection.CENTER, result)
    }

    @Test
    fun objectOnRight_returnsRight() {
        val result = SpatialReasoner.horizontalDirection(0.70f, 0.90f)
        assertEquals(HorizontalDirection.RIGHT, result)
    }
}