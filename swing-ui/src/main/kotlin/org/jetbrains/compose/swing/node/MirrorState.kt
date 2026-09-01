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
 * Mirrors the value a widget property currently holds - a property the widget can change on its own,
 * independently of the composition's declaration - and carries a change the composition has not
 * answered for to the scope that declares it.
 *
 * The mirror is that value as a [State], so [value] answers with what the widget holds now, and reading
 * it while composing subscribes - as [subscribe] does for a component that needs the news without
 * the value. A widget property is not versioned, so what [value] answers with is the live value rather
 * than any snapshot's.
 *
 * A component subscribed to the mirror is invalidated when the widget's value changes under its
 * declaration - whether the user changed it, or a write to some other property of the same widget did.
 * A change a [settle] made and recorded the widget's answer to is the one kind that invalidates nothing:
 * it is where the declaration was already heading, and the pass that made it has already been told where
 * it landed. Settling - reconciling a declaration against what the widget holds - runs during a
 * composition pass, not at the moment of the change, because the declaration to settle against only
 * exists while a pass is running.
 *
 * A change is reported as it happens, and the caller either adopts it into its declared state or does
 * not. The next pass acts on that answer: an adopted change leaves the widget alone, an unadopted one
 * writes the declaration back, so the widget snaps away from what the user left it holding.
 *
 * Swing asks for the repaint a change provokes while the widget is still handling it, ahead of anything
 * the report of that change can schedule. A change carried to the caller through [report] settles inside
 * the event that made it wherever a frame can run there, so the declaration is back on the widget before
 * that repaint is served and the rejected value is never shown. A change only [observed] leaves the pass
 * a later event, following within a few event-dispatch cycles, inside a single display refresh interval:
 * the widget can be painted once holding a value the caller rejected, and what is bounded is how long
 * that value survives. A reported change takes that same later pass where a frame is already running, or
 * where something else is waiting for one.
 *
 * Runs on the event dispatch thread.
 *
 * @param initial the value the widget holds before any declaration has been settled onto it.
 */
