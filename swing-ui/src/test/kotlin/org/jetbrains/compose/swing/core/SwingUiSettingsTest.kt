package org.jetbrains.compose.swing.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SwingUiSettingsTest {
    @Test
    fun `a scale is read from the property value`() {
        assertEquals(0f, motionDurationScaleOf("0").scaleFactor)
        assertEquals(0.5f, motionDurationScaleOf("0.5").scaleFactor)
        assertEquals(10f, motionDurationScaleOf("10").scaleFactor)
    }

    @Test
    fun `an unset property leaves motion at the speed it was written at`() {
        assertEquals(1f, motionDurationScaleOf(null).scaleFactor)
    }

    @Test
    fun `a value this setting does not accept reconfigures nothing`() {
        listOf("", "  ", "slow", "-1", "NaN", "Infinity").forEach { value ->
            assertEquals(1f, motionDurationScaleOf(value).scaleFactor, "\"$value\" should have been rejected")
        }
    }
}
