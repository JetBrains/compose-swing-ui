package org.jetbrains.compose.swing.node

import androidx.compose.runtime.ComposeNodeLifecycleCallback
import androidx.compose.runtime.CompositionLocalMap
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.SwingModifierState
import org.jetbrains.compose.swing.modifier.resetModifierState
import org.jetbrains.compose.swing.tooling.NODE_KEY
import org.jetbrains.compose.swing.tooling.isDebugInspectorInfoEnabled
import org.jetbrains.compose.swing.util.fastForEach
import org.jetbrains.compose.swing.util.fastForEachIndexed
import org.jetbrains.compose.swing.util.get
import org.jetbrains.compose.swing.util.set
import java.awt.Component
import java.util.Collections
import javax.swing.JComponent

/**
 * The region a node's modifier chain declares. It says where the composition wants the component,
 * before the applier has attached it there.
 *
 * @property parentProtocol the protocol identity for the host that owns this region.
 * @property attachment the host's method for installing a component into the region
 * @property name the region's name, such as `viewport` or `corner(UPPER_LEFT)`
 */
internal class DeclaredSlot(
    val parentProtocol: ParentProtocol,
    val attachment: SlotAttachment,
    val name: String,
)

/**
 * The region a node is actually installed in, recorded by the applier once it has attached the
 * component there.
 *
 * [name] is nullable where [DeclaredSlot.name] is not: a component the applier installs through the
 * composition's own root slot fills no region any modifier declared, so it carries an attachment and an
 * uninstall action but no name. A component installed through a region its modifier named takes that
 * region's name instead.
 *
 * @property attachment the attachment that installed the component
 * @property name the name of the region filled, or `null` for the composition's root slot
 * @property uninstall detaches the component and frees the region; runs on removal, move, parking and
 *   release
 */
@VisibleForTesting
internal class InstalledSlot(
    val attachment: SlotAttachment,
    val name: String?,
    val uninstall: () -> Unit,
)

/**
 * The node type of [SwingApplier]. It holds a Swing [Component] and the bookkeeping that the applier
 * and the modifier chain keep for that component.
 *
 * The Compose runtime calls [onRelease], [onReuse] and [onDeactivate] on it. Components built with
 * [SwingNode] reach it through [SwingNodeUpdater].
 *
 * It is the node a group holds in the slot table, so it is what a tool walking a composition finds as a
 * group's node. [SwingComponentNode] is the part of it such a tool may read.
 */
