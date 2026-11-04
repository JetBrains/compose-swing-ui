package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.layout.MeasurementLayoutManager
import org.jetbrains.compose.swing.layout.ParentDataModifier
import org.jetbrains.compose.swing.layout.ParentElement
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.ParentSlotElement
import org.jetbrains.compose.swing.layout.mayBeLeftOut
import org.jetbrains.compose.swing.modifier.LayoutNodeRecord
import org.jetbrains.compose.swing.modifier.ModifierChange
import org.jetbrains.compose.swing.modifier.NodeRecord
import org.jetbrains.compose.swing.modifier.ResolvedParentDeclarations
import org.jetbrains.compose.swing.modifier.layout.SlotElement
import org.jetbrains.compose.swing.modifier.resolveParentDeclarations
import org.jetbrains.compose.swing.util.fastFirstOrNull
import org.jetbrains.compose.swing.util.fastForEach
import java.awt.Component
import java.awt.Container
import java.awt.LayoutManager2

/**
 * What one node declares to the layout manager of the parent that holds it.
 *
 * The folded [parentData] and [parentLayoutElements] are one declaration: a capable parent receives
 * both together, while a conventional Swing parent receives the parent data through its ordinary
 * `LayoutManager2` protocol.
 *
 * The node's own modifier declares part of it. The [VirtualSwingNode]s the node is composed in, up to its host,
 * declare the rest, outermost first and ahead of the node's own. Updates use the groups' current declarations.
 * A departing component withdraws detached layout nodes from its last published declaration.
 */
