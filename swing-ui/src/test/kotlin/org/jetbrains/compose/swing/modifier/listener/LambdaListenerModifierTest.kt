package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.event.ActionEvent
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JSlider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun actionListenerUnscopedFiresEvenWhenEventSourceIsNotComponent() = runComposeSwingTest {
        var eventFired = false
        var capturedEvent: ActionEvent? = null
        setContent {
            Button(
                text = "Click",
                onClick = {},
                modifier =
                    SwingModifier.actionListener { event ->
                        eventFired = true
                        capturedEvent = event
                    },
            )
        }

        val button = onNodeOfType<JButton>().fetch<JButton>()
        val customSource = Any()
        val customEvent = ActionEvent(customSource, ActionEvent.ACTION_PERFORMED, "test")
        button.actionListeners.forEach { it.actionPerformed(customEvent) }

        assertTrue(eventFired, "unscoped actionListener must fire even when event source is not a Component")
        assertEquals(customSource, capturedEvent?.source)
    }

    @Test
    fun aScopedListenerRefusesAnEventSourcedSomewhereElse() = runComposeSwingTest {
        setContent {
            Button(text = "Click", onClick = {}, modifier = SwingModifier.actionListener<JButton> { })
        }

        val button = onNodeOfType<JButton>().fetch<JButton>()
        val foreign = ActionEvent(Any(), ActionEvent.ACTION_PERFORMED, "test")
        val failure =
            assertFailsWith<IllegalStateException> {
                button.actionListeners.forEach { it.actionPerformed(foreign) }
            }
        assertTrue(
            failure.message.orEmpty().contains("is not scoped to JButton"),
            "a scoped listener names the type it could not scope the event to, but was: ${failure.message}",
        )
    }

    @Test
    fun actionListenerScopedReceivesTypedComponentReceiver() = runComposeSwingTest {
        var receiverComponent: JButton? = null
        setContent {
            Button(
                text = "Click",
                onClick = {},
                modifier =
                    SwingModifier.actionListener<JButton> {
                        receiverComponent = this
                    },
            )
        }

        val button = onNodeOfType<JButton>().fetch<JButton>()
        onNodeOfType<JButton>().performClick()

        assertEquals(button, receiverComponent, "scoped actionListener must have the button as its receiver")
    }

    @Test
    fun aScopedItemListenerReceivesTheTypedComponent() = runComposeSwingTest {
        var receiver: JCheckBox? = null
        setContent {
            CheckBox(
                text = "Wrap",
                checked = false,
                onCheckedChange = {},
                modifier = SwingModifier.itemListener<JCheckBox> { receiver = this },
            )
        }

        val box = onNodeOfType<JCheckBox>().fetch<JCheckBox>()
        onNodeOfType<JCheckBox>().performClick()

        assertEquals(box, receiver, "a scoped item listener runs with the box it fired on as its receiver")
    }

    @Test
    fun aScopedChangeListenerReceivesTheTypedComponent() = runComposeSwingTest {
        var receiver: JSlider? = null
        setContent {
            Slider(
                value = 10,
                onValueChange = {},
                modifier = SwingModifier.changeListener<JSlider> { receiver = this },
            )
        }

        val slider = onNodeOfType<JSlider>().fetch<JSlider>()
        slider.value = 20
        awaitIdle()

        assertEquals(slider, receiver, "a scoped change listener runs with the slider it fired on as its receiver")
    }

    @Test
    fun aScopedPropertyChangeListenerReceivesTheTypedComponent() = runComposeSwingTest {
        var receiver: JButton? = null
        var enabled by mutableStateOf(true)
        setContent {
            Button(
                text = "Click",
                onClick = {},
                modifier =
                    SwingModifier
                        .enabled(enabled)
                        .propertyChangeListener<JButton>("enabled") { receiver = this },
            )
        }

        val button = onNodeOfType<JButton>().fetch<JButton>()
        enabled = false
        awaitIdle()

        assertEquals(button, receiver, "a scoped property listener runs with the button it fired on as its receiver")
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
