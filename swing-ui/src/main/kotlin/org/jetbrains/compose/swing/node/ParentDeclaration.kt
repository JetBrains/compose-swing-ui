package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.layout.MeasurementLayoutManager
import org.jetbrains.compose.swing.layout.ParentElement
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.ParentSlotElement
import org.jetbrains.compose.swing.modifier.LayoutNodeRecord
import org.jetbrains.compose.swing.modifier.NodeRecord
import org.jetbrains.compose.swing.modifier.layout.SlotElement
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
     * the modifier declares none.
     */
    var parentLayoutElements: List<ParentLayoutElement> = emptyList()
        private set

    /**
     * Applies the complete parent-layout declaration the modifier currently makes to this node.
     *
     * A capable parent receives [parentData] and [parentLayoutElements] atomically. A conventional
     * parent is re-registered only when its folded parent data changes, as `LayoutManager2` requires.
     *
     * The k-th [ParentLayoutNodeElement] of [parentLayoutElements] is replaced by the k-th layout node standing in the
     * modifier's [chain][org.jetbrains.compose.swing.modifier.SwingModifierState.chain], so this runs once the modifier
     * diff has brought the layout nodes up to date. One the chain does not hold - after a diff that threw partway - is
     * left out. The declaration is applied again whenever [layoutNodesChanged], that is where a layout node of the
     * chain attached, detached or was written since the last call; otherwise the other elements are compared, and a
     * node element needs only a layout node in its place.
     */
    fun applyComponentLayout(
        parentData: Any?,
        parentProtocol: ParentProtocol?,
        parentLayoutElements: List<ParentLayoutElement>,
        layoutNodesChanged: Boolean = false,
    ) {
        if (!layoutNodesChanged && isApplied(parentData, parentProtocol, parentLayoutElements)) return
        checkParentAccepts(parentProtocol, parentLayoutElements)
        this.parentData = parentData
        this.parentProtocol = parentProtocol
        this.parentLayoutElements = chain.withLayoutNodes(parentLayoutElements)
        reapply()
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

    /** Drops the layout nodes a reset modifier detached. The next [applyComponentLayout] declares its own. */
    fun dropLayoutNodes() {
        if (parentLayoutElements.none { it is ParentLayoutNode }) return
        parentLayoutElements = emptyList()
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

    /** Refuses a declaration whose parent-family token does not accept [host], before host mutation. */
    internal fun checkParentElementsAccepted(
        elements: List<ParentElement>,
        candidateHost: Container? = this.host,
    ) {
        if ((node.awaitingAttachment || node.deactivated) && candidateHost === this.host) return
        val resolvedHost = candidateHost ?: return
        elements.fastForEach { element ->
            check(element.parentProtocol.accepts(resolvedHost)) {
                wrongParentHost(resolvedHost, element)
            }
        }
    }

    /** Gives the attached component's complete layout declaration to a capable layout manager. */
    private fun declareComponentLayout() {
        val manager = component.parent?.layout as? MeasurementLayoutManager ?: return
        manager.declareComponentLayout(component, parentData, parentLayoutElements)
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
}

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

/** [declared], with the k-th [ParentLayoutNodeElement] replaced by the k-th layout node this chain holds. */
private fun List<NodeRecord<*, *>>.withLayoutNodes(declared: List<ParentLayoutElement>): List<ParentLayoutElement> {
    var hasNodeElement = false
    for (index in declared.indices) {
        if (declared[index] is ParentLayoutNodeElement<*>) {
            hasNodeElement = true
            break
        }
    }
    // No node element to replace: skip the mapNotNull below. toList() returns the shared empty list when
    // declared is empty, and a defensive copy of declared otherwise.
    if (!hasNodeElement) return declared.toList()
    var next = 0
    return declared.mapNotNull { element ->
        if (element !is ParentLayoutNodeElement<*>) return@mapNotNull element
        while (next < size && this[next] !is LayoutNodeRecord) next++
        getOrNull(next++)?.node as ParentLayoutNode?
    }
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
