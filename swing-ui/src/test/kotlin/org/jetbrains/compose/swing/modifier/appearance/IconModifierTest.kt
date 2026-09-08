package org.jetbrains.compose.swing.modifier.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Font
import java.awt.image.BufferedImage
import javax.swing.AbstractButton
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JMenuItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * An icon is a property of the component, not of one wrapper, so it is carried by a modifier that
 * reaches every component able to show one. A label and a button name the property separately, with
 * nothing between them declaring it, so these also pin that both are served and that a component
 * serving neither is rejected rather than silently ignored. A menu item is built on the same class as a
 * button and reached through a menu tree rather than a component tree, so it is asserted for itself.
 */
class IconModifierTest {
    private fun icon() = ImageIcon(BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB))

    @Test
    fun anIconReachesALabel() = runComposeSwingTest {
        val icon = icon()
        setContent { Label("Legend", modifier = SwingModifier.icon(icon)) }

        assertSame(icon, onNodeOfType<JLabel>().fetch().icon)
    }

    @Test
    fun anIconReachesAButton() = runComposeSwingTest {
        val icon = icon()
        setContent { Button("Save", onClick = { }, modifier = SwingModifier.icon(icon)) }

        assertSame(icon, onNodeOfType<JButton>().fetch().icon)
    }

    @Test
    fun removingAnIconLeavesTheFontALookAndFeelWorkedOutFromIt() = runComposeSwingTest {
        val icon = icon()
        var declared by mutableStateOf(false)
        setContent {
            Button(
                "Save",
                onClick = { },
                modifier = if (declared) SwingModifier.icon(icon) else SwingModifier,
            )
        }
        val button = onNodeOfType<JButton>().fetch()
        val original = button.font
        val derived = original.deriveFont(original.size2D - 2f)
        // A look and feel that styles a button for carrying an icon at all and leaves it styled when the
        // icon goes. Standing in for one here, the case says the same thing on every platform.
        button.addPropertyChangeListener("icon") { event -> if (event.newValue != null) button.font = derived }

        declared = true
        awaitIdle()
        assertEquals(derived, button.font, "the deriving stand-in should have restyled the button")

        declared = false
        awaitIdle()
        assertNull(button.icon, "removing the declaration should put the icon back")
        assertEquals(derived, button.font, "the font worked out from the icon is not the declaration's to put back")
    }

    @Test
    fun droppingAFontLeavesTheFontTheStandingIconDerives() = runComposeSwingTest {
        val icon = icon()
        val declaredFont = Font("Monospaced", Font.BOLD, 22)
        var decorated by mutableStateOf(false)
        var fontDeclared by mutableStateOf(false)
        setContent {
            val chain = if (decorated) SwingModifier.icon(icon) else SwingModifier
            Button("Save", onClick = { }, modifier = if (fontDeclared) chain.font(declaredFont) else chain)
        }
        val button = onNodeOfType<JButton>().fetch()
        val original = button.font
        val derived = original.deriveFont(original.size2D - 2f)
        // A look and feel that styles the component for carrying an icon at all, which is what the font
        // the modifier leaves standing has to answer to.
        button.addPropertyChangeListener("icon") { button.font = derived }

        decorated = true
        awaitIdle()
        assertEquals(derived, button.font, "the deriving stand-in should have restyled the button")

        fontDeclared = true
        awaitIdle()
        assertEquals(declaredFont, button.font, "the declared font should stand over the derived one")

        fontDeclared = false
        awaitIdle()
        assertEquals(
            derived,
            button.font,
            "dropping the font should leave the font the standing icon derives, not the one the modifier found",
        )
    }

    @Test
    fun anIconReachesACheckBox() = runComposeSwingTest {
        val icon = icon()
        setContent { CheckBox("Enabled", checked = false, onCheckedChange = {}, modifier = SwingModifier.icon(icon)) }

        assertSame(icon, onNodeOfType<JCheckBox>().fetch().icon)
    }

    @Test
    fun anIconReachesAMenuItem() = runComposeSwingTest {
        val icon = icon()
        val popup = composeMenu { MenuItem("Open", onClick = { }, modifier = SwingModifier.icon(icon)) }

        assertSame(icon, (popup.getComponent(0) as JMenuItem).icon)
    }

    @Test
    fun aChangedIconReplacesTheOne() = runComposeSwingTest {
        val first = icon()
        val second = icon()
        var current by mutableStateOf(first)
        setContent { Label("Legend", modifier = SwingModifier.icon(current)) }

        val label = onNodeOfType<JLabel>().fetch()
        assertSame(first, label.icon, "the declared icon")

        current = second
        awaitIdle()

        assertSame(second, label.icon, "the icon follows the state driving it")
    }

    @Test
    fun droppingTheModifierRestoresTheIconTheComponentHad() = runComposeSwingTest {
        var decorated by mutableStateOf(true)
        setContent {
            Button("Save", onClick = { }, modifier = if (decorated) SwingModifier.icon(icon()) else SwingModifier)
        }

        val button = onNodeOfType<AbstractButton>().fetch()
        assertTrue(button.icon != null, "the icon is installed while declared")

        decorated = false
        awaitIdle()

        // The element captured what the button had before it applied - no icon - and puts it back.
        assertNull(button.icon, "dropping the modifier restores the component's own icon")
    }

    @Test
    fun aComponentThatShowsNoIconIsRejected() {
        val failure =
            assertFailsWith<IllegalStateException> {
                runComposeSwingTest {
                    setContent { Panel(PanelLayout.Flow(), modifier = SwingModifier.icon(icon())) {} }
                }
            }

        val message = failure.message.orEmpty()
        assertTrue("icon" in message, "the message should name the property: $message")
        assertTrue(JLabel::class.java.name in message, "the message should name a served type: $message")
    }
}
