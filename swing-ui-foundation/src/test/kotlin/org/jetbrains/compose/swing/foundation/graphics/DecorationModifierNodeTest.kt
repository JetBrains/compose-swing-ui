package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.mockk.mockk
import io.mockk.verify
import org.jetbrains.compose.swing.assertAskedToRepaint
import org.jetbrains.compose.swing.assertPixelColor
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.graphics.drawscope.ContentDrawScope
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.PaintOutsets
import org.jetbrains.compose.swing.foundation.layout.RawKind
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.foundation.layout.paintOutsets
import org.jetbrains.compose.swing.foundation.layout.placementLayer
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.key
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onAllNodesOfType
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
import kotlin.test.assertSame
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
                                .padding(4)
                                .then(Step())
                                .padding(4)
                                .fill(Color.BLUE)
                                .styleable(RawKind) { preferredSize(Dimension(32, 32)) },
                    )
                }
            }

            val image = onNodeWithTag("box").captureToImage()
            assertNotEquals(
                Color.RED.rgb,
                image.getRGB(2, 2),
                "The padding declared before the step keeps it off the edge.",
            )
            image.assertPixelColor(Color.RED, 6, 6, "The step paints at the box of the padding after it.")
            image.assertPixelColor(Color.BLUE, 10, 10, "What is declared after the step paints inside it.")
        }

    @Test
    fun aStepPassedAsADecoratorIsRefusedAsItIsDeclared() {
        val failure = assertFailsWith<IllegalArgumentException> { SwingModifier.decoration(StepNode()) }

        assertEquals(
            "org.jetbrains.compose.swing.foundation.graphics.StepNode is a decoration step, and only its element " +
                "attaches it. Declare that element through decoration.",
            failure.message,
        )
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
                                ).then(Step(outset = 4))
                                .styleable { preferredSize(Dimension(32, 32)) },
                    ) {
                        Label(text = "child", modifier = SwingModifier.testTag("child"))
                    }
                }
            }

            onAllNodesOfType<JComponent>().filterToOne(SwingMatcher.hasTestTag("panel")).assertProperty(
                Insets(4, 4, 4, 4),
                "The component reports the outsets the step declares.",
            ) { insets }
            onNodeWithTag("child").assertProperty(
                4,
                "The children are laid out inside the outsets the step reserves.",
            ) { x }
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
                            .then(step)
                            .styleable(RawKind) { preferredSize(Dimension(32, 32)) },
                )
            }
            val node = step.created.single()
            onNodeWithTag(
                "panel",
            ).captureToImage().assertPixelColor(Color.RED, 16, 16, "The step paints with its initial color.")
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
                onNodeWithTag(
                    "panel",
                ).captureToImage().assertPixelColor(
                    Color.GREEN,
                    16,
                    16,
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
                        modifier =
                            SwingModifier
                                .testTag("panel")
                                .then(step)
                                .styleable { preferredSize(Dimension(32, 32)) },
                    ) {
                        Label(text = "child", modifier = SwingModifier.testTag("child"))
                    }
                }
            }
            val node = step.created.single()
            onNodeWithTag("child").assertProperty(0, "Without outsets the child starts at the panel's edge.") { x }
            val panel = onNodeWithTag("panel").fetch<JComponent>()
            withRecordedRepaints { recorder ->
                node.outset = 5
                node.invalidateDecoration()

                assertEquals(
                    0,
                    recorder.relayoutsOver(panel),
                    "A step reserving different outsets revalidates nothing.",
                )
                onNodeWithTag("child").assertProperty(
                    5,
                    "The children are laid out inside the outsets the step now reserves.",
                ) { x }
                onNodeWithTag("child").assertProperty(
                    5,
                    "The children are laid out inside the outsets the step now reserves.",
                ) { y }
            }
        }

    @Test
    fun aStepThatReportedItsOutsetsLeavesNoOutsetsBehindOnceRemoved() =
        runComposeSwingTest {
            var declared by mutableStateOf(true)
            var outset by mutableIntStateOf(0)
            setContent {
                Box {
                    val modifier = SwingModifier.testTag("panel")
                    SwingNode(
                        factory = { DecoratedPanel(FlowLayout(FlowLayout.LEADING, 0, 0)) },
                        modifier =
                            (if (declared) modifier.then(ReportingStep(outset)) else modifier)
                                .styleable { preferredSize(Dimension(32, 32)) },
                    ) {
                        Label(text = "child", modifier = SwingModifier.testTag("child"))
                    }
                }
            }
            val child = onNodeWithTag("child").fetch<JComponent>()
            outset = 5
            awaitIdle()
            onNodeWithTag("child").assertProperty(5, "The step's element reports the outsets it reserves.") { x }

            declared = false
            awaitIdle()
            assertSame(
                child,
                onNodeWithTag("child").fetch<JComponent>(),
                "the component is updated in place, not rebuilt",
            )

            onNodeWithTag("child").assertProperty(0, "A removed step takes the outsets it reserved with it.") { x }
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
                            .fill(Color.BLUE)
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier =
                            (if (declared) modifier.then(Step()) else modifier)
                                .styleable(RawKind) { preferredSize(Dimension(32, 32)) },
                    )
                }
            }
            onNodeWithTag(
                "panel",
            ).captureToImage().assertPixelColor(Color.RED, 16, 16, "The step paints over the fill while declared.")

            declared = false
            awaitIdle()

            onNodeWithTag(
                "panel",
            ).captureToImage().assertPixelColor(Color.BLUE, 16, 16, "Removing the step leaves the fill showing.")
        }

    @Test
    fun stepsAttachingInOnePassAreHandedToTheComponentOnce() =
        runComposeSwingTest {
            var declared by mutableStateOf(false)
            setContent {
                val modifier = SwingModifier.testTag("panel")
                SwingNode(
                    factory = { WriteCountingPanel() },
                    modifier =
                        (if (declared) modifier.then(Step(1)).then(Step(1)).then(Step(1)) else modifier)
                            .styleable { preferredSize(Dimension(32, 32)) },
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
                // Read here rather than in the style, so the change reaches the value and the step in one pass.
                val value = if (changed) PaintOutsets.None else PaintOutsets.Decoration
                SwingNode(factory = { JPanel() }) {
                    SwingNode(
                        factory = { WriteCountingPanel() },
                        modifier =
                            SwingModifier
                                .testTag("panel")
                                .then(Step(if (changed) 2 else 1))
                                .styleable {
                                    preferredSize(Dimension(32, 32))
                                    paintOutsets(value)
                                },
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
            val node = step.created.single()
            onNodeWithTag("panel").assertProperty(
                true,
                "A step covering its whole area leaves an opaque panel opaque.",
            ) { isOpaque }

            node.opaque = false
            node.invalidateDecoration()
            onNodeWithTag("panel").assertProperty(
                false,
                "A step that stops covering its area makes the panel stop being opaque.",
            ) { isOpaque }

            node.opaque = true
            node.invalidateDecoration()
            onNodeWithTag("panel").assertProperty(
                true,
                "and one covering it again makes it opaque again.",
            ) { isOpaque }
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
                            .then(DecoratorElement(if (failing) null else Cut()))
                            .then(FailingToUpdate(failing))
                            .styleable { preferredSize(Dimension(32, 32)) },
                )
            }
            val panel = onNodeWithTag("panel").fetch<JComponent>()
            panel.paintOnto(32, 32).assertPixelColor(Color(0, true), 0, 0, "The cut takes the corner away.")

            failing = true
            val thrown = assertFailsWith<IllegalStateException> { awaitIdle() }
            assertEquals("update fails", thrown.message, "The pass writing the step throws after it.")

            panel
                .paintOnto(
                    32,
                    32,
                ).assertPixelColor(Color.RED, 0, 0, "A step left holding no decorator paints its content unchanged.")
        }

    @Test
    fun aStepDetachedInAPassThatThrowsPaintsNoMore() =
        runComposeSwingTest {
            var failing by mutableStateOf(false)
            setContent {
                SwingNode(
                    factory = { DecoratedPanel().apply { isOpaque = false } },
                    modifier =
                        SwingModifier
                            .testTag("panel")
                            .let {
                                if (failing) {
                                    it.key(2).then(FailingToAttach())
                                } else {
                                    it.key(1).then(ComponentReadingStep())
                                }
                            }.styleable { preferredSize(Dimension(32, 32)) },
                )
            }
            val panel = onNodeWithTag("panel").fetch<JComponent>()
            panel.background = Color.RED
            panel.paintOnto(32, 32).assertPixelColor(Color.RED, 16, 16, "The step fills the panel.")

            failing = true
            val thrown = assertFailsWith<IllegalStateException> { awaitIdle() }
            assertEquals("onAttach fails", thrown.message, "The pass detaching the step throws as it attaches another.")

            panel
                .paintOnto(
                    32,
                    32,
                ).assertPixelColor(Color(0, true), 16, 16, "A step detached by the pass paints no more.")
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
            assertEquals(undecorated, panel.isOpaque, "and reports isOpaque as it did before the step.")
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
            assertEquals(undecorated, panel.isOpaque, "and reports isOpaque as it did before the step.")
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
                                .clip(CircleShape, antialias = true)
                                .styleable { preferredSize(60, 60) },
                    )
                }
            }

            // The panel leaves with the composition, whose teardown holds each departing step to the restore check.
            onNodeWithTag("subject").assertProperty(
                false,
                "A faded, clipped panel is not opaque.",
            ) { isOpaque }
        }

    @Test
    fun aClipLeavingAPanelThatALayerStillFadesIsNotOwedItsOpacity() =
        runComposeSwingTest {
            var clipped by mutableStateOf(true)
            setContent {
                Row {
                    val sized = SwingModifier.placementLayer { alpha = 0.5f }.testTag("subject")
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier =
                            (if (clipped) sized.clip(CircleShape, antialias = true) else sized)
                                .styleable { preferredSize(60, 60) },
                    )
                }
            }

            clipped = false
            awaitIdle()

            onNodeWithTag("subject").assertProperty(
                false,
                "The layer still fades the panel.",
            ) { isOpaque }
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
    fun aStepReadsItsComponentAsADecoratableWhileAttachedAndFailsOnceDetached() =
        runComposeSwingTest {
            var declared by mutableStateOf(true)
            val step = DecoratableReadingStep()
            setContent {
                SwingNode(
                    factory = { DecoratedPanel() },
                    modifier = SwingModifier.testTag("panel").let { if (declared) it.then(step) else it },
                )
            }
            val node = step.created.single()
            val panel = onNodeWithTag("panel").fetch<DecoratedPanel>()

            assertSame(panel, node.readDecoratable(), "an attached step reads its component")

            declared = false
            awaitIdle()

            assertFailsWith<IllegalStateException> { node.readDecoratable() }
        }

    @Test
    fun aStepIsToldItLeavesTheDecorationWhenRemovedReplacedOrItsComponentLeaves() =
        runComposeSwingTest {
            val counter = RemovalCounter(mockk(relaxed = true))
            var declared by mutableStateOf(true)
            var replaced by mutableStateOf(false)
            var componentPresent by mutableStateOf(true)
            var observedValue by mutableIntStateOf(0)
            setContent {
                if (componentPresent) {
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier =
                            SwingModifier
                                .testTag("panel")
                                .let { base ->
                                    when {
                                        !declared -> base
                                        replaced -> base.then(Step())
                                        else -> base.then(CountingStep(counter, observedValue))
                                    }
                                }.styleable { preferredSize(Dimension(32, 32)) },
                    )
                }
            }
            val node = counter.created.single()

            // (1) An in-place update reaches the same node's element through recomposition, and tells no step it left.
            observedValue = 1
            awaitIdle()
            assertEquals(1, node.observedValue, "the recomposed element's update reaches the same node")
            verify(exactly = 0) { counter.onRemoved(true) }

            // (2) Dropping the element from the modifier.
            declared = false
            awaitIdle()
            verify(exactly = 1) { counter.onRemoved(true) }

            // (3) Declaring it again, then replacing it in the same slot by a different element type.
            declared = true
            awaitIdle()
            replaced = true
            awaitIdle()
            verify(exactly = 2) { counter.onRemoved(true) }

            // (4) Declaring it again, then removing the whole component from composition.
            replaced = false
            declared = true
            awaitIdle()
            componentPresent = false
            awaitIdle()
            verify(exactly = 3) { counter.onRemoved(true) }

            verify(exactly = 0) { counter.onRemoved(false) }
        }
}

/** Counts how often a [CountingStepNode] is told it left the decoration, across every instance created. */
private class RemovalCounter(
    val onRemoved: (Boolean) -> Unit,
) {
    val created = ArrayList<CountingStepNode>()
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
        counter.onRemoved(isAttached)
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

private class DecoratableReadingStep : SwingModifier.NodeElement<Component, DecoratableReadingNode>() {
    val created = ArrayList<DecoratableReadingNode>()

    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): DecoratableReadingNode = DecoratableReadingNode().also { created += it }

    override fun update(node: DecoratableReadingNode) = Unit

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

private class DecoratableReadingNode : DecorationModifierNode<Component>() {
    fun readDecoratable(): Decoratable = decoratable

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
 * reporting [opaque] as its opacity.
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
