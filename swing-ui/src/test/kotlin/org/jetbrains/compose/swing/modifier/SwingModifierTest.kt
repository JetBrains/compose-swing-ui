package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.modifier.interaction.onHover
import org.jetbrains.compose.swing.modifier.interaction.onPointerEvent
import org.jetbrains.compose.swing.modifier.layout.alignmentX
import org.jetbrains.compose.swing.modifier.layout.alignmentY
import org.jetbrains.compose.swing.modifier.layout.componentOrientation
import org.jetbrains.compose.swing.modifier.layout.maximumSize
import org.jetbrains.compose.swing.modifier.layout.minimumSize
import org.jetbrains.compose.swing.modifier.layout.visible
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.modifier.listener.mouseMotionListener
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.Component
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavioral tests for [SwingModifier]: they assert what an observer of the live Swing component
 * sees - applied properties, reaction to recomposition, restoration when an element is removed, and
 * real listener behavior under dispatched events - never the internal diff/record machinery.
 */
class SwingModifierTest {
    private fun mouseEntered(component: Component): MouseEvent =
        MouseEvent(component, MouseEvent.MOUSE_ENTERED, 0L, 0, 0, 0, 0, false)

    private fun mouseClicked(component: Component): MouseEvent =
        MouseEvent(component, MouseEvent.MOUSE_CLICKED, 0L, 0, 0, 0, 1, false)

    private fun mouseMoved(component: Component): MouseEvent =
        MouseEvent(component, MouseEvent.MOUSE_MOVED, 0L, 0, 0, 0, 0, false)

    private fun mouseEnterListener(onEnter: () -> Unit): MouseListener = object : MouseAdapter() {
        override fun mouseEntered(e: MouseEvent?): Unit = onEnter()
    }

    @Test
    fun nameModifierMakesComponentFindable() = runComposeSwingTest {
        setContent { Button("Save", modifier = SwingModifier.name("save-button")) }
        onNodeWithName("save-button").assert(SwingMatcher.isOfType<JButton>())
    }

    @Test
    fun appearanceModifierAppliesAndReactsToState() = runComposeSwingTest {
        var accent by mutableStateOf(true)
        setContent {
            Label("X", modifier = SwingModifier.foreground(if (accent) Color.RED else Color.BLUE))
        }
        val label = onNodeOfType<JLabel>()
        assertEquals(Color.RED, label.fetch<JLabel>().foreground, "the modifier should apply the initial color")

        accent = false
        awaitIdle()
        assertEquals(Color.BLUE, label.fetch<JLabel>().foreground, "the modifier should react to the state change")
    }

    @Test
    fun multipleModifierElementsAllApply() = runComposeSwingTest {
        setContent {
            Label(
                "X",
                modifier =
                    SwingModifier
                        .foreground(Color.RED)
                        .background(Color.BLUE)
                        .opaque(true),
            )
        }
        val label = onNodeOfType<JLabel>().fetch()
        assertEquals(Color.RED, label.foreground, "the foreground element should apply")
        assertEquals(Color.BLUE, label.background, "the background element should apply")
        assertTrue(label.isOpaque, "the opaque element should apply")
    }

    @Test
    fun removingAnElementRestoresThePriorDefault() = runComposeSwingTest {
        var styled by mutableStateOf(true)
        setContent {
            Label("untouched")
            Label("styled", modifier = if (styled) SwingModifier.background(Color.YELLOW) else SwingModifier)
        }
        val styledLabel = onNodeWithText("styled")
        val default = onNodeWithText("untouched").fetch<JLabel>().background
        assertEquals(
            Color.YELLOW,
            styledLabel.fetch<JLabel>().background,
            "the element should apply the background while present",
        )

        styled = false
        awaitIdle()
        // The element left the chain, so the background is restored to what it was before the
        // modifier first touched it (the same default the untouched control still shows).
        assertEquals(
            default,
            styledLabel.fetch<JLabel>().background,
            "removing the element should restore the prior default",
        )
    }

    @Test
    fun hoverListenerFiresAndStopsAfterItsElementIsRemoved() = runComposeSwingTest {
        var hoverEnabled by mutableStateOf(true)
        var enterCount = 0
        setContent {
            Button(
                "X",
                modifier = if (hoverEnabled) SwingModifier.onHover(onEnter = { enterCount++ }) else SwingModifier,
            )
        }
        val button = onNodeOfType<JButton>().fetch()

        button.dispatchEvent(mouseEntered(button))
        assertEquals(1, enterCount, "the hover listener should fire while its element is present")

        hoverEnabled = false
        awaitIdle()
        button.dispatchEvent(mouseEntered(button))
        assertEquals(1, enterCount, "listener must be removed when its element leaves the chain")
    }

