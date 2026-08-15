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
 * A callback that changes between passes must reach the built listener without the listener being
 * removed and added again. That rests on the adapter arriving as the same instance every pass, which is
 * true of the non-capturing lambda every call site writes but is a property of how Kotlin compiles such
 * a lambda, not something the language promises. These tests pin it: were it to stop holding, the seam
 * would silently detach and reattach on every pass.
 */
class LiveCallbackListenerTest {
    @Test
    fun aChangedCallbackReachesTheListenerWithoutReRegistering() = runComposeSwingTest {
        var attachments = 0
        var removals = 0
        var reported = ""
        var declared by mutableStateOf("first")
        setContent {
            FlowPanel {
                Button(
                    text = "press",
                    modifier =
                        SwingModifier.liveCallbackListener<JButton, () -> Unit, ActionListener>(
                            callback = { reported = declared },
                            adapter = { current -> ActionListener { current()() } },
                            attach = { component, listener ->
                                attachments++
                                component.addActionListener(listener)
                            },
                            detach = { component, listener ->
                                removals++
                                component.removeActionListener(listener)
                            },
                        ),
                )
            }
        }

        onNodeOfType<JButton>().performClick()
        assertEquals("first", reported, "the listener reads the callback the first pass declared")

        declared = "second"
        awaitIdle()

        onNodeOfType<JButton>().performClick()
        assertEquals("second", reported, "and the callback the latest pass declares, with no remember")
        assertEquals(1, attachments, "the same adapter builds one listener for the component's whole life")
        assertEquals(0, removals, "so a fresh callback never costs a detach")
    }

    @Test
    fun anAdapterThatIsNotTheSameInstanceIsANewRegistration() = runComposeSwingTest {
        var attachments = 0
        var removals = 0
        var swapAdapter by mutableStateOf(false)
        setContent {
            // Two adapters written at two call sites are two instances, which is a different listener
            // and so a real re-registration - the case the identity check is there to tell apart.
            val adapter: (() -> () -> Unit) -> ActionListener =
                if (swapAdapter) {
                    { current -> ActionListener { current()() } }
                } else {
                    { current -> ActionListener { current()() } }
                }
            FlowPanel {
                Button(
                    text = "press",
                    modifier =
                        SwingModifier.liveCallbackListener<JButton, () -> Unit, ActionListener>(
                            callback = {},
                            adapter = adapter,
                            attach = { component, listener ->
                                attachments++
                                component.addActionListener(listener)
                            },
                            detach = { component, listener ->
                                removals++
                                component.removeActionListener(listener)
                            },
                        ),
                )
            }
        }
        assertEquals(1, attachments, "the first pass registers once")

        swapAdapter = true
        awaitIdle()

        assertEquals(2, attachments, "a different adapter builds a different listener, which is added")
        assertEquals(1, removals, "and the one it replaces is removed through the detach that added it")
    }

    @Test
    fun anAttachThatIsNotTheSameInstanceIsNotANewRegistrationWhenTheAdapterIs() = runComposeSwingTest {
        var attachments = 0
        var removals = 0
        var swapAttach by mutableStateOf(false)
        setContent {
            // A fresh attach instance every pass, unlike the adapter case above: the registration's
            // identity is the adapter alone, so this must cost no re-registration on its own - the
            // case swapTo's own identity check guards against.
            val attach: (JButton, ActionListener) -> Unit =
                if (swapAttach) {
                    { component, listener ->
                        attachments++
                        component.addActionListener(listener)
                    }
                } else {
                    { component, listener ->
                        attachments++
                        component.addActionListener(listener)
                    }
                }
            FlowPanel {
                Button(
                    text = "press",
                    modifier =
                        SwingModifier.liveCallbackListener<JButton, () -> Unit, ActionListener>(
                            callback = {},
                            adapter = { current -> ActionListener { current()() } },
                            attach = attach,
                            detach = { component, listener ->
                                removals++
                                component.removeActionListener(listener)
                            },
                        ),
                )
            }
        }
        assertEquals(1, attachments, "the first pass registers once")

        swapAttach = true
        awaitIdle()

        assertEquals(1, attachments, "the same adapter keeps the registration even with a new attach")
        assertEquals(0, removals, "so the attach that added it is never called through a swap")
    }
}
