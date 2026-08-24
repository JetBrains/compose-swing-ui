package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the typed listener builders not exercised in [RawListenerModifierTest], which proves the
 * by-identity attach/detach mechanism itself. Each test here confirms a builder registers the exact
 * listener instance on the live component, through the matching `getXxxListeners()` accessor (or, for
 * the document listener, on the field's document).
 *
 * Several tests also declare the same builder twice in one chain, the shape a text component takes
 * when it keeps its own listener beside a caller's; each declaration turns out to be its own
 * independent attachment.
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
            FlowPanel(modifier = SwingModifier.containerListener(listener)) {
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

    @Test
    fun documentListenerInstanceIsRegisteredOnTheFieldsDocument() = runComposeSwingTest {
        val listener: DocumentListener =
            object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = Unit

                override fun removeUpdate(e: DocumentEvent?) = Unit

                override fun changedUpdate(e: DocumentEvent?) = Unit
            }
        setContent {
            TextField("hello", onValueChange = {}, modifier = SwingModifier.documentListener(listener))
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

    @Test
    fun twoDocumentListenersInOneChainBothReceiveTheEdit() = runComposeSwingTest {
        var value by mutableStateOf("hello")
        var first = 0
        var second = 0
        val firstListener = insertListener { first++ }
        val secondListener = insertListener { second++ }
        setContent {
            TextField(
                value = value,
                onValueChange = { value = it },
                modifier = SwingModifier.documentListener(firstListener).documentListener(secondListener),
            )
        }
        val field = onNodeOfType<JTextComponent>().fetch()
        val attached = field.attachedDocumentListeners()
        assertTrue(attached.any { it === firstListener }, "the first declared listener should be attached")
        assertTrue(attached.any { it === secondListener }, "the second declared listener should be attached")

        field.type("a")
        awaitIdle()
        // Both declarations stay live: neither replaces the other, so one edit reaches both.
        assertEquals(1, first, "the first declared listener should receive the edit")
        assertEquals(1, second, "the second declared listener should receive the edit")
    }

    @Test
    fun droppingOneDeclarationDetachesOnlyThatListener() = runComposeSwingTest {
        var value by mutableStateOf("hello")
        var firstDeclared by mutableStateOf(true)
        var first = 0
        var second = 0
        val firstListener = insertListener { first++ }
        val secondListener = insertListener { second++ }
        setContent {
            TextField(
                value = value,
                onValueChange = { value = it },
                modifier =
                    SwingModifier
                        .let { if (firstDeclared) it.documentListener(firstListener) else it }
                        .documentListener(secondListener),
            )
        }
        val field = onNodeOfType<JTextComponent>().fetch()
        field.type("a")
        awaitIdle()
        assertEquals(1, first, "the first listener should receive the edit while it is declared")
        assertEquals(1, second, "the second listener should receive the edit while it is declared")

        firstDeclared = false
        awaitIdle()
        val attached = field.attachedDocumentListeners()
        assertTrue(attached.none { it === firstListener }, "the dropped listener must leave the document")
        assertTrue(attached.any { it === secondListener }, "the surviving listener must stay on the document")

        field.type("b")
        awaitIdle()
        assertEquals(1, first, "the dropped listener must not receive a later edit")
        assertEquals(2, second, "the surviving listener must keep receiving edits")
    }

    @Test
    fun oneInstanceDeclaredTwiceIsAttachedOncePerDeclaration() = runComposeSwingTest {
        var value by mutableStateOf("hello")
        var declaredTwice by mutableStateOf(true)
        var edits = 0
        val listener = insertListener { edits++ }
        setContent {
            TextField(
                value = value,
                onValueChange = { value = it },
                modifier =
                    SwingModifier
                        .documentListener(listener)
                        .let { if (declaredTwice) it.documentListener(listener) else it },
            )
        }
        val field = onNodeOfType<JTextComponent>().fetch()
        // An attachment belongs to the declaration that made it rather than to the instance, so one
        // instance declared twice is registered twice and hears every edit twice.
        assertEquals(
            2,
            field.attachedDocumentListeners().count { it === listener },
            "each declaration should register the instance on the document",
        )

        field.type("a")
        awaitIdle()
        assertEquals(2, edits, "the instance should be notified once per declaration")

        declaredTwice = false
        awaitIdle()
        assertEquals(
            1,
            field.attachedDocumentListeners().count { it === listener },
            "dropping one declaration should remove one of the two registrations",
        )

        field.type("b")
        awaitIdle()
        assertEquals(3, edits, "the remaining declaration should keep the instance notified once per edit")
    }

    // Counts the insertions a document reports; appended text reaches the document as an insert, so
    // that channel alone observes the edit.
    private fun insertListener(onInsert: () -> Unit): DocumentListener = object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent?): Unit = onInsert()

        override fun removeUpdate(e: DocumentEvent?) = Unit

        override fun changedUpdate(e: DocumentEvent?) = Unit
    }

    // Types [text] at the end of the component's content, the way a keystroke reaches the document.
    private fun JTextComponent.type(text: String) {
        document.insertString(document.length, text, null)
    }

    // The listener instances the component's document carries, duplicates included.
    private fun JTextComponent.attachedDocumentListeners(): List<DocumentListener> =
        (document as? AbstractDocument)?.documentListeners?.toList().orEmpty()
}
