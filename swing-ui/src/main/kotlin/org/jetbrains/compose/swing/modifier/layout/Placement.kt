@file:JvmMultifileClass
@file:JvmName("LayoutModifierKt")

package org.jetbrains.compose.swing.modifier.layout

import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SlotAttachment

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
 * The value is handed over the way `Container.add(Component, Object)` hands it over: a `LayoutManager2`
 * receives it as-is and reports a value it does not understand with its own exception, while a manager
 * that takes no constraints places the component by index.
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
 * [org.jetbrains.compose.swing.node.ChildPlacement] on its own node, and every child composed under it
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
 * @param name which region of the host this fills, written exactly as the call that fills it -
 *   `"SwingModifier.viewport()"`, `"SwingModifier.corner(UPPER_LEFT)"`. It identifies the region among
 *   the host's own, and it is what an error about that region prints, so a caller acts on that text by
 *   typing it.
 * @param attachment installs the component into the host and returns its uninstall action.
 * @return this modifier with the region declared on it.
 */
public fun SwingModifier.slot(
    name: String,
    attachment: SlotAttachment,
): SwingModifier = this then SlotElement(name, attachment)

/**
 * An entry declaring where the node is attached in its parent, rather than a property of the component.
 * The walk over a modifier takes it off for the node holder, which writes it onto the node before the
 * element diff runs, so it carries no [SwingModifier.Node] of its own.
 *
 * The walk resolves the last of each kind: a slot keeps the one declared last, and the constraint
 * elements fold together, since the parts of one constraint are declared one at a time.
 */
@InternalSwingUiApi
public interface PlacementElement :
    SwingModifier.Element,
    SwingModifier.InspectableElement

/**
 * A modifier element declaring the constraint the node's parent registers its component under. A modifier
 * declares one: the [constraint] a caller names outright, or the one a container's own scope builds from
 * what the child declares to it.
 */
@InternalSwingUiApi
public interface ConstraintElement : PlacementElement {
    /**
     * Folds what this element declares into [carried] - what the modifier has declared before it - and
     * answers the constraint standing after it. The modifier is folded in declaration order, so an element
     * that states the whole constraint replaces what came before and one that states a part adds to it.
     */
    public fun foldInto(carried: Any?): Any

    /**
     * Whether this element states the whole constraint rather than a part of it. A modifier mixing the two
     * declares a placement in a parent that holds its children the other way, and is refused.
     */
    public val statesWholeConstraint: Boolean get() = false
}

/** The layout constraint a caller names outright, through [layoutConstraint]. */
internal data class LayoutConstraintElement(
    val constraint: Any,
) : ConstraintElement {
    override val name: String get() = "layoutConstraint"

    override val declaredValues: Map<String, Any?> get() = mapOf("constraint" to constraint)

    /** The whole constraint, so the last one a modifier names is what the component is registered under. */
    override fun foldInto(carried: Any?): Any = constraint

    override val statesWholeConstraint: Boolean get() = true
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
    val regionName: String,
    val attachment: SlotAttachment,
) : PlacementElement {
    override val name: String get() = "slot"

    override val declaredValues: Map<String, Any?> get() = mapOf("region" to regionName)

    override fun equals(other: Any?): Boolean =
        other is SlotElement && regionName == other.regionName && attachment === other.attachment

    override fun hashCode(): Int = 31 * regionName.hashCode() + System.identityHashCode(attachment)
}

/**
 * Why a modifier naming a constraint outright as well as declaring one to a container's own scope is
 * refused.
 */
internal fun twoKindsOfConstraint(): String =
    "A parent registers a child under one layout constraint, and this modifier declares two kinds: one " +
        "named with layoutConstraint(), and one declared to the container's own scope - weight(), " +
        "align() or a cross-axis fill. Declare the one the enclosing container places its children by, " +
        "and drop the other."

/**
 * Refuses a modifier declaring both kinds of placement, before either is written onto the node. A parent
 * holds a child either under a constraint its layout manager registers the component by, or in a region
 * of its own reached through a setter written for that region, and the two are what different containers
 * offer: a modifier declaring one of each names a place in a parent that holds children the other way.
 */
internal fun checkOnePlacement(
    slot: SlotElement?,
    constraint: Any?,
) {
    require(slot == null || constraint == null) {
        "A parent holds a child either under a layout constraint its layout manager registers the " +
            "component by, or in a region of its own reached through a setter written for that region, " +
            "and this modifier declares both: layoutConstraint($constraint) and ${slot?.regionName}. " +
            "Declare the one the enclosing container holds its children by, and drop the other."
    }
}
