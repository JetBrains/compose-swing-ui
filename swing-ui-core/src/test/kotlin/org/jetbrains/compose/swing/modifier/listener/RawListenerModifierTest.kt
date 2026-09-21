// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertDeclaredChainCarriedOnce
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.countTests
import org.jetbrains.compose.swing.fixtures.modifier.listener.ComponentListenerChangeFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ComponentListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ComponentListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ContainerListenerDirectionalFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ContainerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ContainerListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ListenerOnEventFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.MouseListenerEventFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.MouseListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.MouseListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.MouseMotionListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.MouseMotionListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.PropertyChangeListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.PropertyChangeListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.PropertyChangeListenerNameFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.PropertyChangeListenerTargetFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.rawSwingTests
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.TestFactory
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.AdjustmentListener
import java.awt.event.ComponentAdapter
import java.awt.event.ContainerAdapter
import java.awt.event.FocusAdapter
import java.awt.event.HierarchyListener
import java.awt.event.ItemListener
import java.awt.event.KeyAdapter
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionAdapter
import java.awt.event.MouseWheelListener
import java.beans.PropertyChangeListener
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioral tests for the typed instance listener builders. They assert what an observer of the live
 * Swing component sees.
 */
class RawListenerModifierTest {
    private fun mousePressed(component: Component): MouseEvent =
        MouseEvent(component, MouseEvent.MOUSE_PRESSED, 0L, 0, 1, 1, 1, false)

