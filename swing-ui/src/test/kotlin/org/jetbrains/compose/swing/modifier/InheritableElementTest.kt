package org.jetbrains.compose.swing.modifier

import org.jetbrains.compose.swing.modifier.accessibility.mnemonic
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.cursor
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.horizontalAlignment
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.modifier.interaction.focusTraversalIndex
import org.jetbrains.compose.swing.modifier.keyboard.onKeyStroke
import org.jetbrains.compose.swing.modifier.layout.componentOrientation
import org.jetbrains.compose.swing.modifier.layout.location
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.listener.actionListener
import java.awt.Color
import java.awt.Component
import java.awt.ComponentOrientation
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.event.KeyEvent
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InheritableElementTest {
    @Test
    fun standardPropertiesAreInheritableAndNonAdditive() {
        val modifiers =
            listOf(
                SwingModifier.background(Color.RED),
                SwingModifier.foreground(Color.BLUE),
                SwingModifier.font(Font(Font.MONOSPACED, Font.PLAIN, 12)),
                SwingModifier.enabled(false),
                SwingModifier.opaque(true),
                SwingModifier.cursor(Cursor.getDefaultCursor()),
                SwingModifier.componentOrientation(ComponentOrientation.LEFT_TO_RIGHT),
                SwingModifier.horizontalAlignment(SwingConstants.CENTER),
            )

        for (modifier in modifiers) {
            var found = false
            modifier.foldIn(Unit) { _, element ->
                if (element is SwingModifier.NodeElement<*, *>) {
                    found = true
                    assertTrue(element.inheritable, "Element '${element.name}' should be inheritable")
                    assertFalse(element.additive, "Element '${element.name}' must not be additive")
                }
            }
            assertTrue(found, "Modifier should contain at least one NodeElement")
        }
    }

    @Test
    fun behaviorAndPlacementElementsAreNotInheritable() {
        val modifiers =
            listOf(
                SwingModifier.actionListener { },
                SwingModifier.onKeyStroke(KeyStroke.getKeyStroke("control C")) {},
                SwingModifier.toolTip("hint"),
                SwingModifier.border(EmptyBorder(1, 1, 1, 1)),
                SwingModifier.preferredSize(Dimension(10, 10)),
                SwingModifier.location(0, 0),
                SwingModifier.focusTraversalIndex(1),
                SwingModifier.mnemonic(KeyEvent.VK_A),
            )

        for (modifier in modifiers) {
            modifier.foldIn(Unit) { _, element ->
                if (element is SwingModifier.NodeElement<*, *>) {
                    assertFalse(
                        element.inheritable,
                        "Element '${element.name}' should not be inheritable",
                    )
                }
            }
        }
    }

    @Test
    fun customNodeElementDefaultsToNotInheritable() {
        class CustomNode : SwingModifier.ComponentNode<Component>()

        class CustomElement : SwingModifier.NodeElement<Component, CustomNode>() {
            override val targetType: Class<Component> get() = Component::class.java

            override fun create(): CustomNode = CustomNode()

            override fun update(node: CustomNode) = Unit

            override fun equals(other: Any?): Boolean = this === other

            override fun hashCode(): Int = System.identityHashCode(this)
        }

        val element = CustomElement()
        assertFalse(element.inheritable, "Custom NodeElement must default to inheritable = false")
        assertFalse(element.additive, "Custom NodeElement must default to additive = false")
    }
}
