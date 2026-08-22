package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A row or column keeps the working room a layout pass needs - the children it places, their extents and
 * their offsets - and hands the same room to the next pass. Each test here runs two passes over one
 * container and asserts the second is placed by what it declares, not by what the first one left behind.
 */
class LinearLayoutPassReuseTest {
    @Test
    fun aColumnThatLosesAChildPlacesOnlyTheOnesThatRemain() = runComposeSwingTest {
        var count by mutableStateOf(3)
        setContent {
            Column(modifier = containerModifier(CROSS_EXTENT, COLUMN_EXTENT)) {
                repeat(count) { SizedChild(it) }
            }
        }
        assertEquals(columnRows(0, CHILD_HEIGHT, 2 * CHILD_HEIGHT), childBounds(), "three children stack in order")

        count = 2
        awaitIdle()

        assertEquals(
            columnRows(0, CHILD_HEIGHT),
            childBounds(),
            "the children that remain keep their own places once the third leaves",
        )
    }

    @Test
    fun aColumnThatGainsAChildPlacesItAfterTheOthers() = runComposeSwingTest {
        var count by mutableStateOf(2)
        setContent {
            Column(modifier = containerModifier(CROSS_EXTENT, COLUMN_EXTENT)) {
                repeat(count) { SizedChild(it) }
            }
        }
        assertEquals(columnRows(0, CHILD_HEIGHT), childBounds(), "two children stack in order")

        count = 3
        awaitIdle()

        assertEquals(
            columnRows(0, CHILD_HEIGHT, 2 * CHILD_HEIGHT),
            childBounds(),
            "a child arriving takes the place after the ones already there",
        )
    }

    @Test
    fun aChildThatStopsBeingWeightedTakesTheExtentItPrefersAgain() = runComposeSwingTest {
        var weighted by mutableStateOf(true)
        setContent {
            Column(modifier = containerModifier(CROSS_EXTENT, COLUMN_EXTENT)) {
                SizedChild(0)
                SizedChild(1, if (weighted) SwingModifier.weight(1f) else SwingModifier)
            }
        }
        assertEquals(
            listOf(
                Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                Rectangle(0, CHILD_HEIGHT, CHILD_WIDTH, COLUMN_EXTENT - CHILD_HEIGHT),
            ),
            childBounds(),
            "a weighted child takes the height the column has left over",
        )

        weighted = false
        awaitIdle()

        assertEquals(
            columnRows(0, CHILD_HEIGHT),
            childBounds(),
            "dropping the weight must return the child to the extent it prefers, not leave it at its share",
        )
    }

    @Test
    fun anOffsetTheArrangementLeavesAloneIsZeroRatherThanThePreviousPassValue() = runComposeSwingTest {
        val arrangement = GatedArrangement()
        setContent {
            Column(modifier = containerModifier(CROSS_EXTENT, COLUMN_EXTENT), verticalArrangement = arrangement) {
                repeat(CHILD_COUNT) { SizedChild(it) }
            }
        }
        assertEquals(
            columnRows(0, STEP, 2 * STEP),
            childBounds(),
            "the arrangement places each child at its own step while it is writing offsets",
        )

        arrangement.writesOffsets = false
        onNodeWithTag(CONTAINER_TAG).fetch<JPanel>().doLayout()

        assertEquals(
            columnRows(0, 0, 0),
            childBounds(),
            "an offset this pass never wrote must be zero, not the offset the pass before it wrote",
        )
    }

    private companion object {
        const val CROSS_EXTENT = 100
        const val COLUMN_EXTENT = 300
    }
}

/** The gap [GatedArrangement] leaves between children while it is placing them. */
private const val STEP = 60

/**
 * An arrangement that places children at a fixed step until it is told to stop, and then writes no offset
 * at all - which is what a pass reusing the offsets of the one before it would fail to notice.
 */
private class GatedArrangement : Arrangement.Vertical {
    var writesOffsets: Boolean = true

    override fun arrange(
        totalSize: Int,
        sizes: IntArray,
        outPositions: IntArray,
    ) {
        if (!writesOffsets) return
        for (index in outPositions.indices) {
            outPositions[index] = index * STEP
        }
    }
}
