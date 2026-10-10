package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val SLOT_WIDTH = 400
private const val ASKED_HEIGHT = 100
private const val LABEL_WIDTH = 150
private const val LABEL_HEIGHT = 60
private const val HTML = "<html>$WRAPPING_TEXT</html>"

/**
 * A container that asks its child's baseline reads it at the width it would ask the child's height at, and the first
 * baseline it reads at a new width is the one the child has there.
 */
class BaselineQuestionWidthTest {
    @Test
    fun aBaselineAskedAtAnUnboundedWidthIsReadAtTheWidthTheStockChildHolds() =
        runComposeSwingTest {
            lateinit var label: JLabel
            val asked = mutableListOf<BaselineQuestion>()
            setContent {
                QuestionSlots.getValue("JSplitPane first side")(SLOT_WIDTH) { modifier ->
                    readsBaselineNarrower({ label }, asked)(modifier) { leaf ->
                        SwingNode(factory = { JLabel(HTML).also { label = it } }, modifier = leaf)
                    }
                }
            }
            awaitIdle()

            val unbounded = asked.filter { it.width == Constraints.Infinity }
            assertTrue(unbounded.isNotEmpty(), "the container asks a baseline at an unbounded width")
            assertEquals(emptyList(), unbounded.filter { it.changed }, "the baseline questions that resized the label")
        }

    @Test
    fun aBaselineAskedOfAFoundationContainerDoesNotResizeIt() =
        runComposeSwingTest {
            val asked = mutableListOf<BaselineQuestion>()
            setContent {
                QuestionSlots.getValue("BorderLayout NORTH")(SLOT_WIDTH) { modifier ->
                    readsBaselineNarrower({ onNodeWithTag("container").fetch<JComponent>() }, asked)(modifier) { leaf ->
                        Box(modifier = leaf.testTag("container")) { Label("Content") }
                    }
                }
            }
            awaitIdle()

            assertTrue(asked.isNotEmpty(), "the container asks the box's baseline")
            assertEquals(emptyList(), asked.filter { it.changed }, "the baseline questions that resized the box")
        }

    @Test
    fun aBaselineAskedOfAStockChildIsTheOneItHasAtTheWidthAsked() =
        runComposeSwingTest {
            val answered = mutableListOf<Pair<Int, Int>>()
            setContent {
                QuestionSlots.getValue("BorderLayout NORTH")(SLOT_WIDTH) { modifier ->
                    Layout(
                        content = { SwingNode(factory = { JLabel(HTML) }) },
                        modifier = modifier,
                        measurePolicy = { measurables, constraints ->
                            val child = measurables.single()
                            if (constraints.hasBoundedWidth) {
                                val width = constraints.maxWidth / 2
                                val standIn = checkNotNull(child.intrinsicPlaceable(width, ASKED_HEIGHT))
                                answered += width to standIn[FirstBaseline]
                            }
                            val placeable = child.measure(constraints)
                            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        },
                    )
                }
            }
            awaitIdle()

            val reference = JLabel(HTML)

            fun settledBaselineAt(width: Int): Int {
                reference.setSize(width, ASKED_HEIGHT)
                return reference.settledBaseline()
            }
            assertTrue(answered.isNotEmpty(), "the container asks a baseline")
            assertNotEquals(
                settledBaselineAt(SLOT_WIDTH),
                settledBaselineAt(SLOT_WIDTH / 2),
                "the text wraps differently at the width asked than at the slot's width",
            )
            assertEquals(
                answered.map { (width, _) -> settledBaselineAt(width) },
                answered.map { (_, baseline) -> baseline },
                "the baselines answered at ${answered.map { it.first }}, against those of a label laid out at the " +
                    "width asked",
            )
        }

