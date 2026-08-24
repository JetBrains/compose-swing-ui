package org.jetbrains.compose.swing.test.interaction

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pins [assertProperty]: what it reads, what it returns, and what its failure says. The value it
 * compares is read off the node typed as the query named it, so the reader is written against the
 * component's own API without a fetch of its own.
 */
class NodePropertyAssertionTest {
    @Test
    fun aMatchingPropertyPassesAndReturnsTheSameInteraction() = runComposeSwingTest {
        setContent {
            Label(text = "sized", modifier = SwingModifier.preferredSize(Dimension(120, 40)))
        }

        // The returned interaction is the one that was asserted on, so assertions chain off it.
        onNodeOfType<JLabel>()
            .assertProperty(Dimension(120, 40)) { preferredSize }
            .assertProperty("sized") { text }
            .assertTextEquals("sized")
    }

    @Test
    fun aMismatchNamesTheQueryAndBothValues() = runComposeSwingTest {
        setContent { Label(text = "actual") }

        val failure = assertFailsWith<AssertionError> { onNodeOfType<JLabel>().assertProperty("expected") { text } }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("isOfType(JLabel)"), "the failure should name the query: $message")
        assertTrue(
            message.contains("property was actual, expected expected."),
            "the failure should carry both values: $message",
        )
    }

    @Test
    fun aMismatchAppendsTheCallersMessage() = runComposeSwingTest {
        setContent { TextField(value = "typed", onValueChange = { }, columns = 4) }

        val failure =
            assertFailsWith<AssertionError> {
                onNodeOfType<JTextField>().assertProperty(10, "the column count is declared") { columns }
            }
        assertTrue(
            failure.message.orEmpty().endsWith("expected 10. the column count is declared"),
            "the caller's message should follow the values: ${failure.message}",
        )
    }

    @Test
    fun anAbsentNodeFailsAsAnUnresolvedQuery() = runComposeSwingTest {
        setContent { BoxPanel { } }

        // The property is read off a resolved node, so a query that resolves to nothing fails the way
        // every other use of that query does, with a tree dump, rather than as a value mismatch.
        val failure = assertFailsWith<AssertionError> { onNodeOfType<JLabel>().assertProperty("") { text } }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("found none"), "an empty match set should read as none: $message")
        assertTrue(message.contains("Tree:"), "the failure should carry a tree dump: $message")
    }
}
