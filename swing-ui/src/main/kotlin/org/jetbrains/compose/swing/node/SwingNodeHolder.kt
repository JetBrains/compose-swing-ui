package org.jetbrains.compose.swing.node

import androidx.compose.runtime.ComposeNodeLifecycleCallback
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import org.jetbrains.compose.swing.core.COMPOSITION_KEY
import org.jetbrains.compose.swing.modifier.SwingModifierState
import org.jetbrains.compose.swing.modifier.resetModifierState
import java.awt.Component
import java.awt.Container
import java.awt.LayoutManager2
import javax.swing.JComponent

/**
 * Installs a node's [Component] into a Swing host that owns its own attachment slot - a host whose
 * children go through a dedicated setter rather than the generic `Container.add` used by the
 * [SwingApplier]. Two host shapes are covered:
 * - **Single-occupancy slots**, e.g. `JScrollPane`'s viewport / header / corner regions, each reached
 *   via `setViewportView` / `setRowHeaderView` / `setColumnHeaderView` / `setCorner`. The `index` is
 *   always `0`.
 * - **Ordered multi-occupancy hosts**, e.g. a `JTabbedPane` whose tabs are placed at an index via
 *   `insertTab(title, icon, component, tip, index)`.
 *
 * [install] attaches the component and returns the action that detaches it again, releasing the host
 * slot when the node leaves. For a multi-occupancy host, the returned action should detach by
 * component identity (e.g. `JTabbedPane.remove(component)`) so it stays correct as sibling indices
 * shift.
 *
 * Marked [InternalSwingUiApi]; it may change without notice.
 */
@InternalSwingUiApi
public fun interface SlotAttachment {
    /**
     * Attaches [component] into [host] through the host's dedicated setter and returns the action
     * that detaches it again and releases the slot. [index] is the node's composition index among the
     * host's slot children - `0` for a single-occupancy slot, the insertion position for an ordered
     * multi-occupancy host.
     */
    public fun install(
        host: Container,
        component: Component,
        index: Int,
    ): () -> Unit
}

/**
 * The node type of [SwingApplier]: a wrapper around a Swing [Component] that implements
 * [ComposeNodeLifecycleCallback] so the Compose runtime can invoke lifecycle callbacks when a node
 * is released, reused (movableContent / slot reuse), or deactivated.
 *
 * Components built with [SwingNode] interact with it indirectly through [SwingNodeUpdater].
 *
 * Modifier-installed listeners (see [org.jetbrains.compose.swing.modifier.listener]) are detached
 * and the node's modifier-applied properties restored on release, reuse, and deactivation, so a
 * recycled slot starts from a clean baseline.
 *
 * Internal runtime type; not public API. See `docs/CUSTOM-COMPONENTS.md`.
 */
