package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.setContent
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
import javax.swing.JComponent
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
 * matching `getXxxListeners()` accessor (or, for the document listener, on the field's document) — the
 * observable proof that the builder wires the correct AWT registration site.
 */
class RawListenerBuilderAttachmentTest {
    @Test
    fun keyListenerInstanceIsRegistered() = runComposeSwingTest {
        val listener: KeyListener = object : KeyAdapter() {}
        setContent { Button("X", modifier = SwingModifier.name("b").keyListener(listener)) }
        assertTrue(onNodeWithName("b").fetch<JComponent>().keyListeners.any { it === listener })
    }

    @Test
    fun focusListenerInstanceIsRegistered() = runComposeSwingTest {
        val listener: FocusListener = object : FocusAdapter() {}
        setContent { Button("X", modifier = SwingModifier.name("b").focusListener(listener)) }
        assertTrue(onNodeWithName("b").fetch<JComponent>().focusListeners.any { it === listener })
    }

    @Test
    fun componentListenerInstanceIsRegistered() = runComposeSwingTest {
        val listener: ComponentListener = object : ComponentAdapter() {}
        setContent { Button("X", modifier = SwingModifier.name("b").componentListener(listener)) }
        assertTrue(onNodeWithName("b").fetch<JComponent>().componentListeners.any { it === listener })
    }

    @Test
    fun mouseMotionListenerInstanceIsRegistered() = runComposeSwingTest {
        val listener: MouseMotionListener = object : MouseAdapter() {}
        setContent { Button("X", modifier = SwingModifier.name("b").mouseMotionListener(listener)) }
        assertTrue(onNodeWithName("b").fetch<JComponent>().mouseMotionListeners.any { it === listener })
    }

    @Test
    fun mouseWheelListenerInstanceIsRegistered() = runComposeSwingTest {
        val listener = MouseWheelListener { }
        setContent { Button("X", modifier = SwingModifier.name("b").mouseWheelListener(listener)) }
        assertTrue(onNodeWithName("b").fetch<JComponent>().mouseWheelListeners.any { it === listener })
    }

    @Test
    fun hierarchyListenerInstanceIsRegistered() = runComposeSwingTest {
        val listener = HierarchyListener { }
        setContent { Button("X", modifier = SwingModifier.name("b").hierarchyListener(listener)) }
        assertTrue(onNodeWithName("b").fetch<JComponent>().hierarchyListeners.any { it === listener })
    }

    @Test
    fun containerListenerInstanceIsRegisteredOnAPanel() = runComposeSwingTest {
        val listener: ContainerListener = object : ContainerAdapter() {}
        setContent {
            Panel(modifier = SwingModifier.name("p").containerListener(listener)) {
                Button("child")
            }
        }
        val panel = onNodeWithName("p").fetch<Container>()
        assertTrue(panel.containerListeners.any { it === listener })
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
            TextField("hello", modifier = SwingModifier.name("tf").documentListener(listener))
        }
        val field = onNodeWithName("tf").fetch<JTextComponent>()
        // AbstractDocument exposes its registered DocumentListeners; the exact instance must be there.
        assertTrue(
            run {
                val document = field.document
                document is AbstractDocument &&
                    document.documentListeners.any { it === listener }
            },
        )
    }
}
