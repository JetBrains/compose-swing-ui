@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.util.DeferredAction
import java.awt.CardLayout
import java.awt.Component
import javax.swing.JPanel

/**
 * A composable wrapper for JPanel with CardLayout: a deck of cards of which exactly one is shown.
 *
 * Each child names the card it is placed on through [CardPanelScope], and [selectedCard] picks the card
 * the deck shows:
 * ```
 * CardPanel(selectedCard = page) {
 *     Label(text = "Welcome", modifier = SwingModifier.card("welcome"))
 *     Label(text = "Details", modifier = SwingModifier.card("details"))
 * }
 * ```
 * A card holds a single child; two children naming the same card are reported as an error on the event
 * dispatch thread once the change pass that caused it has settled, so a pass that replaces a card's
 * occupant need not take the outgoing child out before the incoming one arrives. The report reaches
 * whatever handles an uncaught exception on that thread - by default the JDK's, which prints it and moves
 * on, so an application that installs none of its own keeps running with both children on the card.
 *
 * Dropping a child (e.g. behind an `if`) takes its card with it, and a
 * [selectedCard] matching no card leaves the card currently on top showing. A child that names no card is
 * placed on the deck's empty-named card, which an empty [selectedCard] shows; that card holds a single
 * child like any other, so two children naming none are refused the way a card named twice is.
 *
 * @param selectedCard the key of the card to show
 * @param modifier the [SwingModifier] applied to the panel
 * @param hgap the horizontal gap between the panel's left/right edges and the shown card
 * @param vgap the vertical gap between the panel's top/bottom edges and the shown card
 * @param content the composable content of the deck; see [CardPanelScope]
 * @see java.awt.CardLayout
 */
@Composable
public fun CardPanel(
    selectedCard: String,
    modifier: SwingModifier = SwingModifier,
    hgap: Int = 0,
    vgap: Int = 0,
    content: @Composable CardPanelScope.() -> Unit,
) {
    SwingNode<JPanel>(
        factory = { StateCardPanel(hgap, vgap) },
        update = {
            set(selectedCard) { (this as StateCardPanel).targetCard = it }
            updateLayout<CardDeckLayout, _>(hgap) { this.hgap = it }
            updateLayout<CardDeckLayout, _>(vgap) { this.vgap = it }
            applyModifier(modifier)
        },
        content = { CardPanelScopeImpl.content() },
    )
}

private class StateCardPanel(
    hgap: Int,
    vgap: Int,
) : ScrollablePanel(CardDeckLayout(hgap, vgap)) {
    var targetCard: String? = null
        set(value) {
            field = value
            applyTargetCard()
        }

    /**
     * Checks the deck holds one child per card, on the turn of the event queue after the one the children
     * were added in. Mid-pass a card can hold two, when the child replacing another arrives before that
     * one has left: a parked child gives its place up in a deactivation the runtime dispatches only once
     * the pass parking it is applied whole.
     */
    private val cardCheck =
        DeferredAction {
            val repeated = (layout as CardDeckLayout).repeatedCardName()
            if (repeated != null) cardHeldByMoreThanOneChild(repeated)
        }

    private fun applyTargetCard() {
        val target = targetCard ?: return
        (layout as CardDeckLayout).show(this, target)
    }

    fun onLayoutComponentAdded(constraints: Any?) {
        cardCheck.schedule()
        if (constraints == targetCard) {
            applyTargetCard()
        }
    }

    override fun remove(index: Int) {
        super.remove(index)
        applyTargetCard()
    }

    override fun removeAll() {
        super.removeAll()
        applyTargetCard()
    }
}

/**
 * The [CardLayout] behind [CardPanel], keeping the record of which child holds which card name.
 *
 * `CardLayout` addresses its cards by name, holds one child per name and hands neither back: a child
 * arriving under a name already taken takes that card over, leaving the child before it in the container
 * and reachable by no key. This record is what lets the panel report that instead.
 */
private class CardDeckLayout(
    hgap: Int,
    vgap: Int,
) : CardLayout(hgap, vgap) {
    /** The card each child was registered under, the empty name for a child that named none. */
    private val cardNames = HashMap<Component, String>()

    override fun addLayoutComponent(
        component: Component,
        constraints: Any?,
    ) {
        // The superclass is what refuses a constraint that is not a card name, and a child it refused
        // holds no card to record.
        super.addLayoutComponent(component, constraints)
        cardNames[component] = (constraints as? String).orEmpty()
        (component.parent as? StateCardPanel)?.onLayoutComponentAdded(constraints)
    }

    override fun removeLayoutComponent(component: Component) {
        cardNames.remove(component)
        super.removeLayoutComponent(component)
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
}

/**
 * Reports [name] as a card of a [CardPanel] that more than one child holds, telling the two cases apart:
 * a name written twice, and children that named no card and so share the deck's empty-named one.
 */
private fun cardHeldByMoreThanOneChild(name: String): Nothing =
    throw IllegalArgumentException(
        if (name.isEmpty()) {
            "A CardPanel card holds a single child, but more than one child of this panel names no " +
                "card: they share the deck's empty-named card, the child that reaches it last takes " +
                "it, and the one before it can be shown by no key at all. Name each card with " +
                "SwingModifier.card(key)."
        } else {
            "A CardPanel card holds a single child, but '$name' is named by more than one: the child " +
                "that names it last takes the card, and the one before it can be shown by no key at all."
        },
    )
