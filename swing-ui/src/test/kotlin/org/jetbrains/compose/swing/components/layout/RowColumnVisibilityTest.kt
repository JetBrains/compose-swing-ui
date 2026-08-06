package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.visible
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A child hidden with `SwingModifier.visible(false)` stays attached to its row or column, and the layout
 * pass steps over it: it takes no space along the axis and no gap of its own, so the children around it
 * close up and the container asks its own parent for less. Revealing it gives both back.
 */
class RowColumnVisibilityTest {
    @Test
    fun anInvisibleChildTakesNoSpaceAndNoGapOfItsOwn() = runComposeSwingTest {
        setContent {
            Column(
                modifier = containerModifier(CROSS_EXTENT, MAIN_EXTENT),
                verticalArrangement = Arrangement.spacedBy(GAP),
            ) {
                SizedChild(0)
                SizedChild(1, SwingModifier.visible(false))
                SizedChild(2)
            }
        }

        assertEquals(CHILD_COUNT, childCount(), "a hidden child stays attached to the column that declares it")
        assertEquals(
            columnRows(0, CHILD_HEIGHT + GAP),
            visibleChildBounds(),
            "an invisible child takes no space and no gap of its own, so the children around it close up",
        )
    }

    @Test
    fun aColumnAsksForNeitherSpaceNorGapForAnInvisibleChild() = runComposeSwingTest {
        setContent {
            Column(
                modifier = SwingModifier.testTag(CONTAINER_TAG),
                verticalArrangement = Arrangement.spacedBy(GAP),
            ) {
                SizedChild(0)
                SizedChild(1, SwingModifier.visible(false))
                SizedChild(2)
            }
        }

        assertEquals(
            Dimension(CHILD_WIDTH, 2 * CHILD_HEIGHT + GAP),
            containerPreferredSize(),
            "an invisible child takes no space and no gap of its own, so the column asks for neither",
        )
    }

    @Test
    fun anInvisibleChildTakesNoSpaceAndNoGapInARowEither() = runComposeSwingTest {
        setContent {
            Row(
                modifier = containerModifier(MAIN_EXTENT, CROSS_EXTENT),
                horizontalArrangement = Arrangement.spacedBy(GAP),
            ) {
                SizedChild(0)
                SizedChild(1, SwingModifier.visible(false))
                SizedChild(2)
            }
        }

        assertEquals(
            rowCells(0, CHILD_WIDTH + GAP),
            visibleChildBounds(),
            "an invisible child takes no space and no gap of its own along either axis",
        )
    }

    @Test
    fun revealingAChildGivesItBackItsSpaceAndItsGap() = runComposeSwingTest {
        var shown by mutableStateOf(false)
        setContent {
            Column(
                modifier = containerModifier(CROSS_EXTENT, MAIN_EXTENT),
                verticalArrangement = Arrangement.spacedBy(GAP),
            ) {
                SizedChild(0)
                SizedChild(1, SwingModifier.visible(shown))
                SizedChild(2)
            }
        }

        assertEquals(
            columnRows(0, CHILD_HEIGHT + GAP),
            visibleChildBounds(),
            "the column should start closed up over the child it hides",
        )

        shown = true
        awaitIdle()

        assertEquals(
            columnRows(0, CHILD_HEIGHT + GAP, 2 * (CHILD_HEIGHT + GAP)),
            visibleChildBounds(),
            "a child shown again takes its space and its gap back, moving the children after it along",
        )

        shown = false
        awaitIdle()

        assertEquals(
            columnRows(0, CHILD_HEIGHT + GAP),
            visibleChildBounds(),
            "a child hidden again gives its space and its gap back up",
        )
    }

    private companion object {
        /** The gap the fixture arrangement holds between two adjacent visible children. */
        const val GAP = 10

        /** The extent the fixture container is given along the axis it arranges its children on. */
        const val MAIN_EXTENT = 300

        /** The extent the fixture container is given across that axis, wider than any child asks for. */
        const val CROSS_EXTENT = 100

        /**
         * The bounds of the children a layout pass placed. The hidden one is left out: it is never
         * placed, so what it reports is whatever it was last laid out at, which is no reading of this pass.
         */
        fun ComposeSwingTest.visibleChildBounds(): List<Rectangle> =
            container().components.filter { it.isVisible }.map { it.bounds }

        /** How many children the container holds, hidden ones included. */
        fun ComposeSwingTest.childCount(): Int = container().componentCount

        fun ComposeSwingTest.container(): JPanel = onNodeWithTag(CONTAINER_TAG).fetch<JPanel>()
    }
}
