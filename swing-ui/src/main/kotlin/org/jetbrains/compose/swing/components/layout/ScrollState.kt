@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Point
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
 * A position outside the content is not refused, exactly as the widget does not refuse one: the pane
 * takes it, and its next layout pass corrects it and reports the corrected value back here. The
 * position outlives the content it was reached in - new content is scrolled to where the state stands,
 * so a pane that leaves the composition and returns comes back where the user left it. A state renders
 * at most one pane; declaring it on a second one moves it to that pane, and it moves back to the first
 * as soon as the second stops declaring it while the first still does.
 */
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

    // Whether this state's position still has to reach a viewport. A position can only be delivered to
    // a viewport that has content to move (Swing ignores it otherwise), and a pane binds before its
    // content is installed, so the position is delivered again on the first change the viewport
    // publishes - the layout pass that installs the content is one - and adopted from the viewport
    // from then on.
    private var positionUndelivered = true

    private val viewportListener = ChangeListener { viewport?.let { bound -> syncFrom(bound) } }

    /** The view coordinate shown at the viewport's leading edge. */
    public var x: Int
        get() = xState
        set(value) {
            xState = value
            deliverPosition()
        }

    /** The view coordinate shown at the viewport's top edge. */
    public var y: Int
        get() = yState
        set(value) {
            yState = value
            deliverPosition()
        }

    /** The width of the visible part of the content, in pixels; `0` while no pane renders this state. */
    public val extentWidth: Int get() = extentWidthState

    /** The height of the visible part of the content, in pixels; `0` while no pane renders this state. */
    public val extentHeight: Int get() = extentHeightState

    /** The full width of the scrolled content, in pixels; `0` while no pane renders this state. */
    public val viewWidth: Int get() = viewWidthState

    /** The full height of the scrolled content, in pixels; `0` while no pane renders this state. */
    public val viewHeight: Int get() = viewHeightState

    /** The largest [x] that shows content; `0` when the content is no wider than the viewport. */
    public val maxX: Int get() = (viewWidthState - extentWidthState).coerceAtLeast(0)

    /** The largest [y] that shows content; `0` when the content is no taller than the viewport. */
    public val maxY: Int get() = (viewHeightState - extentHeightState).coerceAtLeast(0)

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
        // A pane that binds anew is a pane this state's position has not reached yet.
        positionUndelivered = true
        next.addChangeListener(viewportListener)
        syncFrom(next)
    }

    // Drops the bound viewport, along with the metrics that belong to it.
    private fun release() {
        viewport?.removeChangeListener(viewportListener)
        viewport = null
        extentWidthState = 0
        extentHeightState = 0
        viewWidthState = 0
        viewHeightState = 0
    }

    // Reads the viewport's metrics into this state and reconciles the position with it: a position of
    // this state's that has not reached the viewport yet is delivered, and otherwise the viewport's own
    // position - which is what the user scrolled to, or what a layout pass corrected - is adopted.
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
        if (target.view == null) {
            // An empty viewport has no position of its own - it answers (0,0) for want of anything to
            // move - so adopting one would discard the position this state holds. That position is
            // instead owed to whatever content comes next.
            positionUndelivered = true
            return
        }
        if (positionUndelivered) {
            deliverPosition()
        } else {
            val position = target.viewPosition
            xState = position.x
            yState = position.y
        }
    }

    // Scrolls the bound viewport to this state's position, verbatim: Swing clamps nothing here and the
    // pane's next layout pass corrects an out-of-range position, publishing the corrected value back
    // through the change listener. A viewport with no content to move takes nothing, so the position
    // stays undelivered until one has content.
    private fun deliverPosition() {
        val target = viewport ?: return
        if (target.view == null) return
        positionUndelivered = false
        target.viewPosition = Point(xState, yState)
    }
}

/**
 * Creates and remembers a [ScrollState] starting at [x], [y].
 *
 * A later change to [x] or [y] neither recreates the state nor moves the pane; scroll it afterwards
 * through the returned state's [ScrollState.x] and [ScrollState.y].
 *
 * @param x the view coordinate to show at the viewport's leading edge.
 * @param y the view coordinate to show at the viewport's top edge.
 */
@Composable
public fun rememberScrollState(
    x: Int = 0,
    y: Int = 0,
): ScrollState = remember { ScrollState(x, y) }

/**
 * Binds [state] to the composable's scroll pane through the modifier chain, so the binding follows the
 * modifier node's lifecycle: a state swap on recomposition unbinds the previous state before binding
 * the new one, and the node detaching (the pane leaving the composition, being recycled for reuse, or
 * parking while deactivated) unbinds outright. Either way only this pane's own declaration is given up:
 * a state that has since moved to another pane keeps driving that one, and a state this pane drove while
 * another pane declares it too moves onto that pane.
 */
internal fun SwingModifier.scrollStateBinding(state: ScrollState): SwingModifier = this then ScrollStateElement(state)

private class ScrollStateElement(
    private val state: ScrollState,
) : SwingModifier.Element<JScrollPane, ScrollStateElement.Node> {
    override val targetType: Class<JScrollPane> get() = JScrollPane::class.java

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.state = state
    }

    class Node : SwingModifier.Node<JScrollPane>() {
        // The currently bound state, held so a swap unbinds exactly the previous one - the one thing
        // the composable's update block cannot know. Unbinding names this node's pane, so a state this
        // node is giving up but another pane has already taken over is left driving that pane.
        var state: ScrollState? = null
            set(value) {
                if (value === field) return
                field?.unbind(component)
                field = value
                value?.bind(component)
            }

        override fun onDetach() {
            state = null
        }
    }
}
