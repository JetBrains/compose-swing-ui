package org.jetbrains.compose.swing.test

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.Canvas
import javax.accessibility.AccessibleRole
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Behavioral coverage for every [SwingMatcher] factory: each is exercised against a tree holding both
 * a component it must match and one it must not, so a matcher that over- or under-matches fails here
 * rather than silently mis-resolving a downstream consumer's query.
 */
class MatcherContractTest {
    @Test
    fun hasTextMatchesExactlyAndBySubstring() = runComposeSwingTest {
        setContent {
            BoxPanel {
                Label(text = "alpha beta")
                Button(text = "gamma", onClick = {})
            }
        }

        onNode(SwingMatcher.hasText("alpha beta")).assertExists()
        onAllNodes(SwingMatcher.hasText("alpha")).assertCountEquals(0)
        onNode(SwingMatcher.hasText("alpha", substring = true)).assertExists()
        onAllNodes(SwingMatcher.hasText("delta", substring = true)).assertCountEquals(0)
        // A component with no textual content never matches, whatever the sought text.
        onAllNodes(SwingMatcher.hasText("")).assertCountEquals(0)
    }

    @Test
    fun hasNameMatchesTheComponentName() = runComposeSwingTest {
        setContent {
            BoxPanel {
                Label(text = "named", modifier = SwingModifier.name("the-name"))
                Label(text = "unnamed")
            }
        }

        onNode(SwingMatcher.hasName("the-name")).assertTextEquals("named")
        onAllNodes(SwingMatcher.hasName("other-name")).assertCountEquals(0)
    }

    @Test
    fun accessibleMatchersReadTheAccessibleContext() = runComposeSwingTest {
        setContent {
            BoxPanel {
                SwingNode(
                    factory = { JLabel() },
                    update = {
                        set("a11y-label") { this.text = it }
                        set("a11y-description") { this.accessibleContext.accessibleDescription = it }
                    },
                )
                Label(text = "plain")
            }
        }

        // A JLabel publishes its text as its accessible name.
        onNode(SwingMatcher.hasAccessibleName("a11y-label")).assertTextEquals("a11y-label")
        onAllNodes(SwingMatcher.hasAccessibleName("absent")).assertCountEquals(0)

        onNode(SwingMatcher.hasAccessibleDescription("a11y-description")).assertTextEquals("a11y-label")
        onAllNodes(SwingMatcher.hasAccessibleDescription("absent")).assertCountEquals(0)

        // Both labels carry the LABEL role; the enclosing panels do not.
        onAllNodes(SwingMatcher.hasAccessibleRole(AccessibleRole.LABEL)).assertCountEquals(2)
        onAllNodes(SwingMatcher.hasAccessibleRole(AccessibleRole.PUSH_BUTTON)).assertCountEquals(0)
    }

    @Test
    fun hasTitleMatchesNoPlainComponent() = runComposeSwingTest {
        setContent { Label(text = "not a window") }

        // Titles belong to top-level Frames and Dialogs; a component inside the tree never matches,
        // so a mistyped window query cannot silently resolve to a plain component.
        onAllNodes(SwingMatcher.hasTitle("not a window")).assertCountEquals(0)
        onAllNodes(SwingMatcher.hasTitle("")).assertCountEquals(0)
    }

    @Test
    fun isEnabledMatchesTheRequestedState() = runComposeSwingTest {
        setContent {
            BoxPanel {
                Button(text = "on", onClick = {})
                Button(text = "off", onClick = {}, modifier = SwingModifier.enabled(false))
            }
        }

        onNode(SwingMatcher.isEnabled() and SwingMatcher.hasText("on")).assertExists()
        onAllNodes(SwingMatcher.isEnabled() and SwingMatcher.hasText("off")).assertCountEquals(0)

        onNode(SwingMatcher.isEnabled(false)).assertTextEquals("off")
        onAllNodes(SwingMatcher.isEnabled(false) and SwingMatcher.hasText("on")).assertCountEquals(0)
    }

