package org.jetbrains.compose.swing.components.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint

/**
 * The receiver of a [CardPanel]'s content, through which a child names the card it is placed on.
 *
 * Children are written plainly; the card a child names rides along on its `modifier`:
 *
 * ```
 * CardPanel(selectedCard = page) {
 *     Label(text = "Welcome", modifier = SwingModifier.card("welcome"))
 *     Label(text = "Details", modifier = SwingModifier.card("details"))
 * }
 * ```
 *
 * @see java.awt.CardLayout
 */
public sealed interface CardPanelScope {
    /**
     * Places the child on the card named [key], the card the panel shows while its `selectedCard` equals
     * that key. The name follows the value: a child declaring a new key moves to that card, keeping its
     * position among its siblings.
     *
     * [key] cannot be empty, and two children of one panel cannot name the same card; either is refused,
     * and a card named twice is reported by its name.
     *
     * @param key names the card this child is placed on, non-empty
     * @see java.awt.CardLayout.show
     */
    public fun SwingModifier.card(key: String): SwingModifier
}

/** The [CardPanelScope] every [CardPanel] hands its content; it holds nothing of the panel it serves. */
internal object CardPanelScopeImpl : CardPanelScope {
    override fun SwingModifier.card(key: String): SwingModifier {
        // A card is addressed by its name, and the empty name is the one `CardLayout` gives a child added
        // with no card at all - so an empty key would declare a card that cannot be told from no card.
        require(key.isNotEmpty()) { "A CardPanel card key must not be empty." }
        return layoutConstraint(key)
    }
}