    @Test
    fun hoverListenerSeesTheLatestCallbackWithoutReinstalling() = runComposeSwingTest {
        var target by mutableStateOf("first")
        var captured = ""
        setContent {
            Button("X", modifier = SwingModifier.onHover(onEnter = { captured = target }))
        }
        val button = onNodeOfType<JButton>().fetch()

        button.dispatchEvent(mouseEntered(button))
        assertEquals("first", captured, "the hover listener should read the first callback")

        target = "second"
        awaitIdle()
        button.dispatchEvent(mouseEntered(button))
        assertEquals("second", captured, "the installed listener must read the latest callback")
    }

    @Test
    fun enabledModifierAppliesAndRestoresOnRemoval() = runComposeSwingTest {
        var disabled by mutableStateOf(true)
        setContent {
            Button("X", modifier = if (disabled) SwingModifier.enabled(false) else SwingModifier)
        }
        val button = onNodeOfType<JButton>()
        button.assertIsNotEnabled()

        disabled = false
        awaitIdle()
        // The element left the chain, so isEnabled is restored to the pre-modifier default.
        button.assertIsEnabled()
    }

    @Test
    fun visibleModifierAppliesAndRestoresOnRemoval() = runComposeSwingTest {
        var hidden by mutableStateOf(true)
        setContent {
            Button("X", modifier = if (hidden) SwingModifier.visible(false) else SwingModifier)
        }
        val button = onNodeOfType<JButton>()
        button.assertIsNotVisible()

        hidden = false
        awaitIdle()
        // The element left the chain, so isVisible is restored to the pre-modifier default.
        button.assertIsVisible()
    }

    @Test
    fun minimumSizeModifierAppliesAndRestoresOnRemoval() = runComposeSwingTest {
        var constrained by mutableStateOf(true)
        setContent {
            Label("X", modifier = if (constrained) SwingModifier.minimumSize(120, 40) else SwingModifier)
        }
        val constrainedLabel = onNodeOfType<JLabel>().fetch()
        assertEquals(Dimension(120, 40), constrainedLabel.minimumSize, "the modifier should apply the minimum size")
        assertTrue(constrainedLabel.isMinimumSizeSet, "the minimum-size-set flag should be on while present")

        constrained = false
        awaitIdle()
        // The element left the chain, so the explicit minimum size is cleared again.
        assertFalse(
            onNodeOfType<JLabel>().fetch().isMinimumSizeSet,
            "removing the modifier should clear the minimum-size-set flag",
        )
    }

    @Test
    fun maximumSizeModifierAppliesAndRestoresOnRemoval() = runComposeSwingTest {
        var constrained by mutableStateOf(true)
        setContent {
            Label("X", modifier = if (constrained) SwingModifier.maximumSize(Dimension(200, 80)) else SwingModifier)
        }
        val constrainedLabel = onNodeOfType<JLabel>().fetch()
        assertEquals(Dimension(200, 80), constrainedLabel.maximumSize, "the modifier should apply the maximum size")
        assertTrue(constrainedLabel.isMaximumSizeSet, "the maximum-size-set flag should be on while present")

        constrained = false
        awaitIdle()
        // The element left the chain, so the explicit maximum size is cleared again.
        assertFalse(
            onNodeOfType<JLabel>().fetch().isMaximumSizeSet,
            "removing the modifier should clear the maximum-size-set flag",
        )
    }

    @Test
    fun maximumSizeWidthHeightOverloadAppliesTheDimension() = runComposeSwingTest {
        setContent {
            Label("X", modifier = SwingModifier.maximumSize(200, 80))
        }
        val label = onNodeOfType<JLabel>().fetch()
        assertEquals(Dimension(200, 80), label.maximumSize, "the overload should apply the dimension")
        assertTrue(label.isMaximumSizeSet, "the overload should set the maximum-size-set flag")
    }

    @Test
    fun alignmentXModifierAppliesTheSetValue() = runComposeSwingTest {
        setContent {
            Label("X", modifier = SwingModifier.alignmentX(0.0f))
        }
        assertEquals(
            0.0f,
            onNodeOfType<JLabel>().fetch().alignmentX,
            "the modifier should set the x alignment",
        )
    }

    @Test
    fun alignmentYModifierAppliesTheSetValue() = runComposeSwingTest {
        setContent {
            Label("X", modifier = SwingModifier.alignmentY(1.0f))
        }
        assertEquals(
            1.0f,
            onNodeOfType<JLabel>().fetch().alignmentY,
            "the modifier should set the y alignment",
        )
    }

