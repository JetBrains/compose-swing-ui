package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.core.beginSection
import java.awt.Container
import java.util.Collections
import java.util.IdentityHashMap

/**
 * The bookkeeping an applier keeps for one batch of component updates: the section naming the batch and the
 * containers the batch changed.
 *
 * An applier holds one of these for its whole life and drives it from `onBeginChanges`/`onEndChanges`.
 * The section opens before the runtime drives the first change and closes once the widgets are up to date,
 * so the node update blocks the runtime runs in between - where a widget is written and read back - are
 * reported inside the batch they belong to rather than beside it.
 */
internal class ComponentUpdateBatch {
    /** The section covering the batch in flight. `null` while no batch is running. */
    private var section: AutoCloseable? = null

    /** The containers this batch changed, revalidated and repainted once when it ends. */
    private val changedContainers: MutableSet<Container> = Collections.newSetFromMap(IdentityHashMap())

    /**
     * Opens the section, first discarding what an earlier batch left behind. Called from the applier's
     * `onBeginChanges`.
     *
     * A change pass that fails partway through never reaches [end], because the runtime tells the applier
     * changes ended only where the pass ran to completion. Closing the stale section keeps that failed pass
     * from enclosing every pass that follows it on the same thread. Dropping the containers it marked keeps
     * the next batch from refreshing containers it never touched.
     */
    fun begin() {
        changedContainers.clear()
        section?.close()
        section = beginSection("apply")
    }

    /** Records that this batch changed [container]'s children, so it is brought up to date when the batch ends. */
    fun markChanged(container: Container) {
        changedContainers += container
    }

    /**
     * Runs [bringWidgetsUpToDate], brings the containers this batch changed up to date, and closes the
     * section whichever way that goes - a batch that ends by throwing still leaves no section open.
     *
     * Called from the applier's `onEndChanges`, which is the only place a batch ends.
     */
    fun end(bringWidgetsUpToDate: () -> Unit) {
        try {
            bringWidgetsUpToDate()
            refreshChangedContainers()
        } finally {
            changedContainers.clear()
            section?.close()
            section = null
        }
    }

    /**
     * Revalidates and repaints every container this batch changed.
     *
     * The set is cleared by [end], whichever way this goes. The walk is entered only where the batch
     * changed a container: a batch that just wrote widget properties takes no iterator over an empty
     * set. `repaint` is load-bearing for the remove case - `Container.remove` only calls
     * `invalidateIfValid` and never repaints the vacated region, so without it a removed child's pixels
     * linger. Relayout is already covered by `Component.reshape`.
     */
    private fun refreshChangedContainers() {
        if (changedContainers.isEmpty()) return
        for (container in changedContainers) {
            container.revalidate()
            container.repaint()
        }
    }
}
