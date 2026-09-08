@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.constants.Orientation
import org.jetbrains.compose.swing.core.dispatchToCaller
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.hierarchyListener
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.node.declaredName
import org.jetbrains.compose.swing.node.rememberMirrorState
import org.jetbrains.compose.swing.platform.LookAndFeelDefaults
import java.awt.BorderLayout
import java.awt.event.HierarchyEvent
import javax.swing.JToolBar
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.plaf.basic.BasicToolBarUI

/**
 * A strip of items - buttons, toggles, separators - the user reaches without opening a menu, held by a
 * `JToolBar`. The bar holds whether it stands docked or in a window of its own, and reports the drags
 * that move it between the two.
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
 * A drag the caller does not answer with a matching [floating] is settled back, so the bar returns to
 * the state the composition declares.
 *
 * A bar docked back from its window, or dropped onto another edge of its container, returns to the
 * region its [modifier] declares, but not to its place among the container's other children: the look
 * and feel's dock adds the bar at the end of them, and it stays there, in z-order and in focus
 * traversal order. A look and feel turns a bar it docks on a side edge to face along that edge, and it
 * is turned back to the [orientation] declared.
 *
 * A floating bar is held by that window instead of the container it was declared in, and its items keep
 * composing there. The composition still counts the bar among the children of the container it left, so
 * declare a floating bar's siblings - and the bar itself - while it is docked. A bar that leaves the
 * composition while floating takes its window with it.
 *
 * @param modifier the [SwingModifier] applied to the underlying `JToolBar`
 * @param orientation the axis along which items are laid out (an [Orientation] `SwingConstants` value);
 *   the default `HORIZONTAL` lays them out along the bar's reading order
 * @param floatable whether the user can drag the tool bar out into a window of its own, and, while it
 *   is docked, to another edge of the container holding it, or `null` to leave the choice the bar
 *   already carries, which a bar carries as draggable; a look and feel that implements no floating
 *   ignores it. A bar the user can drag out has to stand in a container laid out by a `BorderLayout` -
 *   a panel under [PanelLayout.Border], or a window's own content - which is the only place a look and
 *   feel docks it back
 *   into the region it came from; anywhere else the bar is refused as it is composed, so declare
 *   `false` there
 * @param floating whether the tool bar stands in a window of its own rather than in the container it was
 *   declared in (controlled); `false` by default, so the bar starts docked where it was declared
 * @param onFloatingChange callback invoked with the state the user drags the bar into, or with the docked
 *   state the bar settles for when it cannot take [floating]
 * @param rollover whether the look and feel draws an item's border only while the pointer is over it,
 *   or `null` to leave the choice to it; a choice withdrawn after being declared settles at its answer
 *   for good, and a look and feel may ignore the request
 * @param content the items hosted by the tool bar; empty by default
 * @see javax.swing.JToolBar
 */
