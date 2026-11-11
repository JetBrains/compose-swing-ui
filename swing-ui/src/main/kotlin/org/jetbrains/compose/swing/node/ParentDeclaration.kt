// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.core.runEveryStep
import org.jetbrains.compose.swing.layout.MeasurementLayoutManager
import org.jetbrains.compose.swing.layout.ParentDataModifier
import org.jetbrains.compose.swing.layout.ParentElement
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.ParentSlotElement
import org.jetbrains.compose.swing.layout.mayBeLeftOut
import org.jetbrains.compose.swing.modifier.ModifierChanges
import org.jetbrains.compose.swing.modifier.NodeRecord
import org.jetbrains.compose.swing.modifier.ParentLayoutNodeRecord
import org.jetbrains.compose.swing.modifier.ResolvedParentDeclarations
import org.jetbrains.compose.swing.modifier.layout.SlotElement
import org.jetbrains.compose.swing.modifier.resolveParentDeclarations
import org.jetbrains.compose.swing.style.StyleNode
import org.jetbrains.compose.swing.util.fastFirstOrNull
import org.jetbrains.compose.swing.util.fastForEach
import org.jetbrains.compose.swing.util.fastForEachIndexed
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
 * declare more, outermost first and ahead of the node's own, and the node's style declares the last. The host checks
 * exactly what it receives. Under a group whose declaration changed in the pass in flight, the node checks and
 * publishes only in [GroupDeclarationEffect]; until then only a withdrawal of its detached layout nodes reaches the
 * host.
 */
