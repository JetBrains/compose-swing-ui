package org.jetbrains.compose.swing.components.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.node.ChildPlacement
import org.jetbrains.compose.swing.node.SlotAttachment
import org.jetbrains.compose.swing.node.wrongSlotHost
import java.awt.Component
import java.awt.Container
import javax.swing.JSplitPane

/**
 * The receiver of a [SplitPane]'s content, through which a child declares which of the two sides it
 * occupies.
 *
 * A pane offers two sides, [first] and [second], and holds each child on the one it names rather than
 * among indexed children of its own. Children are written plainly; the side a child names here rides
 * along on its `modifier`:
 *
 * ```
 * SplitPane {
 *     Navigator(modifier = SwingModifier.first())
 *     Editor(modifier = SwingModifier.second())
 * }
 * ```
 *
 * Under [JSplitPane.HORIZONTAL_SPLIT] the [first] side is the left, the [second] the right; under
 * [JSplitPane.VERTICAL_SPLIT] the [first] side is the top, the [second] the bottom.
 *
 * Every child names a side, and a side hosts one child: a child that names none is refused, and so are
 * two children naming one side, which the failure names. The last side named in a chain is the one that
 * child occupies, and a side no child names stays empty. A pass that swaps one child for another on a
 * side is a single occupant throughout, whichever order the two changes reach the pane in.
 *
 * @see javax.swing.JSplitPane
 */
public sealed interface SplitPaneScope {
    /**
     * Places the child on the leading side of the pane: the left under a horizontal split, the top
     * under a vertical one.
     *
     * @see javax.swing.JSplitPane.setLeftComponent
     */
    public fun SwingModifier.first(): SwingModifier

    /**
     * Places the child on the trailing side of the pane: the right under a horizontal split, the
     * bottom under a vertical one.
     *
     * @see javax.swing.JSplitPane.setRightComponent
     */
    public fun SwingModifier.second(): SwingModifier
}

/**
 * The [SplitPaneScope] every [SplitPane] hands its content. A side builder appends the attachment that
 * installs a child into that side of whichever pane encloses it, and holds nothing of the pane it was
 * called under, so one instance serves them all.
 */
internal object SplitPaneScopeImpl : SplitPaneScope {
    override fun SwingModifier.first(): SwingModifier = this.slot(SplitSide.First.label, FirstSideAttachment)

    override fun SwingModifier.second(): SwingModifier = this.slot(SplitSide.Second.label, SecondSideAttachment)
}

/**
 * The two sides a [SplitPane] holds its children on, named as the [SplitPaneScope] builders that fill
 * them, so a child refused a side is told the call that would place it.
 */
internal val SplitPaneSides: ChildPlacement = ChildPlacement.Slots(SplitSide.First.label, SplitSide.Second.label)

/**
 * Whether a side is the leading (`setLeftComponent`) or trailing (`setRightComponent`) one. The [label]
 * is the call a child writes to fill that side, which is how an error about the side refers to it.
 */
private enum class SplitSide(
    val label: String,
) {
    First("SwingModifier.first()"),
    Second("SwingModifier.second()"),
}

/** The child the pane holds on [side], `null` while that side is empty. */
private fun JSplitPane.componentOn(side: SplitSide): Component? =
    when (side) {
        SplitSide.First -> leftComponent
        SplitSide.Second -> rightComponent
    }

/** Gives [side] of the pane to [component], or empties it where [component] is `null`. */
private fun JSplitPane.holdOn(
    side: SplitSide,
    component: Component?,
) {
    when (side) {
        SplitSide.First -> leftComponent = component
        SplitSide.Second -> rightComponent = component
    }
}

/**
 * The pane a side-filling child is hosted by. [SplitPaneScope.first] and [SplitPaneScope.second] install
 * through a `JSplitPane` setter, so a child carrying one under another container's host - a scroll pane's
 * `viewport()`, say, composed under a split pane - is refused here, naming the side's own builder and the
 * host that actually holds it, rather than failing later as a bare `ClassCastException`.
 */
private fun splitPaneHost(
    host: Container,
    side: SplitSide,
): JSplitPane = host as? JSplitPane ?: error(wrongSlotHost(host, JSplitPane::class.java, side.label))

/**
 * Installs a child into [side] of the host `JSplitPane`; uninstall releases that side.
 *
 * The side is released for the child that installed it, which is what it holds unless a replacement has
 * already taken its place - the pass that swaps one child for another need not take the outgoing one out
 * first, and the child arriving is the one the pane keeps. Whether a side ends the pass holding what one
 * child declared is answered once the pass has settled, by the [ChildPlacement.Slots] the pane declares.
 */
private fun splitSideAttachment(side: SplitSide): SlotAttachment =
    SlotAttachment { host, component, _ ->
        val pane = splitPaneHost(host, side)
        pane.holdOn(side, component)
        return@SlotAttachment {
            if (pane.componentOn(side) === component) pane.holdOn(side, null)
        }
    }

/** The attachment installing a child into the leading side; it captures nothing, so one serves every pane. */
private val FirstSideAttachment: SlotAttachment = splitSideAttachment(SplitSide.First)

/** The attachment installing a child into the trailing side; it captures nothing, so one serves every pane. */
private val SecondSideAttachment: SlotAttachment = splitSideAttachment(SplitSide.Second)
