package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor.Companion.accessor
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.AbstractButton
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [ComponentPropertyDescriptor]'s multi-type constructor: a property several unrelated component types each declare
 * for themselves, expressed as [ComponentPropertyDescriptor.ComponentPropertyAccessor]s built with [accessor].
 * Applying it
 * reaches whichever accessor serves the component it lands on; a component none of them serve is rejected,
 * naming the types that are served.
 */
class ComponentPropertyDescriptorTest {
    @Test
    fun aMultiTypePropertyReachesEveryServedTypeThroughItsOwnAccessor() = runComposeSwingTest {
        setContent {
            SwingNode(factory = { JLabel("label") }, modifier = SwingModifier.property(GreetingProperty, "hi"))
            SwingNode(factory = { JButton("button") }, modifier = SwingModifier.property(GreetingProperty, "hi"))
        }
        val label = onNodeOfType<JLabel>().fetch()
        val button = onNodeOfType<JButton>().fetch()
        assertEquals("hi", label.text, "the JLabel accessor serves a label")
        assertEquals("hi", button.text, "the AbstractButton accessor serves a button")
    }

    @Test
    fun aReplacementHandleWithTheSameNameRebindsTheStandingPropertyNode() = runComposeSwingTest {
        val writes = mutableListOf<String>()
        val initial =
            ComponentPropertyDescriptor<JLabel, String>(
                name = "replacementGreeting",
                read = { it.text },
                write = { label, value ->
                    writes += "initial:$value"
                    label.text = value
                },
            )
        val replacement =
            ComponentPropertyDescriptor<JLabel, String>(
                name = "replacementGreeting",
                read = { it.text },
                write = { label, value ->
                    writes += "replacement:$value"
                    label.text = value
                },
            )
        var useReplacement by mutableStateOf(false)
        var value by mutableStateOf("first")
        setContent {
            val property = if (useReplacement) replacement else initial
            SwingNode(factory = { JLabel() }, modifier = SwingModifier.property(property, value))
        }
        assertEquals(listOf("initial:first"), writes, "the initial handle writes the initial value")

        useReplacement = true
        awaitIdle()
        assertEquals(
            listOf("initial:first", "replacement:first"),
            writes,
            "the replacement handle writes through its own accessor",
        )

        value = "second"
        awaitIdle()
        assertEquals(
            listOf("initial:first", "replacement:first", "replacement:second"),
            writes,
            "later updates use the replacement handle's accessor",
        )
    }

    @Test
    fun aMultiTypePropertyAppliedToAnUnservedTypeFailsNamingTheServedTypes() = runComposeSwingTest {
        val failure =
            assertFailsWith<IllegalStateException> {
                setContent {
                    SwingNode(factory = { JPanel() }, modifier = SwingModifier.property(GreetingProperty, "hi"))
                }
            }
        assertTrue(
            failure.message.orEmpty().contains(JLabel::class.java.name) &&
                failure.message.orEmpty().contains(AbstractButton::class.java.name),
            "the failure should name every served type, but said: ${failure.message}",
        )
    }
}

/** A property [JLabel] and [AbstractButton] each declare for themselves, with no shared supertype that does. */
private val GreetingProperty =
    ComponentPropertyDescriptor(
        "greeting",
        accessor<JLabel, String>(
            read = { it.text },
            write = { label, value ->
                label.text = value
            },
        ),
        accessor<AbstractButton, String>(
            read = { it.text },
            write = { button, value -> button.text = value },
        ),
    )
