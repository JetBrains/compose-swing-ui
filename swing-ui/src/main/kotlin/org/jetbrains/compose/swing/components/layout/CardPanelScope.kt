package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable

/**
 * Declarative cards of a [CardPanel].
 *
 * Each card hosts a single composable and is addressed by a key; the panel shows the card whose key
 * equals its `selectedCard`. Declaring the same key more than once replaces the previous declaration -
 * the last call wins.
 *
 * @see java.awt.CardLayout
 */
public sealed interface CardPanelScope {
    /**
     * Declares the card shown while [CardPanel]'s `selectedCard` equals [key].
     *
     * @param key identifies this card among the panel's cards
     * @param content the single composable the card hosts
     */
    public fun card(
        key: String,
        content: @Composable () -> Unit,
    )
}
