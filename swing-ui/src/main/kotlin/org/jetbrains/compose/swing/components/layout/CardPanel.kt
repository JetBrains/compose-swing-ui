@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.SwingNode
import org.jetbrains.compose.swing.core.LocalSwingConstraint
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import java.awt.CardLayout
import javax.swing.JPanel

/**
 * A composable wrapper for JPanel with CardLayout: a deck of cards of which exactly one is shown.
 *
 * Declare the cards in [block] and pick the shown one with [selectedCard]:
 * ```
 * CardPanel(selectedCard = page) {
 *     card("welcome") { WelcomePage() }
 *     card("details") { DetailsPage() }
 * }
 * ```
 * A card hosts exactly one child, and dropping a card (e.g. behind an `if`) removes its child.
 * Declaring the same key twice keeps the last declaration under that key. A [selectedCard] matching no
 * declared key leaves the card currently on top showing.
 *
 * @param selectedCard the key of the card to show
 * @param modifier the [SwingModifier] applied to the panel
 * @param hgap the horizontal gap between the panel's left/right edges and the shown card
 * @param vgap the vertical gap between the panel's top/bottom edges and the shown card
 * @param block declares the cards; see [CardPanelScope]
 */
@Composable
public fun CardPanel(
    selectedCard: String,
    modifier: SwingModifier = SwingModifier,
    hgap: Int = 0,
    vgap: Int = 0,
    block: CardPanelScope.() -> Unit,
) {
    // Collected fresh on every pass, so a card the caller stops declaring loses its child (see SwingNode).
    val scope = CardPanelScopeImpl().apply(block)
    // The panel the node holds, taken from the node on every pass (see SwingNode): the deck is flipped
    // from an effect, which runs outside the node.
    val livePanel = remember { arrayOfNulls<JPanel>(1) }

    SwingNode(
        factory = { JPanel(CardLayout(hgap, vgap)) },
        update = {
            reconcile { livePanel[0] = this }
            updateLayout<CardLayout, _>(hgap) { this.hgap = it }
            updateLayout<CardLayout, _>(vgap) { this.vgap = it }
            applyModifier(modifier)
        },
        content = {
            scope.cards.forEach { (cardKey, cardContent) ->
                // key() gives each card a stable composition identity independent of declaration
                // order, so adding or dropping one card never reshuffles the others.
                key(cardKey) {
                    CompositionLocalProvider(LocalSwingConstraint provides cardKey) {
                        cardContent()
                    }
                }
            }
        },
    )

    // Flip to the selected card once this composition's changes have reached the component tree. The
    // cards are emitted in `content`, which the runtime applies after the node's update block, so a
    // card added by the same composition is not yet a child of the panel while that block runs and
    // CardLayout would have nothing to flip to. Running on every composition also re-asserts the
    // selection after the deck itself changed.
    SideEffect {
        val panel = livePanel[0] ?: return@SideEffect
        (panel.layout as CardLayout).show(panel, selectedCard)
    }
}

/**
 * Collects the card declarations for one composition. Each `card` call stores its content under the
 * key that also becomes the child's [CardLayout] constraint; declaring a key again overwrites the
 * previous entry, so the last declaration wins - the same resolution `CardLayout` applies to a name
 * added twice. The map preserves first-declaration order purely for stable iteration.
 */
private class CardPanelScopeImpl : CardPanelScope {
    val cards: MutableMap<String, @Composable () -> Unit> = LinkedHashMap()

    override fun card(
        key: String,
        content: @Composable () -> Unit,
    ) {
        cards[key] = content
    }
}