internal class ParentDeclaration(
    internal val node: SwingNodeHolder<*>,
) {
    internal val component: Component get() = node.component

    /** The slots of the node's modifier, among which are the layout nodes. */
    private val modifierSlots: List<NodeRecord<*, *>> get() = node.modifierState?.records.orEmpty()

    /** The host and the declaration it last received. */
    private val publication = LayoutPublication(node)

    /** What the node's own modifier declares. */
    internal var own: OwnDeclaration = OwnDeclaration.None
        private set

    /** [own] merged with its virtual ancestors at the most recent check. */
    private var merged: MergedDeclaration = own.mergedWith(emptyList())

    /** The host the applier attached the component to. */
    internal val attachedHost: Container? get() = publication.host

    /** The declarations of the groups the node is composed in now. */
    private val groups: List<ParentElement> get() = node.parent?.childGroupDeclarations.orEmpty()

    /** The current declaration before installation, or the checked one while installed. */
    private val placementDeclaration: MergedDeclaration
        get() = if (node.awaitingAttachment || node.installation == null) own.mergedWith(groups) else merged

    /** The data the retained parent-data declarations fold to, or `null` for indexed placement. */
    val parentData: Any? get() = placementDeclaration.parentData

    /** The family that produced [parentData], or `null` without a parent-data declaration. */
    val parentProtocol: ParentProtocol? get() = placementDeclaration.parentProtocol

    /**
     * The declarations that a measuring parent interprets after parent-data declarations have folded: those of the
     * enclosing groups, then the node's own in modifier declaration order, then the style's, with each
     * [ParentLayoutNodeElement] among them replaced by its node. A style declaration replaces a group's or the
     * modifier's declaration of its key. Empty where none is declared. Kept whole under any parent;
     * [layoutElementsUnder] leaves out what a parent refuses.
     */
    val parentLayoutElements: List<ParentLayoutElement>
        get() = withStyleOverrides(placementDeclaration.elements, node)

    /**
     * Checks the node's own declaration, merged with the groups it is composed in now and with its style, against the
     * host, then takes it and publishes it where the host would receive another than it last did or a node it reads
     * was updated. After a failed node update, only the remaining attached nodes are included. Conventional Swing
     * parents receive parent data through `LayoutManager2` when that data changes. Under a group whose declaration
     * changed in the pass in flight, the node takes it and only withdraws its detached layout nodes: the check and
     * the publication follow in [GroupDeclarationEffect].
     *
     * @param resolved the node's retained parent declarations and folded parent data.
     * @param parentLayoutElements the node's layout declarations or their attached layout nodes.
     * @param layoutChange the changes to the modifier's layout nodes in this update.
     */
    fun applyComponentLayout(
        resolved: ResolvedParentDeclarations,
        parentLayoutElements: List<ParentLayoutElement>,
        layoutChange: ModifierChanges = ModifierChanges.None,
    ) {
        val unchanged =
            !layoutChange.nodesAddedOrRemoved && !layoutChange.parentLayoutUpdated
        if (unchanged && own.isApplied(resolved, parentLayoutElements)) return
        val incoming =
            OwnDeclaration.resolve(
                resolved,
                parentLayoutElements,
                if (parentLayoutElements.isEmpty()) emptyList() else modifierSlots,
            )
        if (node.awaitsGroupEffect) {
            own = incoming
            withdrawLayoutNodes()?.revalidate()
            return
        }
        val parent = measuringParent?.takeUnless { node.awaitingAttachment }
        val received = parent?.let { layoutElementsUnder(it) }
        val next = checkedMerge(incoming)
        val parentDataChanged = next.parentData != merged.parentData || next.parentProtocol !== merged.parentProtocol
        own = incoming
        merged = next
        // Adding or removing a node changes the list; updating a node preserves its identity.
        if (parentDataChanged) {
            publication.publish(parentData, this.parentLayoutElements)
        } else if (parent != null) {
            if (layoutChange.parentLayoutUpdated || layoutElementsUnder(parent) != received) {
                publication.publish(parentData, this.parentLayoutElements)
            }
        }
    }

    /**
     * [incoming] merged with the groups the node is composed in now, checked against [host] where one is given.
     *
     * @param incoming the node's own declaration.
     * @param host the container receiving the declaration, or `null` to check nothing.
     */
    private fun checkedMerge(
        incoming: OwnDeclaration,
        host: Container? = publication.host?.takeIf { node.attachedToHost },
    ): MergedDeclaration {
        val next = incoming.mergedWith(groups)
        host?.let { checkedElements(next, it) }
        return next
    }

    /** Checks [next] against [host], and returns the layout elements it checked, which [host] receives. */
    private fun checkedElements(
        next: MergedDeclaration,
        host: Container,
    ): List<ParentLayoutElement> {
        val received = withStyleOverrides(next.elements, node)
        next.checkAcceptedBy(host, node.declaredSlot, received)
        return received
    }

    /** Withdraws detached nodes without merging declarations; returns the parent whose declaration changed. */
    fun withdrawLayoutNodes(): Container? {
        if (own.layoutElements.fastFirstOrNull { it.isDetachedNode } != null) own = own.withoutDetachedNodes()
        merged = merged.withoutDetachedNodes()
        return publication.withdrawDetachedNodes()
    }

    /**
     * Runs [restate], which changes the layout elements [style] declares, and returns whether it wrote a node the
     * parent receives. Publishes the declaration again, merged with the groups the node is composed in now, to a
     * measuring parent whose list changes, or where [restate] says a node the parent receives was written. A failed
     * restate still publishes the remaining attached nodes. A node awaiting attachment publishes at its
     * attachment.
     */
    fun restateStyleLayout(
        style: StyleNode,
        restate: () -> Boolean,
    ) {
        val parent = measuringParent?.takeIf { node.attachedToHost }
        var standing = false
        node.modifierState?.forEachStyle(node) { if (it === style) standing = true }
        val departingLayout = !standing && style.declaredLayout.isNotEmpty()
        val received =
            parent?.let {
                layoutElementsUnder(it, withStyleOverrides(merged.elements, node))
            }
        // A failed restate can leave a retained node mutated before it reports what was written.
        var written = true
        runEveryStep(
            { written = restate() },
            {
                val changed = departingLayout || written || parent?.let { layoutElementsUnder(it) != received } == true
                if (parent != null && changed) reapply()
            },
        )
    }

    /**
     * Records that the applier has attached this component under [host], and declares its initial layout state after
     * `Container.add` has registered the component with Swing.
     *
     * @param host the container the component was added to.
     * @param received the layout elements [checkAttachableUnder] checked against [host] and returned.
     */
    fun attachedUnder(
        host: Container,
        received: List<ParentLayoutElement>,
    ) {
        merged = own.mergedWith(groups)
        publication.attach(host, parentData, layoutElementsUnder(host, received))
    }

    /**
     * Validates that [host] can accept this declaration before the applier mutates its Swing children, and returns
     * the layout elements it checked, which [host] receives. A node that waits for [GroupDeclarationEffect] is checked
     * there and declares nothing here.
     */
    fun checkAttachableUnder(host: Container): List<ParentLayoutElement> {
        if (node.awaitsGroupEffect) return emptyList()
        val received = checkedElements(own.mergedWith(groups), host)
        node.checkLayoutElementsUnder(host, received)
        return received
    }

    /**
     * Re-applies this component's layout declaration without changing its place among siblings, merged with the
     * groups it is composed in now and checked against the host. A node awaiting attachment declares nothing, and one
     * that waits for [GroupDeclarationEffect] only withdraws its detached layout nodes.
     */
    fun reapply() {
        if (!node.attachedToHost) return
        if (node.awaitsGroupEffect) {
            withdrawLayoutNodes()?.revalidate()
            return
        }
        val next = own.mergedWith(groups)
        val received = publication.host?.let { checkedElements(next, it) }
        merged = next
        publication.publish(parentData, received ?: parentLayoutElements)
    }

    /** The measuring parent the component is still attached to, or `null` without one. */
    internal val measuringParent: Container?
        get() = publication.host?.takeIf { component.parent === it && it.layout is MeasurementLayoutManager }
}

