@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.binding
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import javax.swing.JScrollPane
import javax.swing.JViewport
import javax.swing.event.ChangeListener

/**
 * A hoistable state holder for the scroll position of a [ScrollPane].
 *
 * [x] and [y] are two-way: assigning them scrolls the pane, and the user scrolling it - by wheel,
 * scrollbar or keyboard - writes the new position back. The metrics the position is bounded by
 * ([extentWidth], [extentHeight], [viewWidth], [viewHeight]) and the largest useful position
 * ([maxX], [maxY]) follow the content and the pane's size, so `state.y = state.maxY` scrolls to the
 * bottom of whatever is currently there.
 *
 * Every value is snapshot-observable: reading one inside a composable (or a `snapshotFlow` collector)
 * subscribes to later scrolling and resizing, so the reader recomposes as the pane moves.
 *
 * [canScrollForwardX], [canScrollBackwardX], [canScrollForwardY] and [canScrollBackwardY] answer whether
 * an axis has anywhere left to scroll, for a caller that offers a way to scroll further and wants it
 * disabled at the end. A reader of one of them stands still until the answer itself changes.
 *
 * [revealRect] scrolls to a region of the content instead of to a coordinate, for a caller that knows
 * where something is but not where the pane has to stand to show it.
 *
 * A position outside the content is not refused, exactly as the widget does not refuse one: the pane
 * takes it, and its next layout pass corrects it and reports the corrected value back here. The
 * position outlives the content it was reached in - new content is scrolled to where the state stands,
 * so a pane that leaves the composition and returns comes back where the user left it. A state renders
 * at most one pane; declaring it on a second one moves it to that pane, and it moves back to the first
 * as soon as the second stops declaring it while the first still does.
 *
 * @see javax.swing.JViewport
 */
