package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertAskedToRepaint
import org.jetbrains.compose.swing.foundation.graphics.drawscope.ContentDrawScope
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.key
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics2D
import javax.swing.JButton
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Behavioral tests for [DecorationModifierNode]: where a step paints, and how its changes reach the component. */
class DecorationModifierNodeTest {
    @Test
    fun aPaintOnlyChangeRepaintsWithoutLayingTheComponentOutAgain() =
        runComposeSwingTest {
            val step = Step()
            setContent {
                SwingNode(
                    factory = { DecoratedPanel() },
                    modifier =
                        SwingModifier
                            .testTag("panel")
                            .opaque(false)
                            .preferredSize(Dimension(32, 32))
                            .then(step),
                )
            }
            val node = step.created.single()
            assertEquals(
                Color.RED.rgb,
                onNodeWithTag("panel").captureToImage().getRGB(16, 16),
                "The step paints with its initial color.",
            )
            val panel = onNodeWithTag("panel").fetch<JComponent>()
            withRecordedRepaints { recorder ->
                node.color = Color.GREEN
                node.invalidateDecoration()

                recorder.assertAskedToRepaint(panel, "a changed step")
                assertEquals(
                    0,
                    recorder.relayoutsOver(panel),
                    "A paint-only change does not lay the component out again.",
                )
                assertEquals(
                    Color.GREEN.rgb,
                    onNodeWithTag("panel").captureToImage().getRGB(16, 16),
                    "The repaint draws with the step's changed color.",
                )
            }
        }

    @Test
    fun aStepRemovedFromTheModifierPaintsNoMore() =
        runComposeSwingTest {
            var declared by mutableStateOf(true)
            setContent {
                val modifier =
                    decorated {
                        SwingModifier
                            .testTag("panel")
                            .opaque(false)
                            .preferredSize(Dimension(32, 32))
                            .fill(Color.BLUE)
                    }
                SwingNode(factory = { DecoratedPanel() }, modifier = if (declared) modifier.then(Step()) else modifier)
            }
            assertEquals(
                Color.RED.rgb,
                onNodeWithTag("panel").captureToImage().getRGB(16, 16),
                "The step paints over the fill while declared.",
            )

            declared = false
            awaitIdle()

            assertEquals(
                Color.BLUE.rgb,
                onNodeWithTag("panel").captureToImage().getRGB(16, 16),
                "Removing the step leaves the fill showing.",
            )
        }

    @Test
    fun stepsAttachingInOnePassAreHandedToTheComponentOnce() =
        runComposeSwingTest {
            var declared by mutableStateOf(false)
            setContent {
                val modifier = SwingModifier.testTag("panel").preferredSize(Dimension(32, 32))
                SwingNode(
                    factory = { WriteCountingPanel() },
                    modifier = if (declared) modifier.then(Step()).then(Step()).then(Step()) else modifier,
                )
            }
            val panel = onNodeWithTag("panel").fetch<WriteCountingPanel>()
            panel.writes = 0

            declared = true
            awaitIdle()

            assertEquals(1, panel.writes, "The steps one pass attaches reach the component as one decoration.")
        }

    @Test
    fun aStepThatFlipsItsOpacityFlipsTheComponentsOpacity() =
        runComposeSwingTest {
            val step = Step()
            setContent {
                SwingNode(factory = { DecoratedPanel() }, modifier = SwingModifier.testTag("panel").then(step))
            }
            val panel = onNodeWithTag("panel").fetch<JComponent>()
            val node = step.created.single()
            assertTrue(panel.isOpaque, "A step covering its whole area leaves an opaque panel opaque.")

            node.opaque = false
            node.invalidateDecoration()
            assertFalse(panel.isOpaque, "A step that stops covering its area makes the panel stop being opaque.")

            node.opaque = true
            node.invalidateDecoration()
            assertTrue(panel.isOpaque, "and one covering it again makes it opaque again.")
        }

    @Test
    fun aStepDetachedInAPassThatThrowsPaintsNoMore() =
        runComposeSwingTest {
            var failing by mutableStateOf(false)
            val thrown = ArrayList<String>()
            setContent {
                SwingNode(
                    factory = { DecoratedPanel().apply { isOpaque = false } },
                    modifier =
                        SwingModifier.testTag("panel").preferredSize(Dimension(32, 32)).let {
                            if (failing) {
                                it.key(2).then(FailingToAttach(thrown))
                            } else {
                                it.key(1).then(ComponentReadingStep())
                            }
                        },
                )
            }
            val panel = onNodeWithTag("panel").fetch<JComponent>()
            panel.background = Color.RED
            assertEquals(Color.RED.rgb, panel.paintOnto(32, 32).getRGB(16, 16), "The step fills the panel.")

            failing = true
            awaitIdle()
            assertEquals(listOf("onAttach fails"), thrown, "The pass detaching the step throws as it attaches another.")

            assertEquals(0, panel.paintOnto(32, 32).getRGB(16, 16), "A step detached by the pass paints no more.")
        }