/**
 * Hands the parent declaration the layout nodes the linear diff wrote in place: each retained element is replaced by
 * its record's, and the parent reads the nodes again where [change] says it does. A written element declaring another
 * key, additiveness, parent protocol or inheritability requires the full modifier diff to resolve and prune its slots.
 */
internal fun ParentDeclaration.declareWrittenLayoutNodes(change: ModifierChanges): Boolean {
    if (!own.refreshNodeElements()) return false
    if (change.parentLayoutUpdated) reapply()
    return true
}

/**
 * Whether the measuring parent reads [written] and the updated node requests automatic invalidation.
 *
 * @param written the layout node updated by the modifier.
 */
internal fun ParentDeclaration.parentReads(written: ParentLayoutNode): Boolean =
    written.shouldAutoInvalidate && measuringParent?.let { !written.isLeftOutUnder(it) } == true

/**
 * Refuses incoming [elements] the attached host cannot interpret, unless they [may be left out][mayBeLeftOut].
 * A node awaiting attachment is checked at its destination instead.
 */
internal fun ParentDeclaration.checkParentElementsAccepted(elements: List<ParentElement>) {
    val resolvedHost = checkedHost() ?: return
    elements.fastForEach { checkElementAccepted(it, resolvedHost) }
}

/** The host an incoming declaration is checked against now, or `null` where it is checked later. */
private fun ParentDeclaration.checkedHost(): Container? =
    attachedHost?.takeUnless { node.awaitingAttachment || node.deactivated }

/**
 * What a node's own modifier declares to its parent.
 *
 * @param declarations the parent declarations the modifier retains after key resolution.
 * @property parentData what the retained parent-data declarations fold to.
 * @property parentProtocol the family of [parentData].
 * @property layoutElements the non-data layout declarations, with each [ParentLayoutNodeElement] replaced by its
 *   node.
 * @param retainedNodes the layout records and resolution metadata retained by this declaration.
 */
