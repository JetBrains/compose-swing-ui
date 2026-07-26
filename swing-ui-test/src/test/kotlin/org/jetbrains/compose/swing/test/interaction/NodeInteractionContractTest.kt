package org.jetbrains.compose.swing.test.interaction

import org.jetbrains.compose.swing.SwingNode
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.components.layout.CardPanel
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.modifier.layout.visible
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pins the resolution and assertion contracts of [SwingNodeInteraction] - including the failures.
 *
 * A harness assertion is only worth as much as its failure: it must fail when the tree does not match
 * the claim, and its message must name the query and describe the tree. These cases drive each
 * assertion into its failing state and check both, alongside the text-editing actions.
 */
class NodeInteractionContractTest {
    @Test
    fun assertDoesNotExistFailsWhenTheQueryIsAmbiguous() = runComposeSwingTest {
        setContent {
            BoxPanel {
                Label(text = "dup")
                Label(text = "dup")
            }
        }

        // A unique query that matches several nodes is a broken query, not an absent node: the
        // negative assertion must report the ambiguity rather than silently pass or silently fail.
        val failure = assertFailsWith<AssertionError> { onNodeWithText("dup").assertDoesNotExist() }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("found 2"), "the ambiguity should be quantified: $message")
        assertTrue(message.contains("hasText(\"dup\")"), "the failure should name the query: $message")
        assertTrue(message.contains("Tree:"), "the failure should carry a tree dump: $message")
    }

    @Test
    fun assertDoesNotExistFailsWhenTheNodeIsPresent() = runComposeSwingTest {
        setContent { Label(text = "present") }

        val failure = assertFailsWith<AssertionError> { onNodeWithText("present").assertDoesNotExist() }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("JLabel"), "the failure should describe the node found: $message")
        assertTrue(message.contains("present"), "the failure should carry the node's text: $message")
    }

    @Test
    fun aQueryWithNoMatchNamesItselfAndDumpsTheTree() = runComposeSwingTest {
        setContent { Label(text = "only") }

        val failure = assertFailsWith<AssertionError> { onNodeWithText("absent").assertExists() }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("found none"), "an empty match set should read as none: $message")
        assertTrue(message.contains("hasText(\"absent\")"), "the failure should name the query: $message")
        assertTrue(message.contains("JLabel"), "the tree dump should show what is there instead: $message")
    }

    @Test
    fun anIndexedQueryPastTheMatchSetReportsHowManyMatched() = runComposeSwingTest {
        setContent {
            BoxPanel {
                Label(text = "row")
                Label(text = "row")
            }
        }

        val failure = assertFailsWith<AssertionError> { onAllNodesWithText("row")[5].assertExists() }
        assertTrue(
            failure.message.orEmpty().contains("only 2 node(s) matched"),
            "an out-of-range index should report the size of the match set: ${failure.message}",
        )
    }

    @Test
    fun assertTextEqualsFailsForANodeThatCarriesNoText() = runComposeSwingTest {
        setContent { Label(text = "child") }

        // The root panel is a legitimate query target but has no textual content, so a text claim
        // about it must fail and say the text was null rather than compare against an empty string.
        val failure = assertFailsWith<AssertionError> { onRoot().assertTextEquals("") }
        assertTrue(
            failure.message.orEmpty().contains("null"),
            "a node without text should be reported as null, not as empty text: ${failure.message}",
        )
    }

    @Test
    fun assertIsDisplayedHoldsForALaidOutNode() = runComposeSwingTest {
        setContent {
            BoxPanel {
                Label(text = "laid out")
            }
        }

        // The harness forces a layout pass, so a node in the tree has real bounds off-screen.
        onNodeWithText("laid out").assertIsDisplayed()
        onRoot().assertIsDisplayed()
    }

    @Test
    fun assertIsDisplayedFailsForANodeCollapsedToZeroSize() = runComposeSwingTest {
        setContent {
            BoxPanel {
                SwingNode(
                    factory = { JLabel() },
                    update = {
                        set("collapsed") { this.text = it }
                        set(Dimension(0, 0)) {
                            this.preferredSize = it
                            this.maximumSize = it
                            this.minimumSize = it
                        }
                    },
                )
            }
        }

        val failure = assertFailsWith<AssertionError> { onNodeWithText("collapsed").assertIsDisplayed() }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("zero laid-out size"), "the failure should name the cause: $message")
        assertTrue(message.contains("Tree:"), "the failure should carry a tree dump: $message")
    }

    @Test
    fun aHiddenCardIsStillDisplayedAndOnlyTheVisibilityAssertionTellsThemApart() = runComposeSwingTest {
        setContent {
            CardPanel(selectedCard = "front") {
                card("front") { Label(text = "front") }
                card("back") { Label(text = "back") }
            }
        }

        onNodeWithText("front").assertIsVisible()
        onNodeWithText("back").assertIsDisplayed().assertIsNotVisible()
    }

    @Test
    fun assertIsVisibleFailsForANodeHiddenByItsOwnFlag() = runComposeSwingTest {
        setContent { Label(text = "hidden", modifier = SwingModifier.visible(false)) }

        val failure = assertFailsWith<AssertionError> { onNodeWithText("hidden").assertIsVisible() }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("its own visible flag"), "the failure should name what is hidden: $message")
        assertTrue(message.contains("Tree:"), "the failure should carry a tree dump: $message")
    }

    @Test
    fun assertIsVisibleFailsForANodeHiddenByAnAncestorAndNamesIt() = runComposeSwingTest {
        setContent {
            FlowPanel(modifier = SwingModifier.visible(false)) {
                Label(text = "child")
            }
        }

        val failure = assertFailsWith<AssertionError> { onNodeWithText("child").assertIsVisible() }
        assertTrue(
            failure.message.orEmpty().contains("enclosing JPanel is hidden"),
            "the failure should name the ancestor that hides the node: ${failure.message}",
        )
        // The node's own flag is untouched: what makes it hidden is the walk up to the query root.
        onNodeWithText("child").assertIsNotVisible()
    }

    @Test
    fun assertIsNotVisibleFailsForAShownNode() = runComposeSwingTest {
        setContent { Label(text = "shown") }

        val failure = assertFailsWith<AssertionError> { onNodeWithText("shown").assertIsNotVisible() }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("is visible"), "the failure should say the node is visible: $message")
        assertTrue(message.contains("Tree:"), "the failure should carry a tree dump: $message")
    }

    @Test
    fun performTextInputAppendsAndPerformTextReplacementOverwrites() = runComposeSwingTest {
        setContent { TextField(value = "seed") }

        onNodeOfType<JTextField>().performTextInput("-more")
        assertEquals(
            "seed-more",
            onNodeOfType<JTextField>().fetch<JTextField>().text,
            "text input should append to the field's current content",
        )

        onNodeOfType<JTextField>().performTextReplacement("fresh")
        assertEquals(
            "fresh",
            onNodeOfType<JTextField>().fetch<JTextField>().text,
            "text replacement should overwrite the field's whole content",
        )
    }

    @Test
    fun performTextReplacementRejectsANonTextComponent() = runComposeSwingTest {
        setContent { Label(text = "label") }

        val failure = assertFailsWith<AssertionError> { onNodeWithText("label").performTextReplacement("x") }
        assertTrue(
            failure.message.orEmpty().contains("JTextComponent"),
            "the failure should name the type the action requires: ${failure.message}",
        )
    }

    @Test
    fun onDescendantsExcludesTheNodeItself() = runComposeSwingTest {
        setContent {
            FlowPanel(modifier = SwingModifier.name("outer")) {
                FlowPanel(modifier = SwingModifier.name("inner")) {
                    Label(text = "leaf")
                }
            }
        }

        // The scoping node matches the type query itself, but scoping means "below", so it must not
        // appear in its own scoped result.
        val scoped =
            onNodeWithName("outer")
                .onDescendants()
                .filter(SwingMatcher.isOfType<JPanel>())
                .fetchAll<JPanel>()
        assertEquals(
            listOf("inner"),
            scoped.map { it.name },
            "onDescendants should yield the node's descendants, never the node",
        )
    }

    @Test
    fun assertAppliesAnyMatcherToTheNodeAndChains() = runComposeSwingTest {
        setContent { Label(text = "subject", modifier = SwingModifier.name("subject-name")) }

        val chained =
            onNodeWithText("subject")
                .assert(SwingMatcher.hasName("subject-name"))
                .assert(SwingMatcher.isOfType<JLabel>() or SwingMatcher.isOfType<JTextField>())
        chained.assert(!SwingMatcher.hasName("other"))
    }

    @Test
    fun assertFailsNamingTheMatcherTheNodeAndTheTree() = runComposeSwingTest {
        setContent { Label(text = "subject") }

        val failure =
            assertFailsWith<AssertionError> { onNodeWithText("subject").assert(SwingMatcher.isEnabled(false)) }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("does not satisfy 'isEnabled(false)'"), "the failure names the matcher: $message")
        assertTrue(message.contains("The node is JLabel"), "the failure describes the node: $message")
        assertTrue(message.contains("Tree:"), "the failure should carry a tree dump: $message")
    }
}