internal class ParentDeclaration(
    internal val node: SwingNodeHolder<*>,
) {
    internal val component: Component get() = node.component

    /** The node's modifier chain, including layout nodes. */
    private val chain: List<NodeRecord<*, *>> get() = node.modifierState?.chain.orEmpty()

    /**
     * The container the applier attached the component to, or `null` before it has attached it.
     *
     * A look and feel can reparent a component independently, as `BasicToolBarUI` does for a floating toolbar.
     */
    internal var host: Container? = null
        private set

    private var updatingParentDeclarations: List<ParentElement>? = null

    /** What the node's own modifier declares. */
    private var own: OwnDeclaration = OwnDeclaration.None

    /** [own] merged with its virtual ancestors at the most recent merge. */
    private var merged: MergedDeclaration = own.mergedWith(emptyList())

    /** The declaration accepted for the next attachment, until that attachment completes. */
    private var checkedPlacement: MergedDeclaration? = null

    /** The current placement declaration before installation, or the accepted declaration while installed. */
    private val placementDeclaration: MergedDeclaration
        get() =
            checkedPlacement ?: if (node.awaitingAttachment || node.installation == null) {
                own.mergedWith(node.parent?.childGroupDeclarations.orEmpty())
            } else {
                merged
            }

    /** The data the retained parent-data declarations fold to, or `null` for indexed placement. */
    val parentData: Any? get() = placementDeclaration.parentData

    /** The family that produced [parentData], or `null` without a parent-data declaration. */
    val parentProtocol: ParentProtocol? get() = placementDeclaration.parentProtocol

    /**
     * The declarations that a measuring parent interprets after parent-data declarations have folded: those of the
     * enclosing groups, then the node's own in modifier declaration order, with each [ParentLayoutNodeElement]
     * among them replaced by its node. Empty where none is declared. Kept whole under any parent;
     * [layoutElementsUnder] leaves out what a parent refuses.
     */
    val parentLayoutElements: List<ParentLayoutElement> get() = placementDeclaration.elements

    /**
     * Publishes parent data and resolved layout nodes together when the declaration changes or a node read by the
     * measuring parent is updated. After a failed node update, only the remaining attached nodes are included.
     * Conventional Swing parents receive parent data through `LayoutManager2` when that data changes.
     *
     * @param resolved the node's retained parent declarations and folded parent data.
     * @param parentLayoutElements the node's layout declarations or their attached layout nodes.
     * @param layoutChange the changes to the modifier's layout nodes in this update.
     */
    fun applyComponentLayout(
        resolved: ResolvedParentDeclarations,
        parentLayoutElements: List<ParentLayoutElement>,
        layoutChange: Set<ModifierChange> = emptySet(),
    ) {
        val unchanged =
            ModifierChange.NodesAddedOrRemoved !in layoutChange &&
                ModifierChange.ParentLayoutUpdated !in layoutChange
        if (unchanged && own.isApplied(resolved, parentLayoutElements)) return
        val incoming = OwnDeclaration.resolve(resolved, parentLayoutElements, chain)
        val next = incoming.mergedWith(node.parent?.childGroupDeclarations.orEmpty())
        val parentDataChanged = next.parentData != merged.parentData || next.parentProtocol !== merged.parentProtocol
        val parent = measuringParent?.takeUnless { node.awaitingAttachment }
        val received = parent?.let { layoutElementsUnder(it) }
        val checkedHost = host?.takeIf { node.attachedToHost }
        checkedHost?.let { next.checkAcceptedBy(it, node.declaredSlot) }
        checkedPlacement = null
        own = incoming
        merged = next
        // Adding or removing a node changes the list; updating a node preserves its identity.
        if (parentDataChanged) {
            publish()
        } else if (parent != null) {
            if (ModifierChange.ParentLayoutUpdated in layoutChange ||
                layoutElementsUnder(parent) != received
            ) {
                publish()
            }
        }
    }

    /** Computes and checks the current group merge without changing the accepted declaration. */
    fun checkGroupDeclarations(): MergedDeclaration {
        val next = own.mergedWith(node.parent?.childGroupDeclarations.orEmpty())
        if (node.attachedToHost) host?.let { next.checkAcceptedBy(it, node.declaredSlot) }
        return next
    }

    /** Stores a checked [next] declaration and returns whether it changed. */
    fun storeGroupDeclarations(next: MergedDeclaration): Boolean {
        val changed = !next.sameAs(merged)
        checkedPlacement = null
        merged = next
        return changed
    }

    /**
     * Keeps incoming parent data and current group declarations in the same layout-node update.
     *
     * @param incoming the parent declarations retained by the modifier being applied.
     */
    fun beginModifierUpdate(incoming: List<ParentElement>) {
        updatingParentDeclarations = incoming
    }

    /** Completes the modifier update, including cleanup after a failed update. */
    fun endModifierUpdate() {
        updatingParentDeclarations = null
    }

    /**
     * Removes detached layout nodes and their declarations before another modifier node runs. The measuring
     * parent receives the remaining declaration immediately. A node awaiting attachment is declared only when
     * it attaches at its destination.
     */
    fun dropLayoutNodes() {
        if (own.layoutElements.fastFirstOrNull { it.isDetachedNode } == null) return
        val parent = measuringParent?.takeIf { node.attachedToHost }
        val received = parent?.let { layoutElementsUnder(it) }
        val previous = merged
        checkedPlacement = null
        val incoming = updatingParentDeclarations
        own =
            own.withoutDetachedNodes(
                if (incoming != null &&
                    node.parent
                        ?.childGroupDeclarations
                        .orEmpty()
                        .isNotEmpty()
                ) {
                    incoming
                } else {
                    null
                },
            )
        if (!node.attachedToHost) return
        storeGroupDeclarations(checkGroupDeclarations())
        if (merged.parentData != previous.parentData || merged.parentProtocol !== previous.parentProtocol ||
            measuringParent?.let { layoutElementsUnder(it) } != received
        ) {
            publish()
        }
    }

    /**
     * Withdraws detached layout nodes from the departing component's last published declaration. Its parent is
     * laid out anew on deactivation, but not on release.
     */
    fun withdrawLayoutNodes() {
        if (own.layoutElements.fastFirstOrNull { it.isDetachedNode } != null) {
            checkedPlacement = null
            own = own.withoutDetachedNodes(null)
        }
        val elements = merged.elements
        if (elements.fastFirstOrNull { it.isDetachedNode } == null) return
        val parent = measuringParent
        val remaining = elements.filterNot { it.isDetachedNode }
        checkedPlacement = null
        merged = MergedDeclaration(emptyList(), merged.parentData, merged.parentProtocol, remaining)
        if (parent != null && elements.fastFirstOrNull { it.isDetachedNode && !it.isLeftOutUnder(parent) } != null) {
            val accepted =
                if (remaining.fastFirstOrNull { it.isLeftOutUnder(parent) } == null) {
                    remaining
                } else {
                    remaining.filterNot { it.isLeftOutUnder(parent) }
                }
            val manager = parent.layout
            if (manager is MeasurementLayoutManager) {
                manager.declareComponentLayout(component, merged.parentData, accepted)
                if (node.deactivated) parent.revalidate()
            }
        }
    }

    /**
     * Refuses incoming [elements] the attached host cannot interpret, unless they [may be left out][mayBeLeftOut].
     * A node awaiting attachment is checked at its destination instead.
     */
    internal fun checkParentElementsAccepted(elements: List<ParentElement>) {
        if (node.awaitingAttachment || node.deactivated) return
        val resolvedHost = host ?: return
        checkElementsAccepted(elements, resolvedHost)
    }

    /**
     * Records that the applier has attached this component under [host] and declares its initial layout
     * state after `Container.add` has registered the component with Swing. A declaration already accepted by
     * [checkAttachableUnder] is used unchanged.
     */
    fun attachedUnder(host: Container) {
        val next =
            checkedPlacement ?: own.mergedWith(node.parent?.childGroupDeclarations.orEmpty()).also {
                it.checkAcceptedBy(host, node.declaredSlot)
            }
        merged = next
        checkedPlacement = next
        this.host = host
        try {
            declareComponentLayout()
        } finally {
            checkedPlacement = null
        }
    }

    /** Validates that [host] can accept this declaration before the applier mutates its Swing children. */
    fun checkAttachableUnder(host: Container) {
        if (checkedPlacement != null) return
        val next = own.mergedWith(node.parent?.childGroupDeclarations.orEmpty())
        next.checkAcceptedBy(host, node.declaredSlot)
        checkedPlacement = next
        node.checkLayoutElementsUnder(host)
    }

    /**
     * Re-applies this component's layout declaration without changing its place among siblings, merged with the
     * groups it is composed in now. A merge that changes the declaration is checked against the host first. A node
     * awaiting attachment declares nothing.
     */
    fun reapply() {
        if (!node.attachedToHost) return
        storeGroupDeclarations(checkGroupDeclarations())
        publish()
    }

    /** The measuring parent the component is still attached to, or `null` without one. */
    internal val measuringParent: Container?
        get() = component.parent?.takeIf { it === host && it.layout is MeasurementLayoutManager }
}

