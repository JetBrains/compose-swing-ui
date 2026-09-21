// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.countTests
import org.jetbrains.compose.swing.fixtures.modifier.HierarchyListenerCallbackFixture
import org.jetbrains.compose.swing.fixtures.modifier.HierarchyListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.MouseWheelListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.MouseWheelListenerListenerFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.rawSwingTests
import org.jetbrains.compose.swing.test.interaction.onParent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.TestFactory
import java.awt.Container
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentListener
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerListener
import java.awt.event.FocusAdapter
import java.awt.event.FocusListener
import java.awt.event.HierarchyListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseMotionListener
import java.awt.event.MouseWheelListener
import javax.swing.JButton
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Covers the typed listener builders not exercised in [RawListenerModifierTest], which proves the
 * by-identity attach/detach mechanism itself. Each test here confirms a builder registers the exact
 * listener instance on the live component, through the matching `getXxxListeners()` accessor.
 */
class RawListenerBuilderAttachmentTest {
    @Test
    fun keyListenerInstanceIsRegistered() = runComposeSwingTest {
        val listener: KeyListener = object : KeyAdapter() {}
        setContent { Button("X", onClick = { }, modifier = SwingModifier.keyListener(listener)) }
        assertTrue(
            onNodeOfType<JButton>().fetch().keyListeners.any { it === listener },
            "the exact key listener instance should be registered on the button",
        )
    }

    @Test
    fun focusListenerInstanceIsRegistered() = runComposeSwingTest {
        val listener: FocusListener = object : FocusAdapter() {}
        setContent { Button("X", onClick = { }, modifier = SwingModifier.focusListener(listener)) }
        assertTrue(
            onNodeOfType<JButton>().fetch().focusListeners.any { it === listener },
            "the exact focus listener instance should be registered on the button",
        )
    }

    @Test
    fun componentListenerInstanceIsRegistered() = runComposeSwingTest {
        val listener: ComponentListener = object : ComponentAdapter() {}
        setContent { Button("X", onClick = { }, modifier = SwingModifier.componentListener(listener)) }
        assertTrue(
            onNodeOfType<JButton>().fetch().componentListeners.any { it === listener },
            "the exact component listener instance should be registered on the button",
        )
    }

    @Test
    fun mouseMotionListenerInstanceIsRegistered() = runComposeSwingTest {
        val listener: MouseMotionListener = object : MouseAdapter() {}
        setContent { Button("X", onClick = { }, modifier = SwingModifier.mouseMotionListener(listener)) }
        assertTrue(
            onNodeOfType<JButton>().fetch().mouseMotionListeners.any { it === listener },
            "the exact mouse motion listener instance should be registered on the button",
        )
    }

    @Test
    fun mouseWheelListenerInstanceIsRegistered() = runComposeSwingTest {
        val listener = MouseWheelListener { }
        setContent { Button("X", onClick = { }, modifier = SwingModifier.mouseWheelListener(listener)) }
        assertTrue(
            onNodeOfType<JButton>().fetch().mouseWheelListeners.any { it === listener },
            "the exact mouse wheel listener instance should be registered on the button",
        )
    }

    @Test
    fun hierarchyListenerInstanceIsRegistered() = runComposeSwingTest {
        val listener = HierarchyListener { }
        setContent { Button("X", onClick = { }, modifier = SwingModifier.hierarchyListener(listener)) }
        assertTrue(
            onNodeOfType<JButton>().fetch().hierarchyListeners.any { it === listener },
            "the exact hierarchy listener instance should be registered on the button",
        )
    }

    @Test
    fun containerListenerInstanceIsRegisteredOnAPanel() = runComposeSwingTest {
        val listener: ContainerListener = object : ContainerAdapter() {}
        setContent {
            Panel(PanelLayout.Flow(), modifier = SwingModifier.containerListener(listener)) {
                Button("child", onClick = { })
            }
        }
        // The panel is the container the declared child ended up in.
        val panel = onNodeWithText("child").onParent().fetch<Container>()
        assertTrue(
            panel.containerListeners.any { it === listener },
            "the exact container listener instance should be registered on the panel",
        )
    }

    @TestFactory
    fun hierarchyListenerCallbackRawSwing() = HierarchyListenerCallbackFixture.rawSwingTests()

    @TestFactory
    fun hierarchyListenerCallbackCounts() = HierarchyListenerCallbackFixture.countTests()

    @TestFactory
    fun hierarchyListenerRawSwing() = HierarchyListenerFixture.rawSwingTests()

    @TestFactory
    fun hierarchyListenerCounts() = HierarchyListenerFixture.countTests()

    @TestFactory
    fun mouseWheelListenerRawSwing() = MouseWheelListenerFixture.rawSwingTests()

    @TestFactory
    fun mouseWheelListenerCounts() = MouseWheelListenerFixture.countTests()

    @TestFactory
    fun mouseWheelListenerListenerRawSwing() = MouseWheelListenerListenerFixture.rawSwingTests()

    @TestFactory
    fun mouseWheelListenerListenerCounts() = MouseWheelListenerListenerFixture.countTests()
}
