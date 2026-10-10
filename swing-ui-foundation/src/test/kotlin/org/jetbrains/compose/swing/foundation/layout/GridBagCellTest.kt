package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.GridBagConstraints
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

private const val WIDTH = 320
private const val HEIGHT = 600

/** The step of the zeroing tests whose content `GridBagLayout` answers by zeroing the box. */
private const val ZEROING_STEP = 1

/**
 * A container in a `GridBagLayout` cell. Short of the preferred width, `GridBagLayout` lays the cell out by minimum
 * sizes, and lays a cell with no width or no height out at zero by zero.
 */
class GridBagCellTest {
    @Test
    fun aContainerInACellShortOfRoomTakesTheHeightForTheCellWidthWhereItsContentIsFlatAtItsMinimumWidth() {
        // The label has no minimum size, so its ratio has no height at its minimum width.
        val slots: Map<String, Slot> =
            mapOf(
                "GridBagLayout fill HORIZONTAL" to
                    swingParentSlots(emptyList()).getValue("GridBagLayout fill HORIZONTAL"),
                "GridBagLayout fill HORIZONTAL in a panel of fixed size" to { width, subject ->
                    Panel(PanelLayout.Flow()) {
                        Panel(PanelLayout.GridBag, modifier = SwingModifier.preferredSize(width, HEIGHT)) {
                            subject(SwingModifier.item(weightx = 1.0, fill = GridBagConstraints.HORIZONTAL))
                        }
                    }
                },
            )
        val placements: Map<String, ConstrainedScope.() -> SwingModifier> =
            mapOf(
                "at a ratio" to { SwingModifier.aspectRatio(16f / 9f) },
                "filling the width at a ratio" to { SwingModifier.fillMaxWidth().aspectRatio(16f / 9f) },
            )
        for ((slotName, slot) in slots) {
            for ((placementName, placement) in placements) {
                runComposeSwingTest {
                    setContent {
                        slot(WIDTH) { modifier ->
                            Box(modifier = modifier.testTag("box")) {
                                Label("", modifier = placement().testTag("label").preferredSize(2 * WIDTH, HEIGHT))
                            }
                        }
                    }
                    val box = onNodeWithTag("box").fetch<JComponent>()
                    settleWithPaint()
                    val expected = Dimension(WIDTH, (WIDTH * 9f / 16f).roundToInt())
                    assertEquals(expected, box.size, "$slotName, $placementName: the box takes the cell's width")
                    assertEquals(expected, onNodeWithTag("label").fetch<JComponent>().size, "$slotName, $placementName")
                }
            }
        }
    }

    @Test
    fun aContainerNotLaidOutYetAnswersItsMinimumHeightAtTheWidthItPrefers() =
        runComposeSwingTest {
            setContent {
                // A parent with no layout manager lays nothing out, so the box holds no width when it is asked.
                SwingNode(factory = { JPanel(null) }) {
                    Box(modifier = SwingModifier.testTag("box")) {
                        Label("Preview", modifier = SwingModifier.testTag("label").aspectRatio(16f / 9f))
                    }
                }
            }
            val box = onNodeWithTag("box").fetch<JComponent>()
            val label = onNodeWithTag("label").fetch<JComponent>()
            val preferredWidth = label.preferredSize.width

            assertEquals(0, box.width, "precondition: the box holds no width")
            assertEquals(
                Dimension(label.minimumSize.width, (preferredWidth * 9f / 16f).roundToInt()),
                box.minimumSize,
                "its minimum height is the ratio's at the width it prefers, as a wrapping stock widget's is",
            )
        }

    @Test
    fun aContainerTheCellZeroedAndThenGrantedItsPreferredWidthTakesTheHeightForTheCellWidthOnceItsContentIsWider() {
        // The cell has no height in the second step, so GridBagLayout zeroes it, and grants the width the third step
        // prefers. Content wider than the cell after that has its height at the cell's width, with no paint between.
        val steps: List<ConstrainedScope.() -> SwingModifier> =
            listOf(
                { SwingModifier.aspectRatio(16f / 9f).preferredSize(2 * WIDTH, HEIGHT) },
                { SwingModifier.preferredSize(WIDTH, 0) },
                { SwingModifier.preferredSize(WIDTH, HEIGHT / 10) },
                { SwingModifier.aspectRatio(16f / 9f).preferredSize(2 * WIDTH, HEIGHT) },
            )
        runComposeSwingTest {
            var step by mutableIntStateOf(0)
            setContent {
                Panel(PanelLayout.Flow()) {
                    Panel(PanelLayout.GridBag, modifier = SwingModifier.preferredSize(WIDTH, HEIGHT)) {
                        Box(
                            modifier =
                                SwingModifier
                                    .item(weightx = 1.0, weighty = 1.0, fill = GridBagConstraints.HORIZONTAL)
                                    .testTag("box"),
                        ) {
                            Label("", modifier = steps[step]().testTag("label"))
                        }
                    }
                }
            }
            for (next in 1 until steps.size) {
                step = next
                awaitIdle()
                if (next == ZEROING_STEP) {
                    val box = onNodeWithTag("box").fetch<JComponent>()
                    assertEquals(0, box.width, "precondition: the cell zeroes the box")
                }
            }
            val expected = Dimension(WIDTH, (WIDTH * 9f / 16f).roundToInt())
            assertEquals(expected, onNodeWithTag("box").fetch<JComponent>().size, "the box takes the cell's width")
            assertEquals(expected, onNodeWithTag("label").fetch<JComponent>().size)
        }
    }

