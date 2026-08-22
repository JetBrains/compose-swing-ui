package org.jetbrains.compose.swing.components.selection

import javax.swing.DefaultListSelectionModel
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * Pins that [ListSelectionModel.selectExactly] leaves the model no longer adjusting even where the write it
 * makes throws partway through - every listener in this package filters an adjusting event out, so a flag
 * left standing would leave a caller unable to hear that widget again.
 */
class SelectionModelWriteTest {
    @Test
    fun aWriteThatThrowsStillLeavesTheModelNotAdjusting() {
        val model =
            object : DefaultListSelectionModel() {
                override fun addSelectionInterval(
                    index0: Int,
                    index1: Int,
                ) {
                    error("write failed")
                }
            }

        assertFailsWith<IllegalStateException> {
            model.selectExactly(standing = IntArray(0), selection = sortedSetOf(0, 1))
        }
        assertFalse(model.valueIsAdjusting, "a write that throws should still leave the model settled")
    }
}
