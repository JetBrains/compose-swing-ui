@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.constants.Orientation
import org.jetbrains.compose.swing.core.dispatchToCaller
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.hierarchyListener
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.rememberAppliedValue
import java.awt.event.HierarchyEvent
import javax.swing.JToolBar
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.plaf.basic.BasicToolBarUI

/**
 * A composable wrapper for `JToolBar`, hosting a row or column of items.
 *
 * The items declared in [content] become the tool bar's children in declaration order:
 * ```
 * ToolBar {
 *     Button(text = "New", onClick = { ... })
 *     ToolBarSeparator()
 *     Button(text = "Open", onClick = { ... })
 * }
 * ```
 *
 * A [Glue] among the items pushes the ones after it to the trailing end, which is how a tool bar gets
 * a trailing group.
 *
 * A floatable bar can be dragged out into a window of its own. [floating] is a two-way state: it puts the
 * bar into its own window or brings it back, and [onFloatingChange] reports the state the user drags the
 * bar into or docks it back to. Floating needs a window to open the bar's own beside, and a bar whose
 * look and feel gives it no dragging, whose [floatable] is `false`, or that is not in a window yet has
 * none to open. Where the bar cannot take the declaration it stays docked, and [onFloatingChange] is
 * handed that answer; a bar that comes to stand in a window later takes the standing declaration then.
 *
 * A floating bar is held by that window instead of the container it was declared in, and its items keep
 * composing there. The composition still counts the bar among the children of the container it left, so
 * declare a floating bar's siblings - and the bar itself - while it is docked. A bar that leaves the
 * composition while floating takes its window with it.
 *
 * @param modifier the [SwingModifier] applied to the underlying `JToolBar`
 * @param orientation the axis along which items are laid out (an [Orientation] `SwingConstants` value)
 * @param floatable whether the user can drag the tool bar out into a floating window
 * @param floating whether the tool bar stands in a window of its own rather than in the container it was
 *   declared in (controlled)
 * @param onFloatingChange callback invoked with the state the user drags the bar into, or with the docked
 *   state the bar settles for when it cannot take [floating]
 * @param rollover whether the look and feel draws an item's border only while the pointer is over it,
 *   or `null` to leave that choice to the look and feel; a choice withdrawn after being declared
 *   settles at its answer for good
 * @param content the items hosted by the tool bar
 * @see javax.swing.JToolBar
 */
