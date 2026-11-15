// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import java.awt.AWTEvent
import java.awt.ActiveEvent
import java.awt.EventQueue
import java.awt.SecondaryLoop
import java.awt.event.InvocationEvent
import java.awt.event.MouseEvent
import java.awt.event.PaintEvent
import java.util.Collections
import java.util.WeakHashMap

/**
 * The event queue the benchmark harness installs to count and control what the code under test posts.
 *
 * It counts posted events because the toolkit exposes no such counter. It holds Swing timer ticks until released so
 * wall-clock timing cannot change the counts. It tracks queued events because the harness must know when none is left.
 *
 * @param metrics returns the metrics that receive the counts, or null when no toggle is being measured.
 */
internal class CountingEventQueue(
    private val metrics: () -> ToggleMetrics?,
) : EventQueue() {
    @Volatile
    private var exitingLoop: SecondaryLoop? = null

    // AWT can discard or coalesce a queued event without dispatching it, so the keys are weak: a strong entry would
    // keep the event's source component graph reachable. AWTEvent does not override equals, so the set compares by
    // identity.
    private val queuedEvents = Collections.newSetFromMap(Collections.synchronizedMap(WeakHashMap<AWTEvent, Boolean>()))

    /** Whether timer ticks wait for an explicit release. Clearing it posts every held tick uncounted. */
    @Volatile
    var holdingTicks = false
        set(value) {
            field = value
            if (!value) releaseTicks {}
        }
    private val heldTicks = Collections.synchronizedList(mutableListOf<AWTEvent>())

    override fun postEvent(event: AWTEvent) {
        // Swing exposes no public way to tell a timer tick from other invocation events, so ticks are matched by the
        // timer's event class.
        if (holdingTicks && event is InvocationEvent && event.toString().contains($$"javax.swing.Timer$DoPostEvent")) {
            heldTicks += event
            return
        }
        // Secondary-loop wake-up events belong to the harness rather than the subject.
        if (event.source !== exitingLoop) {
            metrics()?.add("eventsPosted")
            if (!coalescible(event)) queuedEvents += event
        }
        super.postEvent(event)
    }

    override fun dispatchEvent(event: AWTEvent) {
        queuedEvents -= event
        super.dispatchEvent(event)
    }

    /** Whether an event of any type that the code under test posted is still waiting for dispatch. */
    fun eventPending(): Boolean {
        // AWT drops queued events on removeNotify without dispatching them, so absence from the queue ends tracking.
        // The sweep allocates, and the memory suite counts every event-thread allocation of a settle that has no
        // work, so an empty map skips it.
        synchronized(queuedEvents) {
            if (queuedEvents.isNotEmpty()) queuedEvents.removeIf { peekEvent(it.id) == null }
        }
        return queuedEvents.isNotEmpty() || coalescibleIds.any { peekEvent(it) != null }
    }

    private fun coalescible(event: AWTEvent): Boolean = event.id in coalescibleIds && event !is ActiveEvent

    private val coalescibleIds =
        intArrayOf(PaintEvent.PAINT, PaintEvent.UPDATE, MouseEvent.MOUSE_MOVED, MouseEvent.MOUSE_DRAGGED)

    /** Whether a Swing timer tick is held, waiting for [releaseTicks]. */
    val tickHeld: Boolean
        get() = heldTicks.isNotEmpty()

    /**
     * Posts every held tick, calling [onPosted] before each. A caller counts a tick as posted only when it carries
     * frame-driven work; a tick that arrives with nothing to advance only stops its timer.
     */
    fun releaseTicks(onPosted: () -> Unit) {
        val released = synchronized(heldTicks) { heldTicks.toList().also { heldTicks.clear() } }
        released.forEach { tick ->
            onPosted()
            super.postEvent(tick)
        }
    }

    /**
     * Posts [block] to run after every queued event of normal and higher priority. The queue dispatches paint events
     * at low priority, so a probe that re-posted itself at normal priority would starve a queued paint update forever;
     * an update-typed event takes the same low priority without the toolkit-internal event types.
     */
    fun probe(block: () -> Unit) {
        super.postEvent(
            object : AWTEvent(this, PaintEvent.UPDATE), ActiveEvent {
                override fun dispatch() = block()
            },
        )
    }

    fun exitLoop(loop: SecondaryLoop) {
        exitingLoop = loop
        try {
            loop.exit()
        } finally {
            exitingLoop = null
        }
    }
}