/**
 * Whether the measuring parent reads [written] and the updated node requests automatic invalidation.
 *
 * @param written the layout node updated by the modifier.
 */
internal fun ParentDeclaration.parentReads(written: ParentLayoutNode): Boolean =
    written.shouldAutoInvalidate && measuringParent?.let { !written.isLeftOutUnder(it) } == true

/**
 * What a node's own modifier declares to its parent.
 *
 * @property declarations the parent declarations the modifier retains after key resolution.
 * @property parentData what the retained parent-data declarations fold to.
 * @property parentProtocol the family of [parentData].
 * @property layoutElements the non-data layout declarations, with each [ParentLayoutNodeElement] replaced by its
 *   node.
 */
private class OwnDeclaration(
    val declarations: List<ParentElement>,
    val parentData: Any?,
    val parentProtocol: ParentProtocol?,
    val layoutElements: List<ParentLayoutElement>,
) {
    /** This declaration as a node in no group declares it. */
    private var alone: MergedDeclaration? = null

    /**
     * Whether the retained declarations and resolved layout elements match the applied modifier.
     *
     * @param resolved the incoming retained declarations and folded parent data.
     * @param parentLayoutElements the incoming layout declarations or their attached layout nodes.
     */
    fun isApplied(
        resolved: ResolvedParentDeclarations,
        parentLayoutElements: List<ParentLayoutElement>,
    ): Boolean {
        if (resolved.parentData != parentData || resolved.parentProtocol !== parentProtocol) return false
        return resolved.declarations == declarations && parentLayoutElements.standsAs(layoutElements)
    }

    /**
     * Removes detached layout nodes, preserving folded parent data unless [incoming] replaces its declarations.
     *
     * @param incoming the incoming modifier declarations under a group, or `null` to preserve folded parent data.
     */
    fun withoutDetachedNodes(incoming: List<ParentElement>?): OwnDeclaration {
        val retained = ArrayList<ParentElement>(declarations.size)
        var index = 0
        declarations.fastForEach { declaration ->
            if (declaration is ParentLayoutElement && declaration !is ParentDataModifier) {
                if (!layoutElements[index++].isDetachedNode) retained.add(declaration)
            } else if (incoming == null || declaration !is ParentDataModifier) {
                retained.add(declaration)
            }
        }
        incoming?.fastForEach { if (it is ParentDataModifier) retained.add(it) }
        val resolved = incoming?.let { resolveParentDeclarations(retained) }
        val retainedDeclarations = resolved?.declarations ?: retained
        val elements = ArrayList<ParentLayoutElement>(layoutElements.size)
        index = 0
        declarations.fastForEach { declaration ->
            if (declaration is ParentLayoutElement && declaration !is ParentDataModifier) {
                val element = layoutElements[index++]
                if (retainedDeclarations.fastFirstOrNull { it === declaration } != null) elements.add(element)
            }
        }
        return OwnDeclaration(
            retainedDeclarations,
            if (resolved != null) resolved.parentData else parentData,
            if (resolved != null) resolved.parentProtocol else parentProtocol,
            elements,
        )
    }

    /**
     * This declaration with [groups], the declarations of the groups the node is composed in, outermost first, ahead of
     * it. Refuses parent data of two layout families.
     */
    fun mergedWith(groups: List<ParentElement>): MergedDeclaration {
        if (groups.isEmpty()) {
            return alone ?: MergedDeclaration(emptyList(), parentData, parentProtocol, layoutElements).also {
                alone = it
            }
        }
        val resolved = resolveParentDeclarations(groups + declarations)
        // The node's own declarations are retained already and come last, so resolution keeps them whole at the end.
        val fromGroups = resolved.declarations.subList(0, resolved.declarations.size - declarations.size)
        return MergedDeclaration(
            buildList {
                fromGroups.fastForEach { if (it is ParentLayoutElement && it !is ParentDataModifier) add(it) }
            },
            resolved.parentData,
            resolved.parentProtocol,
            layoutElements,
        )
    }

    companion object {
        /**
         * Resolves node-backed declarations from the attached modifier chain. Unattached nodes contribute neither
         * an element nor a key that could replace a group declaration.
         *
         * @param resolved the incoming retained declarations and folded parent data.
         * @param elements the incoming non-data layout declarations.
         * @param chain the modifier records after the layout-node update, including a partially failed update.
         */
        fun resolve(
            resolved: ResolvedParentDeclarations,
            elements: List<ParentLayoutElement>,
            chain: List<NodeRecord<*, *>>,
        ): OwnDeclaration {
            if (elements.fastFirstOrNull { it is ParentLayoutNodeElement<*> } == null) {
                return OwnDeclaration(
                    resolved.declarations,
                    resolved.parentData,
                    resolved.parentProtocol,
                    elements.toList(),
                )
            }
            val declarations = ArrayList<ParentElement>(resolved.declarations.size)
            val attachedElements = ArrayList<ParentLayoutElement>(elements.size)
            var next = 0
            resolved.declarations.fastForEach { declaration ->
                if (declaration is ParentLayoutNodeElement<*> && declaration !is ParentDataModifier) {
                    while (next < chain.size && chain[next] !is LayoutNodeRecord) next++
                    val record = chain.getOrNull(next++)
                    if (record is LayoutNodeRecord && record.node.isAttached) {
                        declarations.add(declaration)
                        attachedElements.add(record.node)
                    }
                } else {
                    declarations.add(declaration)
                    if (declaration is ParentLayoutElement && declaration !is ParentDataModifier) {
                        attachedElements.add(declaration)
                    }
                }
            }
            return OwnDeclaration(declarations, resolved.parentData, resolved.parentProtocol, attachedElements)
        }

        /** What a node whose modifier has declared nothing to its parent declares. */
        val None = OwnDeclaration(emptyList(), null, null, emptyList())
    }
}

