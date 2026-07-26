package org.jetbrains.compose.swing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.core.dispatchToCaller
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

    /** Marks this mirror's own writes to its widget, so a move it provokes is told from the user's. */
    private val appliedWrite = AppliedWrite()

    /**
     * What [declare] last settled [current] against: the declaration and the value the widget held for
     * it, or [Unsettled] before the first pass has run. [isSettled] reads this, and a component composing
     * its own settling rather than going through [declare] calls it directly while composing, to tell a
     * pass with nothing new to settle from one that has.
     */
    private var lastDeclared: Any? = Unsettled
    private var lastHeld: Any? = Unsettled

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
    public val isWriting: Boolean get() = appliedWrite.isWriting

    /**
     * Runs [block] as the wrapper's own write to its widget, so the events it raises are recognizable as
     * such rather than as something the user did.
     */
    public fun write(block: () -> Unit): Unit = appliedWrite.write(block)

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
        onSettled: (V) -> Unit = {},
    ) {
        if (read() != declared) this.write { write(declared) }
        val settled = read()
        observedValue = settled
        // Reached only while a composition is applying its changes, so a failure here would end that
        // composition rather than surface to whoever wrote the callback.
        if (settled != declared) dispatchToCaller { onSettled(settled) }
    }

    /**
     * [settle]s the widget on [declared] unless the pair [declared] and [held] is the one already settled
     * against, and records that pair as what the next pass compares itself with.
     *
     * The pair is what makes settling happen once for a pass however many times it is asked for, which is
     * what lets a declaration and the value the widget holds be followed as the separate values they are.
     */
    internal fun settleUnlessSettled(
        declared: V,
        held: V,
        read: () -> V,
        write: (V) -> Unit,
        onSettled: (V) -> Unit,
    ) {
        if (!isSettled(declared, held)) settle(declared, read, write, onSettled)
    }

    /**
     * Whether [declared] and [held] are the pair this last settled against, and records them as the pair
     * the next call compares itself with either way.
     */
    internal fun isSettled(
        declared: V,
        held: V,
    ): Boolean {
        val unchanged = lastDeclared !== Unsettled && lastDeclared == declared && lastHeld == held
        lastDeclared = declared
        lastHeld = held
        return unchanged
    }
}

/** Marks [AppliedValue.lastDeclared] and [AppliedValue.lastHeld] as not yet settled on anything. */
private object Unsettled

/**
 * Remembers the [AppliedValue] a component settles one of its declarations through, seeded with [initial]
 * so the first pass compares against what the composition declares rather than against a placeholder.
 *
 * A later [initial] neither recreates the record nor moves the widget: what stands is settled on every pass
 * from the value passed to [declare].
 */
@Composable
public fun <V> rememberAppliedValue(initial: V): AppliedValue<V> = remember { AppliedValue(initial) }

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
    // Two sides move independently, and a [SwingNodeUpdater.set] follows one value, so each side gets its
    // own. Whichever moved settles the pair; a pass that moved both finds the second already settled by
    // the first, and a pass that moved neither runs no block at all.
    val held = applied.current
    set(value) { _ ->
        applied.settleUnlessSettled(value, held, { read() }, { written -> write(written) }, { on -> onSettled(on) })
    }
    set(held) { _ ->
        applied.settleUnlessSettled(value, held, { read() }, { written -> write(written) }, { on -> onSettled(on) })
    }
}
