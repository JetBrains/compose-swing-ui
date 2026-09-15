package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.key
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Graphics
import java.awt.GraphicsEnvironment
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The nodes a [Layout] attaches to its panel follow the attach lifecycle of the chain they are part of. */
class LayoutNodeLifecycleTest {
    @Test
    fun aNodeDeclaredBeforeTheLayoutsOwnMeasuresThePanelWhileAttachingAndDetaching() =
        runComposeSwingTest {
            val sizes = mutableListOf<String>()
            var generation by mutableIntStateOf(0)
            setContent {
                Layout(
                    measurePolicy = { _, _ -> layout(7, 9) {} },
                    modifier = SwingModifier.key(generation) then MeasuringElement(sizes),
                )
            }
            assertEquals(listOf("onAttach 7x9"), sizes, "a node must be able to measure its Layout in onAttach")

            generation = 1
            awaitIdle()

            assertEquals(
                listOf("onAttach 7x9", "onDetach 7x9", "onAttach 7x9"),
                sizes,
                "a node must be able to measure its Layout in onDetach as the chain unwinds, and again as it attaches",
            )
        }

    /**
     * A key change detaches the layout's node, which drops the reads behind the answers Swing still caches; its
     * node attaching again revalidates the panel, so a read behind the next answer is observed. A node declared
     * before the layout's own measures the panel on both sides of that change.
     */
    @Test
    fun aMeasurementMadeWhileTheNodesAttachAgainStaysObserved() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val sizes = mutableListOf<String>()
            var width by mutableIntStateOf(7)
            var generation by mutableIntStateOf(0)
            val policy = MeasurePolicy { _, _ -> layout(width, 9) {} }
            setWindowContent {
                Layout(
                    measurePolicy = policy,
                    modifier = SwingModifier.testTag(CONTAINER_TAG).key(generation) then MeasuringElement(sizes),
                )
            }

            generation = 1
            awaitIdle()
            assertEquals(listOf("onAttach 7x9", "onDetach 7x9", "onAttach 7x9"), sizes)
            val container = windowContainer()
            assertTrue(container.isValidUpToTheValidateRoot(), "the container must be laid out again once attached")

            width = 8
            Snapshot.sendApplyNotifications()

            assertFalse(container.isValid, "a read behind a measurement made while attaching must stay observed")
        }

    /** Mounting runs the policy once, in the container's own layout pass, and observes that pass's reads. */
    @Test
    fun aMountedLayoutRunsItsPolicyOnceUnderObservation() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var measures = 0
            var width by mutableIntStateOf(7)
            val policy =
                MeasurePolicy { _, _ ->
                    measures++
                    layout(width, 9) {}
                }
            setWindowContent {
                SwingNode(factory = { JPanel(null) }) {
                    Layout(measurePolicy = policy, modifier = SwingModifier.testTag(CONTAINER_TAG))
                }
            }
            assertEquals(1, measures, "only the container's own layout pass must run its policy")

            width = 8
            Snapshot.sendApplyNotifications()

            assertFalse(windowContainer().isValid, "the read that pass made must be observed")
        }

    /**
     * A container its parent never asks for a size is still laid out, and a key change drops the reads that
     * layout recorded, so the container is laid out again under the nodes that replace them.
     */
    @Test
    fun aSettleReadIsObservedAgainAfterTheModifierNodesAttachAgain() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var gap by mutableIntStateOf(0)
            var generation by mutableIntStateOf(0)
            var settles = 0
            val policy =
                MeasurePolicy { _, constraints ->
                    settles++
                    gap
                    layout(constraints.minWidth, constraints.minHeight) {}
                }
            setWindowContent {
                // A parent with no layout manager asks its child for no size, so only the child's own layout
                // pass runs its policy.
                SwingNode(factory = { JPanel(null) }) {
                    Layout(measurePolicy = policy, modifier = SwingModifier.testTag(CONTAINER_TAG).key(generation))
                }
            }
            assertEquals(1, settles, "the container must have laid itself out once")

            generation = 1
            awaitIdle()
            assertEquals(2, settles, "the key change must lay the container out again under the new nodes")

            gap = 1
            Snapshot.sendApplyNotifications()

            assertFalse(windowContainer().isValid, "a settle read made after the nodes attached again must be observed")
        }

    /**
     * A key change drops the reads the container's own paint recorded, and only a decoration makes such a read;
     * a decoration attaching again repaints by itself. An undecorated container therefore paints nothing again.
     */
    @Test
    fun aKeyChangeOnAnUndecoratedLayoutPaintsNothingAgain() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var generation by mutableIntStateOf(0)
            val child = PaintCountingChild()
            setWindowContent {
                Layout(
                    content = { SwingNode(factory = { child }, modifier = SwingModifier.preferredSize(10, 10)) },
                    measurePolicy = stackedRows(),
                    modifier = SwingModifier.testTag(CONTAINER_TAG).key(generation),
                )
            }
            val paintsBefore = child.paints
            assertEquals(Dimension(10, 10), child.size, "the child must have been laid out")

            generation = 1
            awaitIdle()

            assertEquals(paintsBefore, child.paints, "the nodes attaching again must not repaint the container")

            windowContainer().repaint()
            awaitIdle()

            assertTrue(child.paints > paintsBefore, "a repaint of the container must paint the child")
        }
}

/** Records the preferred size its component, a [Layout]'s panel, answers as the node attaches and detaches. */
private class MeasuringElement(
    private val sizes: MutableList<String>,
) : SwingModifier.NodeElement<Component, MeasuringNode>() {
    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): MeasuringNode = MeasuringNode(sizes)

    override fun update(node: MeasuringNode) = Unit

    override fun equals(other: Any?): Boolean = other is MeasuringElement

    override fun hashCode(): Int = MeasuringElement::class.hashCode()
}

private class MeasuringNode(
    private val sizes: MutableList<String>,
) : SwingModifier.ComponentNode<Component>() {
    override fun onAttach() {
        sizes += "onAttach ${measure()}"
    }

    override fun onDetach() {
        sizes += "onDetach ${measure()}"
    }

    /** Asks the panel's layout manager, past Swing's cache and invalidating nothing, so the policy answers. */
    private fun measure(): String {
        val panel = component as Container
        val size = panel.layout.preferredLayoutSize(panel)
        return "${size.width}x${size.height}"
    }
}

private class PaintCountingChild : JComponent() {
    var paints = 0
        private set

    override fun paintComponent(g: Graphics) {
        paints++
    }
}
