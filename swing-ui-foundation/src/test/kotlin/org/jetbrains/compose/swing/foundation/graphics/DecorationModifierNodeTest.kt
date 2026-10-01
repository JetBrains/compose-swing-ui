package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertAskedToRepaint
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.graphics.drawscope.ContentDrawScope
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.PaintOutsets
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.foundation.layout.paintOutsets
import org.jetbrains.compose.swing.foundation.layout.placementLayer
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
import java.awt.FlowLayout
import java.awt.Graphics2D
import java.awt.Insets
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Behavioral tests for [DecorationModifierNode]: where a step paints, and how its changes reach the component. */
class DecorationModifierNodeTest {
    @Test
    fun aStepPaintsAtItsPositionAmongTheDecorationsDeclaredAroundIt() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag("box")) {
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier =
                            SwingModifier
                                .opaque(false)
                                .preferredSize(Dimension(32, 32))
                                .padding(4)
                                .then(Step())
                                .padding(4)
                                .fill(Color.BLUE),
                    )
                }
            }

            val image = onNodeWithTag("box").captureToImage()
            assertNotEquals(
                Color.RED.rgb,
                image.getRGB(2, 2),
                "The padding declared before the step keeps it off the edge.",
            )
            assertEquals(Color.RED.rgb, image.getRGB(6, 6), "The step paints at the box of the padding after it.")
            assertEquals(Color.BLUE.rgb, image.getRGB(10, 10), "What is declared after the step paints inside it.")
        }

    @Test
    fun theOutsetsAStepReportsOnceItsElementIsAppliedIsReserved() =
        runComposeSwingTest {
            setContent {
                Box {
                    SwingNode(
                        factory = { DecoratedPanel(FlowLayout(FlowLayout.LEADING, 0, 0)) },
                        modifier =
                            SwingModifier
                                .testTag(
                                    "panel",
                                ).preferredSize(Dimension(32, 32))
                                .then(Step(outset = 4)),
                    ) {
                        Label(text = "child", modifier = SwingModifier.testTag("child"))
                    }
                }
            }
            val panel = onNodeWithTag("panel").fetch<JComponent>()
            val child = onNodeWithTag("child").fetch<JComponent>()

            assertEquals(Insets(4, 4, 4, 4), panel.insets, "The component reports the outsets the step declares.")
            assertEquals(4, child.x, "The children are laid out inside the outsets the step reserves.")
        }

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
                    "A step reserving the same outsets does not lay the component out again.",
                )
                assertEquals(
                    Color.GREEN.rgb,
                    onNodeWithTag("panel").captureToImage().getRGB(16, 16),
                    "The repaint draws with the step's changed color.",
                )
            }
        }

    @Test
    fun invalidateDecorationAfterAnOutsetsChangeLaysTheChildrenOutInsideTheNewInsets() =
        runComposeSwingTest {
            val step = Step()
            setContent {
                Box {
                    SwingNode(
                        factory = { DecoratedPanel(FlowLayout(FlowLayout.LEADING, 0, 0)) },
                        modifier = SwingModifier.testTag("panel").preferredSize(Dimension(32, 32)).then(step),
                    ) {
                        Label(text = "child", modifier = SwingModifier.testTag("child"))
                    }
                }
            }
            val node = step.created.single()
            val child = onNodeWithTag("child").fetch<JComponent>()
            assertEquals(0, child.x, "Without outsets the child starts at the panel's edge.")
            val panel = onNodeWithTag("panel").fetch<JComponent>()
            withRecordedRepaints { recorder ->
                node.outset = 5
                node.invalidateDecoration()

                assertEquals(
                    0,
                    recorder.relayoutsOver(panel),
                    "A step reserving different outsets revalidates nothing.",
                )
                assertEquals(5, child.x, "The children are laid out inside the outsets the step now reserves.")
                assertEquals(5, child.y, "The children are laid out inside the outsets the step now reserves.")
            }
        }

    @Test
    fun aStepThatReportedItsOutsetsLeavesNoOutsetsBehindOnceRemoved() =
        runComposeSwingTest {
            var declared by mutableStateOf(true)
            var outset by mutableIntStateOf(0)
            setContent {
                Box {
                    val modifier = SwingModifier.testTag("panel").preferredSize(Dimension(32, 32))
                    SwingNode(
                        factory = { DecoratedPanel(FlowLayout(FlowLayout.LEADING, 0, 0)) },
                        modifier = if (declared) modifier.then(ReportingStep(outset)) else modifier,
                    ) {
                        Label(text = "child", modifier = SwingModifier.testTag("child"))
                    }
                }
            }
            val child = onNodeWithTag("child").fetch<JComponent>()
            outset = 5
            awaitIdle()
            assertEquals(5, child.x, "The step's element reports the outsets it reserves.")

            declared = false
            awaitIdle()

            assertEquals(0, child.x, "A removed step takes the outsets it reserved with it.")
        }

    @Test
    fun aStepRemovedFromTheModifierPaintsNoMore() =
        runComposeSwingTest {
            var declared by mutableStateOf(true)
            setContent {
                DecoratedBox {
                    val modifier =
                        SwingModifier
                            .testTag("panel")
                            .opaque(false)
                            .preferredSize(Dimension(32, 32))
                            .fill(Color.BLUE)
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier = if (declared) modifier.then(Step()) else modifier,
                    )
                }
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
                    modifier = if (declared) modifier.then(Step(1)).then(Step(1)).then(Step(1)) else modifier,
                )
            }
            val panel = onNodeWithTag("panel").fetch<WriteCountingPanel>()
            panel.writes = 0

            declared = true
            awaitIdle()

            assertEquals(1, panel.writes, "The steps one pass attaches reach the component as one decoration.")
        }

    @Test
    fun aPaintOutsetsValueAndAStepChangingInOnePassAreHandedToTheComponentOnce() =
        runComposeSwingTest {
            var changed by mutableStateOf(false)
            setContent {
                SwingNode(factory = { JPanel() }) {
                    SwingNode(
                        factory = { WriteCountingPanel() },
                        modifier =
                            SwingModifier
                                .testTag("panel")
                                .preferredSize(Dimension(32, 32))
                                .paintOutsets(if (changed) PaintOutsets.None else PaintOutsets.Decoration)
                                .then(Step(if (changed) 2 else 1)),
                    )
                }
            }
            val panel = onNodeWithTag("panel").fetch<WriteCountingPanel>()
            panel.writes = 0

            changed = true
            awaitIdle()

            assertEquals(1, panel.writes, "The value and the step one pass changes reach it as one decoration.")
            assertEquals(Insets(2, 2, 2, 2), panel.insets, "The new value leaves the step's new outsets in layout.")
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
    fun aStepLosingItsDecoratorInAPassThatThrowsStillPaintsTheContent() =
        runComposeSwingTest {
            var failing by mutableStateOf(false)
            setContent {
                SwingNode(
                    factory = { DecoratedPanel().apply { background = Color.RED } },
                    modifier =
                        SwingModifier
                            .testTag("panel")
                            .preferredSize(Dimension(32, 32))
                            .then(DecoratorElement(if (failing) null else Cut()))
                            .then(FailingToUpdate(failing)),
                )
            }
            val panel = onNodeWithTag("panel").fetch<JComponent>()
            assertEquals(0, panel.paintOnto(32, 32).getRGB(0, 0), "The cut takes the corner away.")

            failing = true
            val thrown = assertFailsWith<IllegalStateException> { awaitIdle() }
            assertEquals("update fails", thrown.message, "The pass writing the step throws after it.")

            assertEquals(
                Color.RED.rgb,
                panel.paintOnto(32, 32).getRGB(0, 0),
                "A step left holding no decorator paints its content unchanged.",
            )
        }

    @Test
    fun aStepDetachedInAPassThatThrowsPaintsNoMore() =
        runComposeSwingTest {
            var failing by mutableStateOf(false)
            setContent {
                SwingNode(
                    factory = { DecoratedPanel().apply { isOpaque = false } },
                    modifier =
                        SwingModifier.testTag("panel").preferredSize(Dimension(32, 32)).let {
                            if (failing) {
                                it.key(2).then(FailingToAttach())
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
            val thrown = assertFailsWith<IllegalStateException> { awaitIdle() }
            assertEquals("onAttach fails", thrown.message, "The pass detaching the step throws as it attaches another.")

            assertEquals(0, panel.paintOnto(32, 32).getRGB(16, 16), "A step detached by the pass paints no more.")
        }

    @Test
    fun aReleasedComponentIsLeftUndecorated() =
        runComposeSwingTest {
            val panel = DecoratedPanel()
            val undecorated = panel.isOpaque
            var present by mutableStateOf(true)
            setContent {
                DecoratedBox {
                    if (present) SwingNode(factory = { panel }, modifier = SwingModifier.cut())
                }
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
                DecoratedBox {
                    ReusableContentHost(active) {
                        SwingNode(factory = { panel }, modifier = SwingModifier.cut())
                    }
                }
            }
            assertFalse(panel.isOpaque, "The cut leaves the corners uncovered.")

            active = false
            awaitIdle()

            assertEquals(Decoration.None, panel.decoration, "The deactivated component holds no decoration.")
            assertEquals(undecorated, panel.isOpaque, "and answers isOpaque as it did before the step.")
        }

    @Test
    fun aLayerAndAClipOnAnOpaquePanelLeaveTogetherWithoutARestoreFailure() =
        runComposeSwingTest {
            setContent {
                Row {
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier =
                            SwingModifier
                                .placementLayer { alpha = 0.5f }
                                .testTag("subject")
                                .preferredSize(60, 60)
                                .clip(CircleShape, antialias = true),
                    )
                }
            }

            // The panel leaves with the composition, whose teardown holds each departing step to the restore check.
            val panel = onNodeWithTag("subject").fetch<DecoratedPanel>()
            assertFalse(panel.isOpaque, "A faded, clipped panel is not opaque.")
        }

    @Test
    fun aClipLeavingAPanelThatALayerStillFadesIsNotOwedItsOpacity() =
        runComposeSwingTest {
            var clipped by mutableStateOf(true)
            setContent {
                Row {
                    val sized = SwingModifier.placementLayer { alpha = 0.5f }.testTag("subject").preferredSize(60, 60)
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier = if (clipped) sized.clip(CircleShape, antialias = true) else sized,
                    )
                }
            }

            clipped = false
            awaitIdle()

            assertFalse(onNodeWithTag("subject").fetch<DecoratedPanel>().isOpaque, "The layer still fades the panel.")
        }

    @Test
    fun decorationNodesDeclareTheirOwnUpdateInvalidation() {
        assertTrue(StepNode().shouldAutoInvalidate, "A step's write may change its outsets or opacity.")
        assertFalse(
            DrawingNode().shouldAutoInvalidate,
            "A draw node's outsets and opacity never change, and it repaints its own change.",
        )
        assertFalse(PlainNode().shouldAutoInvalidate, "A plain component node owns its update invalidation.")
    }

    @Test
    fun aDecorationSettlingAtFullOpacityLeavesTheComponentUndecorated() =
        runComposeSwingTest {
            var alpha by mutableStateOf(0.5f)
            setContent {
                DecoratedBox {
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier = SwingModifier.testTag("panel").alpha(alpha),
                    )
                }
            }
            val panel = onNodeWithTag("panel").fetch<DecoratedPanel>()
            assertTrue(panel.isPaintingOrigin(), "A fade decorates the component.")

            alpha = 1f
            awaitIdle()

            assertFalse(
                panel.isPaintingOrigin(),
                "A fade of 1 decorates nothing, so the children repaint themselves again.",
            )
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
private class Step(
    private val outset: Int = 0,
) : SwingModifier.NodeElement<Component, StepNode>() {
    val created = ArrayList<StepNode>()

    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): StepNode = StepNode().also { created += it }

    override fun update(node: StepNode) {
        node.outset = outset
    }

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

/** Declares a [StepNode] with [outset] of paint outsets, which the hand-over after the pass gathers. */
private class ReportingStep(
    private val outset: Int,
) : SwingModifier.NodeElement<Component, StepNode>() {
    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): StepNode = StepNode()

    override fun update(node: StepNode) {
        node.outset = outset
    }

    override fun equals(other: Any?): Boolean = other is ReportingStep && other.outset == outset

    override fun hashCode(): Int = outset
}

/**
 * Fills its area with [color], then paints its content over it, declaring [outset] of paint outsets on each side and
 * answering [opaque] as its opacity.
 */
private class StepNode : DecorationModifierNode<Component>() {
    var color: Color = Color.RED
    var outset = 0
    var opaque = true

    override val outsets: Insets get() = Insets(outset, outset, outset, outset)

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

/** Declares a node whose `update` throws where [failing], ending the pass that writes it. */
private class FailingToUpdate(
    private val failing: Boolean,
) : SwingModifier.NodeElement<Component, PlainNode>() {
    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): PlainNode = PlainNode()

    override fun update(node: PlainNode) {
        if (failing) error("update fails")
    }

    override fun equals(other: Any?): Boolean = other is FailingToUpdate && other.failing == failing

    override fun hashCode(): Int = failing.hashCode()
}

/** Declares a node whose `onAttach` throws, ending the pass that attaches it. */
private class FailingToAttach : SwingModifier.NodeElement<Component, PlainNode>() {
    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): PlainNode =
        object : PlainNode() {
            override fun onAttach() {
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