internal class OwnDeclaration private constructor(
    declarations: List<ParentElement>,
    val parentData: Any?,
    val parentProtocol: ParentProtocol?,
    val layoutElements: List<ParentLayoutElement>,
    private val retainedNodes: List<RetainedLayoutDeclaration> = emptyList(),
) {
    private val mutableDeclarations = declarations.toMutableList()
    val declarations: List<ParentElement> get() = mutableDeclarations

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
    ): Boolean =
        retainedNodes.all { it.isCurrent() } &&
            resolved.parentData == parentData && resolved.parentProtocol === parentProtocol &&
            resolved.declarations == declarations && standsAs(parentLayoutElements)

    /** Whether each of [incoming] equals the layout element applied at its place, or is a node element there. */
    private fun standsAs(incoming: List<ParentLayoutElement>): Boolean {
        var stands = incoming.size == layoutElements.size
        var index = 0
        while (stands && index < incoming.size) {
            val element = incoming[index]
            val standing = layoutElements[index]
            stands = if (element is ParentLayoutNodeElement<*>) standing is ParentLayoutNode else element == standing
            index++
        }
        return stands
    }

    /** Refreshes all retained declarations together, or requests resolution before changing any of them. */
    fun refreshNodeElements(): Boolean {
        if (retainedNodes.any { !it.isCurrent() }) return false
        retainedNodes.fastForEach { mutableDeclarations[it.index] = it.record.element }
        return true
    }

    /** Removes detached layout nodes and their declarations, keeping the folded parent data. */
    fun withoutDetachedNodes(): OwnDeclaration {
        val retained = ArrayList<ParentElement>(declarations.size)
        val elements = ArrayList<ParentLayoutElement>(layoutElements.size)
        val records = retainedNodes.associateBy { it.index }
        val keptNodes = ArrayList<RetainedLayoutDeclaration>(retainedNodes.size)
        var index = 0
        declarations.fastForEachIndexed { declarationIndex, declaration ->
            if (declaration is ParentLayoutElement && declaration !is ParentDataModifier) {
                val element = layoutElements[index++]
                if (!element.isDetachedNode) {
                    records[declarationIndex]?.let { keptNodes.add(it.atIndex(retained.size)) }
                    retained.add(declaration)
                    elements.add(element)
                }
            } else {
                retained.add(declaration)
            }
        }
        return OwnDeclaration(retained, parentData, parentProtocol, elements, keptNodes)
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
            if (elements.isEmpty()) {
                return OwnDeclaration(resolved.declarations, resolved.parentData, resolved.parentProtocol, emptyList())
            }
            val records = buildList { chain.fastForEach { it.contributeParentLayout(this) } }
            val declarations = ArrayList<ParentElement>(resolved.declarations.size)
            val attachedElements = ArrayList<ParentLayoutElement>(elements.size)
            val retainedNodes = ArrayList<RetainedLayoutDeclaration>(records.size)
            var next = 0
            resolved.declarations.fastForEach { declaration ->
                if (declaration is ParentLayoutNodeElement<*> && declaration !is ParentDataModifier) {
                    val record = records.getOrNull(next++)
                    if (record != null && record.isStanding && record.isAttached) {
                        retainedNodes.add(RetainedLayoutDeclaration(record, declarations.size))
                        declarations.add(record.element)
                        attachedElements.add(record.node)
                    }
                } else {
                    declarations.add(declaration)
                    if (declaration is ParentLayoutElement && declaration !is ParentDataModifier) {
                        attachedElements.add(declaration)
                    }
                }
            }
            return OwnDeclaration(
                declarations,
                resolved.parentData,
                resolved.parentProtocol,
                attachedElements,
                retainedNodes,
            )
        }

        /** What a node whose modifier has declared nothing to its parent declares. */
        val None = OwnDeclaration(emptyList(), null, null, emptyList())
    }
}

/** A retained record's place and the resolution metadata accepted at that place. */
private class RetainedLayoutDeclaration(
    val record: ParentLayoutNodeRecord,
    val index: Int,
    private val key: Any = record.element.key,
    private val additive: Boolean = record.element.additive,
    private val protocol: ParentProtocol = record.element.parentProtocol,
    private val inheritable: Boolean = record.element.inheritable,
) {
    fun atIndex(index: Int): RetainedLayoutDeclaration =
        RetainedLayoutDeclaration(record, index, key, additive, protocol, inheritable)

    fun isCurrent(): Boolean {
        if (!record.isStanding || !record.isAttached) return false
        val current = record.element
        return current.key == key && current.additive == additive &&
            current.parentProtocol === protocol && current.inheritable == inheritable
    }
}

