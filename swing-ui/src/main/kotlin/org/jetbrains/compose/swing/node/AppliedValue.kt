@file:JvmMultifileClass
@file:JvmName("NodeKt")

package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.annotation.RememberInComposition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.core.dispatchToCaller
import org.jetbrains.compose.swing.core.trace
import java.awt.Component

/**
 * Mirrors the value a widget property currently holds - a property the user can also move
 * independently of the composition's declaration. The mirror is that value as a [State], so [value]
 * answers with what the widget holds now.
 *
 * A component that reads the mirror while composing is invalidated when the widget moves under it -
 * whether the user moved it, or a write to some other property of the same widget did. A move a [settle]
 * made and recorded the widget's answer to is the one kind that invalidates nothing: it is where the
 * declaration was already heading, and the pass that made it has already been told where it landed.
 * Settling - reconciling a declaration against what the widget holds - runs during a composition pass,
 * not at the moment of the move, because the declaration to settle against only exists while a pass is
 * running.
 *
 * A move is reported as it happens, and the caller either adopts it into its declared state or does
 * not. The next pass acts on that answer: an adopted move leaves the widget alone, an unadopted one
 * writes the declaration back, so the widget snaps away from where the user put it.
 *
 * The pass is a later event than the move, and Swing has already queued the repaint the move provoked,
 * so the widget can be painted once holding a value the caller rejected. The pass follows within a few
 * event-dispatch cycles, inside a single display refresh interval. That bounds how long the rejected
 * value survives; it is not a guarantee that the value is never shown.
 *
 * Runs on the event dispatch thread.
 */
public class AppliedValue<V>
    @RememberInComposition
    constructor(
        initial: V,
    ) : State<V> {
        /**
         * Bumped once for each move no [settle] of this mirror's own accounted for. [value] and
         * [redeclare] read this counter before answering, which is what registers their snapshot
         * subscription; the mirrored value itself is a plain field, so a move a settle made and already
         * recorded is not carried to the scope that provoked it, and no pass is scheduled to answer a
         * move that has been answered.
         */
        private var moves by mutableIntStateOf(0)

        /** The widget's current value. */
        private var observedValue: V = initial

        /** Marks this mirror's own writes to its widget, so a move it provokes is told from the user's. */
        private val appliedWrite = AppliedWrite()

        /**
         * Nesting depth of the [settle]s in flight. A move made inside one is a move that settle has
         * already recorded the widget's answer to, so it needs no pass of its own; a move made anywhere
         * else - by the user, or by a write the same pass makes to some other property the widget answers
         * by moving this one - is one no standing settlement has accounted for.
         */
        private var settleDepth: Int = 0

        /** The declaration [redeclare] recorded last, or [Undeclared] while none has been made. */
        private var declaration: Any? = Undeclared

        /**
         * The mirrored value the standing [declaration] has already been answered for: what the mirror
         * held when that declaration was recorded, or - once [settle] has run - the value the widget was
         * left holding. [Undeclared] while no declaration has been made.
         */
        private var declaredAgainst: Any? = Undeclared

        /**
         * The widget's current value. Reading this while composing subscribes to the widget moving under
         * the declaration - see [moves] for the one move that is not such news.
         */
        override val value: V
            get() {
                moves
                return observedValue
            }

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
            if (moved && settleDepth == 0) moves++
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
         * Records [declared] as the declaration this pass makes, alongside the value the mirror holds now,
         * and answers whether either of the two moved since the pair recorded before them - which is when
         * a [settle] has something to do. The first declaration a mirror is given always answers `true`.
         *
         * The mirrored value is part of the answer, so calling this while composing subscribes the
         * composing scope to the widget moving under the declaration, exactly as reading [value] does.
         */
        internal fun redeclare(declared: V): Boolean {
            moves
            val observed = observedValue
            if (declared == declaration && observed == declaredAgainst) return false
            declaration = declared
            declaredAgainst = observed
            return true
        }

        /**
         * Settles the widget on [declared]: writes it through [write] unless [read] already answers with it.
         * The value the widget ends up holding becomes the baseline later moves are measured against, and
         * the one [declared] has now been answered for, so a widget that answered with a value of its own
         * is not written again on every later pass.
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
            val settled =
                settling {
                    if (read() != declared) this.write { write(declared) }
                    read().also { current ->
                        observedValue = current
                        declaredAgainst = current
                    }
                }
            // Runs while the composition applies its changes; dispatchToCaller keeps a callback failure
            // from ending the composition.
            if (settled != declared) dispatchToCaller { onSettled(settled) }
        }

        /**
         * Runs [block] as this mirror's own settling, marking the moves it makes as ones it answered.
         *
         * This is the whole of the settlement path: every write to a widget property the user can also
         * move, and the read-back that records what the widget was left holding, runs inside one of these,
         * which [settle] enters - so it is the outermost bracket the settlement path shares.
         */
        private inline fun <R> settling(block: () -> R): R =
            trace("settle") {
                settleDepth++
                try {
                    block()
                } finally {
                    settleDepth--
                }
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
 *
 * One record serves one node, remembered in that node's own group so that it is released with the node.
 * The record holds what [declare] compares each pass's declaration against, so a record shared between
 * nodes, or remembered above the node it settles, goes on answering for a widget that is no longer there
 * - and the widget built in its place keeps the value its constructor gave it instead of the standing
 * declaration.
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
 *
 * Call this exactly once per pass, for one node, against an [applied] remembered in that node's own
 * group. It makes one [SwingNodeUpdater.set] call, so a [declare] inside a conditional shifts every
 * later slot in the `update` block; and what a declaration is compared against lives on [applied]
 * rather than in the composition, so an [applied] that outlives its node answers for a widget that is
 * no longer there. State a condition in [value], not in whether the call happens.
 */
public fun <C : Component, V> SwingNodeUpdater<C>.declare(
    value: V,
    applied: AppliedValue<V>,
    read: C.() -> V,
    write: C.(V) -> Unit,
    onSettled: C.(V) -> Unit = {},
) {
    // The declaration and the value the widget holds move independently, and one settle answers for both.
    // The mirror keeps the pair it was last settled on and compares this pass's against it in place, so a
    // pass with nothing to settle builds no block to run and no key to compare through.
    val settlement: Settlement<C>? =
        if (applied.redeclare(value)) {
            Settlement { component ->
                applied.settle(
                    value,
                    { component.read() },
                    { written -> component.write(written) },
                    { on -> component.onSettled(on) },
                )
            }
        } else {
            null
        }
    // The slot is taken on every pass, and a fresh settlement is never what the slot already holds, so a
    // settle that is due always runs. The comparison can only ever hold back the null that follows a
    // settlement, which is why the block carries the settlement rather than the declaration: the one pass
    // it holds back has nothing to do. The block captures nothing, so it is one shared instance rather
    // than a per-pass allocation.
    //
    // On the first quiet pass after a settle the slot goes from a settlement to null, which the comparison
    // does not hold back: one operation is scheduled that the null guard turns into nothing.
    updater.set(settlement) { it?.applyTo(component) }
}

/** Stands for a mirror no declaration has reached yet, so the first one it is given always settles. */
private val Undeclared = Any()

/**
 * One widget settle, carried in a composition slot until the composition applies its changes. It has no
 * value identity: two settlements are the same only when they are the same object.
 */
internal fun interface Settlement<in C : Component> {
    fun applyTo(component: C)
}