@PublishedApi
internal class SwingNodeHolder<out T : Component>
    @PublishedApi
    internal constructor(
        override val component: T,
    ) : ComposeNodeLifecycleCallback,
        SwingComponentNode {
        /** What this node declares to its parent's layout manager: where it goes, and how it is measured. */
        internal val declaration: ParentDeclaration = ParentDeclaration(this)

        /** Set by [SwingNode] from the user's `onRelease`. Called once, when the node is released. */
        @PublishedApi
        internal var releaseBlock: (() -> Unit)? = null

        /**
         * Diff state for this node's modifier chain, written by
         * [org.jetbrains.compose.swing.modifier.applyModifier].
         */
        internal var modifierState: SwingModifierState? = null

        /**
         * The [CompositionLocal][androidx.compose.runtime.CompositionLocal]s in scope where this node was
         * declared, captured whole so a further local a node needs to read reaches it without a new
         * [SwingNode]/[MenuNode] parameter. Written by [org.jetbrains.compose.swing.modifier.applyCompositionLocalMap]
         * before the node's own modifier is applied, so it reflects the current pass by the time anything
         * reads it after attach.
         */
        internal var compositionLocalMap: CompositionLocalMap = CompositionLocalMap.Empty

        override val modifier: SwingModifier
            get() = modifierState?.declared ?: SwingModifier

        /**
         * The region this node's modifier chain declares, or `null` when the modifier installs the
         * component through `Container.add` instead of a host's own method.
         *
         * The modifier sets it before the applier attaches the component. It says where the composition
         * wants the component. [installedSlot] says where the applier put it.
         */
        internal var declaredSlot: DeclaredSlot? = null

        /**
         * The region the component is actually installed in, or `null` when the node fills no region,
         * which is every node its parent adds by index.
         *
         * Only the applier writes it.
         */
        internal var installedSlot: InstalledSlot? = null

        /**
         * Records [attachment] and [uninstall] as what installed the component where it is now.
         *
         * The region recorded is the declared one, because an install always fills the region that was
         * declared.
         */
        internal fun installedThrough(
            attachment: SlotAttachment,
            uninstall: () -> Unit,
        ) {
            installedSlot = InstalledSlot(attachment, declaredSlot?.name, uninstall)
        }

        /** Frees the region the component fills, if it fills one. Safe to call twice. */
        internal fun releaseInstalledSlot() {
            installedSlot?.uninstall?.invoke()
            installedSlot = null
        }

        /**
         * The children the applier holds for this component, in composition order.
         *
         * A move reads each moved child's [ParentDeclaration.parentData] from here, because Swing does not
         * give a constraint back after `remove`. A removal from a host that holds its children in regions of
         * its own also reads them from here, and runs each one's [InstalledSlot.uninstall] to release the
         * region.
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
         * Whether [onDeactivate] has run on this node.
         *
         * A parked node stays in the host's [children] until the composition removes it for good - the
         * runtime keeps a deactivated group's place rather than dropping it, since it is what a later
         * reactivation would resume were this node type reusable - so it goes on standing there with its
         * component already detached and its region, if it filled one, already released. Only the
         * applier's own `remove`/`move` calls ever take such a holder out of a host's children.
         *
         * Only [onDeactivate] writes it.
         */
        internal var deactivated: Boolean = false

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
         * What this node's composition owns and every node under it shares - see [SwingCompositionOwner],
         * which states when a node is attached to it and why it has to be then.
         *
         * It is the same owner for the node's whole life, and `null` only on a node not yet inserted
         * into a composition.
         */
        internal var owner: SwingCompositionOwner? = null
            private set

        /**
         * Attaches this node to the composition [owner] stands for, and answers it.
         *
         * An applier attaches a node to the composition its parent stands in as it inserts it, so the
         * owner travels the node tree. The root is attached by the composition itself, being the one node
         * no parent hands an owner down to.
         */
        internal fun attachedTo(owner: SwingCompositionOwner?): SwingNodeHolder<T> = also { it.owner = owner }

        /**
         * The settle this node's update handed over to run against its children, or `null` for a node
         * that declares none. See [SwingNodeUpdater.reconcileWithChildren].
         *
         * It outlives the pass that handed it over, because the pass that changes this node's children is
         * not always a pass that recomposes the node: a strip that grows behind an `if` in the content
         * would otherwise leave the node with nothing to settle its standing declaration against. What
         * the block captured stays current, since a node is recomposed whenever anything it captures
         * moves.
         */
        internal var childSettle: (() -> Unit)? = null

        /**
         * Puts the node back to the state a new node starts from.
         *
         * It removes the published node a tool reads, detaches the listeners the modifier chain installed,
         * restores the properties the modifier changed, drops the settle held against this node's children,
         * and drops the component's tracked reads from the owner's observer. A settle left standing would be
         * run against a declaration the composition no longer makes; an update that still declares one hands
         * it over again on the pass that follows. The detach covers every modifier-installed listener,
         * including the built-in domain listener of the component.
         *
         * It does not change where the component lives: [ParentDeclaration.parentData], [declaredSlot]
         * and [childPlacement] all survive. With [resetNodes], every node of the modifier is reset before any
         * detaches.
         */
        private fun reset(resetNodes: Boolean) {
            clearPublishedNode()
            owner?.snapshotObserver?.clear(component)
            resetModifierState(resetNodes)
            childSettle = null
        }

        /** The node is leaving the composition for good. */
        override fun onRelease() {
            reset(resetNodes = false)
            // The applier frees a region when it removes or moves a node. Whole-subtree disposal goes
            // through neither path: SwingApplier.onClear() drops the root subtree with
            // Container.removeAll() and clears the root's child list, releasing no region on the way.
            // A node installed in a region is therefore still installed when the runtime releases it.
            // The call is unguarded because both the installed and the uninstalled state are
            // legitimate: an ordinary remove or move has already freed the region.
            releaseInstalledSlot()
            release()
        }

        /**
         * The runtime is reusing this holder in place, for content that stayed in the same slot without
         * ever being parked. An `update` follows, and re-applies the whole modifier chain from the clean
         * baseline this leaves.
         *
         * The node type this holder backs is not reusable, so the runtime never reactivates a parked
         * node through this callback: a parked node is released for good, and the content that reactivates
         * it gets a fresh node built by a fresh call to `factory`.
         */
        override fun onReuse() {
            reset(resetNodes = true)
        }

        /**
         * The node was parked: it moved into a parked `movableContent` holder, or the reusable content
         * around it went inactive.
         *
         * A parked node is never driven again - the runtime releases it and the content that reactivates
         * inserts a fresh node in its place - so nothing here is kept for a later pass to restore. The
         * component is removed from its Swing parent, and the region it filled, if any, is released.
         */
        override fun onDeactivate() {
            deactivated = true
            reset(resetNodes = true)
            // No batch runs here, so the parent repaints the area the component leaves itself, once it has
            // revalidated: `Container.remove` only invalidates. Both are read before the region is released,
            // which can take the component out of its parent.
            val parent = component.parent
            val area = component.bounds
            releaseInstalledSlot()
            release()
            if (parent != null) {
                parent.remove(component)
                parent.revalidate()
                parent.repaint(area.x, area.y, area.width, area.height)
            }
        }

        /** Runs this terminal node's teardown once, whether removal or parking ends its lifetime. */
        private fun release() {
            releaseBlock?.invoke()
            releaseBlock = null
        }
    }

