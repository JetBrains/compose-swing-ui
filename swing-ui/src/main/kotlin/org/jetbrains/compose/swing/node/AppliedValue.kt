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
            val settled = leftHolding(declared, read, write)
            // Runs while the composition applies its changes; dispatchToCaller keeps a callback failure
            // from ending the composition.
            if (settled != declared) dispatchToCaller { onSettled(settled) }
        }

        /**
         * Settles [component] on [declared], the way [settle] settles a widget reached through plain
         * blocks: the property is carried by accessors of the component's own type, and [onSettled]
         * hears what the widget was left holding where that is not [declared].
         *
         * This is what a declaration settles through. The accessors a component declares are the same on
         * every pass, so handing them over with the component they read and write - rather than in blocks
         * built around it - is what leaves a settling pass with nothing to build.
         */
        internal fun <C> settleThrough(
            component: C,
            declared: V,
            read: C.() -> V,
            write: C.(V) -> Unit,
            onSettled: C.(V) -> Unit,
        ) {
            val settled = leftHolding(declared, { component.read() }, { written -> component.write(written) })
            if (settled != declared) dispatchToCaller { component.onSettled(settled) }
        }

        /**
         * The value the widget is left holding once [declared] has been settled onto it. That value
         * becomes the baseline later moves are measured against and the one [declared] has now been
         * answered for, so a widget that answered with a value of its own is not written again on every
         * later pass.
         *
         * A widget already holding [declared] is left alone, and what it was read as is what it is left
         * holding - nothing between the two reads could have moved it. Reading it a second time to learn
         * that is what a settle costs where a read is more than a field access: a text component
         * materializes its whole document for one, and the pass that follows a keystroke the caller
         * adopted is exactly this case. Only a widget that was written to is read back, because only
         * there can it answer with a value of its own.
         *
         * Inlined into both routes into it, so a settle carries the accessors it was given rather than
         * blocks built around them.
         */
        private inline fun leftHolding(
            declared: V,
            read: () -> V,
            crossinline write: (V) -> Unit,
        ): V =
            settling {
                val held = read()
                val current =
                    if (held == declared) {
                        held
                    } else {
                        appliedWrite.write { write(declared) }
                        read()
                    }
                answered(current)
                current
            }

        /**
         * Records [value] as what a settlement of this mirror's own left the widget holding, and as the
         * value the standing declaration has now been answered for.
         *
         * [settle] does this for the declaration it writes. A caller that reaches its widget some other
         * way - applying two declarations the widget resolves together, or a write the widget answers by
         * dropping part of what it held - records the answer here instead. Recording both is what makes
         * the next pass quiet: [redeclare] compares the declaration and the mirrored value against this
         * pair, so a pass in which neither moved has nothing to settle, while a move the user makes
         * afterwards is measured against what the widget was actually left on.
         */
        internal fun answered(value: V) {
            observedValue = value
            declaredAgainst = value
        }

        /**
         * Runs [block] as a settlement of this mirror's own: the value the widget is left holding when it
         * returns is one this pass asked for and read back itself, so it is recorded as an answer rather
         * than as news.
         *
         * The overload above settles a declaration by writing it. A write the widget answers by dropping
         * part of what it held settles through this instead, wrapped around both the write and the read
         * that records what survived it. [block] must close the settlement, and throws where it does not -
         * see [SettlementScope] for the two ways to close one.
         *
         * What decides whether a read-back belongs inside is whether this pass still owes the widget
         * anything, and what settles that is whether the pass put the declaration back before it read.
         * A pass that re-applied the declaration and read what the widget was left holding owes nothing
         * further, whatever the widget kept of it: that is an answer. A pass that moved the widget by
         * writing some other property, leaving a standing declaration lying where the widget dropped it,
         * does still owe it: that is news, and its [observed] stays outside, where the move it records is
         * what brings the pass that puts the declaration back.
         */
        public fun <R> settle(block: SettlementScope<V>.() -> R): R {
            val scope = RecordingScope(this)
            return settling {
                try {
                    val outcome = scope.block()
                    check(scope.closed) {
                        "A settlement recorded nothing. Close it with answered() for a value read back, " +
                            "or unchanged() where the write left this property alone."
                    }
                    outcome
                } finally {
                    scope.spend()
                }
            }
        }

        /**
         * What a settlement says the widget was left holding. See [settle].
         *
         * A settlement is closed exactly one of two ways. [answered] records a value this pass read back,
         * which is what a widget that resolved the write its own way has to be measured against
         * afterwards. [unchanged] says the write left this mirror's property alone, so there is nothing to
         * read back and nothing to record - the mirror already holds what the widget holds. Saying neither
         * is a defect rather than a third case: it leaves the widget standing where the write left it with
         * no later pass due to correct it.
         */
        public sealed interface SettlementScope<in V> {
            /** Records [value] - what this settlement read the widget back as - as the answer to what it wrote. */
            public fun answered(value: V)

            /**
             * Closes the settlement without recording, for a write that left this mirror's property alone.
             *
             * This is not [answered] with the value the mirror already holds: reading the widget back to
             * pass one is the work a settlement that changed nothing exists to avoid.
             */
            public fun unchanged()
        }

        /**
         * The [SettlementScope] one settlement runs against, which remembers whether it was closed so
         * [settle] can refuse a settlement that recorded nothing.
         */
        private class RecordingScope<V>(
            private val mirror: AppliedValue<V>,
        ) : SettlementScope<V> {
            var closed: Boolean = false
                private set
            private var spent: Boolean = false

            /** Ends this scope's life with the settlement it was made for, so what escapes the block is inert. */
            fun spend() {
                spent = true
            }

            override fun answered(value: V) {
                check(!spent) { "A settlement was closed after it returned. Record inside the settle block." }
                mirror.answered(value)
                closed = true
            }

            override fun unchanged() {
                check(!spent) { "A settlement was closed after it returned. Record inside the settle block." }
                closed = true
            }
        }

        /**
         * Runs [block] as this mirror's own settling, marking the moves it makes as ones it answered.
         *
         * What runs inside is a write whose answer this mirror reads back and records - through [settle]
         * or [settleThrough], which do both themselves, or through the [settle] overload wrapped around a
         * the [answered] that records what the widget was left holding. A write that only marks itself as
         * the wrapper's, with no answer recorded for it, stays outside, so the move it provokes is
         * counted in [moves] and carries the widget's reply to the next pass.
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
    //
    // The accessors are handed to the mirror as they are, together with the component they read and write:
    // a settling pass builds the settlement itself and nothing besides.
    settleWhenDue(
        applied.redeclare(value),
        { Settlement<C> { component -> applied.settleThrough(component, value, read, write, onSettled) } },
    ) { settlement -> settlement.applyTo(this) }
}

/**
 * Carries the settlement [token] builds when [due], and runs [settle] against the component with it once
 * the composition applies its changes. Nothing runs on a pass where nothing is due.
 *
 * This is how a declaration whose settling depends on more than its own value reaches its widget: what such
 * a pass is compared against lives on the mirrors it settles rather than in the composition, so the slot
 * carries a token standing for a due settlement instead of the declaration behind it. A fresh token is
 * never what the slot already holds, so a settle that is due always runs; the one comparison the slot can
 * hold back is the null that follows a settlement, and that pass has nothing to do.
 *
 * The slot is taken whether or not anything is due, so a call inside a conditional shifts every later slot
 * of the same `update` block. State the condition in [due], not in whether the call happens.
 */
internal inline fun <C : Component, D : Any> SwingNodeUpdater<C>.settleWhenDue(
    due: Boolean,
    token: () -> D,
    crossinline settle: C.(D) -> Unit,
): Unit = set(if (due) token() else null) { pending -> if (pending != null) settle(pending) }

/** Stands for a value no declaration has reached yet, so the first one made always settles. */
internal val Undeclared: Any = Any()

/** One widget settle: the accessors to settle a declaration through, bound to the value it declares. */
internal fun interface Settlement<in C : Component> {
    fun applyTo(component: C)
}
