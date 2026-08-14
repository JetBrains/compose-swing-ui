package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
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
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for [CardPanel] and the cards its children name for themselves.
 *
 * Every child is a real child of the panel; which one is on top is Swing's own visibility state, so each
 * test asserts on the panel's children for what is attached and on each child's visibility for what is
 * shown.
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
                Label("first", SwingModifier.card("first"))
                Label("second", SwingModifier.card("second"))
                Label("third", SwingModifier.card("third"))
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
                Label("first", SwingModifier.card("first"))
                Label("second", SwingModifier.card("second"))
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
                Label("first", SwingModifier.card("first"))
                Label("second", SwingModifier.card("second"))
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
                Label("first", SwingModifier.card("first"))
                if (showSecond) {
                    Label("second", SwingModifier.card("second"))
                }
                Label("third", SwingModifier.card("third"))
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
    fun aChildIsAddressableUnderTheCardItNames() = runComposeSwingTest {
        var named by mutableStateOf("alpha")
        var selected by mutableStateOf("alpha")
        setContent {
            CardPanel(selectedCard = selected) {
                Label("body", SwingModifier.card(named))
                Label("other", SwingModifier.card("other"))
            }
        }

        assertShownCard(shown = "body", hidden = listOf("other"))

        // A child's card name is the key that selects it. Flipping away and back proves a new name
        // reached the deck: a key naming no card leaves the deck standing, so only the flip back onto
        // this child could not happen on a stale name.
        named = "beta"
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
    fun aCardLeavingAChildsChainTakesTheChildOffTheDeck() = runComposeSwingTest {
        var placed by mutableStateOf(true)
        var selected by mutableStateOf("first")
        setContent {
            CardPanel(selectedCard = selected) {
                Label("first", SwingModifier.card("first"))
                Label("second", if (placed) SwingModifier.card("second") else SwingModifier)
            }
        }

        selected = "second"
        awaitIdle()
        assertShownCard(shown = "second", hidden = listOf("first"))

        selected = "first"
        awaitIdle()
        assertShownCard(shown = "first", hidden = listOf("second"))

        // Dropping the card name drops the declaration that flipped the deck onto this child: that key
        // now names no card, and a key naming no card leaves the deck standing.
        placed = false
        awaitIdle()

        selected = "second"
        awaitIdle()
        assertShownCard(shown = "first", hidden = listOf("second"))
    }

    @Test
    fun aCardsContentFollowsItsDeclaration() = runComposeSwingTest {
        var caption by mutableStateOf("first")
        setContent {
            CardPanel(selectedCard = "only") {
                Label(caption, SwingModifier.card("only"))
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
    fun aCardKeepsItsBodyWhenACardIsDeclaredAheadOfIt() = runComposeSwingTest {
        var leading by mutableStateOf(false)
        val created = intArrayOf(0)
        setContent {
            // One call site declares every card, so `key` around each declaration is what distinguishes
            // it from its siblings. Each body names the declaration that created its state and the order
            // that state was created in, so a body that outlives its declaration keeps that name.
            CardPanel(selectedCard = "one") {
                val declared = if (leading) listOf("added", "one", "two") else listOf("one", "two")
                declared.forEach { cardKey ->
                    key(cardKey) { Label(remember { "$cardKey#${++created[0]}" }, SwingModifier.card(cardKey)) }
                }
            }
        }

        assertDeckHolds("one#1", "two#2")
        val standingBody = onNodeWithText("one#1").fetch<JLabel>()

        leading = true
        awaitIdle()
        // State belongs to the key: the standing cards keep theirs and only the new declaration starts
        // fresh, on the very component each card was already realized as.
        assertDeckHolds("added#3", "one#1", "two#2")
        assertSame(standingBody, onNodeWithText("one#1").fetch<JLabel>(), "the standing card should keep its body")
    }

    @Test
    fun twoChildrenOfOnePanelCannotNameTheSameCard() = runComposeSwingTest {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                setContent {
                    CardPanel(selectedCard = "shared") {
                        Label("first", SwingModifier.card("shared"))
                        Label("second", SwingModifier.card("shared"))
                    }
                }
                // The check runs a turn after the pass that added these children, once it has settled.
                awaitIdle()
            }

        assertTrue(
            "shared" in failure.message.orEmpty(),
            "the failure should name the card held twice: ${failure.message}",
        )
    }

    @Test
    fun twoChildrenOfOnePanelCannotBothNameNoCard() = runComposeSwingTest {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                setContent {
                    CardPanel(selectedCard = "") {
                        Label("first")
                        Label("second")
                    }
                }
                awaitIdle()
            }

        assertTrue(
            "SwingModifier.card(key)" in failure.message.orEmpty(),
            "the failure should tell the caller to name the cards: ${failure.message}",
        )
    }

    @Test
    fun aParkedChildYieldsItsCardToTheOneReplacingIt() = runComposeSwingTest {
        var parked by mutableStateOf(false)
        setContent {
            CardPanel(selectedCard = "shared") {
                // A parked child gives up its card as the applier releases it, which is part of the same
                // pass that puts the incoming child on the deck. The card is held twice while that pass
                // runs, and by one child once it has been dispatched whole - which is when it is counted.
                ReusableContentHost(active = !parked) {
                    Label("first", SwingModifier.card("shared"))
                }
                if (parked) Label("second", SwingModifier.card("shared"))
            }
        }

        assertDeckHolds("first")

        parked = true
        awaitIdle()

        assertDeckHolds("second")
        assertShownCard(shown = "second", hidden = emptyList())
    }

    @Test
    fun theChildThatNamesNoCardIsShownByTheEmptyKey() = runComposeSwingTest {
        var selected by mutableStateOf("named")
        setContent {
            CardPanel(selectedCard = selected) {
                Label("unnamed")
                Label("named", SwingModifier.card("named"))
            }
        }

        assertDeckHolds("unnamed", "named")
        assertShownCard(shown = "named", hidden = listOf("unnamed"))

        // One child naming no card is a card of the deck like any other, addressed by the empty key: the
        // deck stands on another card first, so only that key can bring this child back on top.
        selected = ""
        awaitIdle()

        assertShownCard(shown = "unnamed", hidden = listOf("named"))
    }

    @Test
    fun aCardCannotBeNamedByTheEmptyKey() = runComposeSwingTest {
        assertFailsWith<IllegalArgumentException> {
            setContent {
                CardPanel(selectedCard = "only") {
                    Label("only", SwingModifier.card(""))
                }
            }
        }
    }

    @Test
    fun theDeclaredGapsReachTheLayout() = runComposeSwingTest {
        setContent {
            CardPanel(selectedCard = "only", hgap = HGAP, vgap = VGAP) {
                Label("only", SwingModifier.card("only"))
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
