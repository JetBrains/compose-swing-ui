package org.jetbrains.compose.swing.node

import androidx.compose.runtime.ComposeNodeLifecycleCallback
import androidx.compose.runtime.CompositionLocalMap
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.compose.swing.core.checkEventDispatchThread
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.layout.ParentElement
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.ParentSlotElement
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.SwingModifierState
import org.jetbrains.compose.swing.modifier.layout.SlotElement
import org.jetbrains.compose.swing.modifier.resetModifierState
import org.jetbrains.compose.swing.tooling.NODE_KEY
import org.jetbrains.compose.swing.tooling.isDebugInspectorInfoEnabled
import org.jetbrains.compose.swing.util.fastFirstOrNull
import org.jetbrains.compose.swing.util.fastForEach
import org.jetbrains.compose.swing.util.fastForEachIn
import org.jetbrains.compose.swing.util.get
import org.jetbrains.compose.swing.util.set
import java.awt.Component
import java.awt.Container
import java.awt.Rectangle
import java.util.Collections
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * The region a node's modifier chain declares. It says where the composition wants the component,
 * before the applier has attached it there.
 *
 * @property parentProtocol the protocol identity for the host that owns this region.
 * @property attachment the host's method for installing a component into the region, or `null` for a
 *   component [ExistingSwingNode] claims, which its parent already holds there
 * @property name the region's name, such as `Viewport { }` or `corner(UPPER_LEFT)`
 */
internal class DeclaredSlot(
    val parentProtocol: ParentProtocol,
    val attachment: SlotAttachment?,
    val name: String,
)

/**
 * How the applier holds a child's component in its host: what put the component there, and how it leaves.
 * The applier records one as it attaches a child and drops it as it takes the child out.
 */
@VisibleForTesting
internal sealed interface Installation {
    /**
     * The name of the region the component fills, or `null` where it has none: a component in the host's
     * index space, or one the host's content attachment installed, which no modifier named.
     */
    val region: String?

    /** Whether the component fills a region of its host rather than standing in its index space. */
    val fillsRegion: Boolean

    /**
     * Takes [component] out of [container], the container of the host it was attached to, marking in [batch] the
     * container it leaves and the area it covered there, in that container's coordinates.
     */
    fun leave(
        component: Component,
        container: Container,
        batch: ComponentUpdateBatch,
    )

    /**
     * Takes [component] out of the container it stands in as its node is parked. No batch runs then, so the
     * container it leaves revalidates and repaints the area it covered itself: `Container.remove` only
     * invalidates.
     */
    fun park(component: Component)

    /**
     * Frees what holds the component in its host once its node is released, and answers what still stands: a
     * component in its host's index space, or one its parent holds, stays until its host takes it out.
     */
    fun release(): Installation? = this

    /** Whether the composition put [component] in [host], and it stands there now. */
    fun standsByCompositionIn(
        component: Component,
        host: Container,
    ): Boolean = component.parent === host

    /** Whether the composition put [component] in a container and it stands in none now. */
    fun displaced(component: Component): Boolean = component.parent == null

    /** Added to the host's index space with `Container.add`, at the place its composed siblings give it. */
    data object Indexed : Installation {
        override val region: String? get() = null

        override val fillsRegion: Boolean get() = false

        override fun leave(
            component: Component,
            container: Container,
            batch: ComponentUpdateBatch,
        ) {
            val childHost = container.childHost
            val area = component.bounds
            childHost.remove(component)
            batch.markChanged(childHost, area)
        }

        override fun park(component: Component) {
            val childHost = component.parent?.childHost ?: return
            childHost.takeOut(component, component.bounds)
        }
    }

