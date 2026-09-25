package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.layout.MeasurementLayoutManager
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.util.fastFirstOrNull
import org.jetbrains.compose.swing.util.fastForEachIndexed
import java.awt.Component
import java.awt.Container
import java.lang.reflect.Modifier

/** Whether a node declaring this placement holds its children in named regions rather than by index. */
internal val ChildPlacement.holdsRegions: Boolean
    get() = this != ChildPlacement.Indexed

/**
 * Whether [child] fills a region of [host]: it names one, or [host] installs the children that name
 * none as its content. The declaration decides, not the attachment: a component [ExistingSwingNode]
 * claims names the region its parent put it in and carries no attachment.
 */
internal fun fillsRegion(
    host: SwingNodeHolder<*>,
    child: SwingNodeHolder<*>,
): Boolean = child.declaredSlot != null || host.childPlacement.contentAttachment != null

/**
 * Holds a child arriving at this host to the placement the host declares, to the constraint the host's
 * layout manager takes, and to the way the children already here were attached.
 *
 * @param host the container the child is arriving at, named in every refusal.
 * @param child the arriving node.
 */
internal fun SwingNodeHolder<*>.checkPlacementOf(
    host: Container,
    child: SwingNodeHolder<*>,
) {
    val fillsRegion = fillsRegion(this, child)
    child.declaration.checkAttachableUnder(host)
    checkChildKind(host, child, fillsRegion)
    // Every attached child here was held to this same rule as it arrived, so they agree with one another
    // and the first of them answers for them all. A holder in `children` carries the installation that put
    // it where it stands, so what it carries is how that child was reached. They can
    // disagree with the arriving child only where the host declared one placement, took children under it,
    // and then declared another. A child still awaiting attachment carries no such answer yet and speaks
    // for none of them; a host holding nothing else has no child to compare against.
    val held = children.fastFirstOrNull { it.attachedToHost } ?: return
    check((held.installation?.fillsRegion == true) == fillsRegion) {
        val kept = if (fillsRegion) "children added by index" else "children filling regions of its own"
        val arriving = if (fillsRegion) "a child filling ${child.namedRegion()}" else "a child added by index"
        "A ${host.declaredName} already holds $kept, so $arriving cannot join them: a node's children are one " +
            "index space, and the two kinds are reached through different Swing calls. This node states a " +
            "childPlacement it did not state when it took the children it holds. State one childPlacement " +
            "for the node's whole life, or wrap the node in key(childPlacement), so that changing it builds " +
            "a new component to hold the children of the new kind."
    }
}

/**
 * Holds a child to the placement this host declares: a host that holds its children in regions of its own
 * takes only children that fill one, and a host that adds them by index only children placed that way.
 *
 * Asked of a child arriving at the host and of one already here whose modifier has come to say something
 * else, which is why it answers for that child alone: the child being looked at can be one this host is
 * in the middle of moving between two of its regions, and so momentarily installed in neither.
 *
 * @param host the container the child is held by, named in every refusal.
 * @param child the node being placed.
 * @param fillsRegion whether the child is installed into a named region of the host rather than added to
 *   it by index.
 */
internal fun SwingNodeHolder<*>.checkChildKind(
    host: Container,
    child: SwingNodeHolder<*>,
    fillsRegion: Boolean,
) {
    val placement = childPlacement
    // The kind answers first: a child of the wrong kind for this host is held by nothing, and telling it
    // instead to put itself in a Box here would name a container it cannot be a child of either.
    if (placement.holdsRegions) {
        check(fillsRegion) { child.noRegionRefusal(host, placement) }
    } else {
        check(!fillsRegion) {
            val named = child.declaredSlot?.name ?: "the region it names"
            "A ${host.declaredName} adds its children by index and offers no regions of its own, " +
                "but the ${child.component.declaredName} declared here fills ${child.namedRegion()}. " +
                "Declare the child without $named to have it added by index, or declare it under the " +
                "container that offers that region."
        }
    }
    check(
        child.declaration.parentLayoutElements.fastFirstOrNull { !it.isLeftOutUnder(host) } == null ||
            host.layout is MeasurementLayoutManager,
    ) {
        // Names the host being joined, not the one being left: on a relocation this fires as the child
        // attaches, and a message naming where it came from would read as a fault of that container.
        val declared = child.declaration.layoutElementsUnder(host).joinToString { child.declaredCall(it) }
        "A ${host.declaredName} lays each child out at the size the child asks for, so it never " +
            "interprets parent-layout declarations, and the ${child.component.declaredName} joining it " +
            "declares $declared. Put the component in a Box inside this container and declare the layout " +
            "modifiers on the Box's child, which the Box interprets."
    }
}

