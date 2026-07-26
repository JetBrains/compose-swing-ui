package org.jetbrains.compose.swing.core

import androidx.compose.runtime.snapshots.SnapshotStateObserver
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JPanel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * The AWT component-array order [SwingApplier] produces always matches the composition insertion
 * index, for BOTH constrained and unconstrained children. Everything else the applier does addresses
 * that array by index - [SwingApplier.remove] and [SwingApplier.move] included - so a constrained add
 * that appended instead of inserting at its index would leave every later index pointing at the wrong
 * component.
 */
class SwingApplierArrayOrderTest {
    private fun SwingApplier.onContainer(
        holder: SwingNodeHolder<*>,
        block: SwingApplier.() -> Unit,
    ) {
        down(holder)
        block()
        up()
    }

    private fun namedButton(name: String): JButton = JButton(name).apply { this.name = name }

    private fun childNames(container: Container): List<String> = container.components.map { it.name }

    private fun constrainedHolder(
        component: Component,
        constraint: Any,
    ): SwingNodeHolder<*> = SwingNodeHolder(component).also { it.applyConstraint(constraint) }

    private fun holder(component: Component): SwingNodeHolder<*> = SwingNodeHolder(component)

    /** Observers created for the appliers under test, disposed in [disposeObservers]. */
    private val observers = mutableListOf<SnapshotStateObserver>()

    /**
     * Builds a [SwingApplier] over [root] with a snapshot observer this test owns and disposes, so the
     * global apply-observer registration the applier starts is torn down at test end rather than
     * leaked (the production path disposes it with the composition mount).
     */
    private fun applierFor(root: Container): SwingApplier {
        val observer = SnapshotStateObserver { it() }.apply { start() }
        observers += observer
        return SwingApplier(root, observer)
    }

    @AfterTest
    fun disposeObservers() {
        observers.forEach { it.stop() }
        observers.clear()
    }

    /**
     * A BorderLayout container receives a mix of constrained and unconstrained children at ascending
     * composition indices. The component-array order must equal the insertion order, proving the
     * constrained adds inserted at their index rather than appending.
     */
    @Test
    fun componentArrayOrderMatchesInsertionIndexForMixedAdds() {
        val root = JPanel(BorderLayout())
        val applier = applierFor(root)

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            // index 0: constrained NORTH
            insertBottomUp(0, constrainedHolder(namedButton("north"), BorderLayout.NORTH))
            // index 1: constrained CENTER
            insertBottomUp(1, constrainedHolder(namedButton("center"), BorderLayout.CENTER))
            // index 2: constrained SOUTH
            insertBottomUp(2, constrainedHolder(namedButton("south"), BorderLayout.SOUTH))
        }
        applier.onEndChanges()

        // Array order == composition order, NOT BorderLayout's internal region order.
        assertEquals(listOf("north", "center", "south"), childNames(root), "array order should match composition order")
        // And each constraint really was applied (the 3-arg add did both jobs).
        val layout = root.layout as BorderLayout
        assertEquals(
            BorderLayout.NORTH,
            layout.getConstraints(root.getComponent(0)),
            "child 0 should carry the NORTH constraint",
        )
        assertEquals(
            BorderLayout.CENTER,
            layout.getConstraints(root.getComponent(1)),
            "child 1 should carry the CENTER constraint",
        )
        assertEquals(
            BorderLayout.SOUTH,
            layout.getConstraints(root.getComponent(2)),
            "child 2 should carry the SOUTH constraint",
        )
    }

    /**
     * A BorderLayout container that starts WITHOUT a north child, then gets one inserted at
     * composition index 0. The new child must land at array index 0 rather than at the end, so every
     * subsequent index-based operation still addresses the child its composition index names.
     */
    @Test
    fun constrainedInsertAtIndexZeroLandsFirstInArrayNotAppended() {
        val root = JPanel(BorderLayout())
        val applier = applierFor(root)

        // Start with [center, south] (north slot absent), matching the BorderPanel slot order when
        // the conditional north is off - north occupies no composition index.
        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            insertBottomUp(0, constrainedHolder(namedButton("center"), BorderLayout.CENTER))
            insertBottomUp(1, constrainedHolder(namedButton("south"), BorderLayout.SOUTH))
        }
        applier.onEndChanges()
        assertEquals(listOf("center", "south"), childNames(root), "the container should start without a north child")

        // Now the north slot turns on: Compose inserts it at composition index 0.
        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            insertBottomUp(0, constrainedHolder(namedButton("north"), BorderLayout.NORTH))
        }
        applier.onEndChanges()

        assertEquals(
            listOf("north", "center", "south"),
            childNames(root),
            "the index-0 insert should land first, not append",
        )
    }

    /**
     * `remove(0, 1)` drops the composition-index-0 child even in a BorderLayout container, whose own
     * region order differs from the array order.
     */
    @Test
    fun removeIndexZeroRemovesCompositionIndexZeroChildInBorderLayout() {
        val root = JPanel(BorderLayout())
        val applier = applierFor(root)
        val north = namedButton("north")
        val center = namedButton("center")
        val south = namedButton("south")

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            insertBottomUp(0, constrainedHolder(north, BorderLayout.NORTH))
            insertBottomUp(1, constrainedHolder(center, BorderLayout.CENTER))
            insertBottomUp(2, constrainedHolder(south, BorderLayout.SOUTH))
        }
        applier.onEndChanges()
        assertEquals(
            listOf("north", "center", "south"),
            childNames(root),
            "all three children should be present before removal",
        )

        // Remove composition index 0 (the NORTH child).
        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            remove(0, 1)
        }
        applier.onEndChanges()

        // NORTH is gone; CENTER and SOUTH remain with identity and constraints intact.
        assertEquals(listOf("center", "south"), childNames(root), "remove(0) should drop the NORTH child")
        val layout = root.layout as BorderLayout
        assertSame(center, root.getComponent(0), "the CENTER instance should remain at array index 0")
        assertSame(south, root.getComponent(1), "the SOUTH instance should remain at array index 1")
        assertEquals(BorderLayout.CENTER, layout.getConstraints(center), "the CENTER child should keep its constraint")
        assertEquals(BorderLayout.SOUTH, layout.getConstraints(south), "the SOUTH child should keep its constraint")
        // The removed NORTH child no longer has any constraint association.
        assertNull(layout.getConstraints(north), "the removed NORTH child should have no constraint association")
    }

    /**
     * Mixed constrained + unconstrained adds interleaved by index must still produce array order ==
     * composition order (the unconstrained ones also insert at their index, not append).
     */
    @Test
    fun mixedConstrainedAndUnconstrainedKeepArrayOrder() {
        val root = JPanel(BorderLayout())
        val applier = applierFor(root)

        applier.onBeginChanges()
        applier.onContainer(applier.root) {
            insertBottomUp(0, constrainedHolder(namedButton("north"), BorderLayout.NORTH))
            insertBottomUp(1, holder(namedButton("plain"))) // unconstrained
            insertBottomUp(2, constrainedHolder(namedButton("south"), BorderLayout.SOUTH))
        }
        applier.onEndChanges()

        assertEquals(listOf("north", "plain", "south"), childNames(root))
    }
}
