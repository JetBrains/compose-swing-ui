package org.jetbrains.compose.swing.modifier.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.image.BufferedImage
import javax.swing.AbstractButton
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JMenuItem
import kotlin.test.Test
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
                    setContent { FlowPanel(modifier = SwingModifier.icon(icon())) }
                }
            }

        val message = failure.message.orEmpty()
        assertTrue("icon" in message, "the message should name the property: $message")
        assertTrue(JLabel::class.java.name in message, "the message should name a served type: $message")
    }
}
