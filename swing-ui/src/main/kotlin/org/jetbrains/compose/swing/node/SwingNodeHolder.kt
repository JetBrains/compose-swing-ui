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
 * An attachment to a host that has its own method for holding children, such as
 * `JScrollPane.setViewportView`, instead of the usual `Container.add`.
 *
 * The host names its [ChildPlacement]:
 * - [ChildPlacement.Slots] - named regions that hold one child each.
 * - [ChildPlacement.OrderedSlots] - a host that holds many children in order, such as the tabs of a
 *   `JTabbedPane`.
 *
 * The attachment belongs to the host: the container composable wrapping it hands each of its regions
 * the attachment that installs a component there. A child reaches a region through
 * [org.jetbrains.compose.swing.modifier.layout.slot], which names the region it fills.
 *
 * @see org.jetbrains.compose.swing.modifier.layout.slot
 */
public fun interface SlotAttachment {
    /**
     * Attaches [component] to [host], and returns the action that detaches it again.
     *
     * In a host that holds many children, the returned action must detach the component by identity.
     * Positions shift as siblings come and go, so an index captured now can point at another child
     * later.
     *
     * @param host the component that holds the region being filled
     * @param component the child to attach
     * @param index the position among the host's slot children, always `0` for a region that holds
     *   one child
     * @return the action that detaches [component] and frees the region
     */
    public fun install(
        host: Container,
        component: Component,
        index: Int,
    ): () -> Unit
}

/**
 * The node type of [SwingApplier]. It holds a Swing [Component] and the bookkeeping that the applier
 * and the modifier chain keep for that component.
 *
 * The Compose runtime calls [onRelease], [onReuse] and [onDeactivate] on it. Components built with
 * [SwingNode] reach it through [SwingNodeUpdater].
 */
