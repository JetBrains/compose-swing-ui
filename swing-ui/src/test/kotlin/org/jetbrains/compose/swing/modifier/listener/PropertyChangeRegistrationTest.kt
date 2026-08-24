package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.beans.PropertyChangeListener
import javax.swing.JButton
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The property a listener is bound to names the registration it sits on rather than belonging to the
 * listener, so the same listener named against a different property is a different registration.
 * Keeping the caller's own instance is what makes this worth pinning: by-identity reconciliation sees
 * no change and would leave the listener on the property it was first given. The other direction
 * matters just as much - one name has to mean one registration, or every pass would move the listener off
 * the property it is already on and back.
 */
class PropertyChangeRegistrationTest {
    @Test
    fun aBoundListenerMovesWhenThePropertyNameChanges() = runComposeSwingTest {
        val seen = mutableListOf<String>()
        val instance = PropertyChangeListener { event -> seen += event.propertyName }
        var watched by mutableStateOf("enabled")
        setContent {
            FlowPanel {
                Button(
                    text = "press",
                    onClick = { },
                    modifier = SwingModifier.propertyChangeListener(watched, instance),
                )
            }
        }
        val button = onNodeOfType<JButton>().fetch<JButton>()

        button.isEnabled = false
        assertEquals(listOf("enabled"), seen, "the listener hears the property it was named against")

        watched = "foreground"
        awaitIdle()

        button.foreground = java.awt.Color.RED
        assertEquals(
            listOf("enabled", "foreground"),
            seen,
            "the same instance under a new name is a new registration, so it moves",
        )

        button.isEnabled = true
        assertEquals(
            listOf("enabled", "foreground"),
            seen,
            "and it leaves the property it was bound to before",
        )
    }

    @Test
    fun aBoundListenerKeepsItsPlaceWhileThePropertyNameStands() = runComposeSwingTest {
        val order = mutableListOf<String>()
        val composed = PropertyChangeListener { order += "composed" }
        var label by mutableStateOf("first")
        setContent {
            FlowPanel {
                Button(
                    text = label,
                    onClick = { },
                    modifier = SwingModifier.propertyChangeListener("enabled", composed),
                )
            }
        }
        val button = onNodeOfType<JButton>().fetch<JButton>()
        // Registered after the composed one, so it hears the property second for as long as the
        // composed registration is left where it is. A registration taken off and put back moves to
        // the end of the property's list, which is what this tells apart.
        button.addPropertyChangeListener("enabled", PropertyChangeListener { order += "later" })

        label = "second"
        awaitIdle()

        button.isEnabled = false
        assertEquals(
            listOf("composed", "later"),
            order,
            "a pass naming the same property leaves the registration in place",
        )
    }
}
