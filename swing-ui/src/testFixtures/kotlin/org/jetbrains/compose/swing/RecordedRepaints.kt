package org.jetbrains.compose.swing

import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.RepaintManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A [RepaintManager] standing in for the real one, recording which component each request came from.
 *
 * A widget built inside the library cannot be subclassed to count the requests made on it, and every
 * `revalidate()` and `repaint()` a `JComponent` makes arrives here, named by the component that asked.
 * The component is recorded as it is passed, before the real manager walks up to the validate root, so
 * what stands here is who asked rather than who will be laid out.
 *
 * Install it with [withRecordedRepaints] or [repaintsDuring], which put the standing manager back
 * afterwards.
 *
 * @param serve what a repaint request is handed to, where a test needs the paint itself. The recorder
 * answers requests through it instead of passing them to the real manager, handing it the component that
 * asked. Requests arriving before the serving block runs are handed over together, the way Swing
 * coalesces the repaints one gesture makes, so a test counting paints counts the ones the user would see
 * rather than one per request. The block calls its argument once it has served the request.
 */
public class RecordedRepaints(
    private val serve: ((JComponent, served: () -> Unit) -> Unit)? = null,
) : RepaintManager() {
    /** The components asked for a layout pass, in the order they asked. */
    public val relayouts: MutableList<JComponent> = mutableListOf()

    private val dirtyRegions: MutableList<DirtyRegion> = mutableListOf()

    private var awaitingService = false

    /** The components asked to repaint, in the order they asked. */
    public val repaints: List<JComponent> get() = dirtyRegions.map { it.component }

    /** Every region asked for, with the component that asked and the size it had as it asked. */
    public val requests: List<RepaintRequest> get() = dirtyRegions.toList()

    override fun addInvalidComponent(component: JComponent) {
        relayouts += component
        super.addInvalidComponent(component)
    }

    override fun addDirtyRegion(
        component: JComponent,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        dirtyRegions += DirtyRegion(component, Rectangle(x, y, width, height), component.size)
        val serve = serve
        if (serve == null) {
            super.addDirtyRegion(component, x, y, width, height)
        } else if (component.width > 0 && component.height > 0 && !awaitingService) {
            // Only a component with an area is served: the real manager drops a request from one without, so
            // serving it would run a paint Swing never runs and the user never sees.
            awaitingService = true
            serve(component) { awaitingService = false }
        }
    }

    /** How many repaints [component] itself asked for. */
    public fun repaintsOf(component: JComponent): Int = dirtyRegionsOf(component).size

    /** The regions [component] itself asked to repaint, in its own coordinates, in the order it asked. */
    public fun dirtyRegionsOf(component: JComponent): List<Rectangle> =
        dirtyRegions.filter { it.component === component }.map { it.region }

    /** The size [component] had as it asked for each of its repaints, in the order it asked. */
    public fun sizesAskedAt(component: JComponent): List<Dimension> =
        dirtyRegions.filter { it.component === component }.map { it.size }

    /** How many layout passes were asked for over [component], by it or by a container holding it. */
    public fun relayoutsOver(component: JComponent): Int =
        relayouts.count { it === component || it.isAncestorOf(component) }

    /** Drops what has been recorded, so the next change is measured on its own. */
    public fun forget() {
        relayouts.clear()
        dirtyRegions.clear()
    }
}

/** One repaint request: who asked, the region it asked for, and the size it had as it asked. */
public interface RepaintRequest {
    /** The component that asked. */
    public val component: JComponent

    /** The region asked for, in [component]'s own coordinates. */
    public val region: Rectangle
}

private class DirtyRegion(
    override val component: JComponent,
    override val region: Rectangle,
    val size: Dimension,
) : RepaintRequest

/**
 * Runs [body] with [recorded] standing in for the repaint manager, and puts the standing one back
 * afterwards. Call it once the composition has mounted, so the requests the mount itself makes are
 * served by the real manager and counted against nothing.
 */
public inline fun <T> withRecordedRepaints(
    recorded: RecordedRepaints = RecordedRepaints(),
    body: (RecordedRepaints) -> T,
): T {
    val standing = RepaintManager.currentManager(null)
    RepaintManager.setCurrentManager(recorded)
    try {
        return body(recorded)
    } finally {
        RepaintManager.setCurrentManager(standing)
    }
}

/** What [change] asked for, and nothing else. */
public fun repaintsDuring(
    recorded: RecordedRepaints = RecordedRepaints(),
    change: () -> Unit,
): RecordedRepaints =
    withRecordedRepaints(recorded) {
        change()
        it
    }

/** Asserts [component] asked for a layout pass; [change] names what was expected to provoke it. */
public fun RecordedRepaints.assertAskedForLayout(
    component: JComponent,
    change: String,
): Unit =
    assertTrue(
        component in relayouts,
        "$change must ask the ${component.javaClass.simpleName} for a layout pass. Asked: ${named(relayouts)}",
    )

/** Asserts nothing asked [component] to be laid out again; [change] names what was expected to leave it alone. */
public fun RecordedRepaints.assertAskedForNoLayout(
    component: JComponent,
    change: String,
): Unit =
    assertEquals(
        0,
        relayoutsOver(component),
        "$change must leave the ${component.javaClass.simpleName} laid out as it stands. Asked: ${named(relayouts)}",
    )

/**
 * Asserts [component] asked to repaint, and that each region it asked for is the whole of the bounds it
 * had as it asked; [change] names what was expected to provoke it.
 */
public fun RecordedRepaints.assertAskedToRepaint(
    component: JComponent,
    change: String,
) {
    val name = component.javaClass.simpleName
    val sizes = sizesAskedAt(component)
    assertTrue(sizes.isNotEmpty(), "$change must ask the $name to repaint. Asked: ${named(repaints)}")
    assertTrue(
        sizes.all { it.width > 0 && it.height > 0 },
        "the $name had no size as it asked, so its region pins nothing",
    )
    assertEquals(
        sizes.map { Rectangle(0, 0, it.width, it.height) },
        dirtyRegionsOf(component),
        "$change must ask the $name to repaint the whole of its bounds",
    )
}

private fun named(components: List<JComponent>): List<String> = components.map { it.javaClass.simpleName }
