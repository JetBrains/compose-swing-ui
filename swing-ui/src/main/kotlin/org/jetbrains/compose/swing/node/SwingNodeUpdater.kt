package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Updater
import java.awt.Component

/** Receiver of a [SwingNode] `update` block. The typed component [T] is `this` in its operations. */
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
    }

/**
 * Reconciles [block] against this node's children, at the end of the change pass rather than here.
 *
 * A node's update runs before the runtime applies the content that update declared, so a write made
 * here that reads the node's children - a `JTabbedPane` put on one of its own tabs - would be made
 * against the children the pass before it left behind. Handing the write over instead has one pass
 * declare a child and reconcile against it.
 *
 * [block] runs at the end of the pass that hands it over, and again at the end of every later pass
 * that adds, removes or moves this node's children, since that is when a standing declaration can
 * become one the widget answers differently, even when the node itself does not recompose.
 *
 * A [androidx.compose.runtime.SideEffect] cannot replace this: a `SideEffect` runs only when its own
 * scope recomposes, not on every later pass that changes this node's children.
 *
 * Runs on every composition like [SwingNodeUpdater.reconcile]. [block] runs against a scope standing
 * for this node, which reaches the node's own placement - where the composition holds the
 * component - as well as the component itself, without exposing the node itself.
 */
public fun <T : Component> SwingNodeUpdater<T>.reconcileWithChildren(
    block: ReconcileWithChildrenScope<T>.() -> Unit,
): Unit =
    // Runs on every pass, including one whose declared values are all unchanged and one in which the
    // applier changes this node's children. Each run stores the block and holds the node once.
    updater.reconcile {
        requireOwner().updateBatch.declareChildSettle(this) {
            ReconcileWithChildrenScopeImpl(this).block()
        }
    }

/** The receiver [reconcileWithChildren] hands its block, standing for the node without exposing it. */
public sealed interface ReconcileWithChildrenScope<out T : Component> {
    /** The component [reconcileWithChildren] was called for. */
    public val component: T

    /**
     * Puts [component] back under the layout constraint its modifier declares, after something
     * outside the composition put it somewhere else - the look and feel docking a tool bar back
     * under `NORTH`, say.
     *
     * Does nothing when [component] stands in another parent, or its modifier declares no
     * constraint. It sits on this scope because the end of the pass is the first point where the
     * component is placed and the node is in hand. Core keeps no other public way from a component
     * to its declaration.
     */
    public fun restoreDeclaredPlacement()
}

/** The only [ReconcileWithChildrenScope], wrapping [holder] without exposing it. */
private class ReconcileWithChildrenScopeImpl<T : Component>(
    private val holder: SwingNodeHolder<T>,
) : ReconcileWithChildrenScope<T> {
    override val component: T get() = holder.component

    override fun restoreDeclaredPlacement() {
        if (holder.declaration.parentData != null) {
            holder.declaration.reapply()
        }
    }
}
