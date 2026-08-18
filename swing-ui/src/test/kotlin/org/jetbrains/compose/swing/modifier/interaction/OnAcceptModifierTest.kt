package org.jetbrains.compose.swing.modifier.interaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.ExclusiveWindowSystem
import org.jetbrains.compose.swing.assumeKeyboardFocusIsPossible
import org.jetbrains.compose.swing.assumeWindowBecomesFocused
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JTextField
import javax.swing.KeyStroke
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Behavioral coverage for the value-accepted modifier: the field's action event runs the callback, the
 * callback the latest composition declared is the one that runs, applications accumulate, and the
 * registration ends with the element.
 *
 * The key-binding case needs a field that actually holds the keyboard: the field's Enter action asks the
 * platform which text component is focused, so it runs only where the window system grants focus.
 */
@ExclusiveWindowSystem
class OnAcceptModifierTest {
    @Test
    fun theFieldsActionEventRunsTheCallback() = runComposeSwingTest {
        var accepted = 0
        setContent {
            TextField("query", modifier = SwingModifier.onAccept { accepted++ })
        }
        val field = onNodeOfType<JTextField>().fetch()

        field.postActionEvent()
        assertEquals(1, accepted, "the field's action event must run the declared callback once")
    }

    @Test
    fun theCallbackDeclaredByTheLatestCompositionRuns() = runComposeSwingTest {
        var declared by mutableStateOf("first")
        var ran: String? = null
        setContent {
            val current = declared
            TextField("query", modifier = SwingModifier.onAccept { ran = current })
        }
        val field = onNodeOfType<JTextField>().fetch()

        field.postActionEvent()
        assertEquals("first", ran, "the callback of the first composition must run")

        declared = "second"
        awaitIdle()
        field.postActionEvent()
        assertEquals("second", ran, "a recomposition must replace the callback without reattaching")
    }

    @Test
    fun twoApplicationsBothRun() = runComposeSwingTest {
        var first = 0
        var second = 0
        setContent {
            TextField("query", modifier = SwingModifier.onAccept { first++ }.onAccept { second++ })
        }
        val field = onNodeOfType<JTextField>().fetch()

        field.postActionEvent()
        assertEquals(1, first, "the first application must run")
        assertEquals(1, second, "the second application must run")
    }

    @Test
    fun theCallbackStopsRunningOnceItsElementLeavesTheChain() = runComposeSwingTest {
        var attached by mutableStateOf(true)
        var accepted = 0
        setContent {
            TextField(
                "query",
                modifier = if (attached) SwingModifier.onAccept { accepted++ } else SwingModifier,
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        field.postActionEvent()
        assertEquals(1, accepted, "the callback must run while its element is in the chain")

        attached = false
        awaitIdle()
        field.postActionEvent()
        assertEquals(1, accepted, "a detached callback must not run on a later action event")
    }

    @Test
    fun aNonTextFieldTargetIsRejectedNamingTheRequiredType() = runComposeSwingTest {
        val error =
            assertFailsWith<IllegalStateException> {
                setContent {
                    Label("X", modifier = SwingModifier.onAccept { })
                }
                awaitIdle()
            }
        assertTrue(
            JTextField::class.java.name in error.message.orEmpty(),
            "the wrong-target error must name the required JTextField target, but was: ${error.message}",
        )
    }

    @Test
    fun pressingEnterInTheFocusedFieldRunsTheCallback() {
        assumeKeyboardFocusIsPossible()
        runComposeSwingTest {
            var accepted = 0
            lateinit var requester: FocusRequester
            setContent {
                Window(onCloseRequest = {}, title = "on-accept-test") {
                    requester = rememberFocusRequester()
                    TextField(
                        "query",
                        modifier = SwingModifier.focusRequester(requester).onAccept { accepted++ },
                    )
                }
            }
            val window = onWindowWithTitle("on-accept-test")
            assumeWindowBecomesFocused(window.fetch<JFrame>())
            val field = window.onNodeWithText("query").fetch<JTextField>()
            requester.requestFocus()
            waitUntil { field.isFocusOwner }

            field.fireEnterBinding()
            assertEquals(1, accepted, "the field's own Enter binding must run the declared callback")
        }
    }

    /** Runs the action the component's own `InputMap` binds to Enter, the path a key press takes. */
    private fun JComponent.fireEnterBinding() {
        val keyStroke = KeyStroke.getKeyStroke("ENTER")
        val inputMap = getInputMap(JComponent.WHEN_FOCUSED)
        val actionKey = checkNotNull(inputMap.get(keyStroke)) { "the field must bind Enter" }
        val action = checkNotNull(actionMap.get(actionKey)) { "the Enter binding must resolve to an action" }
        action.actionPerformed(null)
    }
}