@PublishedApi
internal class SwingNodeHolder<out T : Component>
    @PublishedApi
    internal constructor(
        @PublishedApi internal val component: T,
    ) : ComposeNodeLifecycleCallback {
        /**
         * The parent-container constraint this node's [component] is placed under (e.g. a `BorderLayout`
         * region), or `null` to be placed by index alone. Read by [SwingApplier] when it adds or re-adds
         * the component, and equally the record of what the parent's layout manager holds: [SwingNode]
         * applies it before the applier attaches the component, so the two never disagree.
         */
        internal var constraint: Any? = null
            private set

        /**
         * Places this node under [value], the constraint its parent provides, re-registering the
         * component with its parent's layout manager when that changes the placement the manager
         * holds. [SwingNode] calls it with the value the current composition asks for.
         */
        @PublishedApi
        internal fun applyConstraint(value: Any?) {
            if (value == constraint) return
            constraint = value
            reregisterWithLayout(value)
        }

        /**
         * Re-registers [component] with its parent's layout manager under [constraint] - the pair of
         * calls `Container.remove` and `Container.addImpl` make on a component's behalf, without
         * touching the AWT child array or the peers, so the component keeps its position, its focus and
         * its native resources while only its placement changes. `removeLayoutComponent` first is what
         * clears the old placement out of a manager that keys children by it, such as a `BorderLayout`
         * region field.
         *
         * A node that is not attached yet is placed by the applier's own add, and a parent with no
         * layout manager has nothing to register.
         */
        private fun reregisterWithLayout(constraint: Any?) {
            val parent = component.parent ?: return
            val manager = parent.layout ?: return
            manager.removeLayoutComponent(component)
            if (manager is LayoutManager2) {
                manager.addLayoutComponent(component, constraint)
            } else if (constraint is String) {
                manager.addLayoutComponent(constraint, component)
            }
            parent.revalidate()
        }

        /** Set by [SwingNode] from the user's `onRelease`. Invoked once, on final release. */
        @PublishedApi
        internal var releaseBlock: (() -> Unit)? = null

        /**
         * Diff state for the node's [org.jetbrains.compose.swing.modifier.SwingModifier] chain.
         * Written by [org.jetbrains.compose.swing.modifier.applyModifier]; reset on
         * release/reuse/deactivation so a recycled node restores its modified properties and drops
         * modifier-installed listeners.
         *
         * Marked [InternalSwingUiApi]; it may change without notice in any release.
         */
        @InternalSwingUiApi
        @PublishedApi
        internal var modifierState: SwingModifierState? = null

        /**
         * Non-`null` when this node's [component] is installed into its parent through a dedicated Swing
         * setter rather than the generic `Container.add` (e.g. a `JScrollPane` region reached via
         * `setViewportView`/`setRowHeaderView`/`setColumnHeaderView`/`setCorner`).
         *
         * This is a structural property of the node, set once at creation, and is retained across
         * [reset]: a recycled node that fills a slot still fills it.
         */
        @PublishedApi
        internal var slotAttachment: SlotAttachment? = null

        /**
         * The teardown returned by [SlotAttachment.install] for a [slotAttachment]-backed node, invoked
         * when the [SwingApplier] removes or moves the node, and on the node's final release, so the host
         * slot is released. `null` while the node is not installed.
         *
         * It survives reuse and deactivation, which leave the component where it is: like every other
         * child, a parked node stays attached to its host and is driven again on reactivation.
         */
        internal var slotUninstall: (() -> Unit)? = null

        /**
         * The children the [SwingApplier] has attached to this node's [component], in composition order.
         *
         * A move reads each moved child's current [constraint] back from here, since Swing drops a
         * constraint on `remove` and no layout manager offers it back; a removal of [childrenFillSlots]
         * children runs each one's [slotUninstall] to release the host slot. Held on the node rather than
         * in a map keyed by container, so it goes away with the node and leaves nothing behind for a
         * subtree the composition has dropped.
         */
        internal val children: MutableList<SwingNodeHolder<*>> = ArrayList()

        /**
         * Whether [children] were installed through their own [slotAttachment] rather than added to the
         * component as ordinary indexed children.
         *
         * A node's children are one index space and the two kinds are reached through different Swing
         * calls, so they cannot be mixed under one node; the applier refuses the second kind. Meaningless
         * while [children] is empty, and set again by the next child added.
         */
        internal var childrenFillSlots: Boolean = false

        /**
         * The composition owner's shared [SnapshotStateObserver], stamped onto this node by the
         * [SwingApplier] on the top-down insert pass. A snapshot-observing component (e.g. `Canvas`) reads
         * it from here to register its paint reads, instead of resolving a `CompositionLocal`. `null` in a
         * composition whose applier observes no snapshot state, such as a menu.
         *
         * Like [slotAttachment] this is an owner-stable structural property: set once at insert and
         * retained across [reset], since a recycled node stays in the same composition owner.
         */
        internal var ownerObserver: SnapshotStateObserver? = null

        /**
         * `true` while this node's [component] carries a [COMPOSITION_KEY] stamp published by
         * [hostSubcompositions] (the `hostsSubcompositions = true` opt-in on [SwingNode]), so the stamp
         * is cleared exactly once on release/reuse/deactivation.
         */
        private var hostsSubcompositions: Boolean = false

        /**
         * Publishes [context] as this node's [COMPOSITION_KEY] client property so a descendant component's
         * `setContent` discovers it and nests into the surrounding composition, or drops the stamp when
         * [context] is `null` so such a walk finds no host here. Backs the `hostsSubcompositions` opt-in
         * on [SwingNode] in both directions; idempotent across recompositions for the same node.
         *
         * Publishing requires the [component] to be a [JComponent]; otherwise this throws
         * [IllegalStateException].
         */
        @PublishedApi
        internal fun hostSubcompositions(context: CompositionContext?) {
            if (context == null) {
                clearSubcompositionStamp()
                return
            }
            val host =
                component as? JComponent
                    ?: error(
                        "SwingNode(hostsSubcompositions = true) requires the factory component to be a " +
                            "JComponent so descendant setContent calls can discover this composition via " +
                            "the COMPOSITION_KEY client property, but it was a " +
                            "'${component.javaClass.name}'. A non-JComponent cannot host subcompositions " +
                            "through the client-property walk.",
                    )
            host.putClientProperty(COMPOSITION_KEY, context)
            hostsSubcompositions = true
        }

        /**
         * Drops the [COMPOSITION_KEY] stamp this node published, so a descendant `setContent` walk finds
         * no host here. A no-op for a node that published none.
         */
        private fun clearSubcompositionStamp() {
            if (!hostsSubcompositions) return
            (component as? JComponent)?.putClientProperty(COMPOSITION_KEY, null)
            hostsSubcompositions = false
        }

        private fun reset() {
            // [constraint] is deliberately NOT cleared. It records what the parent's layout manager
            // holds, and this method changes no manager, so clearing it would assert something untrue -
            // and the first placement applied to the recycled node would then skip the re-registration
            // that returns the component to placement by index.
            //
            // Clear any COMPOSITION_KEY stamp this node published for the hostsSubcompositions opt-in, so
            // a node leaving the composition (release) or being recycled for new content (reuse /
            // deactivate) never leaks a stale parent context to a descendant's setContent walk. Mirrors
            // the window recomposer's stamp-then-clear discipline. The upcoming `update` re-stamps if the
            // incoming node opts in again.
            clearSubcompositionStamp()
            // Detaches every modifier-installed listener (including the built-in domain listener) and
            // restores modified properties, then clears the diff state so a reused or reactivated node
            // (ReusableComposeNode / movableContent / ReusableContent) re-installs its listeners and
            // re-applies its modifier from a clean baseline on the next `update`.
            resetModifierState()
        }

        override fun onRelease() {
            // reset() already detaches modifier-installed listeners and clears the diff state; here we
            // additionally release the host slot and run the one-shot release block, since the node is
            // leaving the composition for good.
            reset()
            // Release the host slot if this node is still installed through a dedicated setter.
            //
            // `slotUninstall` is non-null here exactly when the node is released WITHOUT having gone
            // through the applier's `remove`/`move`, which are the only other call sites that run and
            // null the handle. The concrete production case is whole-subtree disposal:
            // SwingApplier.onClear() tears the root subtree down via `Container.removeAll()` and just
            // clears its tracking maps - it does NOT walk the slot-hosting descendants to null their
            // handles - so a JScrollPane region child (or any slot-attached node) still carries a live
            // `slotUninstall` when the runtime then releases it. Calling it here releases that host slot;
            // skipping it would leak the region's reference to the now-detached child. In the ordinary
            // remove/move path the applier already nulled the handle, so this is a plain no-op. The call
            // is null-safe rather than `check`-guarded precisely because both the null and non-null
            // states are legitimate; nulling afterwards keeps it idempotent against a defensive second
            // release.
            slotUninstall?.invoke()
            slotUninstall = null
            releaseBlock?.invoke()
            releaseBlock = null
        }

        override fun onReuse() {
            // The runtime is recycling this holder for a new node in the same reusable slot (a node
            // emitted via ReusableComposeNode whose group is reused across recompositions - e.g. a
            // ReusableContentHost reactivated after being parked, or structurally-identical content
            // replacing it in the same slot). The recycled holder must behave like a freshly created one
            // for the incoming content: detach every modifier-installed listener and clear the diff
            // state, so the upcoming `update` re-applies the new node's modifier chain from a clean
            // baseline instead of inheriting the previous node's.
            //
            // The component itself keeps its attachment: recycling changes what drives a component, not
            // where it lives. The applier is the sole authority on attachment, and it is not consulted
            // for a reuse, so a holder that detached its component here - through the host slot it
            // occupies, the one attachment a holder can reach on its own - would empty a slot nothing
            // ever refills.
            reset()
        }

        override fun onDeactivate() {
            // The node moved into a deactivated (movableContent) holder. Detach listeners so it does
            // not keep reacting while parked; a later activation re-runs `update` and re-attaches.
            //
            // A parked node keeps its place in the Swing tree, host slot included: parking suspends the
            // composition that drives a component, and the applier - which alone attaches and detaches -
            // records no change for it.
            reset()
        }
    }