    private fun mousePressListener(onPress: () -> Unit): MouseListener = object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent): Unit = onPress()
    }

    @Test
    fun existingListenerInstanceFiresOnTheRealEvent() = runComposeSwingTest {
        var pressed = 0
        val listener = mousePressListener { pressed++ }
        setContent {
            Button("X", onClick = { }, modifier = SwingModifier.mouseListener(listener))
        }
        val button = onNodeOfType<JButton>().fetch()
        assertTrue(button.mouseListeners.any { it === listener }, "the exact listener instance should be registered")

        button.dispatchEvent(mousePressed(button))
        assertEquals(1, pressed, "the registered listener should fire once on a press")
    }

    @Test
    fun removingTheElementDetachesTheSameInstance() = runComposeSwingTest {
        var attached by mutableStateOf(true)
        var pressed = 0
        val listener = mousePressListener { pressed++ }
        setContent {
            Button(
                "X",
                onClick = { },
                modifier = if (attached) SwingModifier.mouseListener(listener) else SwingModifier,
            )
        }
        val button = onNodeOfType<JButton>().fetch()
        assertTrue(button.mouseListeners.any { it === listener }, "the listener should be registered while present")

        attached = false
        awaitIdle()
        assertTrue(
            button.mouseListeners.none {
                it === listener
            },
            "the exact instance must be detached when its element leaves",
        )
        button.dispatchEvent(mousePressed(button))
        assertEquals(0, pressed, "the detached instance must not fire after its element leaves the chain")
    }

    @Test
    fun twoListenersOfTheSameTypeBothInstall() = runComposeSwingTest {
        var first = 0
        var second = 0
        val firstListener = mousePressListener { first++ }
        val secondListener = mousePressListener { second++ }
        setContent {
            Button(
                "X",
                onClick = { },
                modifier = SwingModifier.mouseListener(firstListener).mouseListener(secondListener),
            )
        }
        val button = onNodeOfType<JButton>().fetch()
        assertTrue(button.mouseListeners.any { it === firstListener }, "the first listener should be installed")
        assertTrue(button.mouseListeners.any { it === secondListener }, "the second listener should be installed")

        button.dispatchEvent(mousePressed(button))
        assertEquals(1, first, "the first listener should fire once")
        assertEquals(1, second, "the second listener should fire once")
    }

    @Test
    fun swappingTheInstanceDetachesTheOldAndAttachesTheNew() = runComposeSwingTest {
        var useFirst by mutableStateOf(true)
        var first = 0
        var second = 0
        val firstListener = mousePressListener { first++ }
        val secondListener = mousePressListener { second++ }
        setContent {
            Button(
                "X",
                onClick = { },
                modifier = SwingModifier.mouseListener(if (useFirst) firstListener else secondListener),
            )
        }
        val button = onNodeOfType<JButton>().fetch()
        assertTrue(
            button.mouseListeners.any { it === firstListener },
            "the first instance should be installed initially",
        )
        button.dispatchEvent(mousePressed(button))
        assertEquals(1, first, "the first instance should fire while installed")

        useFirst = false
        awaitIdle()
        assertTrue(button.mouseListeners.none { it === firstListener }, "swapping should detach the old instance")
        assertTrue(button.mouseListeners.any { it === secondListener }, "swapping should attach the new instance")

        button.dispatchEvent(mousePressed(button))
        assertEquals(1, first, "the swapped-out instance must no longer fire")
        assertEquals(1, second, "the swapped-in instance must fire")
    }

    @Test
    fun boundPropertyChangeListenerInstanceFiresOnlyOnItsProperty() = runComposeSwingTest {
        var seenNew: Any? = null
        var fired = 0
        val listener =
            PropertyChangeListener { event ->
                fired++
                seenNew = event.newValue
            }
        setContent {
            Label("X", modifier = SwingModifier.propertyChangeListener("enabled", listener))
        }
        val label = onNodeOfType<JLabel>().fetch()

        label.toolTipText = "changed"
        assertEquals(0, fired, "a different bound property must not notify the enabled-bound listener")

        label.isEnabled = false
        assertEquals(1, fired, "the enabled-bound listener should fire on its own property")
        assertEquals(false, seenNew, "the listener should receive the new property value")
    }

    @Test
    fun unboundPropertyChangeListenerInstanceFiresOncePerChangeOnAnyBoundProperty() = runComposeSwingTest {
        val seenProperties = mutableListOf<String?>()
        var fired = 0
        val listener =
            PropertyChangeListener { event ->
                fired++
                seenProperties += event.propertyName
            }
        setContent {
            Label("X", modifier = SwingModifier.propertyChangeListener(listener))
        }
        val label = onNodeOfType<JLabel>().fetch()
        // Joining the tree is itself a bound-property change, "ancestor"; only the changes below count.
        fired = 0
        seenProperties.clear()

        label.isEnabled = false
        assertEquals(1, fired, "the first bound-property change must notify the unbound listener once")

        label.toolTipText = "changed"
        assertEquals(2, fired, "a second, distinct bound-property change must notify it once more")

        assertTrue(
            "enabled" in seenProperties && "ToolTipText" in seenProperties,
            "the unbound listener must see both changed properties, but saw: $seenProperties",
        )
    }

    @Test
    fun everyLambdaListenerBuilderAppendsToTheChainWithoutRepeatingIt() {
        assertDeclaredChainCarriedOnce { adjustmentListener { } }
        assertDeclaredChainCarriedOnce { componentListener { } }
        assertDeclaredChainCarriedOnce { componentListener(onComponentResized = { }) }
        assertDeclaredChainCarriedOnce { containerListener { } }
        assertDeclaredChainCarriedOnce { containerListener(onComponentAdded = { }) }
        assertDeclaredChainCarriedOnce { focusListener { } }
        assertDeclaredChainCarriedOnce { focusListener(onFocusGained = { }) }
        assertDeclaredChainCarriedOnce { hierarchyListener { } }
        assertDeclaredChainCarriedOnce { itemListener { } }
        assertDeclaredChainCarriedOnce { itemListener<JComboBox<String>> { } }
        assertDeclaredChainCarriedOnce { itemListener(JButton::class) { } }
        assertDeclaredChainCarriedOnce { keyListener { } }
        assertDeclaredChainCarriedOnce { keyListener(onKeyTyped = { }) }
        assertDeclaredChainCarriedOnce { mouseListener { } }
        assertDeclaredChainCarriedOnce { mouseListener(onMouseClicked = { }) }
        assertDeclaredChainCarriedOnce { mouseMotionListener { } }
        assertDeclaredChainCarriedOnce { mouseMotionListener(onMouseDragged = { }) }
        assertDeclaredChainCarriedOnce { mouseWheelListener { } }
        assertDeclaredChainCarriedOnce { propertyChangeListener { } }
        assertDeclaredChainCarriedOnce { propertyChangeListener("enabled") { } }
        assertDeclaredChainCarriedOnce { propertyChangeListener<JButton>("enabled") { } }
        assertDeclaredChainCarriedOnce { propertyChangeListener("enabled", JButton::class) { } }
        assertDeclaredChainCarriedOnce { propertyChangeListener("enabled", PropertyChangeListener { }) }
    }

    @Test
    fun everyRawListenerBuilderAppendsToTheChainWithoutRepeatingIt() {
        val registration =
            ListenerRegistration<JButton, ActionListener>(
                name = "actionListener",
                { component, listener -> component.addActionListener(listener) },
                { component, listener -> component.removeActionListener(listener) },
            )
        val callbacks =
            CallbackRegistration<JButton, (ActionEvent) -> Unit, ActionListener>(
                { callback -> ActionListener { event -> callback.report { it(event) } } },
                registration,
            )

        assertDeclaredChainCarriedOnce { adjustmentListener(AdjustmentListener { }) }
        assertDeclaredChainCarriedOnce { componentListener(object : ComponentAdapter() {}) }
        assertDeclaredChainCarriedOnce { containerListener(object : ContainerAdapter() {}) }
        assertDeclaredChainCarriedOnce { focusListener(object : FocusAdapter() {}) }
        assertDeclaredChainCarriedOnce { hierarchyListener(HierarchyListener { }) }
        assertDeclaredChainCarriedOnce { itemListener(ItemListener { }) }
        assertDeclaredChainCarriedOnce { keyListener(object : KeyAdapter() {}) }
        assertDeclaredChainCarriedOnce { listener(ActionListener { }, registration) }
        assertDeclaredChainCarriedOnce { listener({ _: ActionEvent -> }, callbacks) }
        assertDeclaredChainCarriedOnce { listener(JButton::class, ActionListener { }, registration) }
        assertDeclaredChainCarriedOnce { listener(JButton::class, { _: ActionEvent -> }, callbacks) }
        assertDeclaredChainCarriedOnce { mouseListener(object : MouseAdapter() {}) }
        assertDeclaredChainCarriedOnce { mouseMotionListener(object : MouseMotionAdapter() {}) }
        assertDeclaredChainCarriedOnce { mouseWheelListener(MouseWheelListener { }) }
        assertDeclaredChainCarriedOnce { propertyChangeListener(PropertyChangeListener { }) }
        assertDeclaredChainCarriedOnce { listener<JButton, ActionEvent, ActionListener>(JButton::class, callbacks) { } }
    }

    @TestFactory
    fun mouseListenerEventRawSwing() = MouseListenerEventFixture.rawSwingTests()

    @TestFactory
    fun mouseListenerEventCounts() = MouseListenerEventFixture.countTests()

    @TestFactory
    fun mouseListenerRawSwing() = MouseListenerFixture.rawSwingTests()

    @TestFactory
    fun mouseListenerListenerRawSwing() = MouseListenerListenerFixture.rawSwingTests()

    @TestFactory
    fun mouseListenerListenerCounts() = MouseListenerListenerFixture.countTests()

    @TestFactory
    fun propertyChangeListenerRawSwing() = PropertyChangeListenerFixture.rawSwingTests()

    @TestFactory
    fun propertyChangeListenerCounts() = PropertyChangeListenerFixture.countTests()

    @TestFactory
    fun propertyChangeListenerListenerRawSwing() = PropertyChangeListenerListenerFixture.rawSwingTests()

    @TestFactory
    fun propertyChangeListenerListenerCounts() = PropertyChangeListenerListenerFixture.countTests()

    @TestFactory
    fun propertyChangeListenerNameRawSwing() = PropertyChangeListenerNameFixture.rawSwingTests()

    @TestFactory
    fun propertyChangeListenerTargetRawSwing() = PropertyChangeListenerTargetFixture.rawSwingTests()

    @TestFactory
    fun propertyChangeListenerTargetCounts() = PropertyChangeListenerTargetFixture.countTests()

    @TestFactory
    fun componentListenerChangeRawSwing() = ComponentListenerChangeFixture.rawSwingTests()

    @TestFactory
    fun componentListenerChangeCounts() = ComponentListenerChangeFixture.countTests()

    @TestFactory
    fun componentListenerRawSwing() = ComponentListenerFixture.rawSwingTests()

    @TestFactory
    fun componentListenerCounts() = ComponentListenerFixture.countTests()

    @TestFactory
    fun componentListenerListenerRawSwing() = ComponentListenerListenerFixture.rawSwingTests()

    @TestFactory
    fun componentListenerListenerCounts() = ComponentListenerListenerFixture.countTests()

    @TestFactory
    fun containerListenerDirectionalRawSwing() = ContainerListenerDirectionalFixture.rawSwingTests()

    @TestFactory
    fun containerListenerDirectionalCounts() = ContainerListenerDirectionalFixture.countTests()

    @TestFactory
    fun containerListenerRawSwing() = ContainerListenerFixture.rawSwingTests()

    @TestFactory
    fun containerListenerCounts() = ContainerListenerFixture.countTests()

    @TestFactory
    fun containerListenerListenerRawSwing() = ContainerListenerListenerFixture.rawSwingTests()

    @TestFactory
    fun containerListenerListenerCounts() = ContainerListenerListenerFixture.countTests()

    @TestFactory
    fun listenerRawSwing() = ListenerFixture.rawSwingTests()

    @TestFactory
    fun listenerCounts() = ListenerFixture.countTests()

    @TestFactory
    fun listenerOnEventRawSwing() = ListenerOnEventFixture.rawSwingTests()

    @TestFactory
    fun listenerOnEventCounts() = ListenerOnEventFixture.countTests()

    @TestFactory
    fun mouseMotionListenerRawSwing() = MouseMotionListenerFixture.rawSwingTests()

    @TestFactory
    fun mouseMotionListenerCounts() = MouseMotionListenerFixture.countTests()

    @TestFactory
    fun mouseListenerCounts() = MouseListenerFixture.countTests()

    @TestFactory
    fun propertyChangeListenerNameCounts() = PropertyChangeListenerNameFixture.countTests()

    @TestFactory
    fun mouseMotionListenerListenerRawSwing() = MouseMotionListenerListenerFixture.rawSwingTests()

    @TestFactory
    fun mouseMotionListenerListenerCounts() = MouseMotionListenerListenerFixture.countTests()
}