    @Test
    fun andCombinesBothPredicatesAndTheirDescriptions() = runComposeSwingTest {
        setContent { Label(text = "combined", modifier = SwingModifier.name("combined-name")) }

        val combined = SwingMatcher.hasText("combined") and SwingMatcher.hasName("combined-name")
        onNode(combined).assertExists()
        assertEquals(
            "(hasText(\"combined\") && hasName(\"combined-name\"))",
            combined.description,
            "the combined description should name both operands so failure messages stay readable",
        )

        // Narrowing is a conjunction: failing either operand drops the node out of the match set.
        onAllNodes(SwingMatcher.hasText("combined") and SwingMatcher.hasName("other")).assertCountEquals(0)
    }

    @Test
    fun hasTestTagIgnoresNonSwingComponents() = runComposeSwingTest {
        setContent {
            BoxPanel {
                Label(text = "swing")
                SwingNode(factory = { Canvas() })
            }
        }

        // A raw AWT component carries no client properties; the tag matcher must reject it rather
        // than fail while walking a tree that mixes AWT leaves with Swing components.
        onAllNodes(SwingMatcher.hasTestTag("any")).assertCountEquals(0)
        onNode(SwingMatcher.isOfType<Canvas>()).assertExists()
    }

    @Test
    fun orAdmitsEitherOperandAndNotInvertsTheMatcher() = runComposeSwingTest {
        setContent {
            BoxPanel {
                Label(text = "alpha")
                Button(text = "beta", onClick = {})
                Label(text = "gamma")
            }
        }

        val either = SwingMatcher.hasText("alpha") or SwingMatcher.hasText("beta")
        onAllNodes(either).assertCountEquals(2)
        assertEquals(
            "(hasText(\"alpha\") || hasText(\"beta\"))",
            either.description,
            "the disjunction should name both operands so failure messages stay readable",
        )

        val negated = !SwingMatcher.hasText("alpha")
        onNode(SwingMatcher.isOfType<JLabel>() and negated).assertTextEquals("gamma")
        assertEquals("!(hasText(\"alpha\"))", negated.description, "the negation should name what it inverts")
        // Negating twice is the original matcher, so composition stays predictable.
        onNode(!negated).assertTextEquals("alpha")
    }

    @Test
    fun isSelectedReadsTheSelectedStateOfAButton() = runComposeSwingTest {
        var checked by mutableStateOf(false)
        setContent {
            BoxPanel {
                CheckBox(text = "agree", checked = checked)
                Label(text = "plain")
            }
        }

        onNode(SwingMatcher.isSelected(false)).assertTextEquals("agree")
        onAllNodes(SwingMatcher.isSelected()).assertCountEquals(0)

        checked = true
        awaitIdle()

        onNode(SwingMatcher.isSelected()).assertTextEquals("agree")
        onAllNodes(SwingMatcher.isSelected(false)).assertCountEquals(0)

        // A component that carries no selected state matches neither direction, so a query never
        // silently resolves to one; the negated form is what admits it.
        onNodeWithText("plain").assert(!SwingMatcher.isSelected()).assert(!SwingMatcher.isSelected(false))
    }

    @Test
    fun isEditableReadsTheEditableStateOfTextComponentsAndComboBoxes() = runComposeSwingTest {
        var editable by mutableStateOf(true)
        setContent {
            BoxPanel {
                TextField(value = "typed", editable = editable)
                ComboBox(items = listOf("one", "two"), selectedItem = null, editable = false)
                Label(text = "plain")
            }
        }

        onNode(SwingMatcher.isEditable() and SwingMatcher.isOfType<JTextField>()).assertExists()
        onNode(SwingMatcher.isEditable(false) and SwingMatcher.isOfType<JComboBox<*>>()).assertExists()

        editable = false
        awaitIdle()

        // Both widget families answer the same matcher, which is what makes it worth shipping.
        onAllNodes(SwingMatcher.isEditable(false)).assertCountEquals(2)
        onAllNodes(SwingMatcher.isEditable()).assertCountEquals(0)
        onNodeWithText("plain").assert(!SwingMatcher.isEditable()).assert(!SwingMatcher.isEditable(false))
    }

    @Test
    fun aQueryMatchingNothingFailsWhenResolved() = runComposeSwingTest {
        setContent { Label(text = "only") }

        // Every matcher family reports "no match" the same way: the interaction resolves to nothing
        // and the assertion fails, while the negative assertion holds.
        assertFailsWith<AssertionError> { onNode(SwingMatcher.hasName("absent")).assertExists() }
        onNode(SwingMatcher.hasName("absent")).assertDoesNotExist()
    }
}
