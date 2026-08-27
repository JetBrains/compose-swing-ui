package org.jetbrains.compose.swing.test.interaction

import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins what the single-event deliveries put on a component: a real event, shaped the way the toolkit
 * shapes one, reaching the component's own handling.
 *
 * These are what the `perform` gestures are composed from, and the components here are built by hand and
 * belong to no composition, so what each test asserts is the widget behavior that only follows from a
 * correctly shaped event - not the event itself.
 */
class ComponentInputEventTest {
    @Test
    fun clickingFiresTheButtonThroughItsOwnUi() = runComposeSwingTest {
        var clicks = 0
        val button = JButton("Go").apply { addActionListener { clicks++ } }
        button.size = button.preferredSize

        button.deliverMousePress()
        button.deliverMouseRelease()
        button.deliverMouseClicked()

        assertEquals(1, clicks, "a click at the middle of the button is one the UI resolves and fires")
    }

    @Test
    fun typingInsertsCharactersThroughTheEditor() = runComposeSwingTest {
        val field = JTextField(10)
        field.size = field.preferredSize

        for (character in "hi") {
            field.deliverKeyPressed(
                KeyEvent
                    .getExtendedKeyCodeForChar(character.code),
            )
            field.deliverKeyTyped(character)
            field.deliverKeyReleased(
                KeyEvent
                    .getExtendedKeyCodeForChar(character.code),
            )
        }

        assertEquals("hi", field.text, "each typed character reaches the document through the editor")
    }
}