/**
 * A node's own declaration with the declarations of the groups it is composed in ahead of it.
 *
 * @property groupElements the groups' retained non-data layout declarations, outermost first, without those a
 *   declaration of the node's own replaces.
 * @property parentData the groups' and the node's parent data, folded together.
 * @property parentProtocol the family of [parentData].
 * @property ownElements the node's own non-data layout declarations.
 */
internal class MergedDeclaration(
    val groupElements: List<ParentLayoutElement>,
    val parentData: Any?,
    val parentProtocol: ParentProtocol?,
    val ownElements: List<ParentLayoutElement>,
) {
    /** [groupElements], then the node's own. */
    val elements: List<ParentLayoutElement> = if (groupElements.isEmpty()) ownElements else groupElements + ownElements

    /**
     * Refuses parent data, layout elements or a slot that [host] cannot interpret.
     *
     * @param host the container receiving this declaration.
     * @param slot the slot declared alongside the merged parent declaration, or `null` for indexed placement.
     * @param received the layout elements [host] receives: [elements] with the style overrides applied, so an element
     *   a style replaces is not checked.
     */
    fun checkAcceptedBy(
        host: Container,
        slot: DeclaredSlot?,
        received: List<ParentLayoutElement>,
    ) {
        val layoutHost = host.childHost
        parentProtocol?.let { protocol ->
            check(protocol.accepts(layoutHost)) { wrongParentHost(layoutHost, ParentDataProtocolElement(protocol)) }
        }
        received.fastForEach { checkElementAccepted(it, layoutHost) }
        slot?.let {
            check(it.parentProtocol.accepts(host)) {
                wrongParentHost(host, SlotParentElement(it.parentProtocol, it.name))
            }
        }
    }

    /** This declaration without its own detached layout nodes: itself where it has none. */
    fun withoutDetachedNodes(): MergedDeclaration =
        if (ownElements.fastFirstOrNull { it.isDetachedNode } == null) {
            this
        } else {
            MergedDeclaration(groupElements, parentData, parentProtocol, ownElements.filterNot { it.isDetachedNode })
        }
}

/**
 * The [parentLayoutElements][ParentDeclaration.parentLayoutElements] [host] receives: all but each
 * [inheritable][ParentLayoutElement.inheritable] one whose protocol refuses [host]. The stored list itself where
 * none is left out.
 *
 * @param host the container the elements are filtered for.
 * @param elements the layout declaration to filter for [host], defaulting to the retained declaration.
 */
internal fun ParentDeclaration.layoutElementsUnder(
    host: Container,
    elements: List<ParentLayoutElement> = parentLayoutElements,
): List<ParentLayoutElement> {
    if (elements.fastFirstOrNull { it.isLeftOutUnder(host) } == null) return elements
    return elements.filterNot { it.isLeftOutUnder(host) }.ifEmpty { emptyList() }
}

private fun withStyleOverrides(
    modifier: List<ParentLayoutElement>,
    node: SwingNodeHolder<*>,
): List<ParentLayoutElement> {
    var result = modifier
    node.modifierState?.forEachStyle(node) { style ->
        result = withStyleOverrides(result, style) { node.modifierState?.records.orEmpty() }
    }
    return result
}

/**
 * [declared] (the groups' elements outermost first, then the modifier's) without each non-additive one of a key
 * [style] declares, then the layout elements [style] declares, with its nodes.
 *
 * @param declared the groups' and the modifier's layout elements before style overrides.
 * @param style the style supplying replacement layout declarations, or `null` for no overrides.
 * @param modifierSlots reads the modifier records associating layout nodes with their declared keys; read only
 *   where [style] declares layout elements.
 */
