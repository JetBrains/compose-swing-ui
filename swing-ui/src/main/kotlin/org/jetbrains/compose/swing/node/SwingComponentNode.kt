@file:JvmMultifileClass
@file:JvmName("NodeKt")

package org.jetbrains.compose.swing.node

import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.CompositionGroup
import org.jetbrains.compose.swing.core.checkEventDispatchThread
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component

/**
 * The node a Swing composition stores for one declared component, read off a node group of its
 * [CompositionData]:
 *
 * ```
 * val component = (group.node as? SwingComponentNode<*>)?.component
 * ```
 *
 * A group whose [CompositionGroup.node] is not a [SwingComponentNode] declared no Swing component - it is
 * a control-flow or call group, or a node of some other composition sharing the tree.
 *
 * [org.jetbrains.compose.swing.tooling.findDeclaringGroup] answers from the other end, starting at a
 * component and returning the group holding its node.
 *
 * A node is typed by the component it holds. A [SwingComponentNodeListener] component types the node it is told with
 * [asNodeOf].
 */
public sealed class SwingComponentNode<out C : Component> {
    /**
     * The component this node holds, for as long as the composition holds the node.
     *
     * It is the live component: the composition still owns every property a declared parameter governs,
     * and writing one here is replaced on the next pass that applies it.
     */
    public abstract val component: C

    /**
     * The effective [SwingModifier] chain currently declared on this node, including any inherited
     * component defaults applicable to this component followed by elements passed directly on the
     * node's own modifier.
     *
     * Walk it with [SwingModifier.foldIn], which hands out a [SwingModifier.Element];
     * read the [name][SwingModifier.InspectableElement.name] and
     * [declaredValues][SwingModifier.InspectableElement.declaredValues] of each one that is a
     * [SwingModifier.InspectableElement] to show what the component carries.
     *
     * A [composed][org.jetbrains.compose.swing.modifier.composed] entry appears as the entries its factory
     * returned for this component.
     *
     * It is the whole effective declared modifier chain, placement included: an element saying where the
     * component sits in its parent stands in it alongside the ones saying what it looks like. Inherited
     * defaults are flattened into their effective property elements; provider-key boundaries are not
     * preserved and key names are not presented as synthetic modifier elements.
     *
     * It answers whatever the composition declared last, whether or not the pass that declared it had
     * anything to write, so it never lags the composition. Every node holds its modifier whatever
     * [org.jetbrains.compose.swing.tooling.isDebugInspectorInfoEnabled] says; what that switch decides is
     * whether a tool can reach the node at all.
     */
    public abstract val modifier: SwingModifier

    /** The composition this node stands in, or `null` while it stands in none; see [SwingNodeHolder.owner]. */
    internal abstract val owner: SwingCompositionOwner?

    /** Whether this node is attached: it has an [owner] and is not deactivated. */
    internal abstract val isAttached: Boolean
}

/**
 * Runs [block], recording its reads under this node, as [SwingModifier.Node.observeReads] does for a modifier
 * node; [onChanged] receives this node, on the Event Dispatch Thread.
 *
 * @throws IllegalStateException if this node is not attached or the call is off the Event Dispatch Thread.
 */
public fun <N : SwingComponentNode<*>> N.observeReads(
    onChanged: (N) -> Unit,
    block: () -> Unit,
) {
    checkEventDispatchThread()
    check(isAttached) { "The node holding $component is not attached" }
    requireOwner().snapshotObserver.observeReads(this, onChanged, block)
}

/**
 * Invalidates layout on the Event Dispatch Thread. When the component tree lock is held, invalidation
 * happens immediately and revalidation runs on the next event-queue turn; otherwise revalidation runs
 * immediately. Pending revalidation is canceled when this node detaches: when it is reused, deactivated, released
 * or moved to another composition, or when its composition is disposed. Does nothing while this node is not
 * attached, which includes a root whose composition is disposed.
 *
 * @throws IllegalStateException if called off the Event Dispatch Thread.
 */
public fun SwingComponentNode<*>.invalidateLayout() {
    checkEventDispatchThread()
    val currentOwner = owner?.takeIf { isAttached } ?: return
    currentOwner.invalidateLayout(this)
}

/**
 * This node, typed by [component], which it holds. A [SwingComponentNodeListener] component calls it with `this` to
 * type the node it is told, so the callbacks it records under that node receive the component without a cast.
 *
 * @throws IllegalArgumentException if this node holds another component.
 */
public fun <C : Component> SwingComponentNode<*>.asNodeOf(component: C): SwingComponentNode<C> {
    require(this.component === component) { "This node holds ${this.component}, not $component" }
    // The cast is safe because the node's only member typed by C is its constructor-assigned component, which the check
    // above found identical to the given C.
    @Suppress("UNCHECKED_CAST")
    return this as SwingComponentNode<C>
}

/**
 * A component that is told the [SwingComponentNode]s holding it while they are attached to a composition: a node
 * a composition declares for it, and the node a `setContent` call on it roots its content at. Both can be attached
 * at once, and the component is told about each one separately.
 */
public interface SwingComponentNodeListener {
    /**
     * Called on the Event Dispatch Thread when [node] attaches to a composition.
     *
     * If this throws, the throwable is reported to the thread's uncaught-exception handler and [node] stays attached,
     * as if this had returned. The composition goes on.
     */
    public fun onAttached(node: SwingComponentNode<*>)

    /**
     * Called on the Event Dispatch Thread when [node] detaches: it is reused, deactivated, released, moved to another
     * composition, or its composition is disposed. A reused node is attached again right after.
     *
     * While this runs, [node] still reports attached, whichever way it detaches, and what this records there is dropped
     * when the call returns.
     *
     * If this throws, the throwable is reported to the thread's uncaught-exception handler and [node] detaches as if
     * this had returned: the reads and pending requests recorded under it are dropped, and a node that moves or is
     * reused is attached again and [onAttached] is called for it. The composition goes on.
     */
    public fun onDetached(node: SwingComponentNode<*>)
}

/** Tells a [SwingComponentNodeListener] component this node attached; a throw is reported. */
internal fun SwingComponentNode<*>.tellComponentAttached() {
    val listener = component as? SwingComponentNodeListener ?: return
    runReportingUncaught { listener.onAttached(this) }
}

/** Tells a [SwingComponentNodeListener] component this node detached; a throw is reported. */
internal fun SwingComponentNode<*>.tellComponentDetached() {
    val listener = component as? SwingComponentNodeListener ?: return
    runReportingUncaught { listener.onDetached(this) }
}
