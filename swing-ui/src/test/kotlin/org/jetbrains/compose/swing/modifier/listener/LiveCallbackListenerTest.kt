package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.ActionListener
import javax.swing.JButton
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A callback that changes between passes reaches the built listener without the listener being removed
 * and added again: the callback is not what the registration is made of, the registration is, and a registration
 * held in a `val` is the same instance every pass.
 */
class LiveCallbackListenerTest {
    private var attachments = 0
    private var removals = 0

    private val counted =
        CallbackRegistration<JButton, () -> Unit, ActionListener>(
            adapter = { current -> ActionListener { current()() } },
            registration =
                ListenerRegistration(
                    { component, listener ->
                        attachments++
                        component.addActionListener(listener)
                    },
                    { component, listener ->
                        removals++
                        component.removeActionListener(listener)
                    },
                ),
        )

    @Test
    fun aChangedCallbackReachesTheListenerWithoutReRegistering() = runComposeSwingTest {
        var reported = ""
        var declared by mutableStateOf("first")
        setContent {
            FlowPanel {
                // Captured while the chain is built, so each lambda reports the value its own pass
                // declared. A value read when the click fires is the latest one whichever pass wrote
                // the lambda, which cannot tell a stale callback from a live one.
                val captured = declared
                Button(
                    text = "press",
                    modifier = SwingModifier.listener({ reported = captured }, counted),
                )
            }
        }

        onNodeOfType<JButton>().performClick()
        assertEquals("first", reported, "the listener reads the callback the first pass declared")

        declared = "second"
        awaitIdle()

        onNodeOfType<JButton>().performClick()
        assertEquals("second", reported, "and the callback the latest pass declares, with no remember")
        assertEquals(1, attachments, "one registration builds one listener for the component's whole life")
        assertEquals(0, removals, "so a fresh callback never costs a detach")
    }
}
