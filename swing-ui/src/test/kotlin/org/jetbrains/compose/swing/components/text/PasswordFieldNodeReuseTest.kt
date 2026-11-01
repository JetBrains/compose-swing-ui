package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JPasswordField
import kotlin.test.Test

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
        onNodeOfType<JPasswordField>().assertProperty(
            lookAndFeelEchoChar,
            "the field should start on the look-and-feel mask",
        ) { echoChar }

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        onNodeOfType<JPasswordField>().assertProperty(
            lookAndFeelEchoChar,
            "a reactivated field should still mask with the look-and-feel echo character",
        ) { echoChar }
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
        onNodeOfType<JPasswordField>().assertProperty(
            '#',
            "the declared echo character should be applied",
        ) { this.echoChar }

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        echoChar = null
        awaitIdle()

        onNodeOfType<JPasswordField>().assertProperty(
            lookAndFeelEchoChar,
            "null should still restore the look-and-feel echo character on a reactivated field",
        ) { this.echoChar }
    }
}
