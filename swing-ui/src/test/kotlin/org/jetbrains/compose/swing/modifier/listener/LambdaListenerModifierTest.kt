package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import javax.swing.JButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A lambda handed to a builder is read when the event fires rather than registered by identity, so a
 * call site that writes one inline declares a fresh lambda every pass and still keeps one registration.
 */
class LambdaListenerModifierTest {
    @Test
    fun aLambdaOverloadFiresTheLatestCallbackAndRegistersOnce() = runComposeSwingTest {
        var reported = ""
        var declared by mutableStateOf("first")
        setContent {
            FlowPanel {
                // Read during composition, so declaring a new value recomposes the button and rebuilds
                // its modifier chain with a freshly written lambda. The lambda reports the value this
                // pass captured rather than reading the state again when the click fires: a value read
                // at fire time is the latest one whichever pass wrote the lambda, which cannot tell a
                // stale lambda from a live one.
                val captured = declared
                Button(text = declared, onClick = { }, modifier = SwingModifier.actionListener { reported = captured })
            }
        }

        val registered = onNodeOfType<JButton>().fetch<JButton>().actionListeners.toList()
        onNodeOfType<JButton>().performClick()
        assertEquals("first", reported, "the lambda the first pass declared runs")

        declared = "second"
        awaitIdle()

        onNodeOfType<JButton>().performClick()
        assertEquals("second", reported, "and the lambda the latest pass declares, with no remember")
        // Identity, not count: a lambda registered by identity is detached and re-attached on every
        // pass, which leaves the count untouched and so cannot tell the two registrations apart.
        assertContentSame(
            registered,
            onNodeOfType<JButton>().fetch<JButton>().actionListeners.toList(),
            "a fresh lambda each pass keeps the listener object that was registered",
        )
    }

    @Test
    fun aNamedPropertyChangeLambdaFollowsTheNameItIsDeclaredWith() = runComposeSwingTest {
        val seen = mutableListOf<String>()
        var watched by mutableStateOf("enabled")
        setContent {
            FlowPanel {
                // Captured during composition and reported beside the event's own name: moving the
                // registration builds the listener afresh, and the captured name is what says the
                // rebuilt listener reads the lambda of the pass that moved it.
                val captured = watched
                Button(
                    text = watched,
                    onClick = { },
                    modifier =
                        SwingModifier.propertyChangeListener(watched) {
                            seen += "$captured:${it.propertyName}"
                        },
                )
            }
        }

        val button = onNodeOfType<JButton>().fetch<JButton>()
        button.isEnabled = false
        assertEquals(listOf("enabled:enabled"), seen, "the declared property is the one reported")

        watched = "background"
        awaitIdle()

        button.isEnabled = true
        button.background = Color.RED
        assertEquals(
            listOf("enabled:enabled", "background:background"),
            seen,
            "declaring another name moves the registration to it, and off the one it replaces",
        )
    }

    private fun <T> assertContentSame(
        expected: List<T>,
        actual: List<T>,
        message: String,
    ) {
        assertEquals(expected.size, actual.size, message)
        assertTrue(expected.indices.all { expected[it] === actual[it] }, message)
    }
}
