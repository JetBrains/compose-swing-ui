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
 * A password-field node is recyclable: a parked [ReusableContentHost] child is reactivated onto the
 * `JPasswordField` the node already holds, and the node's factory does not run a second time. The
 * look-and-feel echo character a `null` declaration restores is captured from that field, so it has to
 * travel with the component rather than with the composition - a reactivated field that lost it would
 * unmask the password.
 */
class PasswordFieldNodeReuseTest {
    @Test
    fun aReactivatedPasswordFieldKeepsMaskingWithTheLookAndFeelEchoCharacter() = runComposeSwingTest {
        val lookAndFeelEchoChar = JPasswordField().echoChar
        var active by mutableStateOf(true)
        setContent {
            ReusableContentHost(active = active) {
                PasswordField(value = "hunter2".toCharArray())
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
                PasswordField(value = "hunter2".toCharArray(), echoChar = echoChar)
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
