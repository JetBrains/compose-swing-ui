package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JPasswordField
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A parked [ReusableContentHost] child detaches its `JPasswordField`, and reactivation builds a fresh
 * one from the node's own factory. The look-and-feel echo character a `null` declaration restores is
 * captured from that field as its modifier chain attaches, so it has to travel with whichever component
 * is live rather than with the composition - a reactivated field that lost it would unmask the password.
 */
class PasswordFieldNodeReuseTest {
    @Test
    fun aReactivatedPasswordFieldKeepsMaskingWithTheLookAndFeelEchoCharacter() = runComposeSwingTest {
        val lookAndFeelEchoChar = JPasswordField().echoChar
        var active by mutableStateOf(true)
        setContent {
            ReusableContentHost(active = active) {
                PasswordField(value = "hunter2".toCharArray(), onValueChange = {})
            }
        }
        assertEquals(
            lookAndFeelEchoChar,
            onNodeOfType<JPasswordField>().fetch().echoChar,
            "the field should start on the look-and-feel mask",
        )

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        assertEquals(
            lookAndFeelEchoChar,
            onNodeOfType<JPasswordField>().fetch().echoChar,
            "a reactivated field should still mask with the look-and-feel echo character",
        )
    }

    @Test
    fun aReactivatedPasswordFieldStillRestoresTheDefaultMaskAfterACustomOne() = runComposeSwingTest {
        val lookAndFeelEchoChar = JPasswordField().echoChar
        var active by mutableStateOf(true)
        var echoChar by mutableStateOf<Char?>('#')
        setContent {
            ReusableContentHost(active = active) {
                PasswordField(value = "hunter2".toCharArray(), onValueChange = {}, echoChar = echoChar)
            }
        }
        assertEquals(
            '#',
            onNodeOfType<JPasswordField>().fetch().echoChar,
            "the declared echo character should be applied",
        )

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        echoChar = null
        awaitIdle()

        assertEquals(
            lookAndFeelEchoChar,
            onNodeOfType<JPasswordField>().fetch().echoChar,
            "null should still restore the look-and-feel echo character on a reactivated field",
        )
    }
}
