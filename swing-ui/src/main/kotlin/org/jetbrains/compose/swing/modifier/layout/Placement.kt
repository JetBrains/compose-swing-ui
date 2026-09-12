@file:JvmMultifileClass
@file:JvmName("LayoutModifierKt")

package org.jetbrains.compose.swing.modifier.layout

import org.jetbrains.compose.swing.layout.ParentDataModifier
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.ParentSlotElement
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Container

/**
 * The untyped parent-data protocol used by [layoutConstraint]. It accepts every [Container] by explicit
 * escape-hatch contract: callers are responsible for supplying a constraint their actual parent accepts.
 */
internal val RawParentProtocol: ParentProtocol =
    object : ParentProtocol {
        override val description: String = "an untyped layout constraint"

        override fun accepts(parent: Container): Boolean = true
    }

/**
 * Places the component in its parent container under [constraint] - the value
 * `Container.add(Component, Object)` takes: a `BorderLayout` region name, a `GridBagConstraints`, a
 * `CardLayout` card name, or whatever the enclosing container's layout manager understands.
 *
 * The placement follows the value: change it and the component moves within the same parent, keeping its
 * position among its siblings. It reaches the node whose modifier declares it and travels no further, so a
 * container placed this way lays its own children out under the constraints each of them declares. The
 * last constraint declared in a modifier chain wins, and a modifier declaring none leaves the component placed by
 * index alone.
 *
 * The placement is re-applied whenever the declared value does not compare equal to the one applied last,
 * so a constraint compared by identity - as a raw `GridBagConstraints` is - re-registers the component on
 * every pass that rebuilds it. A constraint compared by value holds still when rebuilt from the same
 * declaration, which is what the scope builders -
 * [org.jetbrains.compose.swing.components.layout.GridBagPanelScope.item] among them - supply.
 *
 * This is the untyped parent-data escape hatch: it can register any value a parent layout understands.
 * Prefer a parent layout's typed scope builder where it offers one; that rejects a hoisted declaration
 * under a different layout before Swing receives it.
 *
 * @param constraint the placement the parent container's layout manager registers the component under.
 * @return this modifier with the placement declared on it.
 * @see java.awt.Container.add
 */
public fun SwingModifier.layoutConstraint(constraint: Any): SwingModifier =
    this then LayoutConstraintElement(constraint)

/**
 * Installs the component into its parent through [attachment] - one of the host's own dedicated setters
 * rather than the generic `Container.add` (e.g. a `JScrollPane` region reached via `setViewportView`).
 * The attachment belongs to the host: a container composable wrapping such a host is what hands each of
 * its regions the attachment that installs a component there and takes it out again.
 *
 * A host that holds its children this way says so, through
 * [org.jetbrains.compose.swing.layout.ChildPlacement] on its own node, and every child composed under it
 * names a region: a modifier declaring none is refused there, and a modifier declaring one is refused under a
 * host that adds its children by index. The last region declared in a modifier chain wins, and a modifier declaring
 * a region as well as a [layoutConstraint] is refused, since a parent holds a child by one of the two.
 *
 * The placement follows the region named: a modifier naming another region moves the component there,
 * released from the region it fills through the [SlotAttachment] that filled it and installed through the
 * one named now, and a modifier that stops naming a region releases the one its component fills. The move
 * lands once the change pass that declared it has settled, so a pass that swaps what two regions hold
 * leaves each component in the region its own modifier names, whichever order the two declarations reach the
 * host in. The [attachment] is how the named region is filled rather than which region that is: one
 * carrying a declaration the host writes onto the region - a tab's title - re-declares that region's
 * contents without moving the component out of it. A node the host moves among its siblings keeps the
 * region it fills.
 *
 * @param parentProtocol the stable protocol identity for the host that owns this slot.
 * @param name which region of the host this fills, written exactly as the call that fills it -
 *   `"SwingModifier.viewport()"`, `"SwingModifier.corner(UPPER_LEFT)"`. It identifies the region among
 *   the host's own, and it is what an error about that region prints, so a caller acts on that text by
 *   typing it.
 * @param attachment installs the component into the host and returns its uninstall action.
 * @return this modifier with the region declared on it.
 */
public fun SwingModifier.slot(
    parentProtocol: ParentProtocol,
    name: String,
    attachment: SlotAttachment,
): SwingModifier = this then SlotElement(parentProtocol, name, attachment)

/** The layout constraint a caller names outright, through [layoutConstraint]. */
internal data class LayoutConstraintElement(
    val constraint: Any,
) : ParentDataModifier {
    override val parentProtocol: ParentProtocol get() = RawParentProtocol

    override val name: String get() = "layoutConstraint"

    override val declaredValues: Map<String, Any?> get() = mapOf("constraint" to constraint)

    override fun modifyParentData(parentData: Any?): Any = constraint
}

/**
 * The host slot a node's component is installed into, and the name of the region it fills.
 *
 * The [attachment] is compared by identity, so this is not a data class: it is what installs the
 * component, and a caller's implementation may carry an `equals` of its own - a function reference
 * converted to the [SlotAttachment] interface does - under which two attachments installing into
 * different hosts compare equal. The node would keep the attachment the composition replaced and
 * install into the host it has left.
 *
 * Re-applying costs the rewrite of the two fields the node records a declared region in. The component
 * itself is moved only where the [regionName] changes, since that is what names one region of a host among
 * the others.
 */
internal class SlotElement(
    override val parentProtocol: ParentProtocol,
    val regionName: String,
    val attachment: SlotAttachment,
) : ParentSlotElement {
    override val name: String get() = "slot"

    override val declaredValues: Map<String, Any?> get() = mapOf("region" to regionName)

    override fun equals(other: Any?): Boolean =
        other is SlotElement &&
            parentProtocol === other.parentProtocol &&
            regionName == other.regionName &&
            attachment === other.attachment

    override fun hashCode(): Int =
        31 * (31 * System.identityHashCode(parentProtocol) + regionName.hashCode()) +
            System.identityHashCode(attachment)
}

/**
 * Refuses a modifier declaring both kinds of placement, before either is written onto the node. A parent
 * holds a child either under a constraint its layout manager registers the component by, or in a region
 * of its own reached through a setter written for that region, and the two are what different containers
 * offer: a modifier declaring one of each names a place in a parent that holds children the other way.
 */
internal fun checkOnePlacement(
    slot: ParentSlotElement?,
    parentDeclarations: List<ParentLayoutElement>,
) {
    require(slot == null || parentDeclarations.isEmpty()) {
        val declared = parentDeclarations.joinToString { "SwingModifier.${it.name}()" }
        "A component filling a region of its host is laid out by that host's own setter rather than " +
            "measured by a layout manager, so there is nothing to measure it under the constraints " +
            "$declared asks for, and this modifier declares both that and " +
            "${(slot as? SlotElement)?.regionName}. Put the " +
            "component in a Box inside the region and declare the layout modifiers on the Box's child."
    }
}