private inline fun withStyleOverrides(
    declared: List<ParentLayoutElement>,
    style: StyleNode?,
    modifierSlots: () -> List<NodeRecord<*, *>>,
): List<ParentLayoutElement> {
    val styleDeclared = style?.declaredLayout
    val own = if (styleDeclared.isNullOrEmpty()) emptyList() else style.layoutElements
    if (own.isEmpty()) return declared
    val slots = modifierSlots()
    val kept =
        declared.filterNot { it.isReplacedBy(styleDeclared, slots) }
    return if (kept.isEmpty()) own else kept + own
}

/** Whether a non-additive declaration of this element's key stands among [styleDeclared]. */
private fun ParentLayoutElement.isReplacedBy(
    styleDeclared: List<ParentLayoutElement>?,
    modifierSlots: List<NodeRecord<*, *>>,
): Boolean {
    // A node represents the element of its slot, which carries the declared key.
    val held =
        if (this is ParentLayoutNode) {
            modifierSlots.fastFirstOrNull { it.node === this }?.element
        } else {
            this
        }
    return held is ParentLayoutElement && !held.additive &&
        styleDeclared?.fastFirstOrNull { it.key == held.key } != null
}

/** Whether this element is a layout node that is not attached. */
private val ParentLayoutElement.isDetachedNode: Boolean
    get() = this is ParentLayoutNode && !isAttached

/** Whether this element [may be left out][mayBeLeftOut] and [host]'s protocol refuses it. */
internal fun ParentLayoutElement.isLeftOutUnder(host: Container): Boolean =
    mayBeLeftOut && !parentProtocol.accepts(host)

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

private fun checkElementAccepted(
    element: ParentElement,
    resolvedHost: Container,
) {
    val mayBeLeftOut = element is ParentLayoutElement && element.mayBeLeftOut
    check(mayBeLeftOut || element.parentProtocol.accepts(resolvedHost)) {
        wrongParentHost(resolvedHost, element)
    }
}

/** The component's installed host and the layout declaration that host last received. */
private class LayoutPublication(
    private val node: SwingNodeHolder<*>,
) {
    var host: Container? = null
        private set

    private var parentData: Any? = null
    private var elements: List<ParentLayoutElement> = emptyList()

    /** Registers the initial measuring declaration after Swing has added the component. */
    fun attach(
        host: Container,
        parentData: Any?,
        elements: List<ParentLayoutElement>,
    ) {
        this.host = host
        val manager = host.layout
        if (manager is MeasurementLayoutManager) {
            manager.declareComponentLayout(node.component, parentData, elements)
            this.elements = elements
        } else {
            this.elements = emptyList()
        }
        this.parentData = parentData
    }

    /** Publishes a checked declaration while retaining the component's position. */
    fun publish(
        parentData: Any?,
        elements: List<ParentLayoutElement>,
    ) {
        val component = node.component
        val parent = component.parent?.takeIf { it === host && node.attachedToHost } ?: return
        val manager = parent.layout ?: return
        if (manager is MeasurementLayoutManager) {
            val received = elements.filterNot { it.isLeftOutUnder(parent) }
            manager.declareComponentLayout(component, parentData, received)
            this.elements = received
        } else {
            manager.removeLayoutComponent(component)
            if (manager is LayoutManager2) {
                manager.addLayoutComponent(component, parentData)
            } else {
                manager.addLayoutComponent(parentData as? String, component)
            }
            this.elements = emptyList()
        }
        this.parentData = parentData
        parent.revalidate()
    }

    /** Removes only detached nodes from the last publication and returns the affected measuring parent. */
    fun withdrawDetachedNodes(): Container? {
        if (elements.fastFirstOrNull { it.isDetachedNode } == null) return null
        elements = elements.filterNot { it.isDetachedNode }
        val parent = node.component.parent?.takeIf { it === host }
        val manager = parent?.layout as? MeasurementLayoutManager
        return if (manager != null) {
            manager.declareComponentLayout(node.component, parentData, elements)
            parent
        } else {
            null
        }
    }
}
