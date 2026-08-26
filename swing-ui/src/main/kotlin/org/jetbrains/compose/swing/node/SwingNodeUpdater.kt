package org.jetbrains.compose.swing.node

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
         * @see Updater.init
         */
        public inline fun init(crossinline block: T.() -> Unit): Unit =
            updater.init {
                component.block()
            }

        /**
         * Unconditionally schedules [block] to run against the typed component on every composition.
         * Prefer [set]/[update] when a single changing value drives the update; reach for [reconcile]
         * only when those are insufficient.
         *
         * @see Updater.reconcile
         */
        public inline fun reconcile(crossinline block: T.() -> Unit): Unit =
            updater.reconcile {
                component.block()
            }

        /**
         * Hands the composition owner's shared [SnapshotStateObserver] to [block] with the typed
         * component as `this`, so a snapshot-observing component can adopt it.
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
                component.block(ownerObserver)
            }
    }