/**
 * Holds this host to one child per region, and to one child filling its content. Called on a
 * [ChildPlacement.Slots] host once the change pass that filled its regions has been dispatched whole and
 * every child it holds is installed in the region its own modifier names, so each child is counted against
 * the region its component is really in. The children installed through the host's content attachment name
 * no region and are counted together.
 *
 * A parked child is not one of them: it gave its region up in [onDeactivate][SwingNodeHolder] and stands in
 * [SwingNodeHolder.children] only until the composition removes it for good, so it carries no installation.
 */
internal fun SwingNodeHolder<*>.checkOneChildPerRegion(
    contentFilledTwice: (Component, Component, Component) -> String = ::contentFilledTwice,
) {
    children.fastForEachIndexed { index, child ->
        val installation = child.installation
        if (installation == null || !installation.fillsRegion) return@fastForEachIndexed
        val name = installation.region
        for (earlier in 0 until index) {
            val occupant = children[earlier]
            val held = occupant.installation ?: continue
            if (held.fillsRegion && held.region == name) {
                error(
                    if (name == null) {
                        contentFilledTwice(component, occupant.component, child.component)
                    } else {
                        "A ${component.declaredName} holds one component per region, but two children declare " +
                            "$name: a ${occupant.component.declaredName} and a ${child.component.declaredName}. " +
                            "Declare one of them, or give the other a region of its own."
                    },
                )
            }
        }
    }
}

/** Two unnamed children under a host that shows one of them as its content. */
private fun contentFilledTwice(
    host: Component,
    first: Component,
    second: Component,
): String =
    "A ${host.declaredName} shows one unnamed child as its content, but two children name no region: a " +
        "${first.declaredName} and a ${second.declaredName}. Keep one, or wrap several in a container of " +
        "their own."

/** A composition emitting two top-level components into a root that shows one of them as its content. */
internal fun compositionEmitsTwo(
    root: Component,
    first: Component,
    second: Component,
): String =
    "A composition mounted into a ${root.declaredName} shows the one top-level component it emits as its " +
        "content. This composition emits two: a ${first.declaredName} and a ${second.declaredName}. Emit " +
        "one, wrapping several in a container of their own."

/** The attachment a host with this placement installs its children naming no region through, if any. */
internal val ChildPlacement.contentAttachment: SlotAttachment?
    get() =
        when (this) {
            is ChildPlacement.Slots -> content
            ChildPlacement.Indexed, is ChildPlacement.OrderedSlots -> null
        }

/** [request] followed by the calls that name this placement's regions, which it must have. */
internal fun ChildPlacement.throughRegionCalls(request: String): String {
    val calls = regionNames
    return "$request through ${if (calls.size == 1) calls.single() else "one of: ${calls.joinToString()}"}."
}

/** The regions a host with this placement offers, as the calls that name them. */
internal val ChildPlacement.regionNames: List<String>
    get() =
        when (this) {
            ChildPlacement.Indexed -> emptyList()
            is ChildPlacement.Slots -> names
            is ChildPlacement.OrderedSlots -> listOf(name)
        }

/** The region this node's modifier names, as an error refers to it. */
private fun SwingNodeHolder<*>.namedRegion(): String =
    declaredSlot?.name?.let { "the region $it" } ?: "a region of its own"

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

/**
 * The name an error calls this component by: the first class in its hierarchy a caller can name. The
 * library holds some components in subclasses of its own, which a caller never declares and an error
 * naming one would send them looking for a class they cannot find.
 */
internal val Component.declaredName: String
    get() =
        generateSequence(javaClass as Class<*>) { it.superclass }
            .first { Modifier.isPublic(it.modifiers) }
            .simpleName
