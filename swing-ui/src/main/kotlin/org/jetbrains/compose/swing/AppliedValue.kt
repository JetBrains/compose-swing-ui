package org.jetbrains.compose.swing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.awt.Component

/**
 * Records the value that is currently in sync between one declared parameter and the widget property
 * holding it.
 *
 * A widget property the user can also move has two writers, so what the composition declares and what the
 * widget holds can differ at any moment. This mirrors the widget's value as snapshot state, which makes the
 * user moving the widget an ordinary composition dependency: a component that declares the property reads
 * the mirror, so a move invalidates it and the pass that follows settles the two against each other.
 *
 * The mirror is the whole of what is kept. A widget knows only what it holds now, so the value it last held
 * is the one thing the binding has to remember - and that is the mirror itself, read before a new value
 * replaces it.
 *
 * Settling belongs to that pass rather than to the moment of the move, because the declaration only exists
 * while a pass is running. A move is reported as it happens, the caller adopts it or does not, and the pass
 * that carries their answer is where the widget is either left alone or written back.
 *
 * Comparing values is also what tells a move by the user from the wrapper's own write, so nothing has to be
 * known about where an event came from: a write leaves the widget holding the declaration, so the value it
 * publishes is the one already declared and reports nothing, while a move to anything else is the user's
 * and is reported once.
 *
 * What a widget settles on is what stands. A widget is free to answer a write with a different value - an
 * index its items do not cover, a number off the grid it snaps to - and that value, not the one asked for,
 * becomes what is compared against, so a declaration the widget cannot hold settles instead of being
 * asserted forever.
 *
 * All of it runs on the event dispatch thread.
 */
public class AppliedValue<V>(
    initial: V,
) {
    /**
     * The widget's value, mirrored as snapshot state. Written whenever the widget publishes a value, so
     * reading [current] while composing subscribes the reader to the user moving the widget.
     */
    private var observedValue by mutableStateOf(initial)

    /** The reentrancy guard [isWriting] and [write] delegate to. */
    private val applied = AppliedWrite()

    /** What the widget currently holds. Reading this while composing subscribes to the user moving it. */
    internal val current: V get() = observedValue

    /**
     * Mirrors [value] as what the widget now holds, and answers whether it is news for the caller. What the
     * widget last held is what the mirror still holds until this replaces it, so a value that differs from
     * it is a move - and a move made under a [write] of this wrapper's own is the wrapper's, not the user's.
     *
     * Call this for every value the widget publishes, in the order it publishes them.
     */
    public fun observed(value: V): Boolean {
        val moved = value != observedValue
        observedValue = value
        return moved && !isWriting
    }

    /** Whether a write of this wrapper's own to its widget is currently in flight. */
    public val isWriting: Boolean get() = applied.isWriting

    /** Runs [block] as the wrapper's own write to its widget. */
    public fun write(block: () -> Unit): Unit = applied.write(block)

    /**
     * Settles the widget on [declared]: written through [write] where [read] does not already answer with
     * it, and whatever the widget settles on becomes the value later moves are measured against.
     *
     * A widget that settles elsewhere - a value its range does not admit, one off the grid it snaps to -
     * leaves the composition holding a value the screen does not show, so where it does, [onSettled] is
     * handed what the widget was left on. It runs after the widget has been left alone, so what it reports
     * is final.
     */
    public fun settle(
        declared: V,
        read: () -> V,
        write: (V) -> Unit,
        onSettled: (V) -> Unit,
    ) {
        if (read() != declared) this.write { write(declared) }
        val settled = read()
        observedValue = settled
        if (settled != declared) onSettled(settled)
    }
}

/**
 * Remembers the [AppliedValue] a component settles one of its declarations through, seeded with [initial]
 * so the first pass compares against what the composition declares rather than against a placeholder.
 *
 * A later [initial] neither recreates the record nor moves the widget: what stands is settled on every pass
 * from the value passed to [declare].
 */
@Composable
public fun <V> rememberAppliedValue(initial: V): AppliedValue<V> = remember { AppliedValue(initial) }

/** The pair a two-way declaration is applied on: what the composition asks for, and what the widget holds. */
private data class Settlement<V>(
    val declared: V,
    val held: V,
)

/**
 * Declares [value] onto a widget property the user can also move, keeping [applied] in sync with it.
 *
 * Unlike [SwingNodeUpdater.set], which compares this pass's declaration against the last one, this also
 * depends on the value the widget holds, so it runs whenever either side moves - and, in particular, on the
 * pass that follows the user moving the widget away from the declaration. Reading the widget's mirrored
 * value here is what subscribes the component to those moves; nothing polls, and a pass in which neither
 * side moved applies nothing.
 *
 * [read] and [write] carry the property, and a widget's own accessors are usually the whole of them:
 *
 * ```
 * declare(checked, applied, JCheckBox::isSelected, JCheckBox::setSelected)
 * ```
 *
 * Pass blocks instead where the write is more than an assignment - one that coerces what it is given, or
 * that has to be skipped for a value the widget already holds.
 *
 * [onSettled] is handed the value where the widget answers the write with one of its own; a widget that can
 * hold every declaration never invokes it, which is why it defaults to doing nothing.
 */
public fun <C : Component, V> SwingNodeUpdater<C>.declare(
    value: V,
    applied: AppliedValue<V>,
    read: C.() -> V,
    write: C.(V) -> Unit,
    onSettled: C.(V) -> Unit = {},
) {
    set(Settlement(value, applied.current)) { settlement ->
        applied.settle(settlement.declared, { read() }, { write(it) }, { onSettled(it) })
    }
}
