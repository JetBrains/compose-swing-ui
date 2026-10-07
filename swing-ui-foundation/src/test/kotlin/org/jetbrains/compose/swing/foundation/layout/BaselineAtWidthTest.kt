package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
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
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val HTML = "<html>$WRAPPING_TEXT</html>"

/**
 * A container that reads its child's baseline reads it at the width it would read the child's height at, and the first
 * baseline it reads at a new width is the one the child has there.
 */
class BaselineAtWidthTest {
    @Test
    fun aBaselineReadAtAnUnboundedWidthUsesTheWidthTheStockChildHolds() {
        for (slotName in listOf("JSplitPane first side", "BorderLayout NORTH")) {
            val baselinesAtWidths =
                baselineReads(
                    slotName,
                    leaf = { modifier ->
                        if (slotName == "JSplitPane first side") {
                            SwingNode(factory = { JLabel(HTML) }, modifier = modifier)
                        } else {
                            Box(modifier = modifier) { Label("Content") }
                        }
                    },
                    width = { it.maxWidth.narrower() },
                )
            val baselineReads =
                if (slotName == "JSplitPane first side") {
                    baselinesAtWidths.filter { it.width == Constraints.Infinity }
                } else {
                    baselinesAtWidths
                }
            assertTrue(baselineReads.isNotEmpty(), "$slotName reads the child's baseline")
            assertEquals(
                emptyList(),
                baselineReads.filter { it.before != it.after },
                "$slotName baseline reads that resized the child",
            )
        }
    }

    @Test
    fun aStockChildsBaselineIsReadAtTheSpecifiedWidth() {
        val baselinesAtWidths =
            baselineReads(
                "BorderLayout NORTH",
                leaf = { SwingNode(factory = { JLabel(HTML) }, modifier = it) },
                width = { if (it.hasBoundedWidth) it.maxWidth / 2 else null },
            )
        runSwingTest {
            val reference = JLabel(HTML)

            fun settledBaselineAt(width: Int): Int {
                reference.setSize(width, 100)
                return reference.settledBaseline()
            }
            assertTrue(baselinesAtWidths.isNotEmpty(), "the container reads a baseline")
            assertNotEquals(
                settledBaselineAt(400),
                settledBaselineAt(400 / 2),
                "the text wraps differently at the specified width than at the slot's width",
            )
            assertEquals(
                baselinesAtWidths.map { settledBaselineAt(it.width) },
                baselinesAtWidths.map { it.baseline },
                "the baselines read at ${baselinesAtWidths.map { it.width }}, " +
                    "against a label laid out at each specified width",
            )
        }
    }

    @Test
    fun aRowAlignsAStockChildByTheBaselineItHasAtTheWidthItIsGranted() {
        val routes = listOf("alignByBaseline", "alignBy reading the baseline", "alignByBaseline of a Box holding it")
        for ((kind, leaf) in WrappingTexts) {
            for (route in routes) {
                runComposeSwingTest {
                    var width by mutableIntStateOf(400)
                    setContent {
                        val slot = SwingModifier.preferredSize(400 * 2, SLOT_HEIGHT)
                        Panel(PanelLayout.Border(), modifier = slot) {
                            Row(modifier = SwingModifier.center().testTag("row")) {
                                val sized = SwingModifier.testTag("leaf").width(width).height(100)
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
                    for (granted in listOf(400, 400 / 2, 400)) {
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
    fun aStockChildPlacedAtANewHeightOnlyHasNoBaselineRead() =
        runComposeSwingTest {
            var width by mutableIntStateOf(150)
            var height by mutableIntStateOf(60)
            val label =
                object : JLabel(HTML) {
                    var baselineReads = 0

                    override fun getBaseline(
                        width: Int,
                        height: Int,
                    ): Int {
                        baselineReads++
                        return super.getBaseline(width, height)
                    }
                }
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(400, SLOT_HEIGHT)) {
                    Box(modifier = SwingModifier.center()) {
                        SwingNode(
                            factory = { label },
                            modifier = SwingModifier.width(width).height(height),
                        )
                    }
                }
            }
            awaitIdle()

            label.baselineReads = 0
            height += 60
            awaitIdle()
            assertEquals(60 * 2, label.height, "the label is placed at the new height")
            assertEquals(0, label.baselineReads, "the baseline reads when only the height changes")

            width += 150
            awaitIdle()
            assertEquals(150 * 2, label.width, "the label is placed at the new width")
            assertTrue(label.baselineReads > 0, "the baseline reads when the width changes")
        }
}

private fun baselineReads(
    slotName: String,
    leaf: @Composable (SwingModifier) -> Unit,
    width: (Constraints) -> Int?,
): List<BaselineRead> {
    val baselineReads = mutableListOf<BaselineRead>()
    runComposeSwingTest {
        setContent {
            PreferenceSlots.getValue(slotName)(400) { modifier ->
                Layout(
                    content = { leaf(SwingModifier.testTag("leaf")) },
                    modifier = modifier,
                    measurePolicy = { measurables, constraints ->
                        val measurable = measurables.single()
                        width(constraints)?.let { widthForBaseline ->
                            val child = onNodeWithTag("leaf").fetch<JComponent>()
                            val before = child.width
                            val baseline = measurable.intrinsicPlaceable(widthForBaseline, 100)?.get(FirstBaseline)
                            baselineReads += BaselineRead(widthForBaseline, before, child.width, baseline)
                        }
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    },
                )
            }
        }
        awaitIdle()
    }
    return baselineReads
}

/** The baseline this component returns at its size once its content is laid out there. */
private fun JComponent.settledBaseline(): Int {
    getBaseline(width, height)
    return getBaseline(width, height)
}

/** Where the top of this component is in [row]. */
private fun JComponent.topIn(row: JComponent): Int = SwingUtilities.convertPoint(this, 0, 0, row).y

/** A baseline read at [width], with the width of the child holding the baseline before and after it. */
private data class BaselineRead(
    val width: Int,
    val before: Int,
    val after: Int,
    val baseline: Int?,
)
