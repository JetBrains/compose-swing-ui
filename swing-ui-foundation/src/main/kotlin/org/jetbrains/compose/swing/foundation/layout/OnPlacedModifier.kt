@file:JvmMultifileClass
@file:JvmName("LayoutKt")

package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.foundation.graphics.layoutBounds
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.CallbackRegistration
import org.jetbrains.compose.swing.modifier.listener.ListenerRegistration
import org.jetbrains.compose.swing.modifier.listener.listener
import java.awt.Component
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

/**
 * Runs [onSizeChanged] with the extent the component occupies whenever its size changes.
 *
 * A child is measured by whatever its parent decides, so this is how the caller who declared the
 * constraints learns what came of them. After the first extent has been reported, a pass that lays the
 * component out at that same extent, or that moves it without resizing it, reports nothing. Declaring
 * this onto a component already placed at a non-zero extent likewise waits for a new extent; an initial
 * zero extent is reported on the first move, resize or show, because it is a valid settled result.
 *
 * The report arrives on the event dispatch thread after placement, and names the size of the layout bounds:
 * paint outsets, such as a shadow's, make the component's bounds larger and leave this report alone.
 *
 * Swing's move, resize and show events drive the report, as they do for any Swing component. A change of the layout
 * bounds that keeps the bounds, as when the component leaves a Foundation container, is reported too. A hidden
 * component reports nothing, and a Foundation parent hides a component it measures but does not place. Shown again,
 * it reports only an extent that differs from the last one it reported.
 *
 * [onSizeChanged] is read when the report fires, so writing a fresh lambda on every recomposition
 * registers nothing again. Declaring this twice reports twice: each declaration is its own slot.
 *
 * @param onSizeChanged receives the extent as its own value, safe to keep.
 * @return this chain with the extent report declared on it.
 * @see onPlaced
 */
public fun SwingModifier.onSizeChanged(onSizeChanged: (Dimension) -> Unit): SwingModifier =
    listener(onSizeChanged, SIZE_CHANGED)

/**
 * Runs [onPlaced] with the bounds the component occupies in its parent whenever those bounds change.
 *
 * This is [onSizeChanged]'s counterpart for where a parent put the child rather than how large it made
 * it, and it reports a resize as well, since a resize is a placement too. After the first bounds have
 * been reported, a pass that lays the component out where it already stood reports nothing, and neither
 * does an ancestor moving: the bounds are stated in the parent's coordinates, which a move further up
 * does not change.
 *
 * The report arrives on the event dispatch thread after placement, and names the layout bounds in the parent's
 * layout coordinates: those of the parent's layout bounds under a Foundation container, and its Swing coordinates
 * under any other parent. Paint outsets, such as a shadow's, move the component's bounds and leave this report
 * alone.
 *
 * Swing's move, resize and show events drive the report, as they drive [onSizeChanged]. Shown again, the component
 * reports its bounds again.
 *
 * @param onPlaced receives the bounds as their own value, safe to keep.
 * @return this chain with the placement report declared on it.
 * @see onSizeChanged
 */
public fun SwingModifier.onPlaced(onPlaced: (Rectangle) -> Unit): SwingModifier = listener(onPlaced, PLACED)

/**
 * One layout report, driven by Swing's move, resize and show events: it hands what [read] takes off the component's
 * layout bounds to the callback declared right now, unless that is the reading it holds as reported already.
 *
 * The held reading answers two questions at once. AWT states a resize and a move as two notifications,
 * so one placement doing both arrives twice, and a move arrives carrying a reading only the bounds
 * carry; holding the last one makes either a single report of what actually changed. The first event is
 * still delivered when it repeats an attach-time zero extent: zero is a real settled result, not the
 * absence of one.
 *
 * And the chain writes geometry before this is listening, whatever order the caller declares it in:
 * every geometry modifier is a keyed element and every listener an additive one, and a pass applies all
 * of the keyed elements before any of the additive ones. Whether AWT even announces such a write is not
 * this modifier's to know - `Component.notifyNewBounds` posts nothing unless some listener is already
 * attached, which depends on what else the chain carries. [seedFrom] takes the reading at attach so the
 * outcome is the same either way: the report names a change from where the component already stood,
 * announced or not.
 *
 * A hidden component reports nothing, and keeps the reading it holds. Hiding it drops the report it owes for the zero
 * extent it was attached at.
 */