    /**
     * Installed into [region] through a host's own setter, which returned [uninstall] to free the region again.
     */
    class Region(
        override val region: String?,
        private val uninstall: () -> Unit,
    ) : Installation {
        override val fillsRegion: Boolean get() = true

        override fun leave(
            component: Component,
            container: Container,
            batch: ComponentUpdateBatch,
        ) {
            val parent = component.parent
            val area = parent?.let { SwingUtilities.convertRectangle(it, component.bounds, container) }
            batch.markChanged(container, area)
            uninstall()
        }

        override fun park(component: Component) {
            // Freeing a region can leave the component in a container the host no longer shows - a header
            // viewport the pane let go of - so it is taken out of the one it stood in as well.
            val parent = component.parent
            val area = component.bounds
            uninstall()
            parent?.takeOut(component, area)
        }

        override fun release(): Installation? {
            uninstall()
            return null
        }
    }

    /**
     * A component [ExistingSwingNode] claims, which its parent put where it stands, in [region] where the host
     * holds its children in regions. The composition never adds, removes or moves it.
     */
    class Claimed(
        override val region: String?,
    ) : Installation {
        override val fillsRegion: Boolean get() = region != null

        override fun leave(
            component: Component,
            container: Container,
            batch: ComponentUpdateBatch,
        ) = Unit

        override fun park(component: Component) = Unit

        override fun standsByCompositionIn(
            component: Component,
            host: Container,
        ): Boolean = false

        override fun displaced(component: Component): Boolean = false
    }
}

/**
 * The node type of [SwingApplier]. It holds a Swing [Component] and the bookkeeping that the applier
 * and the modifier chain keep for that component.
 *
 * The Compose runtime calls [onRelease], [onReuse] and [onDeactivate] on it. Components built with
 * [SwingNode] or claimed with [ExistingSwingNode] reach it through [SwingNodeUpdater].
 *
 * It is the node a group holds in the slot table, so it is what a tool walking a composition finds as a
 * group's node. [SwingComponentNode] is its public component-facing contract.
 *
 * A node is either a [CreatedNodeHolder] or an [ExistingNodeHolder], which tells the applier whether the
 * composition owns the component or only borrows it.
 */
