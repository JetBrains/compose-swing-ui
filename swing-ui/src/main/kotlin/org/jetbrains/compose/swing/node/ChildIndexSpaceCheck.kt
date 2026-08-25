package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import java.awt.Component
import java.awt.Container
import java.util.IdentityHashMap

/**
 * Turns on the debug-only walk that holds an applier's [SwingNodeHolder.children] index space to the
 * real Swing state it stands for, one turn after every change pass - see [checkChildIndexSpace] for
 * what it asserts.
 *
 * Off by default, where a pass pays only the cost of reading this flag.
 * [org.jetbrains.compose.swing.test.ComposeSwingTest] turns it on for the duration of a test; a
 * violation then reaches the event dispatch thread's uncaught-exception handler exactly like any other
 * failure the library raises there.
 *
 * Marked [InternalSwingUiApi]; it may change or be removed without notice in any release.
 */
@InternalSwingUiApi
public var debugValidateChildIndexSpace: Boolean = false

/**
 * Debug-only: holds this applier's whole [SwingNodeHolder.children] index space to the real Swing state
 * it stands for. See [debugValidateChildIndexSpace] for when this runs and what it costs.
 *
 * Deferred a turn, on the same one `checkOneChildPerRegion` and `checkRootShowsOneChild` are called on -
 * see `DeferredRegionCheck` for what that turn is worth. Checked mid-pass instead, this would refuse
 * states the composition itself allows while a pass is still running: a relocated child stands in its
 * new host's children before its component is attached anywhere, and a region may briefly hold two
 * children while a replacement arrives before the child it replaces leaves.
 */
internal fun SwingNodeHolder<*>.checkChildIndexSpace() {
    checkChildIndexSpace(root = this, owners = IdentityHashMap())
}

private fun SwingNodeHolder<*>.checkChildIndexSpace(
    root: SwingNodeHolder<*>,
    owners: MutableMap<SwingNodeHolder<*>, SwingNodeHolder<*>>,
) {
    for (child in children) {
        check(!child.awaitingAttachment) { childStillAwaitingAttachment(component, child.component) }
        val earlierHost = owners.put(child, this)
        check(earlierHost == null) { childHeldByTwoHosts(child.component, earlierHost?.component, component) }
    }
    val placement = childPlacement
    if (placement.holdsRegions) {
        for (child in children) {
            // A deactivated child already gave its region up in onDeactivate, and stands here only
            // until the composition removes it for good - see SwingNodeHolder.deactivated.
            if (child.deactivated) continue
            val installed = child.installedSlot
            check(installed != null && installed.name == child.declaredSlot?.name) {
                childNotInstalledWhereDeclared(component, child.component, child.declaredSlot?.name)
            }
        }
        if (placement is ChildPlacement.Slots) {
            if (this === root) checkRootShowsOneChild() else checkOneChildPerRegion()
        }
    } else {
        checkIndexedChildrenAreInReal()
    }
    for (child in children) child.checkChildIndexSpace(root, owners)
}

/**
 * Holds every live composed child of this indexed host to being attached to some container -
 * not necessarily this one, and not necessarily where the composition put it. See
 * [indexedChildMissing] for why the check stops there rather than asking whether the child is
 * really under this host.
 *
 * Membership, equality and order are deliberately left unchecked: many a Swing component's own
 * look-and-feel delegate gives it real children of its own - `JComboBox` an arrow button, `JTree` a
 * `CellRendererPane` - that no composable ever declared and the applier never attached, standing
 * alongside whatever this host's `content` composed. And a `JLayeredPane` host does not keep its real
 * children in composition order at all: it sorts them by the layer each one's chain names, so two
 * composed siblings on different layers can appear in either order among the real children regardless
 * of which was composed first.
 *
 * A deactivated child is skipped: [onDeactivate][SwingNodeHolder] already detached its component, and it
 * stands in [SwingNodeHolder.children] only until the composition removes it for good - see
 * [SwingNodeHolder.deactivated].
 *
 * So is a child standing under some other container, because a component's own look-and-feel may take it
 * out of the host the composition put it in and hold it elsewhere: a floating `JToolBar` is reparented
 * into a window the UI opens and docks back into this same host, so the applier is right to go on holding
 * it here. What is left is the case this check can answer for - the applier says a child is here and the
 * child is nowhere at all.
 */
private fun SwingNodeHolder<*>.checkIndexedChildrenAreInReal() {
    val container = component as? Container ?: return
    val lost = children.firstOrNull { !it.deactivated && it.component.parent == null } ?: return
    error(indexedChildMissing(component, lost.component, container.childHost.components.toList()))
}

/** A holder [checkChildIndexSpace] finds still marked [SwingNodeHolder.awaitingAttachment]. */
private fun childStillAwaitingAttachment(
    host: Component,
    child: Component,
): String =
    "Child index space check: a ${child.javaClass.name} is still awaiting attachment in the children of " +
        "a ${host.javaClass.name}, a turn after the change pass that relocated it ended."

/** A holder [checkChildIndexSpace] finds in two hosts' [SwingNodeHolder.children] lists at once. */
private fun childHeldByTwoHosts(
    child: Component,
    earlierHost: Component?,
    laterHost: Component,
): String =
    "Child index space check: a ${child.javaClass.name} is held in the children of two hosts at once: " +
        "a ${earlierHost?.javaClass?.name} and a ${laterHost.javaClass.name}."

/** A child of a region-holding host whose installed region does not match the one its chain declares. */
private fun childNotInstalledWhereDeclared(
    host: Component,
    child: Component,
    declaredRegion: String?,
): String =
    "Child index space check: a ${child.javaClass.name} held by a ${host.javaClass.name} declares " +
        "${declaredRegion ?: "no region"} but is not installed there."

/**
 * A composed child of an indexed host that is nowhere among the host's real children. The real children
 * are read through [childHost], the same indirection the applier goes through when it attaches a child,
 * so a `RootPaneContainer` such as `JInternalFrame` names its content pane's children rather than its own.
 */
private fun indexedChildMissing(
    host: Component,
    child: Component,
    real: List<Component>,
): String =
    "Child index space check: a ${host.javaClass.name} does not hold a ${child.javaClass.name} the " +
        "applier's children list has for it. The host's real children are " +
        "${real.map { it.javaClass.simpleName }}."