    @Test
    fun componentOrientationModifierAppliesAndRestoresOnRemoval() = runComposeSwingTest {
        var rtl by mutableStateOf(true)
        setContent {
            Label("untouched")
            Label(
                "styled",
                modifier =
                    if (rtl) SwingModifier.componentOrientation(ComponentOrientation.RIGHT_TO_LEFT) else SwingModifier,
            )
        }
        val styledLabel = onNodeWithText("styled")
        val default = onNodeWithText("untouched").fetch<JLabel>().componentOrientation
        assertEquals(
            ComponentOrientation.RIGHT_TO_LEFT,
            styledLabel.fetch<JLabel>().componentOrientation,
            "the modifier should apply the RTL orientation",
        )

        rtl = false
        awaitIdle()
        // The element left the chain, so the orientation is restored to the pre-modifier default
        // (the same one the untouched control still shows).
        assertEquals(
            default,
            styledLabel.fetch<JLabel>().componentOrientation,
            "removing the modifier should restore the default orientation",
        )
    }

    @Test
    fun twoListenersOnOneComponentBothFire() = runComposeSwingTest {
        var first = 0
        var second = 0
        setContent {
            Button(
                "X",
                modifier =
                    SwingModifier
                        .listener<JButton, MouseListener>(
                            mouseEnterListener { first++ },
                            { component, listener -> component.addMouseListener(listener) },
                            { component, listener -> component.removeMouseListener(listener) },
                        ).listener<JButton, MouseListener>(
                            mouseEnterListener { second++ },
                            { component, listener -> component.addMouseListener(listener) },
                            { component, listener -> component.removeMouseListener(listener) },
                        ),
            )
        }
        val button = onNodeOfType<JButton>().fetch()

        button.dispatchEvent(mouseEntered(button))
        // Listeners are additive: neither slot replaces the other, so both fire.
        assertEquals(1, first, "the first listener should fire once")
        assertEquals(1, second, "the second listener should fire once")
    }

    @Test
    fun twoHoverModifiersBothFire() = runComposeSwingTest {
        var first = 0
        var second = 0
        setContent {
            Button(
                "X",
                modifier =
                    SwingModifier
                        .onHover(onEnter = { first++ })
                        .onHover(onEnter = { second++ }),
            )
        }
        val button = onNodeOfType<JButton>().fetch()

        button.dispatchEvent(mouseEntered(button))
        // Two onHover are additive (each its own slot), so both enter callbacks fire.
        assertEquals(1, first, "the first hover modifier should fire once")
        assertEquals(1, second, "the second hover modifier should fire once")
    }

    @Test
    fun repeatedPropertyElementStillLastWins() = runComposeSwingTest {
        setContent {
            Label("X", modifier = SwingModifier.background(Color.RED).background(Color.BLUE))
        }
        // Property elements keep last-wins semantics: two backgrounds collapse, the later wins.
        assertEquals(Color.BLUE, onNodeOfType<JLabel>().fetch().background)
    }

    @Test
    fun removingOneAdditiveListenerLeavesTheOtherInstalled() = runComposeSwingTest {
        var firstHoverEnabled by mutableStateOf(true)
        var first = 0
        var second = 0
        setContent {
            Button(
                "X",
                modifier =
                    SwingModifier
                        .let { if (firstHoverEnabled) it.onHover(onEnter = { first++ }) else it }
                        .onHover(onEnter = { second++ }),
            )
        }
        val button = onNodeOfType<JButton>().fetch()

        button.dispatchEvent(mouseEntered(button))
        assertEquals(1, first, "the first listener should fire once before removal")
        assertEquals(1, second, "the second listener should fire once before removal")

        // Drop the first additive listener. Positional identity shifts the survivor, so it is
        // detached and reinstalled - but it stays live and keeps firing.
        firstHoverEnabled = false
        awaitIdle()
        button.dispatchEvent(mouseEntered(button))
        assertEquals(1, first, "the removed listener must not fire again")
        assertEquals(2, second, "the surviving listener must stay installed and keep firing")
    }

    @Test
    fun survivingListenerKeepsFiringWhenAKindChangeShiftsItsPosition() = runComposeSwingTest {
        var hoverEnabled by mutableStateOf(true)
        var clickCount = 0
        setContent {
            Button(
                "X",
                modifier =
                    SwingModifier
                        .let { if (hoverEnabled) it.onHover(onEnter = {}) else it }
                        .onPointerEvent(onClick = { clickCount++ }),
            )
        }
        val button = onNodeOfType<JButton>().fetch()

        button.dispatchEvent(mouseClicked(button))
        assertEquals(1, clickCount, "the click listener should fire before the shape change")

        // Dropping the conditional hover shifts the click element onto the hover's position. The
        // kinds differ, so the slot swaps wholesale - the click listener must survive the shift.
        hoverEnabled = false
        awaitIdle()
        button.dispatchEvent(mouseClicked(button))
        assertEquals(2, clickCount, "the surviving click listener must keep firing after the kind change")
    }

