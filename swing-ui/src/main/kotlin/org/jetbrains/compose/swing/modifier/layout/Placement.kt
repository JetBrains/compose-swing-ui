@file:JvmMultifileClass
@file:JvmName("LayoutModifierKt")

package org.jetbrains.compose.swing.modifier.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SlotAttachment
import java.awt.Component

/**
 * Places the component in its parent container under [constraint] - the value
 * `Container.add(Component, Object)` takes: a `BorderLayout` region name, a `GridBagConstraints`, a
 * `CardLayout` card name, or whatever the enclosing container's layout manager understands.
 *
 * The placement follows the value: change it and the component moves within the same parent, keeping its
 * position among its siblings. It reaches the node whose chain declares it and travels no further, so a
 * container placed this way lays its own children out under the constraints each of them declares. The
 * last constraint declared in a chain wins, and a chain declaring none leaves the component placed by
 * index alone.
 *
 * The placement is re-applied whenever the declared value does not compare equal to the one applied last,
 * so a constraint compared by identity - as a raw `GridBagConstraints` is - re-registers the component on
 * every pass that rebuilds it. A constraint compared by value holds still when rebuilt from the same
 * declaration, which is what the scope builders -
 * [org.jetbrains.compose.swing.components.layout.GridBagPanelScope.item] among them - supply.
 *
 * The value is handed over the way `Container.add(Component, Object)` hands it over: a `LayoutManager2`
 * receives it as-is and reports a value it does not understand with its own exception, while a manager
 * that takes no constraints places the component by index.
 *
 * @param constraint the placement the parent container's layout manager registers the component under.
 * @return this chain with the placement declared on it.
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
 * [org.jetbrains.compose.swing.node.ChildPlacement] on its own node, and every child composed under it
 * names a region: a chain declaring none is refused there, and a chain declaring one is refused under a
 * host that adds its children by index. The last region declared in a chain wins, and a chain declaring
 * a region as well as a [layoutConstraint] is refused, since a parent holds a child by one of the two.
 *
 * The placement follows the region named: a chain naming another region moves the component there,
 * released from the region it fills through the [SlotAttachment] that filled it and installed through the
 * one named now, and a chain that stops naming a region releases the one its component fills. The move
 * lands once the change pass that declared it has settled, so a pass that swaps what two regions hold
 * leaves each component in the region its own chain names, whichever order the two declarations reach the
 * host in. The [attachment] is how the named region is filled rather than which region that is: one
 * carrying a declaration the host writes onto the region - a tab's title - re-declares that region's
 * contents without moving the component out of it. A node the host moves among its siblings keeps the
 * region it fills.
 *
 * @param name which region of the host this fills, written exactly as the call that fills it -
 *   `"SwingModifier.viewport()"`, `"SwingModifier.corner(UPPER_LEFT)"`. It identifies the region among
 *   the host's own, and it is what an error about that region prints, so a caller acts on that text by
 *   typing it.
 * @param attachment installs the component into the host and returns its uninstall action.
 * @return this chain with the region declared on it.
 */
public fun SwingModifier.slot(
    name: String,
    attachment: SlotAttachment,
): SwingModifier = this then SlotElement(name, attachment)

/**
 * A chain element declaring where the node is attached in its parent, rather than a property of the
 * component. [org.jetbrains.compose.swing.modifier.applyModifier] takes it off the chain and writes it
 * onto the node holder before the element diff runs, so it never reaches a [SwingModifier.Node] and its
 * [create] and [update] are unreachable.
 *
 * A placement is keyed like any other property element, so the chain walk resolves last-wins for it and a
 * constraint and a slot occupy separate slots. Each subtype keeps the default key of its own class, which
 * is how [removeLayoutConstraint] and [removeSlot] find it among the elements the walk collected.
 */
internal sealed class PlacementElement : SwingModifier.NodeElement<Component, SwingModifier.Node<Component>>() {
    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): Nothing = placementHasNoNode()

    override fun update(node: SwingModifier.Node<Component>): Nothing = placementHasNoNode()
}

/** The layout constraint a node's parent container registers its component under. */
internal data class LayoutConstraintElement(
    val constraint: Any,
) : PlacementElement()

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
 * itself is moved only where the [name] changes, since that is what names one region of a host among
 * the others.
 */
internal class SlotElement(
    val name: String,
    val attachment: SlotAttachment,
) : PlacementElement() {
    override fun equals(other: Any?): Boolean =
        other is SlotElement && name == other.name && attachment === other.attachment

    override fun hashCode(): Int = 31 * name.hashCode() + System.identityHashCode(attachment)
}

/**
 * Takes the layout constraint this partitioned chain declares off it, leaving behind only the elements
 * the diff has nodes for. `null` where the chain declares none, which is what puts a node that has given
 * up its constraint back to placement by index.
 */
internal fun MutableMap<Any, SwingModifier.NodeElement<*, *>>.removeLayoutConstraint(): Any? =
    (remove(LayoutConstraintElement::class.java) as? LayoutConstraintElement)?.constraint

/**
 * Takes the host slot this partitioned chain declares off it, the way [removeLayoutConstraint] does,
 * naming the region as well as the attachment that fills it.
 */
internal fun MutableMap<Any, SwingModifier.NodeElement<*, *>>.removeSlot(): SlotElement? =
    remove(SlotElement::class.java) as? SlotElement

/**
 * Refuses a chain declaring both kinds of placement, before either is written onto the node. A parent
 * holds a child either under a constraint its layout manager registers the component by, or in a region
 * of its own reached through a setter written for that region, and the two are what different containers
 * offer: a chain declaring one of each names a place in a parent that holds children the other way.
 */
internal fun checkOnePlacement(
    slot: SlotElement?,
    constraint: Any?,
) {
    if (slot == null || constraint == null) return
    error(
        "A parent holds a child either under a layout constraint its layout manager registers the " +
            "component by, or in a region of its own reached through a setter written for that region, " +
            "and this chain declares both: layoutConstraint($constraint) and ${slot.name}. Declare the " +
            "one the enclosing container holds its children by, and drop the other.",
    )
}

private fun placementHasNoNode(): Nothing =
    error(
        "A placement is consumed by the node holder, which takes it off the chain and writes it onto the " +
            "node before the element diff runs, so it never becomes a modifier node.",
    )