/**
 * Publishes this node on its own component, if [isDebugInspectorInfoEnabled] is on and the component is
 * a [JComponent]. Called by the applier's `insertTopDown`, on the node's way into the composition - not
 * from [SwingNodeHolder.attachedTo], whose caller for the composition's root node never runs it through
 * the applier, so anything published there would never be cleared.
 */
internal fun SwingNodeHolder<*>.publishForInspection() {
    if (!isDebugInspectorInfoEnabled) return
    val host = component as? JComponent ?: return
    host[NODE_KEY] = this
}

/**
 * Removes the node this node published, leaving another node's standing.
 *
 * Two nodes can hold one component - a `factory` that hands back the same instance - and the applier
 * publishes the node coming in before the runtime releases the one going out, so what a released node
 * finds may be the live node's.
 */
private fun SwingNodeHolder<*>.clearPublishedNode() {
    val host = component as? JComponent ?: return
    if (host[NODE_KEY] === this) host[NODE_KEY] = null
}

/**
 * Whether the applier has attached this child to its host and not parked it since. A child the pass has
 * taken in but not attached yet was never attached, and a parked one is not attached any more - the
 * runtime keeps its place in the composition, so it goes on standing in [SwingNodeHolder.children] with
 * its component already detached.
 *
 * It says nothing about where the component stands now: a look and feel moves one out of the container
 * it was declared in - a tool bar dragged into a window of its own - without the composition hearing of
 * it.
 */
internal val SwingNodeHolder<*>.attachedToHost: Boolean
    get() = !awaitingAttachment && !deactivated

/**
 * The place among this host's attached children that the child composed at [index] takes: the siblings
 * ahead of it that are attached already. A host mid-pass holds every child the composition put here,
 * including any it has yet to attach or has parked, so the position handed to a host is counted rather
 * than composed.
 */
internal fun SwingNodeHolder<*>.attachedSiblingsBefore(index: Int): Int {
    var attached = 0
    children.fastForEach(0 until index) { if (it.attachedToHost) attached++ }
    return attached
}

/**
 * Takes the run of [count] children at [index] out of this host's composition-order child list, handing
 * each of them to [release] before the list loses it.
 */
internal inline fun SwingNodeHolder<*>.removeChildRun(
    index: Int,
    count: Int,
    crossinline release: (SwingNodeHolder<*>) -> Unit,
) {
    children.fastForEach(index until index + count) { release(it) }
    children.subList(index, index + count).clear()
}

/**
 * Moves the run of [count] children at [from] to [to] in this host's composition-order child list, hands
 * each moved child to [detach], and then hands back the ones attached to this host with the index each
 * of them stands at, for the caller to turn into the place its host reads.
 *
 * A child the pass has yet to attach is passed over: it stands nowhere to be placed, and takes the
 * position the list gives it once the pass has settled.
 */
internal inline fun SwingNodeHolder<*>.moveChildRun(
    from: Int,
    to: Int,
    count: Int,
    crossinline detach: (child: SwingNodeHolder<*>) -> Unit,
    crossinline place: (child: SwingNodeHolder<*>, index: Int) -> Unit,
) {
    val target = moveChildRun(from, to, count)
    // The whole run leaves its host before any of it goes back: a place addresses the position among the
    // children that stay, and a run still standing where it was would push each of them one along.
    children.fastForEach(target until target + count) { detach(it) }
    children.fastForEachIndexed(target until target + count) { index, child ->
        if (child.attachedToHost) place(child, index)
    }
}

/**
 * Moves the run of [count] children at [from] to [to] in this host's composition-order child list, and
 * returns the index the run stands at once it has moved: taking a run out at [from] shifts the positions
 * above it down by [count], which is the index space [to] is given in.
 *
 * The run and the children it travels past swap places, so the move is a rotation of the span between
 * the two positions, and no child leaves the list.
 */
internal fun SwingNodeHolder<*>.moveChildRun(
    from: Int,
    to: Int,
    count: Int,
): Int {
    val target = if (from > to) to else to - count
    Collections.rotate(
        children.subList(minOf(from, target), maxOf(from + count, to)),
        if (from > to) count else -count,
    )
    return target
}