@PublishedApi
internal class SwingNodeHolder<out T : Component>
    @PublishedApi
    internal constructor(
        @PublishedApi internal val component: T,
    ) : ComposeNodeLifecycleCallback {
        /**
         * The layout constraint the component is placed under, such as a `BorderLayout` region.
         * `null` means the component is placed by index only.
         *
         * This also records what the parent's layout manager currently holds. The modifier chain sets
         * it before the applier attaches the component, so the two always agree.
         */
        internal var constraint: Any? = null
            private set

        /** Places this node under [value], the constraint its modifier chain declares, or `null` for none. */
        internal fun applyConstraint(value: Any?) {
            if (value == constraint) return
            constraint = value
            reregisterWithLayout(value)
        }

        /**
         * Tells the parent's layout manager that the component now uses [constraint].
         *
         * The component is never removed from its parent, so it keeps its position, its focus and its
         * native resources. Only the placement changes.
         *
         * `removeLayoutComponent` runs first because some managers store the child under its old
         * constraint. `BorderLayout` is one: without this it would hold the component twice.
         *
         * A node that is not attached yet is placed by the applier's own add instead, and a parent
         * with no layout manager has nothing to register.
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

        /** Set by [SwingNode] from the user's `onRelease`. Called once, when the node is released. */
        @PublishedApi
        internal var releaseBlock: (() -> Unit)? = null

        /**
         * Diff state for this node's modifier chain, written by
         * [org.jetbrains.compose.swing.modifier.applyModifier].
         *
         * Marked [InternalSwingUiApi]. It may change without notice in any release.
         */
        @InternalSwingUiApi
        @PublishedApi
        internal var modifierState: SwingModifierState? = null

        /**
         * The attachment this node's modifier chain declares. It is non-`null` when the chain installs
         * the component through a host's own method instead of `Container.add`.
         *
         * The chain sets it before the applier attaches the component. It says where the composition
         * wants the component. [installedSlotAttachment] says where the applier put it.
         */
        internal var declaredSlotAttachment: SlotAttachment? = null

        /**
         * The name of the region the chain declares the component fills, such as `viewport` or
         * `corner(UPPER_LEFT)`. It is non-`null` exactly when [declaredSlotAttachment] is.
         *
         * The name tells one region of a host from another. The applier uses it to decide when a
         * component must move, and to keep a host to one child per region.
         */
        internal var declaredSlotName: String? = null

        /**
         * The attachment that installed the component where it is now. It is `null` when the node
         * fills no region, which is every node its parent adds by index.
         *
         * Only the applier writes it.
         */
        internal var installedSlotAttachment: SlotAttachment? = null

        /** The name of the region the component is installed in. See [installedSlotAttachment]. */
        internal var installedSlotName: String? = null

        /**
         * The action that [installedSlotAttachment] returned. It runs when the applier removes or
         * moves the node, and when the node is released. It is `null` when the node fills no region.
         *
         * It survives reuse and deactivation. A parked node stays attached to its host like every
         * other child, so the region it fills is still filled.
         */
        internal var slotUninstall: (() -> Unit)? = null

        /**
         * Records [attachment] and [uninstall] as what installed the component where it is now.
         *
         * The region recorded is the declared one, because an install always fills the region that was
         * declared. It is `null` for a top-level child, which fills no named region.
         */
        internal fun installedThrough(
            attachment: SlotAttachment,
            uninstall: () -> Unit,
        ) {
            installedSlotAttachment = attachment
            installedSlotName = declaredSlotName
            slotUninstall = uninstall
        }

        /** Frees the region the component fills, if it fills one. Safe to call twice. */
        internal fun releaseInstalledSlot() {
            slotUninstall?.invoke()
            slotUninstall = null
            installedSlotAttachment = null
            installedSlotName = null
        }

        /**
         * The children the applier holds for this component, in composition order.
         *
         * A move reads each moved child's [constraint] from here, because Swing does not give a
         * constraint back after `remove`. A removal from a host that holds its children in regions of
         * its own also reads them from here, and runs each one's [slotUninstall] to release the region.
         * The list lives on the node, so it goes away with the node.
         *
         * A child stands here from the moment the applier takes it in, which for a relocated child is
         * before its component is attached - see [awaitingAttachment].
         */
        internal val children: MutableList<SwingNodeHolder<*>> = ArrayList()

        /**
         * Whether the host holding this node in its [children] has yet to attach the component.
         *
         * A node the composition relocates reaches its new host before its own modifier chain has run
         * there, so the placement it names at that host - and with it the attachment that fills a region
         * of the host - is read once the change pass has settled. It stands in the host's child list
         * meanwhile, which is what a remove or a move later in the same pass addresses it by.
         *
         * Only the applier writes it.
         */
        internal var awaitingAttachment: Boolean = false

        /**
         * How this node holds its children, as declared on [SwingNode]. It is
         * [ChildPlacement.Indexed] when the node declares nothing.
         *
         * The applier reads it when a child arrives, and again when a child is removed or moved. It
         * therefore also records how the attached children were reached.
         */
        @PublishedApi
        internal var childPlacement: ChildPlacement = ChildPlacement.Indexed

        /**
         * The shared [SnapshotStateObserver] of the composition owner. The applier stamps it onto the
         * node on the top-down insert pass. A component adopts it from the node instead of resolving
         * a `CompositionLocal`. The node's update block runs before the bottom-up pass, so a stamp
         * left to that pass would reach a component that has already been attached and painted.
         *
         * A component that observes snapshot state, such as `Canvas`, adopts it once and keeps it for
         * the node's whole life. It is `null` when the composition's applier observes no snapshot
         * state, such as a menu.
         */
        internal var ownerObserver: SnapshotStateObserver? = null

        /**
         * `true` while the component carries a [COMPOSITION_KEY] stamp that this node published.
         *
         * A factory may return a component that hosts a composition of its own and stamps itself, which
         * is how a caller writes a complex component with another Compose instance inside. That stamp is
         * the component's, not this node's, and teardown here must leave it standing.
         */
        private var hostsSubcompositions: Boolean = false

        /**
         * Publishes [context] on the component as the [COMPOSITION_KEY] client property. A
         * `setContent` call on a component below then finds it, and nests into this composition. A
         * `null` [context] removes the stamp.
         *
         * Calling it again for the same node changes nothing.
         *
         * Throws [IllegalStateException] when the component is not a [JComponent], because only a
         * [JComponent] can carry a client property.
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
         * Removes the [COMPOSITION_KEY] stamp this node published. Clearing an absent stamp is a no-op,
         * and no other node can have stamped this component: a node holds its component for the
         * component's whole life, and the only other publisher stamps a window's root pane.
         */
        private fun clearSubcompositionStamp() {
            if (!hostsSubcompositions) return
            (component as? JComponent)?.putClientProperty(COMPOSITION_KEY, null)
            hostsSubcompositions = false
        }

        /**
         * Puts the node back to the state a new node starts from. Recycled or parked content then
         * reacts only to what it reads itself, never to what the previous content read.
         *
         * It removes the subcomposition stamp, detaches the listeners the modifier chain installed,
         * restores the properties the chain changed, and drops the component's tracked reads from the
         * owner's observer. The detach covers every modifier-installed listener, including the
         * built-in domain listener of the component. A stamp left behind would be found by a
         * `setContent` call on a component below. That call would then nest into a composition that no
         * longer runs. A component that observes snapshot state registers its reads again once the
         * composition drives the node again. The shared observer itself keeps running for every other
         * node. It is disposed with the composition.
         *
         * It does not change where the component lives. [constraint], [declaredSlotAttachment],
         * [declaredSlotName], [childPlacement] and [ownerObserver] all survive, because recycling
         * changes what drives a component, not its place in the Swing tree. Clearing [constraint]
         * would be wrong in particular: this method changes no layout manager, so the value would stop
         * matching what the manager holds. [applyConstraint] returns early when the value is
         * unchanged, so a recycled node that declares no constraint would skip re-registration, and
         * its component would stay in its old region.
         */
        private fun reset() {
            clearSubcompositionStamp()
            resetModifierState()
            ownerObserver?.clear(component)
        }

        /** The node is leaving the composition for good. */
        override fun onRelease() {
            reset()
            // The applier frees a region when it removes or moves a node. Whole-subtree disposal goes
            // through neither path: SwingApplier.onClear() drops the root subtree with
            // Container.removeAll() and clears the root's child list, releasing no region on the way.
            // A node installed in a region is therefore still installed when the runtime releases it.
            // The call is unguarded because both the installed and the uninstalled state are
            // legitimate: an ordinary remove or move has already freed the region.
            releaseInstalledSlot()
            releaseBlock?.invoke()
            releaseBlock = null
        }

        /**
         * The runtime is reusing this holder for new content in the same slot. An `update` follows,
         * and re-applies the whole modifier chain from the clean baseline this leaves.
         *
         * The component keeps its attachment. Only the applier attaches and detaches components, and
         * it is not called for a reuse, so a holder that freed its region here would empty a region
         * that nothing fills again.
         *
         * It keeps its visibility too: the new content drives it from here on with no pass in between,
         * so there is no moment at which nothing shows it. A holder recycled while parked stays hidden
         * until the chain that `update` applies shows it, like any other parked node.
         */
        override fun onReuse() {
            reset()
        }

        /**
         * The node was parked: it moved into a parked `movableContent` holder, or the reusable content
         * around it went inactive. Driving it again runs `update` again.
         *
         * A parked node keeps its place in the Swing tree, host region included: parking suspends the
         * composition that drives a component, and the applier - which alone attaches and detaches -
         * records no change for it.
         */
        override fun onDeactivate() {
            reset()
        }
    }
