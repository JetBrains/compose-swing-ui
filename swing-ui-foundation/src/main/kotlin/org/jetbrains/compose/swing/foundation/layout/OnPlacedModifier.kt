@file:JvmMultifileClass
@file:JvmName("FoundationLayoutKt")

package org.jetbrains.compose.swing.foundation.layout

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
 * zero extent is reported on the first resize event because it is a valid settled result.
 *
 * The report arrives on the event dispatch thread after the component has taken its new extent, so the
 * component already reads as the extent handed to the callback.
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
 * The report arrives on the event dispatch thread after the component has taken its new bounds, so the
 * component already reads as the bounds handed to the callback.
 *
 * @param onPlaced receives the bounds as their own value, safe to keep.
 * @return this chain with the placement report declared on it.
 * @see onSizeChanged
 */
public fun SwingModifier.onPlaced(onPlaced: (Rectangle) -> Unit): SwingModifier = listener(onPlaced, PLACED)

/**
 * One layout report: it hands what [read] takes off the component to the callback declared right now,
 * unless that is the reading it holds as reported already.
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
 */
private class BoundsReport<V : Any>(
    private val read: (Component) -> V,
    private val declared: () -> (V) -> Unit,
    private val isZero: (V) -> Boolean,
) : ComponentAdapter() {
    private var reported: V? = null
    private var initialZeroPending: Boolean = false

    /** Takes what [component] reads now as the deduplication baseline, without reporting it. */
    fun seedFrom(component: Component) {
        val baseline = read(component)
        reported = baseline
        initialZeroPending = isZero(baseline)
    }

    override fun componentResized(event: ComponentEvent): Unit = report(event)

    override fun componentMoved(event: ComponentEvent): Unit = report(event)

    private fun report(event: ComponentEvent) {
        val reading = read(event.component)
        if (!initialZeroPending && reading == reported) return
        reported = reading
        initialZeroPending = false
        declared()(reading)
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
        adapter = { current -> BoundsReport({ it.size }, current) { it.width == 0 && it.height == 0 } },
        registration = boundsReportOn("onSizeChanged"),
    )

private val PLACED =
    CallbackRegistration(
        adapter = { current ->
            BoundsReport({ it.bounds }, current) { it.width == 0 && it.height == 0 }
        },
        registration = boundsReportOn("onPlaced"),
    )
