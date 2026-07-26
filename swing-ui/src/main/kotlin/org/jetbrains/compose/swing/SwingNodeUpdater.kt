package org.jetbrains.compose.swing

import androidx.compose.runtime.Updater
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import org.jetbrains.compose.swing.core.SwingNodeHolder
import java.awt.Component

/**
 * The receiver of the `update` block passed to [SwingNode]: exposes the typed Swing component [T] as
 * `this` inside [set], [update] and [reconcile] blocks.
 *
 * Use [set]/[update] for reactive property updates and [reconcile] for unconditional reconciliation.
 * Listeners are installed through the modifier mechanism - see
 * [org.jetbrains.compose.swing.modifier.listener] and `docs/CUSTOM-COMPONENTS.md`.
 */
@JvmInline
public value class SwingNodeUpdater<T : Component>
    @PublishedApi
    internal constructor(
        @PublishedApi internal val updater: Updater<SwingNodeHolder<T>>,
    ) {
        /**
         * Reactively applies [value] to the component. [block] runs (with the typed component as
         * `this` and [value] as its argument) on the first composition and again only when [value]
         * changes between recompositions, so it is the idiomatic way to push a single piece of
         * composition state onto a Swing property.
         *
         * Same semantics as [Updater.set].
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
         * [value] (e.g. a constructor argument), so the block only needs to run on subsequent
         * changes.
         *
         * Same semantics as [Updater.update].
         */
        public inline fun <V> update(
            value: V,
            crossinline block: T.(V) -> Unit,
        ): Unit =
            updater.update(value) {
                component.block(it)
            }

        /**
         * Unconditionally schedules [block] to run against the typed component on every composition.
         * Prefer [set]/[update] when a single changing value drives the update; reach for [reconcile]
         * only when those are insufficient.
         *
         * Same semantics as [Updater.reconcile].
         */
        public inline fun reconcile(crossinline block: T.() -> Unit): Unit =
            updater.reconcile {
                component.block()
            }

        /**
         * Hands the composition owner's shared [SnapshotStateObserver] - stamped onto this node's
         * holder by the applier at insert - to [block] with the typed component as `this`, so a
         * snapshot-observing component (e.g. `Canvas`) can adopt it. Runs on every composition like
         * [reconcile]; the observer is owner-stable, so re-running is idempotent.
         */
        internal fun ownerObserver(block: T.(SnapshotStateObserver?) -> Unit): Unit =
            updater.reconcile {
                component.block(ownerObserver)
            }
    }