@Composable
public fun ToolBar(
    modifier: SwingModifier = SwingModifier,
    @Orientation orientation: Int = SwingConstants.HORIZONTAL,
    floatable: Boolean? = null,
    floating: Boolean = false,
    onFloatingChange: (Boolean) -> Unit = {},
    rollover: Boolean? = null,
    content: @Composable () -> Unit = {},
) {
    // Seeded with what a bar holds when it is built rather than with the declaration: a bar cannot float
    // before it stands in a window, so seeding this `true` would make the bar's first docked reading look
    // like the user having docked it.
    val mirror = rememberMirrorState(false)
    // Subscribed here so that the user dragging the bar out or docking it back invalidates on its own
    // instead of waiting for an unrelated recomposition to notice it. What the bar is left on is nothing
    // this body reads from the mirror: the settle below reads the state off the bar.
    mirror.subscribe()
    // Whether the bar ended the last settle holding something other than what was declared for it, which
    // is what a bar that could not float looks like.
    val refused = remember { booleanArrayOf(false) }
    // Counts the moves of the bar the wrapper did not make, which the composition has to answer and
    // cannot see. Reading the count here is what subscribes this composition to those moves; the number
    // itself means nothing. See `recordPlacement` for what counts as one.
    val placements = remember { mutableIntStateOf(0) }
    placements.intValue
    // The floating state the caller has last been told the bar holds: the one it declared and the bar
    // took, the docked state it was handed for a declaration the bar refused, or the state of a change the
    // user made themselves. Every settle records what the bar was left holding here, so a refusal that
    // still stands is one the caller has already heard. Null while the caller has been told nothing, which
    // is what tells a first refusal apart from a repeat of one.
    val reportedFloating = remember { arrayOfNulls<Boolean>(1) }
    // Whether the bar stands somewhere other than where the composition put it.
    val displaced = remember { booleanArrayOf(false) }

    SwingNode(
        // Rollover is applied here rather than through the update block below, which skips the pass
        // that builds the bar. A bar consults its look and feel for rollover item borders only while it
        // records no choice of its own, and writing the look and feel's own answer onto it records one,
        // so a bar nothing declares a choice for is left alone.
        factory = { JToolBar(orientation).also { bar -> rollover?.let { bar.isRollover = it } } },
        // Nothing is written to the bar from the hierarchy event. Settling belongs to a composition
        // pass, which is the one place the declaration to settle against exists - and writing to the
        // hierarchy from inside a hierarchy event deadlocks, since the event arrives holding the AWT
        // tree lock that the write needs the toolkit to take. A change made inside a write of this
        // wrapper's own is the declaration taking effect, and neither the mirror nor the placement
        // count takes it for the user's.
        modifier =
            modifier
                .hierarchyListener { event ->
                    if (event.changeFlags and HierarchyEvent.PARENT_CHANGED.toLong() == 0L) {
                        return@hierarchyListener
                    }
                    val bar = event.component as JToolBar
                    if (!mirror.isWriting) recordPlacement(event, bar, displaced, refused, placements)
                    val standing = bar.isFloating
                    if (mirror.observed(standing)) {
                        reportedFloating[0] = standing
                        onFloatingChange(standing)
                    }
                }.declaredFloatable(floatable),
        update = {
            set(orientation) { this.orientation = it }
            update(rollover) { isRollover = it ?: LookAndFeelDefaults.toolBarRollover }

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
                val bar = component
                bar.checkStandsWhereItCanDock()
                mirror.settle(
                    floating,
                    { bar.isFloating },
                    { standing ->
                        // A dock is the look and feel putting the bar back where it pleases. It is a
                        // move made inside a write of this wrapper's own, which `recordPlacement` does
                        // not count, so the write records it.
                        if (!standing) displaced[0] = true
                        bar.applyFloating(standing)
                    },
                )
                val settled = bar.isFloating
                // A float the bar took leaves nothing to place there, and so answers every move made
                // before it. One the look and feel refused leaves the bar exactly where it stood, with
                // any outstanding move still owed the placement below.
                if (settled) displaced[0] = false
                placeAsDeclared(mirror, displaced, settled, orientation)
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

/**
 * Whether the user can drag the bar out. The `ToolBar.floatable` key cannot answer for it: a look and
 * feel writes the choice onto the bar itself, so the bar can carry one no key names.
 */
private fun SwingModifier.declaredFloatable(floatable: Boolean?): SwingModifier =
    if (floatable == null) {
        this
    } else {
        property<JToolBar, Boolean>(
            name = "floatable",
            value = floatable,
            read = { it.isFloatable },
            write = { bar, value -> bar.isFloatable = value },
        )
    }

/**
 * Records a move of [bar] the composition did not make, so that a pass answering it is asked for: the
 * bar is put back where it is declared, and a refused float is retried, since only the container the
 * bar is moved into can say whether there is a window to open the bar's own beside.
 *
 * A parent change is announced to every component under the one that moved, so a move of the bar itself
 * is read on the removal half, which `Container.remove` announces with the parent already nulled. Every
 * move the look and feel makes begins by taking the bar out of where it stands, while the applier's own
 * attach only adds, so an arrival alone is this wrapper's own.
 */
private fun recordPlacement(
    event: HierarchyEvent,
    bar: JToolBar,
    displaced: BooleanArray,
    refused: BooleanArray,
    placements: MutableIntState,
) {
    val moved = event.changed === bar && bar.parent == null
    if (moved) displaced[0] = true
    if (moved || refused[0]) placements.intValue++
}

/**
 * Whether the bar stands in a window of its own rather than in the container it was declared in.
 *
 * Dragging a tool bar out is the job of its UI. A look and feel whose tool bars are not draggable never
 * floats one, so this is always `false`.
 */
private val JToolBar.isFloating: Boolean
    get() = (ui as? BasicToolBarUI)?.isFloating == true

/**
 * Puts the bar back how the composition declares it, where a move this wrapper did not make has left it
 * otherwise: under the constraint its own modifier declares, facing the [orientation] declared. A bar
 * that is [floating] is left alone, since the container it is composed in holds no region for it.
 *
 * The orientation is written from here rather than through the declaration the update block applies,
 * which compares against the last declaration and so has nothing to write when the bar alone changed.
 * The write goes through [mirror], which marks it as this wrapper's own.
 */
private fun SwingNodeHolder<JToolBar>.placeAsDeclared(
    mirror: MirrorState<Boolean>,
    displaced: BooleanArray,
    floating: Boolean,
    @Orientation orientation: Int,
) {
    if (!displaced[0] || floating) return
    displaced[0] = false
    mirror.write {
        reapplyConstraint()
        component.orientation = orientation
    }
}

/**
 * Refuses a bar that can be dragged out of a container it could not be docked back into.
 *
 * `BasicToolBarUI` docks a bar by adding it back under a `BorderLayout` region it names itself, and
 * names `BorderLayout.NORTH` where the container's own manager holds no region it could read. Only a
 * `BorderLayout` container answers with the region the bar actually stands in, so only there does a
 * dragged-out bar come back where it was declared.
 *
 * Checked while the bar stands where the composition put it: a floating bar stands in the window its
 * look and feel opened, which answers for nothing here.
 */
private fun JToolBar.checkStandsWhereItCanDock() {
    if (!isFloatable || isFloating) return
    val parent = parent ?: return
    require(parent.layout is BorderLayout) {
        val manager = parent.layout?.let { "a ${it.javaClass.simpleName}" } ?: "no layout manager"
        "A tool bar the user can drag out has to stand in a container laid out by a BorderLayout - a " +
            "panel under PanelLayout.Border, or a window's own content - which is the only place its " +
            "look and feel docks it back into the region it came from, but the $declaredName " +
            "declared here stands in a " +
            "${parent.declaredName} laid out by $manager. " +
            "Declare floatable = false, or hold the bar in a panel under PanelLayout.Border."
    }
}

/**
 * Floats [this] bar out of the container holding it, or docks it back into that container.
 *
 * Floating hands the bar to a new window, which the look and feel opens beside the window the bar already
 * stands in. The bar stays where it is if it stands in no window, if its look and feel gives tool bars no
 * dragging, or if it is not floatable.
 *
 * Docking goes back through the same UI, which picks the region itself; [placeAsDeclared] puts the
 * declared one back.
 */
private fun JToolBar.applyFloating(floating: Boolean) {
    val toolBarUi = ui as? BasicToolBarUI ?: return
    if (floating && SwingUtilities.getWindowAncestor(this) == null) return
    toolBarUi.setFloating(floating, null)
}
