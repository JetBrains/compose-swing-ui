@file:JvmMultifileClass
@file:JvmName("NodeKt")

package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.annotation.RememberInComposition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.core.dispatchToCaller
import java.awt.Component

/**
 * Mirrors, as snapshot state, the value a widget property currently holds - a property the user can
 * also move independently of the composition's declaration. The mirror is that value as a [State], so
 * [value] answers with what the widget holds now.
 *
 * A component that reads the mirror while composing is invalidated when the user moves the widget, the
 * same as any other snapshot-state read. Settling - reconciling a declaration against what the widget
 * holds - runs during a composition pass, not at the moment of the move, because the declaration to
 * settle against only exists while a pass is running.
 *
 * A move is reported as it happens, and the caller either adopts it into its declared state or does
 * not. The next pass acts on that answer: an adopted move leaves the widget alone, an unadopted one
 * writes the declaration back, so the widget snaps away from where the user put it.
 *
 * Runs on the event dispatch thread.
 */
public class AppliedValue<V>
    @RememberInComposition
    constructor(
        initial: V,
    ) : State<V> {
        /** The widget's current value, mirrored as snapshot state. */
        private var observedValue by mutableStateOf(initial)

        /** Marks this mirror's own writes to its widget, so a move it provokes is told from the user's. */
        private val appliedWrite = AppliedWrite()

        /** The widget's current value. Reading this while composing subscribes to the user moving it. */
        override val value: V get() = observedValue

        /**
         * Mirrors [published] as what the widget now holds, and returns whether it is a move by the user: a
         * value that differs from what the mirror held before, and that did not happen inside a [write] of
         * this wrapper's own.
         *
         * Call this for every value the widget publishes, in the order it publishes them.
         */
        public fun observed(published: V): Boolean {
            val moved = published != observedValue
            observedValue = published
            return moved && !isWriting
        }

        /**
         * Whether a write of this wrapper's own to its widget is currently in flight. It is true only for
         * the length of a [write], which runs to completion on the event dispatch thread.
         *
         * Read it from a widget listener, which is where a write of this wrapper's own has to be told
         * from the user's. It is not snapshot state, so reading it while composing subscribes to nothing.
         */
        public val isWriting: Boolean get() = appliedWrite.isWriting

        /**
         * Runs [block] as the wrapper's own write to its widget, so the events it raises are recognizable as
         * such rather than as something the user did.
         */
        public fun write(block: () -> Unit): Unit = appliedWrite.write(block)

        /**
         * Settles the widget on [declared]: writes it through [write] unless [read] already answers with it.
         * The value the widget ends up holding becomes the baseline later moves are measured against.
         *
         * A widget can settle on a different value than [declared] - an index outside its items, a number
         * off the grid it snaps to. When it does, [onSettled] is handed what the widget was left holding,
         * once the widget has been left alone, so what it reports is final.
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
            // Runs while the composition applies its changes; dispatchToCaller keeps a callback failure
            // from ending the composition.
            if (settled != declared) dispatchToCaller { onSettled(settled) }
        }
    }

/**
 * Remembers the [AppliedValue] a component settles a declaration through, seeded with [initial] - what
 * the widget will actually hold on the first pass, which is not always what the composition declares
 * for it: a `ToolBar` is always docked when it is built, whatever `floating` its first declaration asks
 * for, so its seed is `false` rather than the declaration.
 *
 * A later [initial] does not recreate the record or move the widget - [declare] settles against the
 * value passed to it on every pass, not against [initial].
 */
@Composable
public fun <V> rememberAppliedValue(initial: V): AppliedValue<V> = remember { AppliedValue(initial) }

/**
 * Declares [value] onto a widget property the user can also move, keeping [applied] in sync with it.
 *
 * Unlike [SwingNodeUpdater.set], which compares this pass's declaration against the last one, this also
 * depends on the value the widget holds. It runs whenever either side moves, in particular on the pass
 * that follows the user moving the widget away from the declaration - nothing polls; reading the
 * widget's mirrored value is what subscribes the component to those moves.
 *
 * [read] and [write] carry the property, and a widget's own accessors are usually the whole of them:
 *
 * ```
 * declare(checked, applied, JCheckBox::isSelected, JCheckBox::setSelected)
 * ```
 *
 * Pass blocks instead where the write is more than a plain assignment - one that coerces its argument,
 * or that must be skipped for a value the widget already holds.
 *
 * [onSettled] receives the value when the widget answers the write with one of its own. A widget that
 * can hold every declaration it is given never calls it, so the default does nothing.
 */
public fun <C : Component, V> SwingNodeUpdater<C>.declare(
    value: V,
    applied: AppliedValue<V>,
    read: C.() -> V,
    write: C.(V) -> Unit,
    onSettled: C.(V) -> Unit = {},
) {
    // The declaration and the value the widget holds move independently, and one settle answers for both:
    // the two are one key.
    set(value to applied.value) {
        applied.settle(value, { read() }, { written -> write(written) }, { on -> onSettled(on) })
    }
}
