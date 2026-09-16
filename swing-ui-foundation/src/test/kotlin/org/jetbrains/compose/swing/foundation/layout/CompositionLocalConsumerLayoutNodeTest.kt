package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.compose.swing.assertAskedForLayout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.CompositionLocalConsumerModifierNode
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.currentValueOf
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.withRecordedRepaints
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private val LocalWidth = compositionLocalOf { 10 }

private val LocalStaticWidth = staticCompositionLocalOf { 10 }

private val LocalX = compositionLocalOf { 0 }

private val LocalStaticX = staticCompositionLocalOf { 0 }

private val LocalUnrelated = compositionLocalOf { "default" }

/**
 * A layout node reading composition locals through `currentValueOf` while its parent measures and places it.
 *
 * Every test that pins a pass running again composes into a real, showing window: pinning which of measure and
 * place ran again needs Swing's own validity and dirty-region state, which only a shown component carries.
 */
class CompositionLocalConsumerLayoutNodeTest {
    @Test
    fun aMeasureReadingAChangedLocalLaysTheComponentOutAgain() = assertMeasureFollows(LocalWidth)

    @Test
    fun aMeasureReadingAChangedStaticLocalMeasuresPlacesAndLaysTheParentOutAgain() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val panel = JPanel()
            val probe = LocalProbe()
            var width by mutableStateOf(20)
            setWindowContent {
                CompositionLocalProvider(LocalStaticWidth provides width) {
                    Row {
                        SwingNode(
                            factory = { panel },
                            modifier = SwingModifier then LocalReadingElement(probe, LocalStaticWidth),
                        )
                    }
                }
            }
            assertEquals(20, panel.width, "the node must measure the child at the first local's width")
            val parent = checkNotNull(panel.parent as? JComponent)
            val measures = probe.measures
            val placements = probe.placements

