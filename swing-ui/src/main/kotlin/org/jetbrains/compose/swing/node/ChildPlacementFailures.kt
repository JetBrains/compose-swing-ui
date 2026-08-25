package org.jetbrains.compose.swing.node

import java.awt.Component
import java.awt.Container
import java.lang.reflect.Modifier

// What the SwingApplier prints when a child cannot be placed where the composition puts it: the
// container, the component, and the call that fixes it, since only the composition can.

/**
 * The name an error calls this component by: the first class in its hierarchy a caller can name. The
 * library holds some components in subclasses of its own, which a caller never declares and an error
 * naming one would send them looking for a class they cannot find.
 */
private val Component.declaredName: String
    get() =
        generateSequence(javaClass as Class<*>) { it.superclass }
            .first { Modifier.isPublic(it.modifiers) }
            .simpleName

/** A child of a region-holding host that names no region: it would be held by nothing and laid out by nobody. */
internal fun childNamesNoRegion(
    host: Container,
    child: Component,
    placement: ChildPlacement,
): String {
    val calls = placement.regionCalls()
    val add = if (calls.size == 1) "Add ${calls.single()}." else "Add one of: ${calls.joinToString()}."
    return "A ${host.declaredName} holds each child in one of its own regions rather than as an " +
        "indexed child, so every child must declare which region it fills. The " +
        "${child.declaredName} declared here names none. $add"
}

/** A child naming a region of a host that has none: the container offering that region is elsewhere. */
internal fun hostHasNoRegions(
    host: Container,
    child: SwingNodeHolder<*>,
): String {
    val named = child.declaredSlot?.name ?: "the region it names"
    return "A ${host.declaredName} adds its children by index and offers no regions of its own, " +
        "but the ${child.component.declaredName} declared here fills ${child.namedRegion()}. " +
        "Declare the child without $named to have it added by index, or declare it under the container " +
        "that offers that region."
}

/** A composition emitting two top-level children into a root slot that shows one component. */
internal fun rootSlotFilledTwice(
    root: Component,
    first: Component,
    second: Component,
): String =
    "A composition mounted into a ${root.declaredName} installs every top-level component it " +
        "emits into the one root slot it is mounted through, which shows a single component there. " +
        "This composition emits two: a ${first.declaredName} and a ${second.declaredName}. " +
        "Emit one, wrapping several in a container of their own."

/** Two children in one region of a host that shows a single component per region. */
internal fun regionFilledTwice(
    host: Component,
    name: String,
    first: Component,
    second: Component,
): String =
    "A ${host.declaredName} holds one component per region, but two children declare $name: a " +
        "${first.declaredName} and a ${second.declaredName}. Declare one of them, or " +
        "give the other a region of its own."

/**
 * A builder that installs a child through one host type's own setter, reaching a component held by a
 * host of another type - a child carrying one scope's placement modifier composed under a different
 * container's host, such as a scroll pane's `corner()` under a split pane.
 */
internal fun wrongSlotHost(
    host: Container,
    hostType: Class<*>,
    builder: String,
): String =
    "$builder installs its child through a ${hostType.simpleName}'s own setter, so the component " +
        "declaring it must be a direct child of one, but the component declaring it here is held by a " +
        "'${host.declaredName}'."

/** A host holding children of both kinds, which the two Swing calls that reach them cannot both address. */
internal fun mixedChildKinds(
    host: Container,
    child: SwingNodeHolder<*>,
    fillsRegion: Boolean,
): String {
    val name = host.declaredName
    val held = if (fillsRegion) "children added by index" else "children filling regions of its own"
    val arriving = if (fillsRegion) "a child filling ${child.namedRegion()}" else "a child added by index"
    return "A $name already holds $held, so $arriving cannot join them: a node's children are one index " +
        "space, and the two kinds are reached through different Swing calls. This node states a " +
        "childPlacement it did not state when it took the children it holds. State one childPlacement " +
        "for the node's whole life, or wrap the node in key(childPlacement), so that changing it builds " +
        "a new component to hold the children of the new kind."
}

/** The calls that fill this host's regions, as a caller of the container writes them. */
private fun ChildPlacement.regionCalls(): List<String> =
    when (this) {
        ChildPlacement.Indexed -> emptyList()
        is ChildPlacement.Slots -> names
        is ChildPlacement.OrderedSlots -> listOf(name)
    }

/** The region this node's chain names, as an error refers to it. */
private fun SwingNodeHolder<*>.namedRegion(): String =
    declaredSlot?.name?.let { "the region $it" } ?: "a region of its own"
