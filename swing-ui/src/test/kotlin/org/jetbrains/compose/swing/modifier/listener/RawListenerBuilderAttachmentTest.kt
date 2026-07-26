package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.interaction.onParent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
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
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.AbstractDocument
import javax.swing.text.JTextComponent
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Coverage for the remaining typed instance listener builders not exercised in
 * [RawListenerModifierTest]. The by-identity attach/detach mechanism itself is proved there; here each
 * builder is asserted to register the exact listener instance on the live component through the
 * matching `getXxxListeners()` accessor (or, for the document listener, on the field's document) - the
 * observable proof that the builder wires the correct AWT registration site.
 */
class RawListenerBuilderAttachmentTest {
    @Test
    fun keyListenerInstanceIsRegistered() = runComposeSwingTest {
        val listener: KeyListener = object : KeyAdapter() {}
        setContent { Button("X", modifier = SwingModifier.keyListener(listener)) }
        assertTrue(
            onNodeOfType<JButton>().fetch().keyListeners.any { it === listener },
            "the exact key listener instance should be registered on the button",
        )
    }

    @Test
    fun focusListenerInstanceIsRegistered() = runComposeSwingTest {
        val listener: FocusListener = object : FocusAdapter() {}
        setContent { Button("X", modifier = SwingModifier.focusListener(listener)) }
        assertTrue(
            onNodeOfType<JButton>().fetch().focusListeners.any { it === listener },
            "the exact focus listener instance should be registered on the button",
        )
    }

    @Test
    fun componentListenerInstanceIsRegistered() = runComposeSwingTest {
        val listener: ComponentListener = object : ComponentAdapter() {}
        setContent { Button("X", modifier = SwingModifier.componentListener(listener)) }
        assertTrue(
            onNodeOfType<JButton>().fetch().componentListeners.any { it === listener },
            "the exact component listener instance should be registered on the button",
        )
    }

    @Test
    fun mouseMotionListenerInstanceIsRegistered() = runComposeSwingTest {
        val listener: MouseMotionListener = object : MouseAdapter() {}
        setContent { Button("X", modifier = SwingModifier.mouseMotionListener(listener)) }
        assertTrue(
            onNodeOfType<JButton>().fetch().mouseMotionListeners.any { it === listener },
            "the exact mouse motion listener instance should be registered on the button",
        )
    }

    @Test
    fun mouseWheelListenerInstanceIsRegistered() = runComposeSwingTest {
        val listener = MouseWheelListener { }
        setContent { Button("X", modifier = SwingModifier.mouseWheelListener(listener)) }
        assertTrue(
            onNodeOfType<JButton>().fetch().mouseWheelListeners.any { it === listener },
            "the exact mouse wheel listener instance should be registered on the button",
        )
    }

    @Test
    fun hierarchyListenerInstanceIsRegistered() = runComposeSwingTest {
        val listener = HierarchyListener { }
        setContent { Button("X", modifier = SwingModifier.hierarchyListener(listener)) }
        assertTrue(
            onNodeOfType<JButton>().fetch().hierarchyListeners.any { it === listener },
            "the exact hierarchy listener instance should be registered on the button",
        )
    }

    @Test
    fun containerListenerInstanceIsRegisteredOnAPanel() = runComposeSwingTest {
        val listener: ContainerListener = object : ContainerAdapter() {}
        setContent {
            FlowPanel(modifier = SwingModifier.containerListener(listener)) {
                Button("child")
            }
        }
        // The panel is the container the declared child ended up in.
        val panel = onNodeWithText("child").onParent().fetch<Container>()
        assertTrue(
            panel.containerListeners.any { it === listener },
            "the exact container listener instance should be registered on the panel",
        )
    }

    @Test
    fun documentListenerInstanceIsRegisteredOnTheFieldsDocument() = runComposeSwingTest {
        val listener: DocumentListener =
            object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = Unit

                override fun removeUpdate(e: DocumentEvent?) = Unit

                override fun changedUpdate(e: DocumentEvent?) = Unit
            }
        setContent {
            TextField("hello", modifier = SwingModifier.documentListener(listener))
        }
        val field = onNodeOfType<JTextComponent>().fetch()
        // AbstractDocument exposes its registered DocumentListeners; the exact instance must be there.
        assertTrue(
            run {
                val document = field.document
                document is AbstractDocument &&
                    document.documentListeners.any { it === listener }
            },
            "the exact document listener instance should be registered on the field's document",
        )
    }
}
