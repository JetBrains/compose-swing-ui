// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.layout

import java.awt.CardLayout
import java.awt.Component
import java.awt.Container

/**
 * The [CardLayout] behind [PanelLayout.Card]: it keeps the card the deck is to show, so a child arriving
 * on that card or leaving another finds the deck showing it still.
 *
 * Cards are addressed as `CardLayout` addresses them: a child arriving under a name already taken
 * takes that card over.
 */
internal class CardDeckLayout : CardLayout() {
    /** The card the deck is to show, or `null` until [showCard] names one. */
    var targetCard: String? = null
        private set

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
        super.addLayoutComponent(component, constraints)
        if ((constraints as? String).orEmpty() == targetCard) showTargetCard(component.parent)
    }

    override fun removeLayoutComponent(component: Component) {
        // The container hands its child over here before it takes the child's parent away, so the deck
        // to show the card on is the leaving child's own parent.
        val deck = component.parent
        super.removeLayoutComponent(component)
        showTargetCard(deck)
    }

    private fun showTargetCard(deck: Container?) {
        val card = targetCard ?: return
        if (deck != null) show(deck, card)
    }
}
