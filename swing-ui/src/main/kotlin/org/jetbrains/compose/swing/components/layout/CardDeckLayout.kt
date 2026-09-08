package org.jetbrains.compose.swing.components.layout

import org.jetbrains.compose.swing.util.DeferredAction
import java.awt.CardLayout
import java.awt.Component
import java.awt.Container

/**
 * The [CardLayout] behind [PanelLayout.Card]: it keeps the record of which child holds which card name,
 * and the card the deck is to show, so a child arriving on that card or leaving another finds the deck
 * showing it still.
 *
 * `CardLayout` addresses its cards by name, holds one child per name and hands neither back: a child
 * arriving under a name already taken takes that card over, leaving the child before it in the container
 * and reachable by no key. This record is what lets the deck report that instead.
 */
internal class CardDeckLayout : CardLayout() {
    private val cardNames = HashMap<Component, String>()

    private var targetCard: String? = null

    /**
     * Checks the deck holds one child per card, on the turn of the event queue after the one the children
     * were added in. Mid-pass a card can hold two, when the child replacing another arrives before that
     * one has left: a parked child gives its place up in a deactivation the runtime dispatches only once
     * the pass parking it is applied whole.
     */
    private val cardCheck =
        DeferredAction {
            val repeated = repeatedCardName()
            if (repeated != null) cardHeldByMoreThanOneChild(repeated)
        }

    /** Shows [card] of [deck], and holds it as the card a child arriving or leaving restores. */
    fun showCard(
        deck: Container,
        card: String,
    ) {
        targetCard = card
        showTargetCard(deck)
    }

    override fun addLayoutComponent(
        component: Component,
        constraints: Any?,
    ) {
        // The superclass is what refuses a constraint that is not a card name, and a child it refused
        // holds no card to record.
        super.addLayoutComponent(component, constraints)
        val cardName = (constraints as? String).orEmpty()
        cardNames[component] = cardName
        cardCheck.schedule()
        if (cardName == targetCard) showTargetCard(component.parent)
    }

    override fun removeLayoutComponent(component: Component) {
        cardNames.remove(component)
        // The container hands its child over here before it takes the child's parent away, so the deck
        // to show the card on is the leaving child's own parent.
        val deck = component.parent
        super.removeLayoutComponent(component)
        showTargetCard(deck)
    }

    /**
     * The card held by more than one child, or `null` where every card holds one child. The empty name
     * is a card of the deck like any other - `CardLayout` registers a child added under no constraint
     * on it and `show` selects it by the empty key - so children that name no card are children of one
     * card, counted here as any other name held twice is.
     */
    fun repeatedCardName(): String? {
        val seen = HashSet<String>()
        return cardNames.values.firstOrNull { !seen.add(it) }
    }

    private fun showTargetCard(deck: Container?) {
        val card = targetCard ?: return
        if (deck != null) show(deck, card)
    }
}

/**
 * Reports [name] as a card of a [PanelLayout.Card] deck that more than one child holds, telling the two
 * cases apart: a name written twice, and children that named no card and so share the deck's empty-named
 * one.
 */
private fun cardHeldByMoreThanOneChild(name: String): Nothing =
    throw IllegalArgumentException(
        if (name.isEmpty()) {
            "A PanelLayout.Card card holds a single child, but more than one child of this panel names " +
                "no card: they share the deck's empty-named card, the child that reaches it last takes " +
                "it, and the one before it can be shown by no key at all. Name each card with " +
                "SwingModifier.card(key)."
        } else {
            "A PanelLayout.Card card holds a single child, but '$name' is named by more than one: the " +
                "child that names it last takes the card, and the one before it can be shown by no key " +
                "at all."
        },
    )