    @Test
    fun aRowAlignsAStockChildByTheBaselineItHasAtTheWidthItIsGranted() {
        val routes = listOf("alignByBaseline", "alignBy reading the baseline", "alignByBaseline of a Box holding it")
        for ((kind, leaf) in WrappingTexts) {
            for (route in routes) {
                runComposeSwingTest {
                    var width by mutableIntStateOf(SLOT_WIDTH)
                    setContent {
                        val slot = SwingModifier.preferredSize(SLOT_WIDTH * 2, SLOT_HEIGHT)
                        Panel(PanelLayout.Border(), modifier = slot) {
                            Row(modifier = SwingModifier.center().testTag("row")) {
                                val sized = SwingModifier.testTag("leaf").width(width).height(ASKED_HEIGHT)
                                when (route) {
                                    "alignByBaseline" -> leaf(sized.alignByBaseline())
                                    "alignBy reading the baseline" -> leaf(sized.alignBy { it[FirstBaseline] })
                                    else -> Box(modifier = SwingModifier.alignByBaseline()) { leaf(sized) }
                                }
                                Label("Side", modifier = SwingModifier.testTag("side").alignByBaseline())
                            }
                        }
                    }
                    val row = onNodeWithTag("row").fetch<JComponent>()
                    val text = onNodeWithTag("leaf").fetch<JComponent>()
                    val side = onNodeWithTag("side").fetch<JComponent>()
                    for (granted in listOf(SLOT_WIDTH, SLOT_WIDTH / 2, SLOT_WIDTH)) {
                        width = granted
                        awaitIdle()

                        val name = "$kind, $route, granted $granted wide"
                        assertEquals(granted, text.width, "$name: the width granted")
                        val settled = text.settledBaseline()
                        val sideBaseline = side.settledBaseline()
                        val line = maxOf(settled, sideBaseline)
                        assertEquals(
                            if (settled < 0) 0 else line - settled,
                            text.topIn(row),
                            "$name: the text sits where its baseline at that width puts it",
                        )
                        assertEquals(line - sideBaseline, side.topIn(row), "$name: the side label sits on that line")
                    }
                }
            }
        }
    }

    @Test
    fun aStockChildPlacedAtANewHeightOnlyIsNotAskedItsBaseline() =
        runComposeSwingTest {
            var width by mutableIntStateOf(LABEL_WIDTH)
            var height by mutableIntStateOf(LABEL_HEIGHT)
            lateinit var label: BaselineCountingLabel
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(SLOT_WIDTH, SLOT_HEIGHT)) {
                    Box(modifier = SwingModifier.center()) {
                        SwingNode(
                            factory = { BaselineCountingLabel().also { label = it } },
                            modifier = SwingModifier.width(width).height(height),
                        )
                    }
                }
            }
            awaitIdle()

            label.baselineReads = 0
            height += LABEL_HEIGHT
            awaitIdle()
            assertEquals(LABEL_HEIGHT * 2, label.height, "the label is placed at the new height")
            assertEquals(0, label.baselineReads, "the baseline reads when only the height changes")

            width += LABEL_WIDTH
            awaitIdle()
            assertEquals(LABEL_WIDTH * 2, label.width, "the label is placed at the new width")
            assertTrue(label.baselineReads > 0, "the baseline reads when the width changes")
        }

    private companion object {
        /**
         * Reads the baseline of its one child at a width narrower than it measures it at, recording the width of
         * [leaf] before and after each read.
         */
        fun readsBaselineNarrower(
            leaf: () -> JComponent,
            asked: MutableList<BaselineQuestion>,
        ): Asker =
            { modifier, content ->
                Layout(
                    content = { content(SwingModifier) },
                    modifier = modifier,
                    measurePolicy = { measurables, constraints ->
                        val child = measurables.single()
                        val width = constraints.maxWidth.narrower()
                        val before = leaf().width
                        child.intrinsicPlaceable(width, ASKED_HEIGHT)?.get(FirstBaseline)
                        asked += BaselineQuestion(width, before, leaf().width)
                        val placeable = child.measure(constraints)
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    },
                )
            }
    }
}

/** The baseline this component answers at its size once its content is laid out there. */
private fun JComponent.settledBaseline(): Int {
    getBaseline(width, height)
    return getBaseline(width, height)
}

/** Where the top of this component is in [row]. */
private fun JComponent.topIn(row: JComponent): Int = SwingUtilities.convertPoint(this, 0, 0, row).y

/** A label showing HTML that counts the times it is asked its baseline. */
private class BaselineCountingLabel : JLabel(HTML) {
    var baselineReads = 0

    override fun getBaseline(
        width: Int,
        height: Int,
    ): Int {
        baselineReads++
        return super.getBaseline(width, height)
    }
}

/** A baseline asked at [width], with the width of the child holding the baseline before and after it. */
private data class BaselineQuestion(
    val width: Int,
    val before: Int,
    val after: Int,
) {
    val changed: Boolean get() = before != after
}