            withRecordedRepaints { recorded ->
                width = 40
                awaitIdle()

                assertEquals(40, panel.width, "and at the changed local's width after the change")
                assertTrue(probe.measures > measures, "a changed static local must measure the node again")
                assertTrue(probe.placements > placements, "a changed static local must place the node again")
                recorded.assertAskedForLayout(panel, "a changed static local")
                assertTrue(recorded.repaintsOf(parent) > 0, "a changed static local must repaint the parent")
            }
        }

    @Test
    fun aPlacementReadingAChangedLocalPlacesAgainWithoutMeasuringAgain() = assertPlacementFollows(LocalX)

    @Test
    fun aPlacementReadingAChangedStaticLocalMeasuresPlacesAndLaysTheParentOutAgain() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val probe = LocalProbe()
            val panel = JPanel()
            var x by mutableStateOf(0)
            setWindowContent {
                CompositionLocalProvider(LocalStaticX provides x) {
                    Box {
                        Row {
                            SwingNode(
                                factory = { panel },
                                modifier =
                                    SwingModifier then
                                        LocalReadingElement(probe, width = null, x = LocalStaticX),
                            )
                        }
                    }
                }
            }
            assertEquals(0, panel.x, "the node must place the child at the first local's x")
            val parent = checkNotNull(panel.parent as? JComponent)
            val measures = probe.measures
            val placements = probe.placements

            withRecordedRepaints { recorded ->
                x = 3
                awaitIdle()

                assertEquals(3, panel.x, "and at the changed local's x after the change")
                assertTrue(probe.measures > measures, "a changed static local must measure the node again")
                assertTrue(probe.placements > placements, "a changed static local must place the node again")
                recorded.assertAskedForLayout(panel, "a changed static local")
                assertTrue(recorded.repaintsOf(parent) > 0, "a changed static local must repaint the parent")
            }
        }

    @Test
    fun aChangeOfALocalTheNodeDoesNotReadRunsNothingAgain() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val probe = LocalProbe()
            var unrelated by mutableStateOf("hello")
            var width by mutableStateOf(10)
            setWindowContent {
                CompositionLocalProvider(LocalUnrelated provides unrelated, LocalWidth provides width) {
                    Row { SwingNode(factory = { JPanel() }, modifier = SwingModifier then LocalReadingElement(probe)) }
                }
            }
            val passes = probe.passes()

            unrelated = "changed"
            awaitIdle()

            assertEquals(
                passes,
                probe.passes(),
                "a change of a local the node does not read must run none of its passes",
            )

            width = 20
            awaitIdle()

            assertTrue(probe.measures > passes.first(), "a change of the local the node reads must measure it again")
        }

    @Test
    fun aNodeMovedToAnotherRowReadsTheLocalInScopeThere() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val panel = JPanel()
            var inFirstRow by mutableStateOf(true)
            setWindowContent {
                val child =
                    remember {
                        movableContentOf {
                            SwingNode(
                                factory = { panel },
                                modifier = SwingModifier then LocalReadingElement(LocalProbe(), LocalStaticWidth),
                            )
                        }
                    }
                Column {
                    CompositionLocalProvider(LocalStaticWidth provides 20) { Row { if (inFirstRow) child() } }
                    CompositionLocalProvider(LocalStaticWidth provides 30) { Row { if (!inFirstRow) child() } }
                }
            }
            assertEquals(20, panel.width, "the node must read the local provided in its own row")

            inFirstRow = false
            awaitIdle()

            assertEquals(30, panel.width, "and the new row's local after the child moves there")
        }

    @Test
    fun readingALocalWhileDetachedFails() =
        runComposeSwingTest {
            val probe = LocalProbe()
            var present by mutableStateOf(true)
            setContent {
                Row {
                    if (present) {
                        SwingNode(factory = { JPanel() }, modifier = SwingModifier then LocalReadingElement(probe))
                    }
                }
            }
            val node = checkNotNull(probe.node)

            present = false
            awaitIdle()

            assertFailsWith<IllegalStateException> { node.currentValueOf(LocalWidth) }
        }

    private fun assertMeasureFollows(local: ProvidableCompositionLocal<Int>) =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val panel = JPanel()
            val probe = LocalProbe()
            var width by mutableStateOf(20)
            setWindowContent {
                CompositionLocalProvider(local provides width) {
                    Row {
                        SwingNode(factory = { panel }, modifier = SwingModifier then LocalReadingElement(probe, local))
                    }
                }
            }
            assertEquals(20, panel.width, "the node must measure the child at the first local's width")

            width = 40
            awaitIdle()

            assertEquals(40, panel.width, "and at the changed local's width after the change")
        }

    private fun assertPlacementFollows(local: ProvidableCompositionLocal<Int>) =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val probe = LocalProbe()
            val panel = JPanel()
            var x by mutableStateOf(0)
            setWindowContent {
                CompositionLocalProvider(local provides x) {
                    // A parent policy measures the Row, so the Row's layout pass settles on that measure.
                    Box {
                        Row {
                            SwingNode(
                                factory = { panel },
                                modifier = SwingModifier then LocalReadingElement(probe, width = null, x = local),
                            )
                        }
                    }
                }
            }
            // Settles what the window's first passes leave, so the counts below start from a layout that places
            // again without measuring.
            x = 3
            awaitIdle()
            assertEquals(3, panel.x, "the node must place the child at the first local's x")
            val measures = probe.measures
            val placements = probe.placements

            x = 7
            awaitIdle()

            assertEquals(7, panel.x, "and at the changed local's x after the change")
            assertEquals(measures, probe.measures, "a placement read must not measure again")
            assertEquals(placements + 1, probe.placements, "a placement read must place once again")
        }
}

/** What a [LocalReadingNode] saw: itself, and how many times each of its passes ran. */
private class LocalProbe {
    var node: LocalReadingNode? = null
    var measures = 0
    var placements = 0

    fun passes(): List<Int> = listOf(measures, placements)
}

/**
 * Measures the content as wide as [width] reads and places it at [x]; each pass reads no local where its local is
 * `null`.
 */
private class LocalReadingElement(
    private val probe: LocalProbe,
    private val width: CompositionLocal<Int>? = LocalWidth,
    private val x: CompositionLocal<Int>? = LocalX,
) : LayoutModifierNodeElement<LocalReadingNode>() {
    override fun create(): LocalReadingNode = LocalReadingNode(probe, width, x).also { probe.node = it }

    override fun update(node: LocalReadingNode) = Unit

    override fun equals(other: Any?): Boolean =
        other is LocalReadingElement && other.probe === probe && other.width === width && other.x === x

    override fun hashCode(): Int = System.identityHashCode(probe)
}

private class LocalReadingNode(
    private val probe: LocalProbe,
    private val width: CompositionLocal<Int>?,
    private val x: CompositionLocal<Int>?,
) : LayoutModifierNode(),
    CompositionLocalConsumerModifierNode {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        probe.measures++
        val placeable = measurable.measure(Constraints.fixed(width?.let { currentValueOf(it) } ?: 10, 10))
        return layout(placeable.width, placeable.height) {
            probe.placements++
            val x = x?.let { currentValueOf(it) } ?: 0
            placeable.place(x, 0)
        }
    }
}