    @Test
    fun aReleasedComponentIsLeftUndecorated() =
        runComposeSwingTest {
            val panel = DecoratedPanel()
            val undecorated = panel.isOpaque
            var present by mutableStateOf(true)
            setContent {
                if (present) SwingNode(factory = { panel }, modifier = decorated { SwingModifier.cut() })
            }
            assertFalse(panel.isOpaque, "The cut leaves the corners uncovered.")

            present = false
            awaitIdle()

            assertEquals(Decoration.None, panel.decoration, "The released component holds no decoration.")
            assertEquals(undecorated, panel.isOpaque, "and answers isOpaque as it did before the step.")
        }

    @Test
    fun aDeactivatedComponentIsLeftUndecorated() =
        runComposeSwingTest {
            val panel = DecoratedPanel()
            val undecorated = panel.isOpaque
            var active by mutableStateOf(true)
            setContent {
                ReusableContentHost(active) {
                    SwingNode(factory = { panel }, modifier = decorated { SwingModifier.cut() })
                }
            }
            assertFalse(panel.isOpaque, "The cut leaves the corners uncovered.")

            active = false
            awaitIdle()

            assertEquals(Decoration.None, panel.decoration, "The deactivated component holds no decoration.")
            assertEquals(undecorated, panel.isOpaque, "and answers isOpaque as it did before the step.")
        }

    @Test
    fun aDecoratableNeedsAStepAfterAWriteButNotADrawNode() {
        val panel = DecoratedPanel()

        assertTrue(panel.needsNodesAfterWrite(StepNode()), "A step's write may change its opacity.")
        assertFalse(
            panel.needsNodesAfterWrite(DrawingNode()),
            "A draw node's opacity never changes, and it repaints its own change.",
        )
        assertFalse(panel.needsNodesAfterWrite(PlainNode()), "A node that is no decoration step changes nothing.")
    }

    @Test
    fun aStepCreatedByAPropertyElementIsRefused() =
        runComposeSwingTest {
            val failure =
                assertFailsWith<IllegalStateException> {
                    setContent {
                        SwingNode(factory = { DecoratedPanel() }, modifier = SwingModifier.then(KeyedStep()))
                    }
                }

            assertTrue(failure.message.orEmpty().contains("additive element"), "${failure.message}")
        }

    @Test
    fun aStepNamingAWiderTargetIsRefusedOnANonDecoratableComponent() =
        runComposeSwingTest {
            val failure =
                assertFailsWith<IllegalStateException> {
                    setContent {
                        SwingNode(factory = { JButton() }, modifier = SwingModifier.then(Step()))
                    }
                }

            assertEquals(
                "A decoration step requires a ${Decoratable::class.java.name} target, but the component is a " +
                    JButton::class.java.name,
                failure.message,
                "a custom element naming a wider target is refused as its node attaches",
            )
        }

    @Test
    fun aStepIsToldItLeavesTheDecorationWhenRemovedReplacedOrItsComponentLeaves() =
        runComposeSwingTest {
            val counter = RemovalCounter()
            var declared by mutableStateOf(true)
            var replaced by mutableStateOf(false)
            var componentPresent by mutableStateOf(true)
            var observedValue by mutableIntStateOf(0)
            setContent {
                if (componentPresent) {
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier =
                            SwingModifier.testTag("panel").preferredSize(Dimension(32, 32)).let { base ->
                                when {
                                    !declared -> base
                                    replaced -> base.then(Step())
                                    else -> base.then(CountingStep(counter, observedValue))
                                }
                            },
                    )
                }
            }
            val node = counter.created.single()

            // (1) An in-place update reaches the same node's element through recomposition, and tells no step it left.
            observedValue = 1
            awaitIdle()
            assertEquals(1, node.observedValue, "the recomposed element's update reaches the same node")
            assertEquals(0, counter.removedCount, "an in-place update tells no step it left")

            // (2) Dropping the element from the modifier.
            declared = false
            awaitIdle()
            assertEquals(1, counter.removedCount, "a dropped step is told it left")

            // (3) Declaring it again, then replacing it in the same slot by a different element type.
            declared = true
            awaitIdle()
            replaced = true
            awaitIdle()
            assertEquals(2, counter.removedCount, "a step replaced by another element is told it left")

