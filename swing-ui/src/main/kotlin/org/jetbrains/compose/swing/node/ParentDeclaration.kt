package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.core.runEveryStep
import org.jetbrains.compose.swing.layout.MeasurementLayoutManager
import org.jetbrains.compose.swing.layout.ParentElement
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.ParentSlotElement
import org.jetbrains.compose.swing.layout.mayBeLeftOut
import org.jetbrains.compose.swing.modifier.ModifierChanges
import org.jetbrains.compose.swing.modifier.NodeRecords
import org.jetbrains.compose.swing.modifier.ParentLayoutNodeType
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.SlotElement
import org.jetbrains.compose.swing.modifier.nextPositionalIndex
import org.jetbrains.compose.swing.style.StyleNode
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

    /** The family that produced [parentData], or `null` where no parent-data declaration is retained. */
    var parentProtocol: ParentProtocol? = null
        private set

    /**
     * The declarations that a measuring parent interprets after parent-data declarations have folded: the
     * modifier's, in declaration order, then the style's, with each [ParentLayoutNodeElement] among them replaced by
     * its node. A style declaration replaces a modifier declaration of its key. Empty where neither declares any.
     * Kept whole under any parent; [layoutElementsUnder] leaves out what a parent refuses.
     */
    val parentLayoutElements: List<ParentLayoutElement>
        get() = withStyleOverrides(modifierElements, node)

    /** The modifier's part of [parentLayoutElements], including the declarations the style replaces. */
    private var modifierElements: List<ParentLayoutElement> = emptyList()

    /**
     * Publishes parent data and resolved layout nodes together when the declaration changes or a node read by the
     * measuring parent is updated. After a failed node update, only the remaining attached nodes are included.
     * Conventional Swing parents receive parent data through `LayoutManager2` when that data changes.
     *
     * @param parentData the data the parent-data declarations fold to, or `null` for indexed placement.
     * @param parentProtocol the family that produced [parentData], or `null` where there is none.
     * @param parentLayoutElements the declarations a measuring parent interprets, in declaration order.
     * @param layoutChange the changes the modifier pass made to the nodes the parent reads.
     */
    fun applyComponentLayout(
        parentData: Any?,
        parentProtocol: ParentProtocol?,
        parentLayoutElements: List<ParentLayoutElement>,
        layoutChange: ModifierChanges = ModifierChanges.None,
    ) {
        val unchanged =
            !layoutChange.nodesAddedOrRemoved && !layoutChange.parentLayoutUpdated
        // Where the layout nodes did not change, this node holds the declaration given if each element is
        // the one applied at its place.
        val parentDataApplied = parentData == this.parentData && parentProtocol === this.parentProtocol
        if (unchanged && parentDataApplied && parentLayoutElements.standsAs(modifierElements)) return
        checkParentAccepts(parentProtocol, parentLayoutElements)
        val parent = measuringParent
        val received = parent?.let { layoutElementsUnder(it) }
        this.parentData = parentData
        this.parentProtocol = parentProtocol
        modifierElements =
            node.modifierState?.records.withLayoutNodes(parentLayoutElements, modifierElements)
        // Adding or removing a node changes the list; updating a node preserves its identity.
        if (!parentDataApplied) {
            reapply()
        } else if (parent != null &&
            (layoutChange.parentLayoutUpdated || layoutElementsUnder(parent) != received)
        ) {
            reapply()
        }
    }

    /**
     * Drops the layout nodes that have detached from [parentLayoutElements], and declares the elements left to a
     * measuring parent that received one of the nodes dropped. With [layOut] set the parent also lays the component
     * out anew; without it the parent is declared to and is not laid out, for a component that is being released,
     * which no pass lays out again. Called as a layout node detaches, so a parent holds no detached node while
     * another node of the modifier runs.
     */
    fun dropLayoutNodes(layOut: Boolean = true) {
        if (modifierElements.fastFirstOrNull { it.isDetachedNode } == null) return
        val parent = measuringParent
        val received = parent?.let { layoutElementsUnder(it) }
        modifierElements = modifierElements.filterNot { it.isDetachedNode }
        if (parent != null && layoutElementsUnder(parent) != received) {
            if (layOut) reapply() else declareComponentLayout()
        }
    }

    /**
     * Runs [restate], which changes the layout elements [style] declares, and returns whether it wrote a node the
     * parent receives. Declares the component again to a measuring parent whose list changes, or where [restate]
     * says a node the parent receives was written. A failed restate still publishes the remaining attached nodes.
     *
     * @param style the style whose layout declaration changes.
     * @param restate updates the declaration and returns whether a node received by the parent was written.
     */
    fun restateStyleLayout(
        style: StyleNode,
        restate: () -> Boolean,
    ) {
        val parent = measuringParent
        var standing = false
        node.modifierState?.forEachStyle(node) { if (it === style) standing = true }
        val departingLayout = !standing && style.declaredLayout.isNotEmpty()
        val received =
            parent?.let {
                layoutElementsUnder(it, withStyleOverrides(modifierElements, node))
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
     * ([SwingModifier.Node.shouldAutoInvalidate]) and the parent receives it. Where [written] is
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
        result = withStyleOverrides(result, style) { node.modifierState?.records }
    }
    return result
}

/**
 * [modifier] without each declaration of a key [style] declares, then the layout elements [style] declares, with
 * its nodes. Neither a declaration nor the style element replacing it is additive.
 *
 * @param modifier the modifier's layout elements before style overrides.
 * @param style the style supplying replacement layout declarations, or `null` for no overrides.
 * @param records reads the modifier records associating layout nodes with their declared keys; read only
 *   where [style] declares layout elements.
 */
private inline fun withStyleOverrides(
    modifier: List<ParentLayoutElement>,
    style: StyleNode?,
    records: () -> NodeRecords?,
): List<ParentLayoutElement> {
    val declared = style?.declaredLayout
    val own = if (declared.isNullOrEmpty()) emptyList() else style.layoutElements
    if (own.isEmpty()) return modifier
    val installed = records()
    val kept =
        modifier.filterNot { element ->
            // A node represents the element of its slot, which carries the declared key.
            val declaredElement =
                if (element is ParentLayoutNode) {
                    installed?.fastFirstOrNull { it.node === element }?.element
                } else {
                    element
                }
            declaredElement is ParentLayoutElement && !declaredElement.additive &&
                declared?.fastFirstOrNull { it.key == declaredElement.key } != null
        }
    return if (kept.isEmpty()) own else kept + own
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
private fun NodeRecords?.withLayoutNodes(
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
                val records = this ?: return@mapNotNullTo null
                next = records.nextPositionalIndex(next, ParentLayoutNodeType)
                if (next < records.size) ParentLayoutNodeType.typed(records[next++]).node else null
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
