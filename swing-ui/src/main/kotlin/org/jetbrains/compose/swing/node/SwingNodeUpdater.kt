package org.jetbrains.compose.swing.node

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.Updater
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import java.awt.Component

/**
 * The receiver of the `update` block passed to [SwingNode]: the typed Swing component [T] is `this`
 * inside [set], [update], [init] and [reconcile].
 *
 * Use [set]/[update] for reactive property updates, [init] for one-time setup after creation, and
 * [reconcile] for unconditional reconciliation. Listeners are installed through the modifier
 * mechanism - see [org.jetbrains.compose.swing.modifier.listener].
 */
@JvmInline
public value class SwingNodeUpdater<T : Component>
    @PublishedApi
    internal constructor(
        @PublishedApi internal val updater: Updater<SwingNodeHolder<T>>,
    ) {
        /**
         * Reactively applies [value] to the component. [block] runs, with the typed component as
         * `this` and [value] as its argument, on the first composition and again only when [value]
         * changes between recompositions.
         *
         * @param value the declaration to apply, compared against the previous pass's with `equals`.
         * @param block applies [value] to the component. It runs while the composition applies its
         *   changes, not while composing.
         * @see Updater.set
         */
        public inline fun <V> set(
            value: V,
            crossinline block: T.(V) -> Unit,
        ): Unit =
            updater.set(value) {
                component.block(it)
            }

        /**
         * Reactively applies [value] to the component, but - unlike [set] - skips the very first
         * composition. Use it when the [factory][SwingNode] already initialized the component with
         * [value] (e.g. a constructor argument).
         *
         * @param value the declaration to apply, compared against the previous pass's as in [set].
         * @param block applies [value] to the component, from the first recomposition that changes it.
         * @see Updater.update
         */
        public inline fun <V> update(
            value: V,
            crossinline block: T.(V) -> Unit,
        ): Unit =
            updater.update(value) {
                component.block(it)
            }

        /**
         * Runs [block], with the typed component as `this`, exactly once: on the pass that creates the
         * node, after the [set]/[update] blocks declared above it in the same `update` lambda have run
         * against the freshly built component. Use it for setup that must happen once at creation but
         * needs a value [set]/[update] computed - a value the [factory][SwingNode] cannot see - and that
         * [reconcile] would otherwise redo on every composition.
         *
         * The blocks run in the order the `update` lambda declares them, so an [init] that needs what
         * another block writes is declared after it.
         *
         * @param block the setup to run against the freshly built component. A node released and built
         *   again runs it against the new component.
         * @see Updater.init
         */
        public inline fun init(crossinline block: T.() -> Unit): Unit =
            updater.init {
                component.block()
            }

        /**
         * Publishes [context] on the component, so that a `setContent` call on a component below it joins
         * this composition - sharing its scope and its
         * [CompositionLocal][androidx.compose.runtime.CompositionLocal]s. Without it such a call joins
         * whatever its place in the Swing tree resolves to - the content composition above it, or the one
         * its window shares - so it recomposes with everything else there but sees none of the
         * `CompositionLocal`s this node stands under. A `null` [context] leaves the component hosting
         * nothing.
         *
         * Read the context where the node is declared and hand it over here:
         *
         * ```
         * val context = rememberCompositionContext()
         * SwingNode(
         *     factory = { JPanel() },
         *     update = { hostSubcompositions(context) },
         * )
         * ```
         *
         * It is declared rather than taken by [SwingNode] itself because reading the enclosing context is
         * work on every pass rather than a value a node remembers, and a node that hosts nothing would
         * otherwise pay for it.
         *
         * The component must be a [javax.swing.JComponent], which is what carries the client property a
         * descendant `setContent` walks up to find; anything else throws [IllegalStateException].
         *
         * @param context the composition a `setContent` below this component joins. Applied through
         *   [set], so it takes a slot in the `update` block whether or not it is `null`.
         */
        public fun hostSubcompositions(context: CompositionContext?): Unit =
            updater.set(context) {
                hostSubcompositions(it)
            }

        /**
         * Unconditionally schedules [block] to run against the typed component on every composition.
         * Prefer [set]/[update] when a single changing value drives the update; reach for [reconcile]
         * only when those are insufficient.
         *
         * @param block runs against the component while the composition applies its changes.
         * @see Updater.reconcile
         */
        public inline fun reconcile(crossinline block: T.() -> Unit): Unit =
            updater.reconcile {
                component.block()
            }

        /**
         * Settles [block] against this node's children, at the end of the change pass rather than here.
         *
         * A node's update runs before the runtime applies the content that update declared, so a write
         * made here that reads the node's children - a `JTabbedPane` put on one of its own tabs - would
         * be made against the children the pass before it left behind. Handing the write over instead
         * has one pass declare a child and settle on it.
         *
         * The block runs at the end of the pass that hands it over, and again at the end of every later
         * pass that adds, removes or moves this node's children, since that is when a standing
         * declaration can become one the widget answers differently. See [SwingNodeHolder.childSettle]
         * for why the block held from an earlier pass still applies.
         *
         * Runs on every composition like [reconcile].
         */
        internal fun settleWithChildren(block: T.() -> Unit): Unit =
            updater.reconcile {
                childSettle = { component.block() }
                requireOwner().updateBatch.holdForChildSettle(this)
            }

        /**
         * Applies [mirror] to this node, so the mirror belongs to it - which is what lets
         * [MirrorState.report] answer a change inside the event that made it rather than from an event
         * of its own.
         *
         * [declare] states it for the declaration it settles, so a component built the usual way needs
         * nothing here. State it directly where a component settles a mirror some other way - applying
         * two declarations the widget resolves together, or reading back a property no declaration is
         * written through. A mirror that reports without it fails at the first change.
         *
         * Runs once, on the pass that creates the node: the composition a node stands in is the same for
         * its whole life.
         *
         * @param mirror the record whose reports settle in this node's composition. Remember it in this
         *   node's own group, so it is released with the node it answers for.
         */
        public fun applyMirror(mirror: MirrorState<*>): Unit = updater.init(mirror) { it.owner = owner }

        /**
         * Hands the composition owner's shared [SnapshotStateObserver] - stamped onto this node's
         * holder by the applier at insert - to [block] with the typed component as `this`, so a
         * snapshot-observing component (e.g. `Canvas`) can adopt it.
         *
         * The observer is the same for the node's whole life, and is handed over before the applier
         * attaches the component. [block] receives `null` only under an applier that owns no observer,
         * such as a menu.
         *
         * A component that registers reads with the observer must use the component instance itself as
         * the observation scope: the holder clears that same scope when the node resets, so a different
         * scope object would leave the reads in place and the component still driven by an observer no
         * longer meant to reach it.
         *
         * Runs on every composition like [reconcile].
         */
        internal fun ownerObserver(block: T.(SnapshotStateObserver?) -> Unit): Unit =
            updater.reconcile {
                component.block(owner?.observer)
            }
    }