@Composable
public fun ToolBar(
    modifier: SwingModifier = SwingModifier,
    @Orientation orientation: Int = SwingConstants.HORIZONTAL,
    floatable: Boolean = true,
    floating: Boolean = false,
    onFloatingChange: (Boolean) -> Unit = {},
    rollover: Boolean? = null,
    content: @Composable () -> Unit = {},
) {
    // Seeded with what a bar holds when it is built rather than with the declaration: a bar cannot float
    // before it stands in a window, so seeding this `true` would make the bar's first docked reading look
    // like the user having docked it.
    val applied = rememberAppliedValue(false)
    // Reading the mirror here subscribes this composition to the user dragging the bar out or docking it
    // back, so a move away from the declaration invalidates on its own instead of waiting for an
    // unrelated recomposition to notice it. The value itself is nothing this body needs: the settle below
    // reads the state off the bar.
    applied.value
    // Whether the bar ended the last settle holding something other than what was declared for it, which
    // is what a bar that could not float looks like.
    val refused = remember { booleanArrayOf(false) }
    // Counts the containers a refused bar has been moved into. Floating needs a window to open the bar's
    // own beside, and which window that is - or whether there is one at all - is the container's to say,
    // so a refusal is answered by the bar being moved rather than by anything the composition declares.
    // Reading the count here is what subscribes this composition to those moves; the number itself means
    // nothing.
    val placements = remember { mutableIntStateOf(0) }
    placements.intValue
    // The floating state the caller has last been told the bar holds: the one it declared and the bar
    // took, the docked state it was handed for a declaration the bar refused, or the state of a move the
    // user made themselves. Every settle records what the bar was left holding here, so a refusal that
    // still stands is one the caller has already heard. Null while the caller has been told nothing, which
    // is what tells a first refusal apart from a repeat of one.
    val reportedFloating = remember { arrayOfNulls<Boolean>(1) }

    SwingNode(
        factory = { JToolBar(orientation).also { bar -> rollover?.let { bar.isRollover = it } } },
        update = {
            set(orientation) { this.orientation = it }
            set(floatable) { this.isFloatable = it }
            update(rollover) { declared ->
                isRollover = declared ?: UIManager.getBoolean(ROLLOVER_DEFAULT)
            }
            // Nothing is written to the bar from the hierarchy event. Settling belongs to a composition
            // pass, which is the one place the declaration to settle against exists - and writing to the
            // hierarchy from inside a hierarchy event deadlocks, since the event arrives holding the AWT
            // tree lock that the write needs the toolkit to take. A move made inside a write of this
            // wrapper's own is the declaration taking effect, and neither the mirror nor the placement
            // count takes it for the user's.
            applyModifier(
                modifier.hierarchyListener { event ->
                    if (event.changeFlags and HierarchyEvent.PARENT_CHANGED.toLong() == 0L) {
                        return@hierarchyListener
                    }
                    val standing = (event.component as JToolBar).isFloating
                    if (refused[0] && !applied.isWriting) placements.intValue++
                    if (applied.observed(standing)) {
                        reportedFloating[0] = standing
                        onFloatingChange(standing)
                    }
                },
            )

            // A bar is declared before it is anywhere - the applier runs this node's update block between
            // its top-down and bottom-up passes - so a floating declaration written here would be written
            // against a bar that stands nowhere and has no window to float out of. Settled at the end of
            // the change pass instead, which is where the bar already hangs in the container that declared
            // it: the pass declaring a floating bar is the pass that moves it out.
            //
            // The refusal reaches the caller from here rather than from inside the settle, so the record of
            // what the caller was told is what decides whether it is news: this runs again on every pass
            // that recomposes the bar or changes its items, and a refusal that already stands is not a
            // second answer. It reaches the caller contained, the way a settle would have dispatched it,
            // so a throw out of it is reported rather than left to end the composition applying this pass.
            settleWithChildren {
                applied.settle(floating, { isFloating }, { standing -> applyFloating(standing) })
                val settled = isFloating
                val unheard = reportedFloating[0] != settled
                reportedFloating[0] = settled
                refused[0] = floating != settled
                if (refused[0] && unheard) dispatchToCaller { onFloatingChange(settled) }
            }
        },
        onRelease = {
            // The floating window is the look and feel's own and outlives the bar unless closed here: the
            // bar is leaving the composition, so the window holding it has nothing left to show.
            if (isFloating) SwingUtilities.getWindowAncestor(this)?.dispose()
        },
        content = content,
    )
}

/** The look-and-feel default a tool bar's UI reads while the bar records no rollover choice of its own. */
private const val ROLLOVER_DEFAULT: String = "ToolBar.isRollover"

/**
 * Whether the bar stands in a window of its own rather than in the container it was declared in.
 *
 * Dragging a tool bar out is the job of its UI. A look and feel whose tool bars are not draggable never
 * floats one, so this is always `false`.
 */
private val JToolBar.isFloating: Boolean
    get() = (ui as? BasicToolBarUI)?.isFloating == true

/**
 * Floats [this] bar out of the container holding it, or docks it back into that container at the place it
 * was taken from.
 *
 * Floating hands the bar to a new window, which the look and feel opens beside the window the bar already
 * stands in. The bar stays where it is if it stands in no window, if its look and feel gives tool bars no
 * dragging, or if it is not floatable.
 */
private fun JToolBar.applyFloating(floating: Boolean) {
    val toolBarUi = ui as? BasicToolBarUI ?: return
    if (floating && SwingUtilities.getWindowAncestor(this) == null) return
    toolBarUi.setFloating(floating, null)
}