    @Test
    fun listenerLeavingTheChainIsDetachedWhenAKindChangeShiftsPositions() = runComposeSwingTest {
        var hoverEnabled by mutableStateOf(true)
        var enterCount = 0
        setContent {
            Button(
                "X",
                modifier =
                    SwingModifier
                        .let { if (hoverEnabled) it.onHover(onEnter = { enterCount++ }) else it }
                        .onPointerEvent(onClick = {}),
            )
        }
        val button = onNodeOfType<JButton>().fetch()

        button.dispatchEvent(mouseEntered(button))
        assertEquals(1, enterCount, "the hover listener should fire while its element is present")

        // The hover leaves the chain and a different kind takes over its position: the hover node
        // is detached, so its listener is gone from the component.
        hoverEnabled = false
        awaitIdle()
        button.dispatchEvent(mouseEntered(button))
        assertEquals(1, enterCount, "the hover listener must be detached when its element leaves the chain")
    }

    @Test
    fun instanceListenerKindChangeAtOnePositionSwapsInstances() = runComposeSwingTest {
        var mouseEnabled by mutableStateOf(true)
        var enterCount = 0
        var moveCount = 0
        val mouse = mouseEnterListener { enterCount++ }
        val motion =
            object : MouseMotionListener {
                override fun mouseMoved(e: MouseEvent?) {
                    moveCount++
                }

                override fun mouseDragged(e: MouseEvent?) = Unit
            }
        setContent {
            Button(
                "X",
                modifier =
                    SwingModifier
                        .let { if (mouseEnabled) it.mouseListener(mouse) else it }
                        .mouseMotionListener(motion),
            )
        }
        val button = onNodeOfType<JButton>().fetch()

        button.dispatchEvent(mouseEntered(button))
        button.dispatchEvent(mouseMoved(button))
        assertEquals(1, enterCount, "the mouse listener should fire before the shape change")
        assertEquals(1, moveCount, "the motion listener should fire before the shape change")

        // The two builders share an element class but pair different listener types with their own
        // add/remove calls: shifting the motion listener onto the mouse listener's position must
        // remove the mouse listener through its own pairing and keep the motion listener live.
        mouseEnabled = false
        awaitIdle()
        button.dispatchEvent(mouseEntered(button))
        button.dispatchEvent(mouseMoved(button))
        assertEquals(1, enterCount, "the mouse listener must be detached when its element leaves the chain")
        assertEquals(2, moveCount, "the surviving motion listener must keep firing after the kind change")
    }

    @Test
    fun sameKindPersistingPositionKeepsTheListenerInstalledWithoutReattaching() = runComposeSwingTest {
        var label by mutableStateOf("first")
        var attachCount = 0
        var detachCount = 0
        var enterCount = 0
        val stable = mouseEnterListener { enterCount++ }
        setContent {
            Button(
                label,
                modifier =
                    SwingModifier.listener<JButton, MouseListener>(
                        stable,
                        { component, listener ->
                            attachCount++
                            component.addMouseListener(listener)
                        },
                        { component, listener ->
                            detachCount++
                            component.removeMouseListener(listener)
                        },
                    ),
            )
        }
        assertEquals(1, attachCount, "the listener should be attached once on first apply")

        // Recomposition rebuilds the chain with a fresh element instance. The position persists with
        // the same kind, so the slot keeps its node and the stable instance is not re-registered.
        label = "second"
        awaitIdle()
        assertEquals(1, attachCount, "a same-kind persisting position must not re-attach the listener")
        assertEquals(0, detachCount, "a same-kind persisting position must not detach the listener")

        val button = onNodeOfType<JButton>().fetch()
        button.dispatchEvent(mouseEntered(button))
        assertEquals(1, enterCount, "the persisting listener must still fire after recomposition")
    }

    @Test
    fun reuseDrainsBothPropertyAndAdditiveRecordsThenReinstalls() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var enterCount = 0
        setContent {
            ReusableContentHost(active = active) {
                Label(
                    "X",
                    modifier =
                        SwingModifier
                            .background(Color.GREEN)
                            .onHover(onEnter = { enterCount++ }),
                )
            }
        }
        val beforeReuse = onNodeOfType<JLabel>().fetch()
        assertEquals(Color.GREEN, beforeReuse.background, "the property element should apply before reuse")
        beforeReuse.dispatchEvent(mouseEntered(beforeReuse))
        assertEquals(1, enterCount, "the hover listener should fire once before reuse")