/**
 * A node's own declaration with the declarations of the groups it is composed in ahead of it.
 *
 * @property groupElements the groups' retained non-data layout declarations, outermost first, without those a
 *   declaration of the node's own replaces.
 * @property parentData the groups' and the node's parent data, folded together.
 * @property parentProtocol the family of [parentData].
 * @param ownElements the node's own non-data layout declarations.
 */
internal class MergedDeclaration(
    val groupElements: List<ParentLayoutElement>,
    val parentData: Any?,
    val parentProtocol: ParentProtocol?,
    ownElements: List<ParentLayoutElement>,
) {
    /** [groupElements], then the node's own. */
    val elements: List<ParentLayoutElement> = if (groupElements.isEmpty()) ownElements else groupElements + ownElements

    /**
     * Refuses parent data, layout elements or a slot that [host] cannot interpret.
     *
     * @param host the container receiving this declaration.
     * @param slot the slot declared alongside the merged parent declaration, or `null` for indexed placement.
     */
    fun checkAcceptedBy(
        host: Container,
        slot: DeclaredSlot?,
    ) {
        val layoutHost = host.childHost
        parentProtocol?.let { protocol ->
            check(protocol.accepts(layoutHost)) { wrongParentHost(layoutHost, ParentDataProtocolElement(protocol)) }
        }
        checkElementsAccepted(elements, layoutHost)
        slot?.let {
            check(it.parentProtocol.accepts(host)) {
                wrongParentHost(host, SlotParentElement(it.parentProtocol, it.name))
            }
        }
    }

    /** Whether [other] declares the same parent data, protocol and layout elements. */
    fun sameAs(other: MergedDeclaration): Boolean =
        parentData == other.parentData && parentProtocol === other.parentProtocol && elements == other.elements
}