@PublishedApi
internal sealed class SwingNodeHolder<out T : Component> :
    ComposeNodeLifecycleCallback,
    SwingComponentNode {
    abstract override val component: T

    /** What this node declares to its parent's layout manager: where it goes, and how it is measured. */
    internal val declaration: ParentDeclaration = ParentDeclaration(this)

    /**
     * Set by [SwingNode] from the user's `onRelease`, and by the applier on a node [ExistingSwingNode]
     * claims, to end its claim. Called once, when the node is released or parked.
     */
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

    override fun invalidateLayout() {
        checkEventDispatchThread()
        val currentOwner = owner ?: return
        if (deactivated) return
        currentOwner.invalidateLayout(this)
    }

    /**
     * Refuses a placement this kind of node cannot declare, before the modifier writes it onto the node.
     *
     * @param slot the region the modifier names, or `null` where it names none.
     * @param parentDeclarations the parent declarations the modifier keeps once each key has resolved.
     */
    internal abstract fun checkDeclaredPlacement(
        slot: SlotElement?,
        parentDeclarations: List<ParentElement>,
    )

    /** The refusal of this node naming no region under [host], which holds each child in the regions of [placement]. */
    internal abstract fun noRegionRefusal(
        host: Container,
        placement: ChildPlacement,
    ): String

    /**
     * Called as the applier inserts this node under [parent] for the first time, before the node's update runs.
     */
    internal abstract fun insertedUnder(parent: SwingNodeHolder<*>)

    /** Attaches this node, composed at [index], to [container], the container of [host], through [regions]. */
    internal abstract fun attachTo(
        host: SwingNodeHolder<*>,
        container: Container,
        index: Int,
        regions: ChildRegions,
    )

    /**
     * The region this node's modifier chain declares, or `null` when the modifier installs the
     * component through `Container.add` instead of a host's own method.
     *
     * The modifier sets it before the applier attaches the component. It says where the composition
     * wants the component. [installation] says where the applier put it.
     */
    internal var declaredSlot: DeclaredSlot? = null

    /**
     * How the applier holds the component in its host, or `null` while it holds it nowhere: before the node is
     * attached, and once the applier has taken it out or the runtime has parked it.
     *
     * Only the applier and the node's own lifecycle write it.
     */
    internal var installation: Installation? = null

    /**
     * The children the applier holds for this component, in composition order.
     *
     * A move reads each moved child's [ParentDeclaration.parentData] from here, because Swing does not
     * give a constraint back after `remove`. A removal also reads them from here, and takes each one out
     * through its [installation].
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
     * How this node holds its children, as declared on [SwingNode] or [ExistingSwingNode]. It is
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
     * It is null before insertion and after the node is released.
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
        try {
            clearPublishedNode()
            owner?.snapshotObserver?.clear(component)
            resetModifierState(resetNodes)
        } finally {
            owner?.cancelLayoutInvalidation(this)
            childSettle = null
        }
    }

    /**
     * The node is leaving the composition for good. The region it fills is released and its teardown runs
     * whether or not the reset throws.
     */
    override fun onRelease() {
        try {
            reset(resetNodes = false)
        } finally {
            // The applier frees a region when it removes or moves a node. Whole-subtree disposal goes
            // through neither path: SwingApplier.onClear() removes the root's composed children and
            // clears its child list, releasing no region on the way.
            // A node installed in a region is therefore still installed when the runtime releases it.
            // The call is unguarded because both the installed and the uninstalled state are
            // legitimate: an ordinary remove or move has already freed the region.
            try {
                installation = installation?.release()
            } finally {
                release(clearOwner = true)
            }
        }
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
     * component is removed from its Swing parent, and the region it filled, if any, is released, whether or
     * not the reset throws. A component [ExistingSwingNode] claims stays where its parent holds it.
     */
    override fun onDeactivate() {
        deactivated = true
        try {
            reset(resetNodes = true)
        } finally {
            try {
                val installed = installation
                installation = null
                installed?.park(component)
            } finally {
                release(clearOwner = false)
            }
        }
    }

    /** Runs this terminal node's teardown once, whether removal or parking ends its lifetime. */
    private fun release(clearOwner: Boolean) {
        try {
            releaseBlock?.invoke()
        } finally {
            owner?.cancelLayoutInvalidation(this)
            releaseBlock = null
            if (clearOwner) owner = null
        }
    }
}

/** A node whose component its own factory built; the composition owns the component. */
@PublishedApi
internal class CreatedNodeHolder<out T : Component>
    @PublishedApi
    internal constructor(
        override val component: T,
    ) : SwingNodeHolder<T>() {
        /** The component is built by the node's own factory, so there is nothing to find. */
        override fun insertedUnder(parent: SwingNodeHolder<*>) = Unit

        /** The composition puts the component into [host]: installed into the region it names, or added by index. */
        override fun attachTo(
            host: SwingNodeHolder<*>,
            container: Container,
            index: Int,
            regions: ChildRegions,
        ) = with(regions) { host.attachCreated(container, this@CreatedNodeHolder, index) }

        override fun noRegionRefusal(
            host: Container,
            placement: ChildPlacement,
        ): String =
            "A ${host.declaredName} holds each child in one of its own regions rather than as an indexed child, " +
                "so every child must declare which region it fills. The ${component.declaredName} declared here " +
                "names none. ${placement.throughRegionCalls("Name the region it fills")}"

        /** The composition installs the component into the region it names, so the region needs an attachment. */
        override fun checkDeclaredPlacement(
            slot: SlotElement?,
            parentDeclarations: List<ParentElement>,
        ) {
            if (slot == null) return
            requireNotNull(slot.attachment) {
                "A ${component.declaredName} names the region ${slot.regionName} without a SlotAttachment, so " +
                    "nothing would put it there: slot(parentProtocol, name) only names the region a component " +
                    "claimed by ExistingSwingNode already stands in. Pass the attachment that installs it: " +
                    "SwingModifier.slot(parentProtocol, name, attachment)."
            }
        }
    }

/**
 * A node whose component its claim found; the composition only borrows it. See [ExistingSwingNode].
 *
 * [claim] runs as the applier first inserts the node, on the component of the node it inserts it under,
 * before the node's update runs. It is refused when that component is not a [parentType], or when it returns
 * `null` or something that is not a [type].
 *
 * [declaringCall] is the call that declared this node, which a refusal names it by when no slot does; null for a
 * caller's own ExistingSwingNode.
 */
@PublishedApi
internal class ExistingNodeHolder<P : Component, out T : Component>
    @PublishedApi
    internal constructor(
        private val parentType: Class<P>,
        private val type: Class<out T>,
        internal val declaringCall: String?,
        private val claim: P.() -> Component?,
    ) : SwingNodeHolder<T>() {
        private lateinit var claimed: T

        override val component: T
            get() = claimed

        /**
         * The component of the node this one is composed directly under, which placed [component]. A node
         * relocated under another parent is refused.
         */
        internal lateinit var parentComponent: Component
            private set

        /** Runs the claim on the component of [parent], the node this one is first inserted under. */
        override fun insertedUnder(parent: SwingNodeHolder<*>) {
            check(!::claimed.isInitialized) {
                "An ExistingSwingNode claims its component once, when it is first inserted."
            }
            val claimedFrom = parent.component
            check(parentType.isInstance(claimedFrom)) {
                "ExistingSwingNode claims its component from a ${parentType.simpleName}, but it is composed under a " +
                    "${claimedFrom.declaredName}. Compose it directly under the ${parentType.simpleName} whose " +
                    "component it configures."
            }
            val found =
                checkNotNull(parentType.cast(claimedFrom).claim()) {
                    "ExistingSwingNode found no component: its claim returned null on the " +
                        "${claimedFrom.declaredName} it is composed under. Claim a part the " +
                        "${claimedFrom.declaredName} has for as long as this node is composed."
                }
            check(type.isInstance(found)) {
                "ExistingSwingNode claims a ${type.simpleName}, but its claim returned a ${found.declaredName} " +
                    "from the ${claimedFrom.declaredName}."
            }
            claimed = type.cast(found)
            parentComponent = claimedFrom
        }

        /** The parent already holds the component where it stands, so [host] only takes the node in. */
        override fun attachTo(
            host: SwingNodeHolder<*>,
            container: Container,
            index: Int,
            regions: ChildRegions,
        ) = with(regions) { host.takeClaimed(this@ExistingNodeHolder) }

        override fun noRegionRefusal(
            host: Container,
            placement: ChildPlacement,
        ): String =
            "A ${component.declaredName} claimed by ExistingSwingNode names no region of the ${host.declaredName}, " +
                "which holds each child in one. ${placement.throughRegionCalls("Name the region it stands in")}"

        /**
         * Naming the region the component stands in is the one placement this node declares, and it names it
         * without an attachment: the parent placed the component, so nothing installs it and no layout manager
         * would read parent data for it.
         */
        override fun checkDeclaredPlacement(
            slot: SlotElement?,
            parentDeclarations: List<ParentElement>,
        ) {
            if (slot != null) {
                require(slot.attachment == null) {
                    "A ${component.declaredName} claimed by ExistingSwingNode names the region ${slot.regionName} " +
                        "with a SlotAttachment, but its parent already holds it there, so the attachment would never " +
                        "run. Name the region without one: SwingModifier.slot(parentProtocol, name)."
                }
            }
            checkPlacedByParent(parentDeclarations)
        }

        /**
         * Refuses a parent-data or parent-layout declaration among [declarations]. An inheritable layout
         * declaration whose protocol refuses [parentComponent] is left out instead.
         */
        internal fun checkPlacedByParent(declarations: List<ParentElement>) {
            val parent = parentComponent
            val element =
                declarations.fastFirstOrNull {
                    it !is ParentSlotElement &&
                        !(it is ParentLayoutElement && parent is Container && it.isLeftOutUnder(parent))
                } ?: return
            error(
                "${declaredCall(element)} places a component in its parent, but this ExistingSwingNode configures a " +
                    "${component.declaredName} its parent ${parent.declaredName} placed. Declare placement where the " +
                    "parent builds the component.",
            )
        }

        /** The node leaves; its component stays where the parent holds it, so the content it composed leaves it. */
        override fun onRelease() {
            try {
                super.onRelease()
            } finally {
                removeComposedChildren()
            }
        }

        /**
         * Takes the components this node's content added by index out of its component, which stays where its
         * parent holds it rather than leaving with them. A child filling a region releases it itself, and a child
         * the composition relocated in the same pass stands under its new host and is left there.
         */
        private fun removeComposedChildren() {
            val host = (component as? Container)?.childHost ?: return
            var removed = false
            children.removeComposedFrom(host) {
                host.repaint(it.x, it.y, it.width, it.height)
                removed = true
            }
            if (removed) host.revalidate()
        }
    }

/**
 * [element] as the modifier call that declared it. A layout node stands in its element's place, so the call
 * named is the element's.
 */
internal fun SwingNodeHolder<*>.declaredCall(element: ParentElement): String {
    val declared = modifierState?.chain?.fastFirstOrNull { it.node === element }?.element as? ParentElement ?: element
    return "SwingModifier.${declared.name}()"
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

/** Removes [component] from this container and repaints [area], which it covered, once the container revalidates. */
private fun Container.takeOut(
    component: Component,
    area: Rectangle,
) {
    remove(component)
    revalidate()
    repaint(area.x, area.y, area.width, area.height)
}

/**
 * Takes this child's component out of [container], the container of the host it was attached to, through the
 * [installation] that put it there, marking in [batch] the container it leaves and the area it covered there.
 * A child the applier holds nowhere leaves nothing.
 */
internal fun SwingNodeHolder<*>.leaveHost(
    container: Container,
    batch: ComponentUpdateBatch,
) {
    val installed = installation ?: return
    installation = null
    installed.leave(component, container, batch)
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
 * Whether the composition put this child's component in [host] and it stands there now. A child
 * [ExistingSwingNode] claims never does, even where it stands in [host]: its parent placed it, as it places
 * a component nobody composed.
 */
internal fun SwingNodeHolder<*>.standsByCompositionIn(host: Container): Boolean =
    attachedToHost && installation?.standsByCompositionIn(component, host) == true

/**
 * Whether the composition put this child's component in a container, has not parked it, and it stands in no
 * container now. A child [ExistingSwingNode] claims never is: its parent places it.
 */
internal val SwingNodeHolder<*>.displaced: Boolean
    get() = !deactivated && installation?.displaced(component) == true

/**
 * Takes out of [host] the component of each of these children that
 * [stands in it by composition][standsByCompositionIn], handing [removed] the area each one leaves, in
 * [host]'s coordinates.
 */
internal inline fun List<SwingNodeHolder<*>>.removeComposedFrom(
    host: Container,
    removed: (area: Rectangle) -> Unit,
) {
    fastForEach { child ->
        if (child.standsByCompositionIn(host)) {
            val area = child.component.bounds
            host.remove(child.component)
            removed(area)
        }
    }
}

/**
 * The place among this host's attached children that the child composed at [index] takes: the siblings
 * ahead of it that are attached already. A host mid-pass holds every child the composition put here,
 * including any it has yet to attach or has parked, so the position handed to a host is counted rather
 * than composed.
 */
internal fun SwingNodeHolder<*>.attachedSiblingsBefore(index: Int): Int {
    var attached = 0
    children.fastForEachIn(0 until index) { if (it.attachedToHost) attached++ }
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
    children.fastForEachIn(index until index + count) { release(it) }
    children.subList(index, index + count).clear()
}

/**
 * Moves the run of [count] children at [from] to [to] in this host's composition-order child list, hands
 * each moved child to [detach], and then hands back the ones attached to this host with the index each
 * of them stands at, for the caller to turn into the place its host reads.
 *
 * A child the pass has yet to attach is not handed to [place]: it stands nowhere to be placed, and takes
 * the position the list gives it once the pass has settled.
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
    children.fastForEachIn(target until target + count) { detach(it) }
    for (index in target until target + count) {
        val child = children[index]
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
