package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.layout.MeasurementLayoutManager
import org.jetbrains.compose.swing.layout.ParentElement
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.ParentSlotElement
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
     * The declarations that a measuring parent interprets after parent-data declarations have folded,
     * in modifier declaration order. Empty where the modifier declares none.
     */
    var parentLayoutElements: List<ParentLayoutElement> = emptyList()
        private set

    /**
     * Applies the complete parent-layout declaration the modifier currently makes to this node.
     *
     * A capable parent receives [parentData] and [parentLayoutElements] atomically. A conventional
     * parent is re-registered only when its folded parent data changes, as `LayoutManager2` requires.
     */
    fun applyComponentLayout(
        parentData: Any?,
        parentProtocol: ParentProtocol?,
        parentLayoutElements: List<ParentLayoutElement>,
    ) {
        if (
            parentData == this.parentData &&
            parentProtocol === this.parentProtocol &&
            parentLayoutElements == this.parentLayoutElements
        ) {
            return
        }
        checkParentAccepts(parentProtocol, parentLayoutElements)
        this.parentData = parentData
        this.parentProtocol = parentProtocol
        this.parentLayoutElements = parentLayoutElements.toList()
        reapply()
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