/**
 * The [parentLayoutElements][ParentDeclaration.parentLayoutElements] [host] receives: all but each
 * [inheritable][ParentLayoutElement.inheritable] one whose protocol refuses [host]. The stored list itself where
 * none is left out.
 */
internal fun ParentDeclaration.layoutElementsUnder(host: Container): List<ParentLayoutElement> {
    val elements = parentLayoutElements
    if (elements.fastFirstOrNull { it.isLeftOutUnder(host) } == null) return elements
    return elements.filterNot { it.isLeftOutUnder(host) }.ifEmpty { emptyList() }
}

/** Whether this element is a layout node that is not attached. */
private val ParentLayoutElement.isDetachedNode: Boolean
    get() = this is ParentLayoutNode && !isAttached

/** Whether this element [may be left out][mayBeLeftOut] and [host]'s protocol refuses it. */
internal fun ParentLayoutElement.isLeftOutUnder(host: Container): Boolean =
    mayBeLeftOut && !parentProtocol.accepts(host)

/** Whether each of these elements equals the one [applied] at its place, or is a node element where a node stands. */
private fun List<ParentLayoutElement>.standsAs(applied: List<ParentLayoutElement>): Boolean {
    var stands = size == applied.size
    var index = 0
    while (stands && index < size) {
        val element = this[index]
        val standing = applied[index]
        stands = if (element is ParentLayoutNodeElement<*>) standing is ParentLayoutNode else element == standing
        index++
    }
    return stands
}

/** Represents folded parent data for the shared acceptance pass. */
private class ParentDataProtocolElement(
    override val parentProtocol: ParentProtocol,
) : ParentLayoutElement

/** Represents a recorded slot for the shared acceptance pass. */
private class SlotParentElement(
    override val parentProtocol: ParentProtocol,
    val slotName: String,
) : ParentSlotElement

/** Explains why [element] cannot declare data to [host]. */
private fun wrongParentHost(
    host: Container,
    element: ParentElement,
): String {
    val slotName =
        when (element) {
            is SlotElement -> element.regionName
            is SlotParentElement -> element.slotName
            else -> null
        }
    val namePrefix = if (slotName != null) "$slotName " else ""
    val description = element.parentProtocol.description
    return "$namePrefix$description can be declared only under a compatible parent, but the actual host is " +
        host.declaredName + "."
}

private fun checkElementsAccepted(
    elements: List<ParentElement>,
    resolvedHost: Container,
) {
    elements.fastForEach { element ->
        val mayBeLeftOut = element is ParentLayoutElement && element.mayBeLeftOut
        check(mayBeLeftOut || element.parentProtocol.accepts(resolvedHost)) {
            wrongParentHost(resolvedHost, element)
        }
    }
}

/**
 * Gives the attached component's complete layout declaration to a capable layout manager. A node awaiting
 * attachment declares nothing: its attachment declares to the host it arrives at.
 */
private fun ParentDeclaration.declareComponentLayout() {
    val parent = component.parent?.takeIf { node.attachedToHost } ?: return
    val manager = parent.layout as? MeasurementLayoutManager ?: return
    manager.declareComponentLayout(component, parentData, layoutElementsUnder(parent))
}

/**
 * Declares the stored declaration again, keeping the component's place among siblings.
 *
 * A [MeasurementLayoutManager] keeps the component's state and receives the complete declaration
 * again. Other managers retain Swing's normal behavior: they are told that the old component left,
 * then receive the folded parent data through their `LayoutManager` or `LayoutManager2` entry point.
 * A node awaiting attachment declares nothing.
 */
internal fun ParentDeclaration.publish() {
    val parent = component.parent?.takeIf { it === host && node.attachedToHost } ?: return
    val manager = parent.layout ?: return
    val declaredParentData = parentData
    if (manager is MeasurementLayoutManager) {
        declareComponentLayout()
    } else {
        manager.removeLayoutComponent(component)
        if (manager is LayoutManager2) {
            manager.addLayoutComponent(component, declaredParentData)
        } else {
            manager.addLayoutComponent(declaredParentData as? String, component)
        }
    }
    parent.revalidate()
}
