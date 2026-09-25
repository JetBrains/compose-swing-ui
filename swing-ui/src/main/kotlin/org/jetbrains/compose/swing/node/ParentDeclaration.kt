package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.layout.MeasurementLayoutManager
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
import org.jetbrains.compose.swing.modifier.layout.SlotElement
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
 */
internal class ParentDeclaration(
    private val node: SwingNodeHolder<*>,
) {
    private val component: Component get() = node.component

    /** The slots of the node's modifier, among which the layout nodes stand. */
    private val chain: List<NodeRecord<*, *>> get() = node.modifierState?.chain.orEmpty()

    /**
     * The container the applier attached the component to, or `null` before it has attached it.
     *
     * This is where the composition put the component, which is not always where it stands: a look
     * and feel moves one of its own accord, as `BasicToolBarUI` does for a tool bar the user drags out.
     */
    private var host: Container? = null

    /** The data the retained parent-data declarations fold to, or `null` for indexed placement. */
    var parentData: Any? = null
        private set

    /** The family that produced [parentData], or `null` where no parent-data declaration stands. */
    var parentProtocol: ParentProtocol? = null
        private set

    /**
     * The declarations that a measuring parent interprets after parent-data declarations have folded, in
     * modifier declaration order, with each [ParentLayoutNodeElement] among them replaced by its node. Empty where
     * the modifier declares none. Kept whole under any parent; [layoutElementsUnder] leaves out what a parent refuses.
     */
    var parentLayoutElements: List<ParentLayoutElement> = emptyList()
        private set

    /**
     * Publishes parent data and resolved layout nodes together when the declaration changes or a node read by the
     * measuring parent is updated. After a failed node update, only the remaining attached nodes are included.
     * Conventional Swing parents receive parent data through `LayoutManager2` when that data changes.
     */
    fun applyComponentLayout(
        parentData: Any?,
        parentProtocol: ParentProtocol?,
        parentLayoutElements: List<ParentLayoutElement>,
        layoutChange: Set<ModifierChange> = emptySet(),
    ) {
        val unchanged =
            ModifierChange.NodesAddedOrRemoved !in layoutChange && ModifierChange.ParentLayoutUpdated !in layoutChange
        if (unchanged && isApplied(parentData, parentProtocol, parentLayoutElements)) return
        checkParentAccepts(parentProtocol, parentLayoutElements)
        val parentDataChanged = parentData != this.parentData || parentProtocol !== this.parentProtocol
        val parent = measuringParent
        val received = parent?.let { layoutElementsUnder(it) }
        this.parentData = parentData
        this.parentProtocol = parentProtocol
        this.parentLayoutElements = chain.withLayoutNodes(parentLayoutElements, this.parentLayoutElements)
        // Adding or removing a node changes the list; updating a node preserves its identity.
        if (parentDataChanged) {
            reapply()
        } else if (parent != null &&
            (ModifierChange.ParentLayoutUpdated in layoutChange || layoutElementsUnder(parent) != received)
        ) {
            reapply()
        }
    }

    /** Whether this node holds the declaration given, taking the layout nodes applied as the ones standing. */
    private fun isApplied(
        parentData: Any?,
        parentProtocol: ParentProtocol?,
        parentLayoutElements: List<ParentLayoutElement>,
    ): Boolean =
        parentData == this.parentData &&
            parentProtocol === this.parentProtocol &&
            parentLayoutElements.standsAs(this.parentLayoutElements)

    /**
     * Drops the layout nodes that have detached from [parentLayoutElements], and declares the elements left to a
     * measuring parent that received one of the nodes dropped. With [layOut] set the parent also lays the component
     * out anew; without it the parent is declared to and is not laid out, for a component that is being released,
     * which no pass lays out again. Called as a layout node detaches, so a parent holds no detached node while
     * another node of the modifier runs.
     */
    fun dropLayoutNodes(layOut: Boolean = true) {
        val elements = parentLayoutElements
        if (elements.fastFirstOrNull { it.isDetachedNode } == null) return
        val parent = measuringParent
        val received = parent?.let { layoutElementsUnder(it) }
        parentLayoutElements = elements.filterNot { it.isDetachedNode }
        if (parent != null && layoutElementsUnder(parent) != received) {
            if (layOut) reapply() else declareComponentLayout()
        }
    }

    /**
     * Refuses parent-data declared for another layout family and non-data declarations where the
     * composition host has no protocol for interpreting them.
     */
    private fun checkParentAccepts(
        parentProtocol: ParentProtocol?,
        elements: List<ParentLayoutElement>,
        candidateHost: Container? = this.host,
    ) {
        val resolvedHost = candidateHost ?: return
        val parentElements = elements.toMutableList()
        parentProtocol?.let { protocol ->
            parentElements += ParentDataProtocolElement(protocol)
        }
        checkParentElementsAccepted(parentElements, resolvedHost)
    }

    /**
     * Refuses a declaration whose parent-family token does not accept [host], before host mutation, unless it
     * [may be left out][mayBeLeftOut].
     */
    internal fun checkParentElementsAccepted(
        elements: List<ParentElement>,
        candidateHost: Container? = this.host,
    ) {
        if ((node.awaitingAttachment || node.deactivated) && candidateHost === this.host) return
        val resolvedHost = candidateHost ?: return
        elements.fastForEach { element ->
            val mayBeLeftOut = element is ParentLayoutElement && element.mayBeLeftOut
            check(mayBeLeftOut || element.parentProtocol.accepts(resolvedHost)) {
                wrongParentHost(resolvedHost, element)
            }
        }
    }

    /** Gives the attached component's complete layout declaration to a capable layout manager. */
    private fun declareComponentLayout() {
        val parent = component.parent ?: return
        val manager = parent.layout as? MeasurementLayoutManager ?: return
        manager.declareComponentLayout(component, parentData, layoutElementsUnder(parent))
    }

    /**
     * Records that the applier has attached this component under [host] and declares its initial layout
     * state after `Container.add` has registered the component with Swing.
     */
    fun attachedUnder(host: Container) {
        checkParentElementsAccepted(parentElements(), host)
        this.host = host
        declareComponentLayout()
    }

    /** Validates that [host] can accept this declaration before the applier mutates its Swing children. */
    fun checkAttachableUnder(host: Container) {
        checkParentElementsAccepted(parentElements(), host)
    }

    /** The complete retained declaration, including the slot that the applier will install through. */
    private fun parentElements(): List<ParentElement> =
        buildList {
            parentProtocol?.let { add(ParentDataProtocolElement(it)) }
            addAll(parentLayoutElements)
            node.declaredSlot?.let { add(SlotParentElement(it.parentProtocol, it.name)) }
        }

    /**
     * Re-applies this component's layout declaration without changing its place among siblings.
     *
     * A [MeasurementLayoutManager] keeps the component's state and receives the complete declaration
     * again. Other managers retain Swing's normal behavior: they are told that the old component left,
     * then receive the folded parent data through their `LayoutManager` or `LayoutManager2` entry point.
     */
    fun reapply() {
        val parent = component.parent?.takeIf { it === host } ?: return
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

    /**
     * Whether this component's layout is declared again to its measuring parent, which lays it out anew, after the
     * layout node [written] took another declaration: where [written] has its parent lay the component out again
     * ([ParentLayoutNode.shouldAutoInvalidate]) and the parent receives it. Where [written] is
     * [left out][isLeftOutUnder] under that parent, the list the parent receives is the one it holds. A parent that
     * does not measure receives no parent-layout element and keeps its registration.
     */
    fun parentReads(written: ParentLayoutNode): Boolean =
        written.shouldAutoInvalidate && measuringParent?.let { !written.isLeftOutUnder(it) } == true

    /** The measuring parent the applier attached this component to, or `null` where it stands under none. */
    private val measuringParent: Container?
        get() = component.parent?.takeIf { it === host && it.layout is MeasurementLayoutManager }
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

/**
 * [declared], with the k-th [ParentLayoutNodeElement] replaced by the k-th layout node this chain holds - or
 * [applied] itself where every element already resolves to it.
 */
private fun List<NodeRecord<*, *>>.withLayoutNodes(
    declared: List<ParentLayoutElement>,
    applied: List<ParentLayoutElement>,
): List<ParentLayoutElement> {
    var hasNodeElement = false
    for (index in declared.indices) {
        if (declared[index] is ParentLayoutNodeElement<*>) {
            hasNodeElement = true
            break
        }
    }
    // No node element to replace: skip the mapping below. toList() returns the shared empty list when
    // declared is empty, and a defensive copy of declared otherwise.
    val resolved =
        if (!hasNodeElement) {
            declared.toList()
        } else {
            var next = 0
            declared.mapNotNullTo(ArrayList(declared.size)) { element ->
                if (element !is ParentLayoutNodeElement<*>) return@mapNotNullTo element
                while (next < size && this[next] !is LayoutNodeRecord) next++
                getOrNull(next++)?.node as ParentLayoutNode?
            }
        }
    return if (resolved == applied) applied else resolved
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
