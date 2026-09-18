package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.core.beginSection
import java.awt.Container
import java.awt.Rectangle
import java.util.IdentityHashMap

/**
 * The bookkeeping an applier keeps for one batch of component updates: the section naming the batch, the
 * containers the batch changed, and the nodes whose settle has to wait until their children are in
 * place.
 *
 * An applier holds one of these for its whole life and drives it from `onBeginChanges`/`onEndChanges`.
 * The section opens before the runtime drives the first change and closes once the widgets are up to date,
 * so the node update blocks the runtime runs in between - where a widget is written and read back - are
 * reported inside the batch they belong to rather than beside it.
 *
 * A node whose own update reads its children settles at the end of the batch instead of inside that
 * update - see [SwingNodeUpdater.settleWithChildren]. Those settles run once the last child of the
 * batch is attached, so each reads the children the composition declares now.
 */
internal class ComponentUpdateBatch {
    /** The section covering the batch in flight. `null` while no batch is running. */
    private var section: AutoCloseable? = null

    /**
     * The containers this batch changed, each against the union of the areas [markChanged] named for it.
     * Revalidated and repainted once when the batch ends; a container no area was named for holds an empty
     * union and repaints nothing.
     */
    private val changedContainers: MutableMap<Container, Rectangle> = IdentityHashMap()

    /** The nodes still to be settled against their children, in the order the batch held them. */
    private val heldForChildSettle: MutableList<SwingNodeHolder<*>> = ArrayList()

    /**
     * Opens the section, first discarding what an earlier batch left behind. Called from the applier's
     * `onBeginChanges`.
     *
     * A change pass that fails partway through never reaches [end], because the runtime tells the applier
     * changes ended only where the pass ran to completion. Closing the stale section keeps that failed pass
     * from enclosing every pass that follows it on the same thread. Dropping the containers it marked and the
     * settles it held keeps the next batch from refreshing or settling anything the failed pass never
     * finished touching.
     */
    fun begin() {
        changedContainers.clear()
        heldForChildSettle.clear()
        section?.close()
        section = beginSection("apply")
    }

    /**
     * Records that this batch changed [container], so it is revalidated when the batch ends.
     *
     * [area] names the region of [container] the change repaints, in [container]'s own coordinates, and a
     * container named more than once repaints the union of every area named for it. Pass a child's bounds
     * read just before it leaves, and just after it arrives with bounds it already had: `Container.remove`
     * and `Container.add` only invalidate, and the relayout that follows repaints only the children whose
     * bounds change. An absent or empty area repaints nothing.
     */
    fun markChanged(
        container: Container,
        area: Rectangle? = null,
    ) {
        // A negative size makes the union empty until the first area is added, which it then becomes.
        val union = changedContainers.getOrPut(container) { Rectangle(0, 0, -1, -1) }
        if (area != null && !area.isEmpty) union.add(area)
    }

    /**
     * Holds [node] to be settled against its children once this batch has brought them into place.
     * Holding a node twice settles it once, and a node that declares no such settle is not held at all.
     *
     * The applier calls this for every host whose children the batch changed, and the node's own update
     * calls it for the batch that hands the settle over.
     */
    fun holdForChildSettle(node: SwingNodeHolder<*>) {
        if (node.childSettle == null || node in heldForChildSettle) return
        heldForChildSettle += node
    }

    /**
     * Runs [bringWidgetsUpToDate], brings the containers this batch changed up to date, then runs every
     * settle this batch held, and closes the section whichever way that goes - a batch that ends by
     * throwing still leaves no section open.
     *
     * Called from the applier's `onEndChanges`, which is the only place a batch ends.
     */
    fun end(bringWidgetsUpToDate: () -> Unit) {
        try {
            bringWidgetsUpToDate()
            refreshChangedContainers()
            runHeldChildSettles()
        } finally {
            heldForChildSettle.clear()
            changedContainers.clear()
            section?.close()
            section = null
        }
    }

    /**
     * Revalidates every container this batch changed, since `Container.add` and `Container.remove` only
     * invalidate, then repaints the union of the areas [markChanged] named for it.
     *
     * The map is cleared by [end], whichever way this goes. The walk is entered only where the batch
     * changed a container: a batch that just wrote widget properties takes no iterator over an empty map.
     */
    private fun refreshChangedContainers() {
        if (changedContainers.isEmpty()) return
        for ((container, area) in changedContainers) {
            container.revalidate()
            if (!area.isEmpty) container.repaint(area.x, area.y, area.width, area.height)
        }
    }

    /**
     * Settles every node this batch held, after its last child is attached.
     *
     * A settle writes a widget property, and a Swing setter invalidates and repaints what it changed, so
     * it needs no place in the revalidate walk a batch ends with. The walk here is safe to make directly:
     * a settle writes a widget property and cannot re-enter the applier, so nothing it runs adds to the
     * list being walked.
     */
    private fun runHeldChildSettles() {
        if (heldForChildSettle.isEmpty()) return
        for (node in heldForChildSettle) node.childSettle?.invoke()
    }
}