@Stable
public class ScrollState internal constructor(
    x: Int,
    y: Int,
) {
    private var xState by mutableIntStateOf(x)
    private var yState by mutableIntStateOf(y)
    private var extentWidthState by mutableIntStateOf(0)
    private var extentHeightState by mutableIntStateOf(0)
    private var viewWidthState by mutableIntStateOf(0)
    private var viewHeightState by mutableIntStateOf(0)

    // The viewport this state drives and observes, or null while unbound.
    private var viewport: JViewport? = null

    // Every pane whose declaration of this state is live, each once, oldest declaration first: the last is
    // the pane the state drives, and a pane giving its declaration up hands the binding back to the one
    // before it. A pane is dropped the moment it stops declaring this state, so no entry outlives a
    // declaration and none of them keeps a pane alive past its composition.
    private val claimants = mutableListOf<JScrollPane>()

    private val viewportListener = ChangeListener { viewport?.let { bound -> syncFrom(bound) } }

    // A position can only be delivered to a viewport that has content to move - Swing ignores it
    // otherwise - and a pane binds before its content is installed, so the install is what the position
    // waits for. The arrival is heard here rather than read off a published change: a viewport publishes
    // no change of its own for the first view it is ever given, and a position still owed to a viewport
    // that already holds content would overwrite whatever moved it in the meantime.
    private val contentListener =
        object : ContainerAdapter() {
            override fun componentAdded(event: ContainerEvent) {
                deliverPosition()
            }
        }

    /**
     * The view coordinate shown at the viewport's left edge.
     *
     * @see javax.swing.JViewport.setViewPosition
     */
    public var x: Int
        get() = xState
        set(value) {
            xState = value
            deliverPosition()
        }

    /**
     * The view coordinate shown at the viewport's top edge.
     *
     * @see javax.swing.JViewport.setViewPosition
     */
    public var y: Int
        get() = yState
        set(value) {
            yState = value
            deliverPosition()
        }

    /**
     * The width of the visible part of the content, in view coordinates; `0` while no pane renders
     * this state.
     *
     * @see javax.swing.JViewport.getExtentSize
     */
    public val extentWidth: Int get() = extentWidthState

    /**
     * The height of the visible part of the content, in view coordinates; `0` while no pane renders
     * this state.
     *
     * @see javax.swing.JViewport.getExtentSize
     */
    public val extentHeight: Int get() = extentHeightState

    /**
     * The full width of the scrolled content, in view coordinates; `0` while no pane renders this state.
     *
     * @see javax.swing.JViewport.getViewSize
     */
    public val viewWidth: Int get() = viewWidthState

    /**
     * The full height of the scrolled content, in view coordinates; `0` while no pane renders this
     * state.
     *
     * @see javax.swing.JViewport.getViewSize
     */
    public val viewHeight: Int get() = viewHeightState

    /** The largest [x] that shows content; `0` when the content is no wider than the viewport. */
    public val maxX: Int get() = (viewWidthState - extentWidthState).coerceAtLeast(0)

    /** The largest [y] that shows content; `0` when the content is no taller than the viewport. */
    public val maxY: Int get() = (viewHeightState - extentHeightState).coerceAtLeast(0)

    /**
     * Whether any content is left beyond the visible part across the pane: `false` once [x] stands at
     * [maxX], and while the content is no wider than the viewport or no pane renders this state.
     */
    public val canScrollForwardX: Boolean by derivedStateOf(structuralEqualityPolicy()) { xState < maxX }

    /**
     * Whether any content is left before the visible part across the pane: `false` while [x] stands at
     * `0`.
     */
    public val canScrollBackwardX: Boolean by derivedStateOf(structuralEqualityPolicy()) { xState > 0 }

    /**
     * Whether any content is left beyond the visible part down the pane: `false` once [y] stands at
     * [maxY], and while the content is no taller than the viewport or no pane renders this state.
     */
    public val canScrollForwardY: Boolean by derivedStateOf(structuralEqualityPolicy()) { yState < maxY }

    /**
     * Whether any content is left before the visible part down the pane: `false` while [y] stands at
     * `0`.
     */
    public val canScrollBackwardY: Boolean by derivedStateOf(structuralEqualityPolicy()) { yState > 0 }

    /**
     * Brings the region [rect] names of the pane's content into view - in the content's own coordinates,
     * where its top left corner is the origin, whatever the pane is currently scrolled to - and returns
     * whether it was reached.
     *
     * Revealing is a gesture rather than a declaration: it scrolls where it is called and leaves nothing
     * behind, so no later pass scrolls back and where the user scrolls afterwards stands. Wherever it
     * lands is reported back through [x] and [y], like the user's own scrolling.
     *
     * `false` means nothing was revealed: no pane renders this state, or the pane holds no content to
     * scroll. `true` means the pane was asked to show that region, which scrolls it as far as the content
     * reaches: a region larger than the viewport is shown from its leading edge, and one already in view
     * leaves the pane where it stands.
     *
     * @param rect the region asked for, read during the call and neither kept nor modified.
     * @return whether the pane was asked to show the region.
     * @see javax.swing.JViewport.scrollRectToVisible
     */
    public fun revealRect(rect: Rectangle): Boolean {
        val target = viewport
        val view = target?.view ?: return false
        // The viewport takes the region in its own coordinates, which the view's position - the scrolled
        // offset, negated - translates the content's into.
        target.scrollRectToVisible(Rectangle(rect.x + view.x, rect.y + view.y, rect.width, rect.height))
        return true
    }

    /**
     * Starts driving and observing [target]'s viewport, taking over from the pane this state drove
     * before, if any. The state's own position is delivered to the new pane, and its metrics are read
     * from it. The pane taken over from keeps its own declaration, so it takes the binding back if
     * [target] gives it up.
     */
    internal fun bind(target: JScrollPane) {
        claimants.remove(target)
        claimants.add(target)
        drive(target.viewport)
    }

    /**
     * Gives up [target]'s declaration of this state, leaving [target] scrolled where it is.
     *
     * A state drives the most recent pane that declares it, so giving up a declaration another pane has
     * since taken the binding over from changes nothing about what the state drives, and giving up the
     * declaration the state does drive hands the binding back to the pane that declared it before - the
     * state stops driving anything only once no pane declares it at all. A state therefore keeps driving
     * whichever pane still renders it, in whatever order the two declarations were made.
     *
     * The position is kept so that binding again - the same pane returning to the composition, or
     * another one taking over - restores it; the metrics belong to the pane and are dropped.
     */
    internal fun unbind(target: JScrollPane) {
        claimants.remove(target)
        if (viewport !== target.viewport) return
        val fallback = claimants.lastOrNull()
        if (fallback == null) release() else drive(fallback.viewport)
    }

    // Moves the binding onto `next`, dropping the one held before it, and reconciles this state with the
    // new viewport: the position this state holds is delivered to it, and its metrics are read back.
    private fun drive(next: JViewport) {
        if (viewport === next) return
        release()
        viewport = next
        next.addContainerListener(contentListener)
        next.addChangeListener(viewportListener)
        // A pane that binds anew is a pane this state's position has not reached yet; content already
        // in it takes that position now, and content arriving later takes it as it arrives.
        deliverPosition()
        syncFrom(next)
    }

    // Drops the bound viewport, along with the metrics that belong to it.
    private fun release() {
        viewport?.removeContainerListener(contentListener)
        viewport?.removeChangeListener(viewportListener)
        viewport = null
        extentWidthState = 0
        extentHeightState = 0
        viewWidthState = 0
        viewHeightState = 0
    }

    // Reads the viewport's metrics into this state and adopts its position - what the user scrolled to,
    // or what a layout pass corrected.
    //
    // The sizes are read out as Ints and never kept as the Dimensions they came in: getViewSize can hand
    // back the view's own instance, which the view keeps mutating.
    private fun syncFrom(target: JViewport) {
        val extent = target.extentSize
        extentWidthState = extent.width
        extentHeightState = extent.height
        val view = target.viewSize
        viewWidthState = view.width
        viewHeightState = view.height
        // An empty viewport has no position of its own - it answers (0,0) for want of anything to move -
        // so adopting one would discard the position this state holds. That position is instead owed to
        // whatever content comes next.
        if (target.view == null) return
        val position = target.viewPosition
        xState = position.x
        yState = position.y
    }

    // Scrolls the bound viewport to this state's position, verbatim: Swing clamps nothing here and the
    // pane's next layout pass corrects an out-of-range position, publishing the corrected value back
    // through the change listener. A viewport with no content to move takes nothing, and the position
    // reaches it once content arrives.
    private fun deliverPosition() {
        val target = viewport ?: return
        if (target.view == null) return
        target.viewPosition = Point(xState, yState)
    }
}

/**
 * Creates and remembers a [ScrollState] starting at [x], [y].
 *
 * A later change to [x] or [y] neither recreates the state nor moves the pane; scroll it afterwards
 * through the returned state's [ScrollState.x] and [ScrollState.y].
 *
 * @param x the view coordinate to show at the viewport's left edge.
 * @param y the view coordinate to show at the viewport's top edge.
 */
@Composable
public fun rememberScrollState(
    x: Int = 0,
    y: Int = 0,
): ScrollState = remember { ScrollState(x, y) }

/**
 * Binds [state] to the composable's scroll pane through the modifier chain (see [binding]), which gives
 * up this pane's own declaration of the state - see [ScrollState.unbind] for what the state keeps
 * driving afterwards.
 */
internal fun SwingModifier.scrollStateBinding(state: ScrollState): SwingModifier =
    binding(JScrollPane::class.java, "scrollState", state, ScrollState::bind, ScrollState::unbind)