private abstract class BoundsReport<V : Any>(
    private val declared: () -> (V) -> Unit,
) : ComponentAdapter() {
    protected var reported: V? = null
    private var initialZeroPending: Boolean = false

    /** What this report names of [layoutBounds]. */
    abstract fun read(layoutBounds: Rectangle): V

    /** Takes what [component] reads now as the deduplication baseline, without reporting it. */
    fun seedFrom(component: Component) {
        val baseline = component.layoutBounds
        reported = read(baseline)
        initialZeroPending = baseline.width == 0 && baseline.height == 0
    }

    override fun componentResized(event: ComponentEvent): Unit = report(event.component)

    override fun componentMoved(event: ComponentEvent): Unit = report(event.component)

    override fun componentShown(event: ComponentEvent): Unit = report(event.component)

    override fun componentHidden(event: ComponentEvent) {
        initialZeroPending = false
    }

    fun report(component: Component) {
        if (!component.isVisible) return
        val reading = read(component.layoutBounds)
        if (!initialZeroPending && reading == reported) return
        reported = reading
        initialZeroPending = false
        declared()(reading)
    }
}

/** The [onSizeChanged] report. */
private class SizeReport(
    declared: () -> (Dimension) -> Unit,
) : BoundsReport<Dimension>(declared) {
    override fun read(layoutBounds: Rectangle): Dimension = layoutBounds.size
}

/**
 * The [onPlaced] report. Shown again, the component reports its placement again, as androidx reports a node placed
 * again.
 */
private class PlacementReport(
    declared: () -> (Rectangle) -> Unit,
) : BoundsReport<Rectangle>(declared) {
    override fun read(layoutBounds: Rectangle): Rectangle = layoutBounds

    override fun componentShown(event: ComponentEvent) = reportAgain(event.component)

    /** Reports [component]'s placement again, dropping the reading held as reported already. */
    fun reportAgain(component: Component) {
        reported = null
        report(component)
    }
}

/**
 * Where a report named [name] registers: `addComponentListener`, with the reading the component holds
 * taken as it goes on, so the report starts from where the component stands.
 */
private fun boundsReportOn(name: String) =
    ListenerRegistration<Component, BoundsReport<*>>(
        name = name,
        attach = { component, report ->
            report.seedFrom(component)
            component.addComponentListener(report)
        },
        detach = { component, report -> component.removeComponentListener(report) },
    )

private val SIZE_CHANGED =
    CallbackRegistration(
        adapter = ::SizeReport,
        registration = boundsReportOn("onSizeChanged"),
    )

private val PLACED =
    CallbackRegistration(
        adapter = ::PlacementReport,
        registration = boundsReportOn("onPlaced"),
    )

/**
 * Posts a move event for this component through the event queue: its layout bounds changed where its bounds, which
 * Swing announces through a component listener, did not. The event arrives after the running one, never inside a
 * layout.
 */
internal fun Component.postComponentMoved() {
    if (componentListeners.isEmpty()) return
    toolkit.systemEventQueue.postEvent(ComponentEvent(this, ComponentEvent.COMPONENT_MOVED))
}

/**
 * Reports this component's own [onPlaced] declarations again, as androidx reports a node placed again, and its
 * [onSizeChanged] declarations only where the size differs from the one last reported.
 */
internal fun Component.reportPlacedAgain() {
    for (listener in componentListeners) {
        when (listener) {
            is PlacementReport -> listener.reportAgain(this)
            is SizeReport -> Snapshot.withoutReadObservation { listener.report(this) }
        }
    }
}