        // Deactivate then reactivate: resetModifierState drains both the keyed property record
        // (restoring the background) and the additive hover record (detaching the listener).
        active = false
        awaitIdle()
        active = true
        awaitIdle()

        // Property re-applied and listener re-installed on the reused node.
        val afterReuse = onNodeOfType<JLabel>().fetch()
        assertEquals(Color.GREEN, afterReuse.background, "the property element must be re-applied after reuse")
        afterReuse.dispatchEvent(mouseEntered(afterReuse))
        assertEquals(2, enterCount, "the additive listener must be re-installed after reuse")
    }

    @Test
    fun customElementWrapsAnArbitraryProperty() = runComposeSwingTest {
        setContent { Button("X", modifier = SwingModifier.then(ToolTipElement("hello"))) }
        assertEquals("hello", onNodeOfType<JButton>().fetch().toolTipText)
    }

    @Test
    fun readingNodeComponentBeforeAttachFailsWithADiagnosticMessage() = runComposeSwingTest {
        // The target is injected between create() and onAttach(), so an element author reading the
        // node's component from create() is told what went wrong instead of hitting a null target.
        val failure =
            assertFailsWith<IllegalStateException> {
                setContent { Label("X", modifier = SwingModifier.then(EarlyComponentReadElement())) }
            }
        assertEquals(NOT_ATTACHED_MESSAGE, failure.message, "an unattached node should name the reason")
    }

    @Test
    fun readingNodeComponentAfterItsElementLeavesTheChainFailsWithADiagnosticMessage() = runComposeSwingTest {
        var styled by mutableStateOf(true)
        val nodes = ArrayList<ToolTipElement.Node>()
        setContent {
            Button(
                "X",
                modifier = if (styled) SwingModifier.then(ToolTipElement("hello", nodes::add)) else SwingModifier,
            )
        }
        assertEquals("hello", onNodeOfType<JButton>().fetch().toolTipText)
        val node = nodes.single()

        // The element leaves the chain, so its node no longer owns the component: reading the target
        // fails exactly as it does before attach, instead of handing out a widget it may not touch.
        styled = false
        awaitIdle()
        val failure = assertFailsWith<IllegalStateException> { node.component }
        assertEquals(NOT_ATTACHED_MESSAGE, failure.message, "a detached node should name the reason")
    }

    /**
     * An element that reads its node's component from [SwingModifier.NodeElement.create], before the
     * applier has injected a target.
     */
    private class EarlyComponentReadElement :
        SwingModifier.NodeElement<JComponent, EarlyComponentReadElement.Node>() {
        override val targetType: Class<JComponent> get() = JComponent::class.java

        override fun create(): Node = Node().also { it.component }

        override fun update(node: Node) = Unit

        override fun equals(other: Any?): Boolean = this === other

        override fun hashCode(): Int = System.identityHashCode(this)

        class Node : SwingModifier.Node<JComponent>()
    }

    /**
     * A user-authored element proving the public [SwingModifier.NodeElement] escape hatch works. It
     * targets [JComponent] via [targetType], so the node's `component` arrives already typed and the
     * body performs no cast. It captures the original tooltip in [Node.onAttach], writes the new value
     * in `update`, and restores the original in [Node.onDetach].
     */
    private class ToolTipElement(
        private val text: String,
        private val onCreate: (Node) -> Unit = {},
    ) : SwingModifier.NodeElement<JComponent, ToolTipElement.Node>() {
        override val targetType: Class<JComponent> get() = JComponent::class.java

        override fun create(): Node = Node().also(onCreate)

        override fun update(node: Node) {
            node.text = text
            node.apply()
        }

        override fun equals(other: Any?): Boolean =
            other is ToolTipElement && text == other.text && onCreate === other.onCreate

        override fun hashCode(): Int = 31 * text.hashCode() + System.identityHashCode(onCreate)

        class Node : SwingModifier.Node<JComponent>() {
            var text: String? = null
            private var original: String? = null

            override fun onAttach() {
                original = component.toolTipText
            }

            fun apply() {
                component.toolTipText = text
            }

            override fun onDetach() {
                component.toolTipText = original
            }
        }
    }

    private companion object {
        /** The message [SwingModifier.Node.component] fails with outside the attached window. */
        const val NOT_ATTACHED_MESSAGE = "Node is not attached"
    }
}