    @Test
    fun aContainerTheCellZeroedAndThenLaidOutAtItsPreferredWidthTakesTheWidthItsWiderContentPrefers() {
        // Short of room, a cell that does not fill its width zeroes the box at its minimum width. Where the box fits,
        // the cell lays it out at the width it prefers.
        val steps: List<ConstrainedScope.() -> SwingModifier> =
            listOf(
                { SwingModifier.aspectRatio(16f / 9f).preferredSize(2 * WIDTH, HEIGHT) },
                { SwingModifier.preferredSize(WIDTH / 4, HEIGHT / 10) },
                { SwingModifier.aspectRatio(16f / 9f).preferredSize(WIDTH / 2, HEIGHT) },
            )
        runComposeSwingTest {
            var step by mutableIntStateOf(0)
            setContent {
                Panel(PanelLayout.Flow()) {
                    Panel(PanelLayout.GridBag, modifier = SwingModifier.preferredSize(WIDTH, HEIGHT)) {
                        Box(modifier = SwingModifier.item().testTag("box")) {
                            Label("", modifier = steps[step]().testTag("label"))
                        }
                    }
                }
            }
            assertEquals(0, onNodeWithTag("box").fetch<JComponent>().width, "precondition: the cell zeroes the box")
            for (next in 1 until steps.size) {
                step = next
                awaitIdle()
            }
            val expected = Dimension(WIDTH / 2, (WIDTH / 2 * 9f / 16f).roundToInt())
            assertEquals(expected, onNodeWithTag("box").fetch<JComponent>().size, "the box takes the width it prefers")
            assertEquals(expected, onNodeWithTag("label").fetch<JComponent>().size)
        }
    }

    @Test
    fun aContainerMovedFromAZeroedCellIntoAFlowLayoutTakesTheWidthItsWiderContentPrefers() {
        // The cell lays the box out at the cell's width, then zeroes it for having no height. The flow layout it moves
        // into lays it out at the width it prefers.
        val steps: List<ConstrainedScope.() -> SwingModifier> =
            listOf(
                { SwingModifier.preferredSize(WIDTH / 4, HEIGHT / 10) },
                { SwingModifier.preferredSize(WIDTH / 4, 0) },
                { SwingModifier.preferredSize(WIDTH / 4, HEIGHT / 10) },
                { SwingModifier.aspectRatio(16f / 9f).preferredSize(WIDTH / 2, HEIGHT) },
            )
        runComposeSwingTest {
            var step by mutableIntStateOf(0)
            setContent {
                val box =
                    remember {
                        movableContentOf<SwingModifier, ConstrainedScope.() -> SwingModifier> { modifier, label ->
                            Box(modifier = modifier.testTag("box")) { Label("", modifier = label().testTag("label")) }
                        }
                    }
                Panel(PanelLayout.Flow()) {
                    Panel(PanelLayout.GridBag, modifier = SwingModifier.preferredSize(WIDTH, HEIGHT)) {
                        if (step < 2) {
                            box(
                                SwingModifier.item(weightx = 1.0, weighty = 1.0, fill = GridBagConstraints.HORIZONTAL),
                                steps[step],
                            )
                        }
                    }
                    Panel(PanelLayout.Flow()) { if (step >= 2) box(SwingModifier, steps[step]) }
                }
            }
            for (next in 1 until steps.size) {
                step = next
                awaitIdle()
                if (next == ZEROING_STEP) {
                    val box = onNodeWithTag("box").fetch<JComponent>()
                    assertEquals(0, box.width, "precondition: the cell zeroes the box")
                }
            }
            val expected = Dimension(WIDTH / 2, (WIDTH / 2 * 9f / 16f).roundToInt())
            assertEquals(expected, onNodeWithTag("box").fetch<JComponent>().size, "the box takes the width it prefers")
            assertEquals(expected, onNodeWithTag("label").fetch<JComponent>().size)
        }
    }
}