            // (4) Declaring it again, then removing the whole component from composition.
            replaced = false
            declared = true
            awaitIdle()
            componentPresent = false
            awaitIdle()
            assertEquals(3, counter.removedCount, "a step whose component leaves is told it left")

            assertTrue(
                counter.attachedAtEveryRemoval.all { it },
                "the step is still attached each time it is told it left the decoration",
            )
        }
}

/** Counts how often a [CountingStepNode] is told it left the decoration, across every instance created. */
private class RemovalCounter {
    val created = ArrayList<CountingStepNode>()
    var removedCount = 0
    val attachedAtEveryRemoval = ArrayList<Boolean>()
}

/** Declares a [CountingStepNode] holding [value], which reports every removal from the decoration to [counter]. */
private class CountingStep(
    private val counter: RemovalCounter,
    private val value: Int,
) : SwingModifier.NodeElement<Component, CountingStepNode>() {
    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): CountingStepNode =
        CountingStepNode(counter).apply {
            observedValue = value
            counter.created += this
        }

    override fun update(node: CountingStepNode) {
        node.observedValue = value
    }

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

/** Paints its content unchanged, reporting every removal from the decoration to [counter]. */
private class CountingStepNode(
    private val counter: RemovalCounter,
) : DecorationModifierNode<Component>() {
    var observedValue: Int = 0

    override fun onRemovedFromDecoration() {
        counter.removedCount++
        counter.attachedAtEveryRemoval += isAttached
    }

    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        content(graphics, width, height)
    }
}

/** Declares a [StepNode], a decoration step written against the public API alone. */
private class Step : SwingModifier.NodeElement<Component, StepNode>() {
    val created = ArrayList<StepNode>()

    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): StepNode = StepNode().also { created += it }

    override fun update(node: StepNode) = Unit

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

/** Declares a [StepNode] as a property rather than an additive element. */
private class KeyedStep : SwingModifier.NodeElement<Component, StepNode>() {
    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): StepNode = StepNode()

    override fun update(node: StepNode) = Unit

    override fun equals(other: Any?): Boolean = other is KeyedStep

    override fun hashCode(): Int = 0
}

/** Fills its area with [color], then paints its content over it, answering [opaque] as its opacity. */
private class StepNode : DecorationModifierNode<Component>() {
    var color: Color = Color.RED
    var opaque = true

    override val isOpaque: Boolean get() = opaque

    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        graphics.color = color
        graphics.fillRect(0, 0, width, height)
        content(graphics, width, height)
    }
}

/** A [DecoratedPanel] counting the values the library writes to its decoration. */
private class WriteCountingPanel : DecoratedPanel() {
    var writes = 0

    override var decoration: Decoration
        get() = super.decoration
        set(value) {
            writes++
            super.decoration = value
        }
}

/**
 * Declares a node whose `update` throws where [failing], ending the pass that writes it, and records it in [thrown].
 */
private class FailingToUpdate(
    private val failing: Boolean,
    private val thrown: MutableList<String>,
) : SwingModifier.NodeElement<Component, PlainNode>() {
    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): PlainNode = PlainNode()

    override fun update(node: PlainNode) {
        if (!failing) return
        thrown += "update fails"
        error("update fails")
    }

    override fun equals(other: Any?): Boolean = other is FailingToUpdate && other.failing == failing

    override fun hashCode(): Int = failing.hashCode()
}

/** Declares a node whose `onAttach` throws, ending the pass that attaches it, and records it in [thrown]. */
private class FailingToAttach(
    private val thrown: MutableList<String>,
) : SwingModifier.NodeElement<Component, PlainNode>() {
    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): PlainNode =
        object : PlainNode() {
            override fun onAttach() {
                thrown += "onAttach fails"
                error("onAttach fails")
            }
        }

    override fun update(node: PlainNode) = Unit

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = System.identityHashCode(this)
}

/** A node that is no decoration step. */
private open class PlainNode : SwingModifier.ComponentNode<Component>()

/** Declares a [ComponentReadingNode]. */
private class ComponentReadingStep : SwingModifier.NodeElement<Component, ComponentReadingNode>() {
    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): ComponentReadingNode = ComponentReadingNode()

    override fun update(node: ComponentReadingNode) = Unit

    override fun equals(other: Any?): Boolean = other is ComponentReadingStep

    override fun hashCode(): Int = 0
}

/** Fills its area with its component's background before its content, reading [component] as it paints. */
private class ComponentReadingNode : DecorationModifierNode<Component>() {
    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        graphics.color = component.background
        graphics.fillRect(0, 0, width, height)
        content(graphics, width, height)
    }
}

/** A draw node drawing nothing but its content. */
private class DrawingNode : DrawModifierNode<Component>() {
    override fun ContentDrawScope.draw() = drawContent()
}
