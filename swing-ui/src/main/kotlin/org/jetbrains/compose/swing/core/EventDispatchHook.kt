package org.jetbrains.compose.swing.core

import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.compose.swing.node.MirrorState
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import javax.swing.SwingUtilities

/**
 * Queues a settlement for an unanswered change no component reported, ahead of the repaint it provokes.
 *
 * A widget property changing under the composition's declaration is what [MirrorState] holds - what such
 * a change is, and what settling one means. A component that reports its own changes settles them from
 * the report. This carries the rest: the widget's value changed, and nothing was declared to hear it.
 *
 * A toolkit listener is handed an event before the component processes it, and the repaint the change
 * asks for is asked for during that processing. So a settlement queued from here is queued ahead of that
 * repaint: the settlement puts the declaration back onto the widget, and the repaint paints the
 * declaration rather than the change. A settlement queued from the report of the change is behind that
 * repaint however early it is queued, which is why a report cannot carry this.
 *
 * By the time the settlement runs, the event that made the change has returned and the caller has had
 * its answer - a component reports a change from a listener that event runs - so the settlement runs
 * against a declaration the caller is no longer about to change.
 *
 * [SETTLED_EVENTS] is what a settlement is queued behind: the events a widget changes its declared value
 * on. Most of them carry what the user did to the widget - key, mouse, mouse motion, input method -
 * while a focus loss is the widget changing itself as it answers the event, which is how
 * `JFormattedTextField.processFocusEvent` commits the text it was edited with. A drag is included even
 * though it is a continuous gesture, because a drag step the caller does not adopt is painted like any
 * other change, and a step costs no pass the recomposer was not already going to run - only one queued
 * settlement, and only where one is not queued already.
 *
 * A temporary focus loss - what a window deactivation raises - commits nothing, so the settlement queued
 * for it finds nothing to settle.
 *
 * Two changes settle on the pass that follows instead. While an input method composition is in flight,
 * `JFormattedTextField` defers its commit through `EventQueue.invokeLater`, which lands after the
 * settlement queued from the focus loss. And a drop reaches no toolkit listener, so nothing is queued
 * for it at all.
 *
 * One listener stands for every recomposer: a clock subscribes with [subscribe] and is withdrawn by its
 * own [SwingFrameClock.dispose], and a toolkit is listened to only while it holds a subscriber, so
 * nothing is installed while no recomposer is live. Each event costs one queued settlement for every
 * clock that owes one, whatever the number of recomposers.
 *
 * Every method here runs on the event dispatch thread, which is where a recomposer is created, disposed
 * and dispatched to alike, so the subscriptions need no guard.
 */
internal object EventDispatchHook : AWTEventListener {
    /** The clocks settled from each toolkit's events, in subscription order. */
    private val subscribers = LinkedHashMap<Toolkit, MutableSet<SwingFrameClock>>()

    /**
     * Settles [clock] from the events [toolkit] dispatches, installing the listener on that toolkit when
     * it gains its first subscriber.
     */
    fun subscribe(
        toolkit: Toolkit,
        clock: SwingFrameClock,
    ) {
        val clocks =
            subscribers.getOrPut(toolkit) {
                toolkit.addAWTEventListener(this, SETTLED_EVENTS)
                LinkedHashSet()
            }
        clocks += clock
    }

    /**
     * Stops settling [clock], withdrawing the listener from the toolkit it was subscribed on when that
     * toolkit loses its last subscriber. A settlement already queued for [clock] still runs, and settles
     * nothing that is not owed.
     *
     * A clock is looked up under the toolkit it was subscribed on rather than one named here, so a
     * withdrawal cannot miss a component free to answer with a toolkit of its own. [SwingFrameClock.dispose]
     * is what calls this, which is the invariant behind the map: no subscription outlives the clock it
     * settles. Withdrawing a clock that has none is a no-op.
     */
    fun unsubscribe(clock: SwingFrameClock) {
        val (toolkit, clocks) = subscribers.entries.find { clock in it.value } ?: return
        clocks -= clock
        if (clocks.isEmpty()) {
            subscribers -= toolkit
            toolkit.removeAWTEventListener(this)
        }
    }

    /**
     * The number of clocks settled from [toolkit]'s events.
     *
     * One registration on a toolkit stands for every clock behind it, so a registration count does not
     * rise when a clock joins one that is already installed. This is what states that a given recomposer
     * is settled from a toolkit.
     */
    @VisibleForTesting
    fun subscriberCount(toolkit: Toolkit): Int = subscribers[toolkit].orEmpty().size

    override fun eventDispatched(event: AWTEvent) {
        // The toolkit the component reports is the one it was subscribed under, so the event reaches the
        // clocks standing behind it and no others. A focus event names the component losing or gaining
        // focus, like every other event here.
        val toolkit = (event.source as? Component)?.toolkit ?: return
        val settling = subscribers[toolkit].orEmpty().filterNot { it.settlementOwedForEvent }
        if (settling.isEmpty()) return
        settling.forEach { it.settlementOwedForEvent = true }
        // The clocks are taken now rather than read back later, so a clock unsubscribed between this
        // event and the settlement is still cleared and still declines the settlement itself.
        SwingUtilities.invokeLater {
            settling.forEach { clock ->
                // A component reporting a change settles it from the report, which is earlier than this
                // and clears the debt, so what is left here is the change no component reported.
                if (clock.settlementOwedForEvent) clock.settleInPlace()
            }
        }
    }

    /** The events a settlement is queued behind: the ones a widget changes its declared value on. */
    private const val SETTLED_EVENTS: Long =
        AWTEvent.KEY_EVENT_MASK or
            AWTEvent.MOUSE_EVENT_MASK or
            AWTEvent.MOUSE_MOTION_EVENT_MASK or
            AWTEvent.INPUT_METHOD_EVENT_MASK or
            AWTEvent.FOCUS_EVENT_MASK
}