public class MirrorState<V>
    @RememberInComposition
    constructor(
        initial: V,
    ) : State<V> {
        /**
         * The composition a reported change takes its frame in, handed over by the node this mirror
         * settles through as that node is created - see [SwingNodeUpdater.applyMirror].
         *
         * A mirror with none cannot [report]: the change would reach the caller and the widget would keep
         * it until an event of its own carried the answer, which is the flash [report] exists to
         * prevent. So the first change through an unstated mirror fails, rather than quietly taking a
         * slower path nothing announces.
         */
        internal var owner: SwingCompositionOwner? = null

        /**
         * The channel an unanswered change travels to the composition on, and the whole of what [subscribe]
         * subscribes to. It is bumped once per change no [settle] of this mirror's own answered for; the
         * number it reaches carries no meaning, and nothing reads it for one.
         *
         * It is snapshot state because that is the only kind of subscription reachable from where a
         * declaration is made: a node's update block forbids composable calls, so the scope that
         * declares through a mirror cannot be registered with the mirror directly, and a state read is
         * what attaches it. Bumping it is also how a change the caller does not adopt schedules the pass
         * that puts the declaration back - such a change touches no state anywhere else.
         */
        private var unanswered by mutableIntStateOf(0)

        /** The widget's current value. */
        private var observedValue: V = initial

        /** Marks this mirror's own writes to its widget, so a change it provokes is told from the user's. */
        private val appliedWrite = AppliedWrite()

        /**
         * Nesting depth of the [settle]s in flight. A change made inside one is a change that settle has
         * already recorded the widget's answer to, so it needs no pass of its own; a change made anywhere
         * else - by the user, or by a write the same pass makes to some other property the widget answers
         * by changing this one - is one no standing settlement has answered for, and travels to the
         * composition through [subscribe].
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
         * The widget's current value. Reading it while composing subscribes to the widget's value changing
         * under its declaration, as [subscribe] does - a change a [settle] answered for is the one kind
         * that is not such news.
         *
         * The value is what the widget holds now, not what it held when a snapshot was taken: a widget
         * property is not versioned, so a read inside [androidx.compose.runtime.snapshots.Snapshot.takeSnapshot]
         * or `snapshotFlow` answers with the live value rather than that snapshot's.
         */
        override val value: V
            get() {
                subscribe()
                return observedValue
            }

        /**
         * Subscribes the composing scope to the widget's value changing under its declaration, so that a
         * change the caller does not adopt brings the pass that puts the declaration back. Answers
         * nothing: what a subscribed scope does about a change is read the widget on the pass after it.
         *
         * A change a [settle] made and recorded the widget's answer to is the one kind this does not
         * carry:
         * it is where the declaration was already heading, and the pass that made it has already been
         * told where it landed.
         *
         * [declare] does this for the declaration it settles. Call it directly where a component derives
         * something from the widget in its own body rather than through a declaration.
         */
        public fun subscribe() {
            unanswered
        }

        /**
         * Mirrors [published] as what the widget now holds, and returns whether it is a change by the user: a
         * value that differs from what the mirror held before, and that did not happen inside a [write] of
         * this wrapper's own.
         *
         * Call this for every value the widget publishes, in the order it publishes them.
         *
         * @param published the value the widget now holds; it is mirrored whether or not the change is
         *   the user's.
         * @return `true` where the user changed the value, `false` both for a value this wrapper wrote
         *   through [write] and for one that repeats what the mirror already held.
         */
        public fun observed(published: V): Boolean {
            val changed = published != observedValue
            observedValue = published
            if (changed && settleDepth == 0) unanswered++
            return changed && !isWriting
        }

        /**
         * Carries a value the widget published to the caller: mirrors [published] through [observed] and,
         * where that is a change of the user's, hands it to [onChanged] and settles the composition on the
         * answer - inside the event that made the change wherever a frame can run there, so the
         * declaration is back on the widget before the repaint that change asked for is served. Where one
         * cannot, the pass follows a later event, as it does for a change only [observed].
         *
         * This is how a widget property the user can also change is reported. [onChanged] runs first and
         * settling follows it, so a caller that adopts the change is settled on what it wrote and one that
         * does not is settled back onto its standing declaration.
         *
         * Report a discrete interaction this way - a click, a selection, a step. A continuous gesture
         * reports a change per drag step, and the value one step lands on is replaced by the next well
         * inside a display refresh interval, so there is nothing visible to correct and batching the
         * steps is worth more than the pass: mirror a gesture through [observed] instead. A change
         * carried on a second channel behind a listener the caller supplied goes the same way: that
         * channel fires before the caller's own and would settle the widget against a declaration the
         * caller is about to change.
         *
         * Runs on the event dispatch thread.
         *
         * @param published the value the widget now holds, mirrored through [observed] even where it is
         *   no change of the user's.
         * @param onChanged handed [published] where the change is the user's, and not called at all
         *   where it is not.
         */
        public fun report(
            published: V,
            onChanged: (V) -> Unit,
        ) {
            if (!observed(published)) return
            val reportingOwner =
                checkNotNull(owner) {
                    "This mirror has not been applied to a node. Declare the property with declare(), " +
                        "which applies it, or apply it directly with applyMirror() in the update block " +
                        "of the node the mirror settles."
                }
            onChanged(published)
            reportingOwner.settleNow()
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
         *
         * @param block the write to make. A failure raised by a listener it provokes is contained rather
         *   than ending the composition, and a [write] nested inside another counts as part of it.
         */
        public fun write(block: () -> Unit): Unit = appliedWrite.write(block)

        /**
         * Records [declared] as the declaration this pass makes, alongside the value the mirror holds now,
         * and answers whether either of the two changed since the pair recorded before them - which is when
         * a [settle] has something to do. The first declaration a mirror is given always answers `true`.
         *
         * The mirrored value is part of the answer, so calling this while composing subscribes the
         * composing scope to the widget's value changing under the declaration, as [subscribe] does.
         */
        internal fun redeclare(declared: V): Boolean {
            subscribe()
            val observed = observedValue
            if (declared == declaration && observed == declaredAgainst) return false
            declaration = declared
            declaredAgainst = observed
            return true
        }

        /**
         * Settles the widget on [declared]: writes it through [write] unless [read] already answers with it.
         * The value the widget ends up holding becomes the baseline later changes are measured against, and
         * the one [declared] has now been answered for, so a widget that answered with a value of its own
         * is not written again on every later pass.
         *
         * A widget can settle on a different value than [declared] - an index outside its items, a number
         * off the grid it snaps to. When it does, [onSettled] is handed what the widget was left holding,
         * once the widget has been left alone, so what it reports is final.
         *
         * @param declared the value this pass declares for the property.
         * @param read answers with the value the widget holds. Called once before the write, and a
         *   second time only where a write was made.
         * @param write puts a value onto the widget, marked as this wrapper's own so the events it
         *   raises are not taken for the user's.
         * @param onSettled handed what the widget was left holding, where that differs from [declared].
         *   Defaults to doing nothing.
         */
        public fun settle(
            declared: V,
            read: () -> V,
            write: (V) -> Unit,
            onSettled: (V) -> Unit = {},
        ) {
            val settled = leftHolding(declared, read) { _, declaredValue -> write(declaredValue) }
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
         *
         * [write] is told what the widget holds as well as what is declared, so a write that is a
         * transition rather than an assignment can write only the part that differs. The held value is
         * the one this settlement has already read, so that write costs no second read of the widget.
         */
        internal fun <C> settleThrough(
            component: C,
            declared: V,
            read: C.() -> V,
            write: C.(held: V, declared: V) -> Unit,
            onSettled: C.(V) -> Unit,
        ) {
            val settled =
                leftHolding(declared, { component.read() }) { held, written -> component.write(held, written) }
            if (settled != declared) dispatchToCaller { component.onSettled(settled) }
        }

        /**
         * The value the widget is left holding once [declared] has been settled onto it. That value
         * becomes the baseline later changes are measured against and the one [declared] has now been
         * answered for, so a widget that answered with a value of its own is not written again on every
         * later pass.
         *
         * A widget already holding [declared] is left alone, and what it was read as is what it is left
         * holding - nothing between the two reads could have changed it. Reading it a second time to learn
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
            crossinline write: (held: V, declared: V) -> Unit,
        ): V =
            bracketed {
                val held = read()
                val current =
                    if (held == declared) {
                        held
                    } else {
                        appliedWrite.write { write(held, declared) }
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
         * pair, so a pass in which neither changed has nothing to settle, while a change the user makes
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
         * [settle] settles a declaration by writing it. A write the widget answers by dropping part of
         * what it held settles through this instead, wrapped around both the write and the read that
         * records what survived it. [block] must close the settlement, and throws where it does not -
         * see [SettlementScope] for the two ways to close one.
         *
         * What decides whether a read-back belongs inside is whether this pass still owes the widget
         * anything, and what settles that is whether the pass put the declaration back before it read.
         * A pass that re-applied the declaration and read what the widget was left holding owes nothing
         * further, whatever the widget kept of it: that is an answer. A pass that changed the widget by
         * writing some other property, leaving a standing declaration lying where the widget dropped it,
         * does still owe it: that is news, and its [observed] stays outside, where the change it records
         * is what brings the pass that puts the declaration back.
         *
         * @param block makes the write and closes the settlement on its [SettlementScope] receiver.
         *   A change it makes to this mirror's property needs no pass of its own.
         * @return whatever [block] returned, once the settlement has closed.
         */
        public fun <R> settle(block: SettlementScope<V>.() -> R): R {
            val scope = RecordingScope(this)
            return bracketed {
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

        private inline fun <R> bracketed(block: () -> R): R =
            trace("settle") {
                settleDepth++
                try {
                    block()
                } finally {
                    settleDepth--
                }
            }

        /**
         * What a settlement says the widget was left holding. See the [MirrorState.settle] a block runs in.
         *
         * A settlement is closed exactly one of two ways. [answered] records a value this pass read back, which is
         * what a widget that resolved the write its own way has to be measured against afterwards. [unchanged] says
         * the write left this mirror's property alone, so there is nothing to read back and nothing to record - the
         * mirror already holds what the widget holds. Saying neither is a defect rather than a third case: it
         * leaves the widget standing where the write left it with no later pass due to correct it.
         */
        public sealed interface SettlementScope<in V> {
            /**
             * Records [value] - what this settlement read the widget back as - as the answer to what it wrote.
             *
             * @param value what the widget was left holding, which the next pass measures its declaration
             *   against.
             */
            public fun answered(value: V)

            /**
             * Closes the settlement without recording, for a write that left this mirror's property untouched.
             *
             * This is not [answered] with the value the mirror already holds: reading the widget back to pass one
             * is the work a settlement that changed nothing exists to avoid.
             */
            public fun unchanged()
        }

        /**
         * The [SettlementScope] one settlement runs against, which remembers whether it was closed so
         * [MirrorState.settle] can refuse a settlement that recorded nothing.
         */
        private class RecordingScope<V>(
            private val mirror: MirrorState<V>,
        ) : SettlementScope<V> {
            var closed: Boolean = false
                private set
            private var spent: Boolean = false

            /** Ends this scope's life with the settlement it was made for, so what escapes the block is inert. */
            fun spend() {
                spent = true
            }

            override fun answered(value: V) {
                check(!spent) { "A settlement was closed after it returned. Record inside the settling block." }
                mirror.answered(value)
                closed = true
            }

            override fun unchanged() {
                check(!spent) { "A settlement was closed after it returned. Record inside the settling block." }
                closed = true
            }
        }
    }

/**
 * Remembers the [MirrorState] a component settles a declaration through, seeded with [initial] - what
 * the widget will actually hold on the first pass, which is not always what the composition declares
 * for it: a `ToolBar` is always docked when it is built, whatever `floating` its first declaration asks
 * for, so its seed is `false` rather than the declaration.
 *
 * A later [initial] does not recreate the record or write the widget - [declare] settles against the
 * value passed to it on every pass, not against [initial].
 *
 * One record serves one node, remembered in that node's own group so that it is released with the node.
 * The record holds what [declare] compares each pass's declaration against, so a record shared between
 * nodes, or remembered above the node it settles, goes on answering for a widget that is no longer there
 * - and the widget built in its place keeps the value its constructor gave it instead of the standing
 * declaration.
 *
 * @param initial what the widget holds before anything is declared onto it, read once when the record is
 *   created.
 * @return the record to hand [declare] for every pass of the node it belongs to.
 */
@Composable
public fun <V> rememberMirrorState(initial: V): MirrorState<V> = remember { MirrorState(initial) }

/**
 * Declares [value] onto a widget property the user can also change, keeping [mirror] in sync with it.
 *
 * Unlike [SwingNodeUpdater.set], which compares this pass's declaration against the last one, this also
 * depends on the value the widget holds. It runs whenever either side changes, in particular on the pass
 * that follows the user taking the widget away from the declaration - nothing polls; reading the
 * widget's mirrored value is what subscribes the component to those changes.
 *
 * [read] and [write] carry the property, and a widget's own accessors are usually the whole of them:
 *
 * ```
 * declare(checked, mirror, JCheckBox::isSelected, JCheckBox::setSelected)
 * ```
 *
 * Pass blocks instead where the write is more than a plain assignment - one that coerces its argument,
 * or that must be skipped for a value the widget already holds.
 *
 * Call this exactly once per pass, for one node, against a [mirror] remembered in that node's own group.
 * It calls [SwingNodeUpdater.set], so a [declare] inside a conditional shifts every later slot in the
 * `update` block; and what a declaration is compared against lives on [mirror] rather than in the
 * composition, so a [mirror] that outlives its node answers for a widget that is no longer there. State
 * a condition in [value], not in whether the call happens.
 *
 * @param value the declaration for the property, settled on every pass either side changes.
 * @param mirror the record this declaration and the widget's value are compared against.
 * @param read answers with the value the widget holds. Called once before the write, and a second time
 *   only where a write was made.
 * @param write puts a value onto the widget, marked as the library's own so the events it raises are
 *   not taken for the user's.
 * @param onSettled handed what the widget was left holding, where that differs from [value]. Defaults
 *   to doing nothing.
 */
public fun <C : Component, V> SwingNodeUpdater<C>.declare(
    value: V,
    mirror: MirrorState<V>,
    read: C.() -> V,
    write: C.(V) -> Unit,
    onSettled: C.(V) -> Unit = {},
) {
    applyMirror(mirror)
    // The declaration and the value the widget holds change independently, and one settle answers for both.
    // The mirror keeps the pair it was last settled on and compares this pass's against it in place, so a
    // pass with nothing to settle builds no block to run and no key to compare through.
    //
    // The accessors are handed to the mirror as they are, together with the component they read and write:
    // a settling pass builds the settlement itself and nothing besides.
    settleWhenDue(
        mirror.redeclare(value),
        {
            Settlement<C> { component ->
                mirror.settleThrough(component, value, read, { _, written -> write(written) }, onSettled)
            }
        },
    ) { settlement -> settlement.applyTo(this) }
}

/**
 * Declares [value] onto a widget property whose write is a transition rather than an assignment: [write]
 * is handed what the widget holds alongside what is declared, so it can write only the part that differs
 * and leave what the rest of the value anchors standing - the caret inside a document, above all. The
 * held value is the one the settlement has already read, so the write costs no second read.
 *
 * Everything the assigning overload says about when a settlement runs, and about calling it once per
 * pass for one node, holds here too.
 *
 * @param value the declaration for the property, settled on every pass either side changes.
 * @param mirror the record this declaration and the widget's value are compared against.
 * @param read answers with the value the widget holds. Called once before the write, and a second time
 *   only where a write was made.
 * @param write moves the widget from the value it holds to the one declared, marked as the library's
 *   own so the events it raises are not taken for the user's.
 * @param onSettled handed what the widget was left holding, where that differs from [value]. Defaults
 *   to doing nothing.
 */
public fun <C : Component, V> SwingNodeUpdater<C>.declare(
    value: V,
    mirror: MirrorState<V>,
    read: C.() -> V,
    write: C.(held: V, declared: V) -> Unit,
    onSettled: C.(V) -> Unit = {},
) {
    applyMirror(mirror)
    settleWhenDue(
        mirror.redeclare(value),
        { Settlement<C> { component -> mirror.settleThrough(component, value, read, write, onSettled) } },
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
private val Undeclared: Any = Any()

/** One widget settle: the accessors to settle a declaration through, bound to the value it declares. */
internal fun interface Settlement<in C : Component> {
    fun applyTo(component: C)
}
