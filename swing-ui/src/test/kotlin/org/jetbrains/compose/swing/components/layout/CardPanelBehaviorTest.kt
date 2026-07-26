package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.onChildAt
import org.jetbrains.compose.swing.test.interaction.onChildren
import org.jetbrains.compose.swing.test.interaction.onParent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.CardLayout
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral tests for the [CardPanel] scope-based DSL.
 *
 * Every declared card is a real child of the panel; which one is on top is Swing's own visibility
 * state, so each test asserts on the panel's children for what is attached and on each card's
 * visibility for what is shown.
 */
class CardPanelBehaviorTest {
    /** Asserts the deck holds exactly the cards captioned [captions], as its children in that order. */
    private fun ComposeSwingTest.assertDeckHolds(vararg captions: String) {
        val deck = onNodeWithText(captions.first()).onParent()
        deck.onChildren().assertCountEquals(captions.size)
        captions.forEachIndexed { index, caption -> deck.onChildAt(index).assertTextEquals(caption) }
    }

    /** Asserts the card captioned [shown] is the one on top and every card in [hidden] is not. */
    private fun ComposeSwingTest.assertShownCard(
        shown: String,
        hidden: List<String>,
    ) {
        onNodeWithText(shown).assertIsVisible()
        hidden.forEach { onNodeWithText(it).assertIsNotVisible() }
    }

    @Test
    fun onlyTheSelectedCardIsShown() = runComposeSwingTest {
        setContent {
            CardPanel(selectedCard = "second") {
                card("first") { Label("first") }
                card("second") { Label("second") }
                card("third") { Label("third") }
            }
        }

        assertDeckHolds("first", "second", "third")
        assertShownCard(shown = "second", hidden = listOf("first", "third"))
    }

    @Test
    fun changingTheSelectedCardSwapsTheShownChild() = runComposeSwingTest {
        var selected by mutableStateOf("first")
        setContent {
            CardPanel(selectedCard = selected) {
                card("first") { Label("first") }
                card("second") { Label("second") }
            }
        }

        assertShownCard(shown = "first", hidden = listOf("second"))

        selected = "second"
        awaitIdle()

        assertShownCard(shown = "second", hidden = listOf("first"))
    }

    @Test
    fun aKeyMatchingNoCardLeavesTheShownCardInPlace() = runComposeSwingTest {
        var selected by mutableStateOf("second")
        setContent {
            CardPanel(selectedCard = selected) {
                card("first") { Label("first") }
                card("second") { Label("second") }
            }
        }

        assertShownCard(shown = "second", hidden = listOf("first"))

        selected = "absent"
        awaitIdle()

        assertShownCard(shown = "second", hidden = listOf("first"))
    }

    @Test
    fun droppingACardRemovesItAndLeavesTheRestIntact() = runComposeSwingTest {
        var showSecond by mutableStateOf(true)
        setContent {
            CardPanel(selectedCard = "third") {
                card("first") { Label("first") }
                if (showSecond) {
                    card("second") { Label("second") }
                }
                card("third") { Label("third") }
            }
        }

        assertDeckHolds("first", "second", "third")
        assertShownCard(shown = "third", hidden = listOf("first", "second"))

        showSecond = false
        awaitIdle()

        onNodeWithText("second").assertDoesNotExist()
        assertDeckHolds("first", "third")
        assertShownCard(shown = "third", hidden = listOf("first"))
    }

    @Test
    fun aCardIsAddressableUnderTheKeyItIsDeclaredWith() = runComposeSwingTest {
        var key by mutableStateOf("alpha")
        var selected by mutableStateOf("alpha")
        setContent {
            CardPanel(selectedCard = selected) {
                card(key) { Label("body") }
                card("other") { Label("other") }
            }
        }

        assertShownCard(shown = "body", hidden = listOf("other"))

        // The key a card is declared with is the one the deck is flipped by, so a card declared under
        // a new key is the one that new key selects. Flipping away and back is what shows the new key
        // reached the deck: a key naming no card leaves the deck where it stands, so it is only the
        // flip back onto the card that a stale key could not produce.
        key = "beta"
        selected = "beta"
        awaitIdle()
        assertShownCard(shown = "body", hidden = listOf("other"))

        selected = "other"
        awaitIdle()
        assertShownCard(shown = "other", hidden = listOf("body"))

        selected = "beta"
        awaitIdle()
        assertShownCard(shown = "body", hidden = listOf("other"))
    }

    @Test
    fun aCardsContentFollowsItsDeclaration() = runComposeSwingTest {
        var caption by mutableStateOf("first")
        setContent {
            CardPanel(selectedCard = "only") {
                card("only") { Label(caption) }
            }
        }

        val body = onNodeWithText("first").fetch<JLabel>()

        caption = "second"
        awaitIdle()
        // The card renders its new caption on the very component it already held, still on top.
        assertEquals("second", body.text, "the card's content should follow its declaration")
        onNodeWithText("second").assertIsVisible()
    }

    @Test
    fun theDeclaredGapsReachTheLayout() = runComposeSwingTest {
        setContent {
            CardPanel(selectedCard = "only", hgap = HGAP, vgap = VGAP) {
                card("only") { Label("only") }
            }
        }

        val layout = onNodeWithText("only").onParent().fetch<JPanel>().layout as CardLayout
        assertEquals(HGAP, layout.hgap, "hgap")
        assertEquals(VGAP, layout.vgap, "vgap")
    }

    private companion object {
        const val HGAP = 7
        const val VGAP = 9
    }
}
