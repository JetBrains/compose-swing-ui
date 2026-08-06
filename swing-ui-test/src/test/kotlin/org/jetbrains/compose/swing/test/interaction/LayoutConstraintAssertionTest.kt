package org.jetbrains.compose.swing.test.interaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.components.layout.CardPanel
import org.jetbrains.compose.swing.components.layout.GridBagPanel
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.Insets
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pins what `assertLayoutConstraint` answers for, one layout manager at a time: the region a
 * `BorderLayout` places a child in, and the cell constraints a `GridBagLayout` places it under. Each
 * is driven into both the matching and the mismatching state, and a manager that reports no per-child
 * constraint has to say which manager it is, so a reader can tell that limit from an assertion that is
 * merely missing - and, for a card deck, reach for the visibility assertions that answer better.
 */
class LayoutConstraintAssertionTest {
    @Test
    fun borderRegionsMatchAndAWrongRegionNamesBoth() = runComposeSwingTest {
        setContent {
            BorderPanel {
                north { Label(text = "N") }
                center { Label(text = "C") }
                south { Label(text = "S") }
                west { Label(text = "W") }
                east { Label(text = "E") }
            }
        }

        onNodeWithText("N").assertLayoutConstraint(BorderLayout.NORTH)
        onNodeWithText("C").assertLayoutConstraint(BorderLayout.CENTER)
        onNodeWithText("S").assertLayoutConstraint(BorderLayout.SOUTH)
        onNodeWithText("W").assertLayoutConstraint(BorderLayout.WEST)
        onNodeWithText("E").assertLayoutConstraint(BorderLayout.EAST)

        val failure = assertFailsWith<AssertionError> { onNodeWithText("N").assertLayoutConstraint(BorderLayout.SOUTH) }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("placed in the North region"), "the failure names the actual region: $message")
        assertTrue(message.contains("expected South"), "the failure names the expected region: $message")
    }

    @Test
    fun gridBagCellsMatchFieldByFieldAndAWrongCellRendersBoth() = runComposeSwingTest {
        setContent {
            GridBagPanel {
                item(gridx = 0, gridy = 0) { Label(text = "origin") }
                item(gridx = 1, gridy = 2, gridwidth = 2, weightx = 1.0, insets = Insets(1, 2, 3, 4)) {
                    Label(text = "spanning")
                }
            }
        }

        // The manager answers with a copy of what it was handed, and GridBagConstraints carries no
        // equality, so a freshly built expectation with the same fields has to match.
        onNodeWithText("origin").assertLayoutConstraint(
            GridBagConstraints().apply {
                gridx = 0
                gridy = 0
            },
        )
        onNodeWithText("spanning").assertLayoutConstraint(
            GridBagConstraints().apply {
                gridx = 1
                gridy = 2
                gridwidth = 2
                weightx = 1.0
                insets = Insets(1, 2, 3, 4)
            },
        )

        val failure =
            assertFailsWith<AssertionError> {
                onNodeWithText("origin").assertLayoutConstraint(
                    GridBagConstraints().apply {
                        gridx = 3
                        gridy = 0
                    },
                )
            }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("is placed at gridx=0, gridy=0"), "the failure renders the actual cell: $message")
        assertTrue(message.contains("expected gridx=3, gridy=0"), "the failure renders the expected cell: $message")
    }

    @Test
    fun gridBagRejectsAnExpectationThatIsNotCellConstraints() = runComposeSwingTest {
        setContent {
            GridBagPanel {
                item(gridx = 0, gridy = 0) { Label(text = "origin") }
            }
        }

        val failure =
            assertFailsWith<AssertionError> { onNodeWithText("origin").assertLayoutConstraint(BorderLayout.NORTH) }
        assertTrue(
            failure.message.orEmpty().contains("places each child under GridBagConstraints"),
            "the failure should say what a GridBagLayout expects: ${failure.message}",
        )
    }

    @Test
    fun aCardDeckReportsNoConstraintAndIsAssertedThroughTheCardItShows() = runComposeSwingTest {
        var selected by mutableStateOf("second")
        setContent {
            CardPanel(selectedCard = selected) {
                card("first") { Label(text = "one") }
                card("second") { Label(text = "two") }
            }
        }

        // A CardLayout keeps the name each card is registered under to itself, so the assertion says
        // so instead of guessing - reading it back would mean driving the deck and perturbing the
        // very component tree under test.
        val failure = assertFailsWith<AssertionError> { onNodeWithText("one").assertLayoutConstraint("first") }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("CardLayout"), "the failure should name the parent's manager: $message")
        assertTrue(
            message.contains("reports no per-child layout constraint"),
            "the failure should say the manager keeps none: $message",
        )

        // What a deck is asserted on instead, and what actually matters about it: the declared card
        // is the one on show, and every other card is hidden with it.
        onNodeWithText("two").assertIsVisible()
        onNodeWithText("one").assertIsNotVisible()

        selected = "first"
        awaitIdle()

        onNodeWithText("one").assertIsVisible()
        onNodeWithText("two").assertIsNotVisible()
    }

    @Test
    fun aManagerThatKeepsNoConstraintIsNamedAsSuch() = runComposeSwingTest {
        setContent {
            BoxPanel {
                Label(text = "boxed")
            }
        }

        val failure =
            assertFailsWith<AssertionError> { onNodeWithText("boxed").assertLayoutConstraint(BorderLayout.CENTER) }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("BoxLayout"), "the failure should name the parent's manager: $message")
        assertTrue(
            message.contains("reports no per-child layout constraint"),
            "the failure should say the manager keeps none: $message",
        )
    }

    @Test
    fun aParentWithNoLayoutManagerPlacesNoChildUnderAConstraint() = runComposeSwingTest {
        setContent {
            SwingNode(
                factory = { JPanel().apply { layout = null } },
                content = { Label(text = "unmanaged") },
            )
        }

        val failure =
            assertFailsWith<AssertionError> { onNodeWithText("unmanaged").assertLayoutConstraint(BorderLayout.CENTER) }
        assertTrue(
            failure.message.orEmpty().contains("has no layout manager"),
            "the failure should say the parent manages no placement: ${failure.message}",
        )
    }

    @Test
    fun aNodeWithNoParentHasNoConstraintToRead() = runComposeSwingTest {
        setContent { Label(text = "child") }

        // The composition root is nobody's child, so it carries no layout constraint to read.
        val failure = assertFailsWith<AssertionError> { onRoot().assertLayoutConstraint(BorderLayout.NORTH) }
        assertTrue(
            failure.message.orEmpty().contains("no parent container"),
            "the failure should say the node has no parent: ${failure.message}",
        )
    }
}
