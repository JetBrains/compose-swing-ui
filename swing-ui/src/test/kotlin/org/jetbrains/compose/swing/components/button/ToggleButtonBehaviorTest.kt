package org.jetbrains.compose.swing.components.button

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.image.BufferedImage
import javax.swing.ImageIcon
import javax.swing.JToggleButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for [ToggleButton], driven through the real composition pipeline and asserting
 * against the live `JToggleButton`.
 *
 * The central guarantees: text and icon render onto the button; clicking toggles the selected state
 * and reports the new value; the pressed state is controlled from composition; and an external
 * pressed-state update applies without echoing back as a spurious callback.
 */
class ToggleButtonBehaviorTest {
    private fun ComposeSwingTest.toggle(): JToggleButton = onNodeOfType<JToggleButton>().fetch()

    @Test
    fun textRendersOntoTheButton() = runComposeSwingTest {
        setContent { ToggleButton(text = "Bold") }

        assertEquals("Bold", toggle().text)
    }

    @Test
    fun clickingTogglesAndReportsTheNewState() = runComposeSwingTest {
        var pressed by mutableStateOf(false)
        val received = mutableListOf<Boolean>()
        setContent {
            ToggleButton(
                text = "Bold",
                pressed = pressed,
                onPressedChange = {
                    received += it
                    pressed = it
                },
            )
        }

        val button = toggle()
        assertFalse(button.isSelected, "starts unpressed")

        onNodeWithText("Bold").performClick()
        assertTrue(button.isSelected, "click presses the button")
        assertEquals(listOf(true), received, "reports the new pressed state")

        onNodeWithText("Bold").performClick()
        assertFalse(button.isSelected, "second click releases the button")
        assertEquals(listOf(true, false), received, "the second click reports the released state")
    }

    @Test
    fun controlledPressedStateAppliesWithoutCallback() = runComposeSwingTest {
        var pressed by mutableStateOf(false)
        val received = mutableListOf<Boolean>()
        setContent {
            ToggleButton(text = "Bold", pressed = pressed, onPressedChange = { received += it })
        }

        val button = toggle()
        assertFalse(button.isSelected, "the button should start unpressed")

        // Pushing pressed=true from composition must update the button without firing the callback:
        // only a user-driven click reports through onPressedChange.
        pressed = true
        awaitIdle()

        assertTrue(button.isSelected, "external pressed state applied")
        assertEquals(emptyList(), received, "a controlled update must not fire onPressedChange")
    }

    @Test
    fun iconRendersOntoTheButton() = runComposeSwingTest {
        val icon = ImageIcon(BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB))
        setContent { ToggleButton(text = "Star", icon = icon) }

        assertSame(icon, toggle().icon, "icon installed on the button")
    }
}
